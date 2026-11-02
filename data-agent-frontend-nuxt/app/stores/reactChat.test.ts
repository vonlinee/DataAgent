import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { ChatMessage } from '~/services/chat/index';

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

import { useReActChatStore } from './reactChat';

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
	});
});
