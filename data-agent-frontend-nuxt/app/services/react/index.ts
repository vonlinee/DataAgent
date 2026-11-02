import graphService, {
	type GraphRequest,
	type StreamResponse,
} from '../graph/index';

export enum ReActEventType {
	THINKING = 'THINKING',
	TOOL_CALL = 'TOOL_CALL',
	TOOL_RESULT = 'TOOL_RESULT',
	FINAL_ANSWER = 'FINAL_ANSWER',
	ERROR = 'ERROR',
}

export interface ReActStreamEvent extends StreamResponse {
	agentId: string;
	threadId: string;
	eventType?: ReActEventType;
	stepId?: string;
	attempt?: number;
	textType?: string;
	text?: string;
	error?: boolean;
	complete?: boolean;
}

class ReActService {
	streamSearch(
		request: GraphRequest,
		onMessage: (response: ReActStreamEvent) => Promise<void>,
		onError?: (error: Error) => Promise<void>,
		onComplete?: () => Promise<void>,
	) {
		return graphService.streamSearch<ReActStreamEvent>(
			request,
			onMessage,
			onError,
			onComplete,
		);
	}
}

export default new ReActService();
