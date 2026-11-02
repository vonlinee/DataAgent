import { computed, ref, watch } from 'vue';
import { defineStore } from 'pinia';
import chatService, { type ChatMessage } from '~/services/chat/index';
import type { GraphRequest } from '~/services/graph/index';
import reactService, {
	ReActEventType,
	type ReActStreamEvent,
} from '~/services/react/index';
import { useChatStore } from './chat';

export const useReActChatStore = defineStore('reactChat', () => {
	const chatStore = useChatStore();

	const isStreaming = ref(false);
	const stepBlocks = ref<ReActStreamEvent[][]>([]);
	const stepDurationMs = ref<number | undefined>();
	const lastRequest = ref<GraphRequest | null>(null);

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

		const userMessage: ChatMessage = {
			sessionId: session.id,
			role: 'user',
			content: query,
			messageType: 'text',
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
			threadId: undefined,
		});
	}

	async function streamMessage(request: GraphRequest) {
		const session = currentSession.value;
		if (!session) return;

		const sessionId = session.id;
		lastRequest.value = request;
		isStreaming.value = true;
		stepBlocks.value = [];
		stepDurationMs.value = undefined;
		const startedAt = Date.now();

		let currentStepId: string | null = null;
		let currentBlockIndex = -1;
		let finalReply: string | null = null;

		const syncView = () => {
			stepBlocks.value = [...stepBlocks.value];
		};

		const stream = await reactService.streamSearch(
			request,
			async (response) => {
				if (response.error) return;
				if (lastRequest.value) lastRequest.value.threadId = response.threadId;
				if (response.eventType === ReActEventType.FINAL_ANSWER) {
					finalReply = response.text?.trim() || null;
					return;
				}

				const responseStepId =
					response.stepId ||
					`${response.eventType || 'event'}:${response.attempt || 1}`;
				const isNewStep = currentStepId !== responseStepId;
				if (isNewStep) {
					stepBlocks.value.push([{ ...response }]);
					currentBlockIndex = stepBlocks.value.length - 1;
					currentStepId = responseStepId;
				} else {
					const currentBlock = stepBlocks.value[currentBlockIndex];
					if (currentBlock) currentBlock.push({ ...response });
				}
				syncView();
			},
			async (error) => {
				stepDurationMs.value = Date.now() - startedAt;
				if (stepBlocks.value.length > 0) {
					await chatService.saveMessage(sessionId, {
						sessionId,
						role: 'assistant',
						content: JSON.stringify({
							blocks: stepBlocks.value,
							durationMs: stepDurationMs.value,
						}),
						messageType: 'timeline',
					} as ChatMessage);
				}
				await chatService.saveMessage(sessionId, {
					sessionId,
					role: 'assistant',
					content: error.message || '请求失败，请检查网络连接并重试。',
					messageType: 'error',
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
						}),
						messageType: 'timeline',
					} as ChatMessage);
				}
				if (finalReply) {
					await chatService.saveMessage(sessionId, {
						sessionId,
						role: 'assistant',
						content: finalReply,
						messageType: 'text',
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
				} as ChatMessage);
			}
			isStreaming.value = false;
			stepBlocks.value = [];
			await chatService.saveMessage(session.id, {
				sessionId: session.id,
				role: 'assistant',
				content: '用户已终止本次对话。',
				messageType: 'warning',
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
		sendMessage,
		stopStreaming,
		clearRuntimeState,
		switchModel: chatStore.switchModel,
		switchDatasource: chatStore.switchDatasource,
	};
});
