import type { ReActStreamEvent } from '../services/react/index';

/** Combine streamed fragments that belong to the same ReAct step. */
export function aggregateThoughtText(block: ReActStreamEvent[]): string {
	return block.map((response) => response.text || '').join('');
}
