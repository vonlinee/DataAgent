import { describe, expect, it } from 'vitest';
import { ReActEventType, type ReActStreamEvent } from '../services/react/index';
import { aggregateThoughtText } from './reactThoughts';

function thoughtFragment(text: string): ReActStreamEvent {
	return {
		agentId: '4',
		threadId: 'run-1',
		stepId: 'thinking-1',
		attempt: 1,
		textType: 'TEXT',
		eventType: ReActEventType.THINKING,
		text,
		error: false,
		complete: false,
	};
}

describe('aggregateThoughtText', () => {
	it('preserves every streamed fragment in order', () => {
		expect(
			aggregateThoughtText([
				thoughtFragment('先分析'),
				thoughtFragment('天气信息'),
				thoughtFragment('，最后结束。'),
			]),
		).toBe('先分析天气信息，最后结束。');
	});
});
