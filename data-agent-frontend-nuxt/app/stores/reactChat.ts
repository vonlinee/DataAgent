import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import chatService, { type ChatMessage } from '~/services/chat/index';
import type { GraphRequest } from '~/services/graph/index';
import reactService, {
	ReActEventType,
	type ReActStreamEvent,
} from '~/services/react/index';
import { useChatStore } from './chat';

export interface ReActTraceSelection {
	messageId?: number;
	threadId?: string;
	blocks: ReActStreamEvent[][];
	durationMs?: number;
	finalText?: string;
	finalTimestamp?: number;
	selectedStepId?: string;
	messageLabel: string;
	userContent?: string;
	roundIndex?: number;
}

export const useReActChatStore = defineStore('reactChat', () => {
	const chatStore = useChatStore();

	const isStreaming = ref(false);
	const stepBlocks = ref<ReActStreamEvent[][]>([]);
	const stepDurationMs = ref<number | undefined>();
	const lastRequest = ref<GraphRequest | null>(null);
	const traceDrawerOpen = ref(false);
	const traceSelection = ref<ReActTraceSelection | null>(null);

	let closeStream: ((cancelRun?: boolean) => Promise<void>) | null = null;

	const currentSession = computed(() => chatStore.currentSession);
	const currentMessages = computed(() => chatStore.currentMessages);
	const currentAgentId = computed(() => chatStore.currentAgentId);
	const currentAgentName = computed(() => chatStore.currentAgentName);
	const currentAgentAvatar = computed(() => chatStore.currentAgentAvatar);
	const currentAgentDescription = computed(
		() => chatStore.currentAgentDescription,
	);
	const chatModels = computed(() => chatStore.chatModels);
	const activeModelConfig = computed(() => chatStore.activeModelConfig);
	const allDatasources = computed(() => chatStore.allDatasources);
	const activeDatasource = computed(() => chatStore.activeDatasource);
	const chatSidebarCollapsed = computed({
		get: () => chatStore.chatSidebarCollapsed,
		set: (value: boolean) => {
			chatStore.chatSidebarCollapsed = value;
		},
	});

	function clearRuntimeState(cancelBackend = false) {
		const stream = closeStream;
		closeStream = null;
		if (stream)
			void stream(cancelBackend).catch((error) => console.error(error));
		isStreaming.value = false;
		stepBlocks.value = [];
		stepDurationMs.value = undefined;
		lastRequest.value = null;
		traceDrawerOpen.value = false;
		traceSelection.value = null;
	}

	function createTraceThreadId() {
		if (
			typeof crypto !== 'undefined' &&
			typeof crypto.randomUUID === 'function'
		) {
			return crypto.randomUUID();
		}
		return `react-${Date.now()}-${Math.random().toString(36).slice(2)}`;
	}

	function parseMetadata(metadata?: string) {
		if (!metadata) return {} as Record<string, unknown>;
		try {
			const parsed = JSON.parse(metadata) as Record<string, unknown>;
			return parsed && typeof parsed === 'object' ? parsed : {};
		} catch {
			return {} as Record<string, unknown>;
		}
	}

	function traceThreadId(message?: ChatMessage) {
		const value = parseMetadata(message?.metadata).threadId;
		return typeof value === 'string' && value ? value : undefined;
	}

	function parseTimeline(content: string) {
		try {
			const parsed = JSON.parse(content) as
				| ReActStreamEvent[][]
				| { blocks?: ReActStreamEvent[][]; durationMs?: number; finalTimestamp?: number };
			if (Array.isArray(parsed))
				return { blocks: mergeToolCallResultBlocks(parsed), durationMs: undefined };
			return {
				blocks: Array.isArray(parsed.blocks)
					? mergeToolCallResultBlocks(parsed.blocks)
					: [],
				durationMs: parsed.durationMs,
				finalTimestamp: parsed.finalTimestamp,
			};
		} catch {
			return { blocks: [], durationMs: undefined };
		}
	}

	function mergeToolCallResultBlocks(blocks: ReActStreamEvent[][]) {
		const normalized = blocks.map((block) => [...block]);
		const pendingResultStarts: number[] = [];
		const legacyMergedIndexes = new Set<number>();

		for (let index = 0; index < normalized.length; index += 1) {
			const block = normalized[index];
			if (isLegacyToolResultStart(block)) {
				pendingResultStarts.push(index);
				continue;
			}
			if (pendingResultStarts.length > 0 && isToolResultContent(block)) {
				const startIndex = pendingResultStarts.shift() as number;
				normalized[startIndex] = [...normalized[startIndex], ...block];
				legacyMergedIndexes.add(index);
			}
		}

		const legacyNormalized = normalized.filter(
			(_block, index) => !legacyMergedIndexes.has(index),
		);
		const pendingStarts: number[] = [];
		const callIndexesByKey = new Map<string, number>();
		const mergedIndexes = new Set<number>();

		for (let index = 0; index < legacyNormalized.length; index += 1) {
			const block = legacyNormalized[index];
			if (block[0]?.eventType === ReActEventType.TOOL_CALL) {
				const key = toolCorrelationKey(block[0]?.stepId);
				if (key) callIndexesByKey.set(key, index);
				pendingStarts.push(index);
				continue;
			}
			if (block[0]?.eventType !== ReActEventType.TOOL_RESULT) continue;

			const key = toolCorrelationKey(block[0]?.stepId);
			const matchingIndex = key ? callIndexesByKey.get(key) : undefined;
			const callIndex = matchingIndex ?? pendingStarts.shift();
			if (callIndex === undefined) continue;
			const pendingPosition = pendingStarts.indexOf(callIndex);
			if (pendingPosition >= 0) pendingStarts.splice(pendingPosition, 1);

			legacyNormalized[callIndex] = [...legacyNormalized[callIndex], ...block];
			mergedIndexes.add(index);
			if (key) callIndexesByKey.delete(key);
		}

		return legacyNormalized.filter((_block, index) => !mergedIndexes.has(index));
	}

	function isLegacyToolResultStart(block: ReActStreamEvent[]) {
		return (
			block[0]?.eventType === ReActEventType.TOOL_RESULT &&
			block.length === 1 &&
			block[0]?.text?.trim() === '工具返回结果'
		);
	}

	function isToolResultContent(block: ReActStreamEvent[]) {
		return (
			block[0]?.eventType === ReActEventType.TOOL_RESULT &&
			block.some((event) => event.text?.trim() && event.text?.trim() !== '工具返回结果')
		);
	}

	function toolCorrelationKey(stepId?: string) {
		if (!stepId) return '';
		const separatorIndex = stepId.indexOf(':');
		return separatorIndex >= 0 ? stepId.slice(separatorIndex + 1) : stepId;
	}

	function findTimelineMessage(message: ChatMessage) {
		const messages = chatStore.currentMessages;
		const threadId = traceThreadId(message);
		if (threadId) {
			const exact = messages.find(
				(candidate) =>
					candidate.messageType === 'timeline' &&
					traceThreadId(candidate) === threadId,
			);
			if (exact) return exact;
		}

		const index = messages.indexOf(message);
		if (index < 0) return undefined;
		if (message.messageType === 'timeline') return message;
		if (message.role === 'user') {
			for (let i = index + 1; i < messages.length; i += 1) {
				const candidate = messages[i];
				if (candidate.role === 'user') break;
				if (candidate.messageType === 'timeline') return candidate;
			}
		} else {
			for (let i = index - 1; i >= 0; i -= 1) {
				const candidate = messages[i];
				if (candidate.role === 'user') break;
				if (candidate.messageType === 'timeline') return candidate;
			}
		}
		return undefined;
	}

	function findRelatedMessage(threadId: string | undefined, role: string, timeline: ChatMessage) {
		if (threadId) {
			const exact = chatStore.currentMessages.find(
				(candidate) =>
					candidate.role === role &&
					candidate.messageType === 'text' &&
					traceThreadId(candidate) === threadId,
			);
			if (exact) return exact;
		}

		const index = chatStore.currentMessages.indexOf(timeline);
		if (index < 0) return undefined;
		const direction = role === 'user' ? -1 : 1;
		for (let i = index + direction; i >= 0 && i < chatStore.currentMessages.length; i += direction) {
			const candidate = chatStore.currentMessages[i];
			if (candidate.role === role && candidate.messageType === 'text') return candidate;
			if (candidate.role === 'user' && role !== 'user') break;
		}
		return undefined;
	}

	const traceRounds = computed<ReActTraceSelection[]>(() => {
		const rounds = chatStore.currentMessages
			.filter((message) => message.messageType === 'timeline')
			.map((timelineMessage, index) => {
				const timeline = parseTimeline(timelineMessage.content);
				if (timeline.blocks.length === 0) return undefined;
				const threadId =
					traceThreadId(timelineMessage) || timeline.blocks[0]?.[0]?.threadId;
				const userMessage = findRelatedMessage(threadId, 'user', timelineMessage);
				const finalMessage = findRelatedMessage(threadId, 'assistant', timelineMessage);
				return {
					messageId: timelineMessage.id,
					threadId,
					blocks: timeline.blocks,
					durationMs: timeline.durationMs,
					finalTimestamp: timeline.finalTimestamp,
					finalText:
						finalMessage?.messageType === 'text'
							? finalMessage.content
							: undefined,
					selectedStepId:
						timeline.blocks.find(
								(block) => block[0]?.eventType === ReActEventType.USER_MESSAGE,
							)?.[0]?.stepId || timeline.blocks[0]?.[0]?.stepId,
					messageLabel: `第 ${index + 1} 轮`,
					userContent: userMessage?.content,
					roundIndex: index,
				};
			})
			.filter((round): round is ReActTraceSelection => Boolean(round));

		if (isStreaming.value && stepBlocks.value.length > 0) {
			const userMessage = [...chatStore.currentMessages]
				.reverse()
				.find((message) => message.role === 'user');
			rounds.push({
				threadId: lastRequest.value?.threadId,
				blocks: mergeToolCallResultBlocks(stepBlocks.value),
				durationMs: stepDurationMs.value,
				finalTimestamp: undefined,
				selectedStepId: stepBlocks.value[0]?.[0]?.stepId,
				messageLabel: `第 ${rounds.length + 1} 轮`,
				userContent: userMessage?.content,
				roundIndex: rounds.length,
			});
		}

		return rounds;
	});

	function openTraceForMessage(message: ChatMessage) {
		const timelineMessage = findTimelineMessage(message);
		if (!timelineMessage) return;
		const timeline = parseTimeline(timelineMessage.content);
		if (timeline.blocks.length === 0) return;
		const threadId =
			traceThreadId(message) ||
			traceThreadId(timelineMessage) ||
			timeline.blocks[0]?.[0]?.threadId;
		const finalMessage =
			chatStore.currentMessages.find(
				(candidate) =>
					candidate.messageType === 'text' &&
					candidate.role === 'assistant' &&
					threadId !== undefined &&
					traceThreadId(candidate) === threadId,
			) ||
			(message.role === 'assistant' && message.messageType === 'text'
				? message
				: undefined);
		const selectedStepId =
			message.messageType === 'timeline'
				? timeline.blocks.at(-1)?.[0]?.stepId
				: message.role === 'user'
					? timeline.blocks.find(
							(block) => block[0]?.eventType === ReActEventType.USER_MESSAGE,
						)?.[0]?.stepId || timeline.blocks[0]?.[0]?.stepId
					: message.messageType === 'error' || message.messageType === 'warning'
						? timeline.blocks.find(
								(block) => block[0]?.eventType === ReActEventType.ERROR,
							)?.[0]?.stepId || timeline.blocks.at(-1)?.[0]?.stepId
						: 'final';

		traceSelection.value = {
			messageId: message.id,
			threadId,
			blocks: timeline.blocks,
			durationMs: timeline.durationMs,
			finalText: finalMessage?.content,
			selectedStepId,
			messageLabel:
				message.role === 'user'
					? '用户消息'
					: message.messageType === 'timeline'
						? '执行轨迹'
						: '最终回答',
		};
		traceDrawerOpen.value = true;
	}

	function openTraceOverview() {
		traceDrawerOpen.value = true;
		if (!traceSelection.value) {
			traceSelection.value = traceRounds.value.at(-1) || null;
		}
	}

	function selectTraceStep(round: ReActTraceSelection, stepId: string) {
		traceSelection.value = { ...round, selectedStepId: stepId };
		traceDrawerOpen.value = true;
	}

	function hasTrace(message: ChatMessage) {
		return Boolean(findTimelineMessage(message));
	}

	function closeTrace() {
		traceDrawerOpen.value = false;
	}

	watch(
		() => currentSession.value?.id,
		(sessionId, previousSessionId) => {
			if (sessionId !== previousSessionId) clearRuntimeState(true);
		},
	);

	async function sendMessage(query: string) {
		const session = currentSession.value;
		const agentId = currentAgentId.value;
		if (!session || !agentId || isStreaming.value) return;
		traceDrawerOpen.value = false;
		traceSelection.value = null;

		const threadId = createTraceThreadId();
		const userMessage: ChatMessage = {
			sessionId: session.id,
			role: 'user',
			content: query,
			messageType: 'text',
			metadata: JSON.stringify({ threadId }),
			titleNeeded: !session.title || session.title === '新会话',
		};
		const saved = await chatService.saveMessage(session.id, userMessage);
		chatStore.currentMessages.push(saved);

		await streamMessage({
			agentId: String(agentId),
			conversationId: session.id,
			query,
			datasourceId: chatStore.activeDatasource?.id,
			humanFeedback: false,
			humanFeedbackContent: undefined,
			rejectedPlan: false,
			nl2sqlOnly: false,
			threadId,
		});
	}

	async function streamMessage(request: GraphRequest) {
		const session = currentSession.value;
		if (!session) return;

		const sessionId = session.id;
		const traceThreadIdValue = request.threadId;
		lastRequest.value = request;
		isStreaming.value = true;
		stepBlocks.value = [];
		stepDurationMs.value = undefined;
		const startedAt = Date.now();

		let currentStepId: string | null = null;
		let currentBlockIndex = -1;
		let finalReply: string | null = null;
		let finalTimestamp: number | undefined;

		const syncView = () => {
			stepBlocks.value = [...stepBlocks.value];
		};

		const appendTraceEvent = (response: ReActStreamEvent) => {
			const responseStepId =
				response.stepId ||
				`${response.eventType || 'event'}:${response.attempt || 1}`;
			const isNewStep = currentStepId !== responseStepId;
			if (isNewStep) {
				stepBlocks.value.push([{ ...response, stepId: responseStepId }]);
				currentBlockIndex = stepBlocks.value.length - 1;
				currentStepId = responseStepId;
			} else {
				const currentBlock = stepBlocks.value[currentBlockIndex];
				if (currentBlock) currentBlock.push({ ...response, stepId: responseStepId });
			}
			syncView();
		};

		const appendErrorTrace = (message: string) => {
			appendTraceEvent({
				agentId: request.agentId,
				threadId: traceThreadIdValue || lastRequest.value?.threadId || '',
				eventType: ReActEventType.ERROR,
				stepId: 'error',
				attempt: 1,
				nodeName: 'Agent',
				textType: 'TEXT',
				text: message,
				error: true,
				complete: true,
			});
		};

		const stream = await reactService.streamSearch(
			request,
			async (response) => {
				if (lastRequest.value) lastRequest.value.threadId = response.threadId;
				if (response.error) {
					appendTraceEvent({
						...response,
						eventType: response.eventType || ReActEventType.ERROR,
						stepId: response.stepId || 'error',
						text: response.text || 'Agent 执行失败',
					});
					return;
				}
				if (response.eventType === ReActEventType.FINAL_ANSWER) {
					finalReply = response.text?.trim() || null;
					finalTimestamp = response.timestamp;
					return;
				}

				appendTraceEvent(response);
			},
			async (error) => {
				appendErrorTrace(error.message || '请求失败，请检查网络连接并重试。');
				stepDurationMs.value = Date.now() - startedAt;
				if (stepBlocks.value.length > 0) {
					await chatService.saveMessage(sessionId, {
						sessionId,
						role: 'assistant',
						content: JSON.stringify({
							blocks: stepBlocks.value,
							durationMs: stepDurationMs.value,
							finalTimestamp,
						}),
						messageType: 'timeline',
						metadata: JSON.stringify({ threadId: traceThreadIdValue }),
					} as ChatMessage);
				}
				await chatService.saveMessage(sessionId, {
					sessionId,
					role: 'assistant',
					content: error.message || '请求失败，请检查网络连接并重试。',
					messageType: 'error',
					metadata: JSON.stringify({ threadId: traceThreadIdValue }),
				} as ChatMessage);
				closeStream = null;
				isStreaming.value = false;
				currentStepId = null;
				chatStore.currentMessages =
					await chatService.getSessionMessages(sessionId);
			},
			async () => {
				stepDurationMs.value = Date.now() - startedAt;
				if (stepBlocks.value.length > 0) {
					await chatService.saveMessage(sessionId, {
						sessionId,
						role: 'assistant',
						content: JSON.stringify({
							blocks: stepBlocks.value,
							durationMs: stepDurationMs.value,
							finalTimestamp,
						}),
						messageType: 'timeline',
						metadata: JSON.stringify({ threadId: traceThreadIdValue }),
					} as ChatMessage);
				}
				if (finalReply) {
					await chatService.saveMessage(sessionId, {
						sessionId,
						role: 'assistant',
						content: finalReply,
						messageType: 'text',
						metadata: JSON.stringify({ threadId: traceThreadIdValue }),
					} as ChatMessage);
				}
				isStreaming.value = false;
				const activeStream = closeStream;
				closeStream = null;
				if (activeStream) await activeStream();
				chatStore.currentMessages =
					await chatService.getSessionMessages(sessionId);
				stepBlocks.value = [];
			},
		);
		closeStream = stream;
	}

	async function stopStreaming() {
		const stream = closeStream;
		closeStream = null;
		const session = currentSession.value;
		if (!stream || !session) return;
		const traceThreadIdValue = lastRequest.value?.threadId;

		try {
			await stream(true);
		} finally {
			if (stepBlocks.value.length > 0) {
				await chatService.saveMessage(session.id, {
					sessionId: session.id,
					role: 'assistant',
					content: JSON.stringify({
						blocks: stepBlocks.value,
						durationMs: stepDurationMs.value,
					}),
					messageType: 'timeline',
					metadata: JSON.stringify({ threadId: traceThreadIdValue }),
				} as ChatMessage);
			}
			isStreaming.value = false;
			stepBlocks.value = [];
			await chatService.saveMessage(session.id, {
				sessionId: session.id,
				role: 'assistant',
				content: '用户已终止本次对话。',
				messageType: 'warning',
				metadata: JSON.stringify({ threadId: traceThreadIdValue }),
			} as ChatMessage);
			chatStore.currentMessages = await chatService.getSessionMessages(
				session.id,
			);
		}
	}

	return {
		currentSession,
		currentMessages,
		currentAgentId,
		currentAgentName,
		currentAgentAvatar,
		currentAgentDescription,
		chatModels,
		activeModelConfig,
		allDatasources,
		activeDatasource,
		chatSidebarCollapsed,
		isStreaming,
		stepBlocks,
		stepDurationMs,
		lastRequest,
		traceDrawerOpen,
		traceSelection,
		traceRounds,
		sendMessage,
		stopStreaming,
		clearRuntimeState,
		openTraceForMessage,
		openTraceOverview,
		selectTraceStep,
		hasTrace,
		closeTrace,
		switchModel: chatStore.switchModel,
		switchDatasource: chatStore.switchDatasource,
	};
});
