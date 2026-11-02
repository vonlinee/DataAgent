import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import graphService from './index';

type EventHandler = (event: Event | MessageEvent) => void | Promise<void>;

class MockEventSource {
	static readonly CLOSED = 2;

	static instances: MockEventSource[] = [];

	readonly url: string;

	readyState = 1;

	onmessage: EventHandler = () => undefined;

	onerror: EventHandler = () => undefined;

	private readonly listeners = new Map<string, EventHandler[]>();

	constructor(url: string) {
		this.url = url;
		MockEventSource.instances.push(this);
	}

	addEventListener(type: string, listener: EventHandler) {
		const handlers = this.listeners.get(type) || [];
		handlers.push(listener);
		this.listeners.set(type, handlers);
	}

	close() {
		this.readyState = MockEventSource.CLOSED;
	}

	async emitMessage(data: object) {
		await this.onmessage(
			new MessageEvent('message', { data: JSON.stringify(data) }),
		);
	}

	async emitConnectionError() {
		this.readyState = 1;
		await this.onerror(new Event('error'));
	}
}

describe('GraphService.streamSearch', () => {
	beforeEach(() => {
		MockEventSource.instances = [];
		vi.stubGlobal('EventSource', MockEventSource);
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('finishes normally when the terminal message is followed by connection close', async () => {
		const onMessage = vi.fn(async () => undefined);
		const onError = vi.fn(async () => undefined);
		const onComplete = vi.fn(async () => undefined);

		await graphService.streamSearch(
			{
				agentId: '4',
				conversationId: 'conversation-1',
				query: 'hello',
				humanFeedback: false,
				rejectedPlan: false,
				nl2sqlOnly: false,
			},
			onMessage,
			onError,
			onComplete,
		);

		const eventSource = MockEventSource.instances[0];
		expect(eventSource).toBeDefined();
		if (!eventSource) return;
		await eventSource.emitMessage({
			agentId: '4',
			threadId: 'run-1',
			eventType: 'FINAL_ANSWER',
			stepId: 'final',
			attempt: 1,
			nodeName: 'FinalAnswer',
			textType: 'MARK_DOWN',
			text: 'answer',
			error: false,
			complete: true,
		});
		await eventSource.emitConnectionError();

		expect(onMessage).toHaveBeenCalledOnce();
		expect(onComplete).toHaveBeenCalledOnce();
		expect(onError).not.toHaveBeenCalled();
	});

	it('includes the selected datasource id in the stream request', async () => {
		await graphService.streamSearch(
			{
				agentId: '4',
				conversationId: 'conversation-1',
				query: 'hello',
				datasourceId: 12,
				humanFeedback: false,
				rejectedPlan: false,
				nl2sqlOnly: false,
			},
			async () => undefined,
		);

		expect(MockEventSource.instances[0]?.url).toContain('datasourceId=12');
	});
});
