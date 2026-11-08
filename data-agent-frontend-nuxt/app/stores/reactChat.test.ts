import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { ChatMessage } from '~/services/chat/index';
import { useReActChatStore } from './reactChat';

const mocks = vi.hoisted(() => ({
	chatStore: {
		currentSession: { id: 'session-1', title: '测试会话' },
		currentMessages: [] as ChatMessage[],
		currentAgentId: 1,
		currentAgentName: '测试智能体',
		currentAgentAvatar: undefined,
		currentAgentDescription: '',
		chatModels: [],
		activeModelConfig: undefined,
		allDatasources: [],
		activeDatasource: undefined,
		chatSidebarCollapsed: false,
		switchModel: vi.fn(),
		switchDatasource: vi.fn(),
	},
	saveMessage: vi.fn(),
	getSessionMessages: vi.fn(),
	streamSearch: vi.fn(),
}));

vi.mock('~/services/chat/index', () => ({
	default: {
		saveMessage: mocks.saveMessage,
		getSessionMessages: mocks.getSessionMessages,
	},
}));

vi.mock('~/services/react/index', () => ({
	ReActEventType: {
		CONTEXT: 'CONTEXT',
		USER_MESSAGE: 'USER_MESSAGE',
		ASSISTANT: 'ASSISTANT',
		THINKING: 'THINKING',
		TOOL_CALL: 'TOOL_CALL',
		TOOL_RESULT: 'TOOL_RESULT',
		FINAL_ANSWER: 'FINAL_ANSWER',
		ERROR: 'ERROR',
	},
	default: {
		streamSearch: mocks.streamSearch,
	},
}));

vi.mock('./chat', () => ({
	useChatStore: () => mocks.chatStore,
}));

describe('useReActChatStore', () => {
	beforeEach(() => {
		setActivePinia(createPinia());
		mocks.chatStore.currentMessages = [];
		mocks.saveMessage.mockReset();
		mocks.getSessionMessages.mockReset();
		mocks.streamSearch.mockReset();

		const persistedMessages: ChatMessage[] = [];
		mocks.saveMessage.mockImplementation(
			async (_sessionId: string, message: ChatMessage) => {
				const saved = { ...message, id: persistedMessages.length + 1 };
				persistedMessages.push(saved);
				return saved;
			},
		);
		mocks.getSessionMessages.mockResolvedValue(persistedMessages);
		mocks.streamSearch.mockImplementation(async (_request, onMessage) => {
			await onMessage({
				agentId: 'agent-1',
				threadId: 'thread-1',
				eventType: 'CONTEXT',
				stepId: 'context',
				text: '系统提示词',
			});
			await onMessage({
				agentId: 'agent-1',
				threadId: 'thread-1',
				eventType: 'USER_MESSAGE',
				stepId: 'user-message',
				text: '分析上个月的营业额',
			});
			await onMessage({
				agentId: 'agent-1',
				threadId: 'thread-1',
				eventType: 'THINKING',
				stepId: 'step-1',
				text: '已经输出的思考内容',
			});
			return vi.fn().mockResolvedValue(undefined);
		});
	});

	it('keeps streamed ReAct output when the user stops the run', async () => {
		const store = useReActChatStore();

		await store.sendMessage('分析上个月的营业额');
		expect(store.traceRounds).toHaveLength(1);
		await store.stopStreaming();

		const savedMessages = mocks.saveMessage.mock.calls.map(
			([, message]) => message as ChatMessage,
		);
		const timeline = savedMessages.find(
			(message) => message.messageType === 'timeline',
		);
		const warning = savedMessages.find(
			(message) => message.messageType === 'warning',
		);

		expect(timeline).toBeDefined();
		expect(timeline?.content).toContain('已经输出的思考内容');
		expect(warning?.content).toBe('用户已终止本次对话。');

		const userMessage = mocks.chatStore.currentMessages.find(
			(message) => message.role === 'user',
		);
		expect(userMessage?.metadata).toContain('threadId');
		expect(userMessage && store.hasTrace(userMessage)).toBe(true);
		if (userMessage) store.openTraceForMessage(userMessage);
		expect(store.traceDrawerOpen).toBe(true);
		expect(store.traceSelection?.selectedStepId).toBe('user-message');
	});

	it('persists a stream error as a trace message', async () => {
		mocks.streamSearch.mockImplementationOnce(
			async (_request, _onMessage, onError) => {
				await onError?.(new Error('工具执行失败'));
				return vi.fn().mockResolvedValue(undefined);
			},
		);

		const store = useReActChatStore();
		await store.sendMessage('执行查询');

		const savedMessages = mocks.saveMessage.mock.calls.map(
			([, message]) => message as ChatMessage,
		);
		const timeline = savedMessages.find(
			(message) => message.messageType === 'timeline',
		);
		const errorMessage = savedMessages.find(
			(message) => message.messageType === 'error',
		);

		expect(timeline?.content).toContain('"eventType":"ERROR"');
		expect(errorMessage?.content).toBe('工具执行失败');
	});

	it('merges legacy split tool result blocks', () => {
		const store = useReActChatStore();
		mocks.chatStore.currentMessages = [
			{
				id: 1,
				sessionId: 'session-1',
				role: 'assistant',
				messageType: 'timeline',
				content: JSON.stringify({
					blocks: [
						[
							{
								eventType: 'TOOL_CALL',
								stepId: 'tool-call:call-1',
								text: '{"sql":"select 1"}',
							},
						],
						[
							{
								eventType: 'TOOL_RESULT',
								stepId: 'tool-result:reply-1',
								text: '工具返回结果',
							},
						],
						[
							{
								eventType: 'TOOL_RESULT',
								stepId: 'tool-result:reply-1',
								text: '{"rows":[{"count":1}]}',
							},
						],
					],
				}),
			} as ChatMessage,
		];

		store.openTraceOverview();

		expect(store.traceRounds[0]?.blocks).toHaveLength(1);
		expect(store.traceRounds[0]?.blocks[0]).toHaveLength(3);
	});
});
