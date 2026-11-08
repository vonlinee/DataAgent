<template>
	<section class="trace-panel" aria-label="ReAct 消息轨迹">
		<div class="trace-toolbar">
			<div class="trace-toolbar-actions">
				<button type="button" class="trace-toolbar-button trace-toolbar-button--active">
					<v-icon size="15">mdi-clock-outline</v-icon>
					<span>时长</span>
				</button>
				<button
					type="button"
					class="trace-toolbar-button"
					:class="{ 'trace-toolbar-button--active': allRoundsExpanded }"
					:aria-pressed="allRoundsExpanded"
					@click="toggleAllRounds"
				>
					<v-icon size="15">
						{{ allRoundsExpanded ? 'mdi-minus-box-outline' : 'mdi-plus-box-outline' }}
					</v-icon>
					<span>轮次</span>
				</button>
				<button type="button" class="trace-toolbar-button">
					<v-icon size="15">mdi-minus-box-outline</v-icon>
					<span>调用</span>
				</button>
			</div>
			<div class="trace-toolbar-stats trace-panel-stats">
				<div>
					<span>轮次</span>
					<strong>{{ rounds.length }}</strong>
				</div>
				<div>
					<span>当前消息</span>
					<strong>{{ traceMessages.length }}</strong>
				</div>
			</div>
			<label class="trace-search">
				<v-icon size="16">mdi-magnify</v-icon>
				<input v-model="searchQuery" type="search" placeholder="搜索" aria-label="搜索轨迹" />
			</label>
		</div>

		<div v-if="rounds.length" class="trace-overview">
			<div class="trace-overview-labels">
				<span>输入</span>
				<span>模型</span>
				<span>工具</span>
			</div>
			<div
				class="trace-overview-track"
				@wheel.prevent="zoomTimeline"
				@pointerdown="beginTimeSelection"
				@pointermove="updateHoverPosition"
				@pointerenter="hoverAxisPercent = null"
				@pointerleave="hoverAxisPercent = null"
				@pointerup="finishTimeSelection"
				@pointercancel="cancelTimeSelection"
			>
				<span
					v-for="bar in overviewBars"
					:key="bar.key"
					class="trace-overview-bar"
					:class="[
						`trace-overview-bar--${bar.kind.toLowerCase()}`,
						{
							'trace-overview-bar--faded': hasTimeSelection && !isBarSelected(bar),
							'trace-overview-bar--selected': isBarSelected(bar),
						},
					]"
					:style="{ left: `${bar.left}%`, width: `${bar.width}%` }"
					:aria-label="bar.title"
					:data-trace-bar-key="bar.key"
				/>
				<span
					v-if="activeTimeRange"
					class="trace-overview-selection"
					:style="{
						left: `${activeTimeRange.start}%`,
						width: `${activeTimeRange.end - activeTimeRange.start}%`,
					}"
				/>
				<span
					v-if="hoverDisplayPercent !== null"
					class="trace-overview-cursor"
					:style="{ left: `${hoverDisplayPercent}%` }"
				>
					<span class="trace-overview-cursor-label">{{ hoverTimeLabel }}</span>
				</span>
			</div>
		</div>

		<div
			v-if="rounds.length"
			class="trace-workspace"
			:class="{
				'trace-workspace--detail-closed': !detailOpen,
				'trace-workspace--resizing': isResizing,
			}"
			:style="{ '--trace-detail-width': `${detailWidthPercent}%` }"
		>
			<div ref="traceRoundsContainer" class="trace-rounds">
				<div class="trace-list-heading">
					<span>执行轮次</span>
					<span>{{ rounds.length }} 轮</span>
				</div>
				<section
					v-for="(round, roundIndex) in rounds"
					:key="roundKey(round)"
					class="trace-round"
					:class="{
						'trace-round--selected': roundKey(round) === selectedRoundKey,
						'trace-round--expanded': isExpanded(round),
					}"
				>
					<button
						type="button"
						class="trace-round-header"
						:aria-expanded="isExpanded(round)"
						@click="toggleRound(round)"
					>
						<span class="trace-round-heading">
							<span class="trace-round-index">{{ roundIndex + 1 }}</span>
							<span class="trace-round-copy">
								<strong>第 {{ roundIndex + 1 }} 轮</strong>
								<span>{{ round.userContent || 'Agent 执行轨迹' }}</span>
							</span>
						</span>
						<span class="trace-round-meta">
							<span>{{ formatDuration(round.durationMs) }}</span>
							<v-icon size="18">
								{{ isExpanded(round) ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
							</v-icon>
						</span>
					</button>

					<div v-if="isExpanded(round)" class="trace-round-messages">
						<button
							v-for="(item, itemIndex) in filteredMessagesForRound(round)"
							:key="item.key"
							:ref="(element) => setMessageRowRef(item.key, element)"
							type="button"
							class="trace-message-row"
							:class="{
								'trace-message-row--selected':
									roundKey(round) === selectedRoundKey &&
										item.stepId === selection?.selectedStepId,
								'trace-message-row--time-selected': isMessageTimeSelected(item),
								'trace-message-row--time-faded':
									hasTimeSelection && !isMessageTimeSelected(item),
							}"
							@click="selectItem(round, item)"
						>
							<span class="trace-message-index">{{ itemIndex + 1 }}</span>
							<span
								class="trace-message-kind"
								:class="`trace-message-kind--${messageTypeClass(item.kind)}`"
							>
								{{ messageTypeLabel(item.kind) }}
							</span>
							<span class="trace-message-copy">
								<span class="trace-message-preview">{{ preview(item.content) }}</span>
							</span>
							<v-icon size="16" class="trace-message-arrow">mdi-chevron-right</v-icon>
						</button>
						<p
							v-if="filteredMessagesForRound(round).length === 0"
							class="trace-round-empty"
						>
							未找到匹配消息
						</p>
					</div>
				</section>
			</div>

			<div
				v-if="detailOpen"
				class="trace-resizer"
				role="separator"
				tabindex="0"
				aria-label="调整消息列表和详情宽度"
				aria-orientation="vertical"
				:aria-valuemin="25"
				:aria-valuemax="60"
				:aria-valuenow="Math.round(detailWidthPercent)"
				@pointerdown="beginResize"
				@pointermove="updateResize"
				@pointerup="endResize"
				@pointercancel="endResize"
				@keydown="resizeWithKeyboard"
			/>

			<article v-if="detailOpen && selectedItem" class="trace-detail">
				<header class="trace-detail-header">
					<div>
						<p class="trace-eyebrow">第 {{ selectedRoundIndex + 1 }} 轮</p>
						<h3>{{ selectedItem.title }}</h3>
					</div>
					<div class="trace-detail-actions">
						<span class="trace-detail-source">{{ selectedItem.source }}</span>
						<button
							type="button"
							class="trace-detail-close"
							title="关闭消息详情"
							aria-label="关闭消息详情"
							@click="closeDetail"
						>
							<v-icon size="18">mdi-close</v-icon>
						</button>
					</div>
				</header>
				<nav class="trace-detail-tabs" aria-label="消息详情视图">
					<button
						v-for="tab in visibleDetailTabs"
						:key="tab.value"
						type="button"
						class="trace-detail-tab"
						:class="{ 'trace-detail-tab--active': activeDetailTab === tab.value }"
						:aria-selected="activeDetailTab === tab.value"
						role="tab"
						@click="activeDetailTab = tab.value"
					>
						{{ tab.label }}
					</button>
				</nav>
				<div
					v-if="['overview', 'params', 'result'].includes(activeDetailTab)"
					class="trace-detail-body"
				>
					<div v-if="activeDetailTab === 'overview'" class="trace-detail-meta">
						<span>完整消息</span>
						<span>状态：已完成</span>
						<span v-if="selectedItem.attempt">第 {{ selectedItem.attempt }} 次</span>
						<code v-if="selection?.threadId">{{ shortId(selection.threadId) }}</code>
					</div>
					<p v-else class="trace-detail-label">
						{{ activeDetailTab === 'params' ? '调用参数' : '返回结果' }}
					</p>
					<div class="trace-content-viewer" :class="`trace-content-viewer--${contentView.kind}`">
						<div v-if="contentView.kind === 'markdown'" class="trace-markdown-content" v-html="contentView.html" />
						<div v-else-if="contentView.kind === 'code'" class="trace-code-panel">
							<div class="trace-code-header">
								<span>{{ contentView.languageLabel }}</span>
								<button type="button" class="trace-code-copy" title="复制代码" aria-label="复制代码" @click="copyContent">
									<v-icon size="15">mdi-content-copy</v-icon>
									<span>{{ copiedContent ? '已复制' : '复制' }}</span>
								</button>
							</div>
							<pre class="trace-detail-content trace-code-content hljs"><code v-html="contentView.html" /></pre>
						</div>
						<pre v-else-if="contentView.text" class="trace-detail-content">{{ contentView.text }}</pre>
						<p v-else class="trace-content-empty">{{ detailEmptyMessage }}</p>
					</div>
				</div>
				<div v-else-if="activeDetailTab === 'schema'" class="trace-detail-body">
					<p class="trace-detail-label">Schema</p>
					<pre class="trace-detail-content">{{ schemaContent }}</pre>
				</div>
				<div v-else class="trace-detail-body">
					<p class="trace-detail-label">执行计时</p>
					<div class="trace-timing-grid">
						<span>轮次耗时</span><strong>{{ formatDuration(selection?.durationMs) }}</strong>
						<span>消息序号</span><strong>{{ selectedMessageIndex }}</strong>
						<span>执行状态</span><strong>已完成</strong>
					</div>
				</div>
			</article>
			<div v-else-if="detailOpen" class="trace-detail trace-detail--empty">
				<v-icon size="28" color="#9aa6b8">mdi-cursor-default-click-outline</v-icon>
				<p>点击左侧消息查看完整内容</p>
			</div>
		</div>

		<div v-else class="trace-empty">
			<v-icon size="30" color="#9aa6b8">mdi-timeline-outline</v-icon>
			<strong>暂无消息轨迹</strong>
			<p>完成一次 ReAct 对话后，这里会按轮次显示完整消息。</p>
		</div>
	</section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import DOMPurify from 'dompurify';
import { useReActChatStore, type ReActTraceSelection } from '~/stores/reactChat';
import type { ReActStreamEvent } from '~/services/react';
import { renderMarkdownContent } from '~/utils/markdown';
import { hljs } from '~/utils/markdown/markdown-plugin-highlight';

const store = useReActChatStore();
const rounds = computed(() => store.traceRounds);
const selection = computed(() => store.traceSelection);
const expandedRounds = ref<Record<string, boolean>>({});
const searchQuery = ref('');
const detailOpen = ref(true);
const copiedContent = ref(false);
type DetailTab = 'overview' | 'params' | 'result' | 'schema' | 'timing';
const activeDetailTab = ref<DetailTab>('overview');
const timeSelection = ref<{ start: number; end: number } | null>(null);
const draggingTimeSelection = ref<{ start: number; current: number } | null>(null);
const timelineZoom = ref(1);
const timelineCenter = ref(50);
const hoverAxisPercent = ref<number | null>(null);
const traceRoundsContainer = ref<HTMLElement | null>(null);
const messageRowRefs = new Map<string, HTMLElement>();
const detailWidthPercent = ref(33);
const isResizing = ref(false);
let resizeStartX = 0;
let resizeStartWidth = 33;
let timeSelectionTrack: HTMLElement | null = null;
let clickedTimelineBarKey: string | null = null;
const detailTabs: Array<{ label: string; value: DetailTab }> = [
	{ label: '概述', value: 'overview' },
	{ label: '参数', value: 'params' },
	{ label: '结果', value: 'result' },
	{ label: 'Schema', value: 'schema' },
	{ label: '计时', value: 'timing' },
];

interface TraceMessage {
	key: string;
	stepId: string;
	kind: string;
	title: string;
	source: string;
	content: string;
	textType: string;
	parameterContent: string;
	resultContent: string;
	resultTextType: string;
	stepIds: string[];
	startedAt?: number;
	endedAt?: number;
	durationMs?: number;
	attempt?: number;
}

type ContentView = {
	kind: 'markdown' | 'code' | 'text';
	text: string;
	html: string;
	languageLabel: string;
	code: string;
};

type OverviewBar = {
	key: string;
	kind: string;
	item: TraceMessage;
	roundKey: string;
	left: number;
	width: number;
	timeStart: number;
	timeEnd: number;
	title: string;
};

const selectedRoundKey = computed(() => {
	if (!selection.value) return '';
	return roundKey(selection.value);
});

const traceMessages = computed(() =>
	selection.value ? messagesForRound(selection.value) : [],
);

const timelineBars = computed(() => {
	const source = rounds.value.flatMap((round, roundIndex) =>
		messagesForRound(round).map((item, itemIndex) => ({
			key: `${roundKey(round)}-${item.key}-${roundIndex}-${itemIndex}`,
			kind: item.kind,
			item,
			roundKey: roundKey(round),
		})),
	);
	const knownTimes = source.flatMap(({ item }) =>
		item.startedAt === undefined ? [] : [item.startedAt],
	);
	if (source.length === 0) return [];

	const timelineStart = knownTimes.length > 0 ? Math.min(...knownTimes) : 0;
	let cursor = timelineStart;
	const positioned = source.map(({ key, kind, item, roundKey }) => {
		const actualStart = item.startedAt ?? cursor;
		const actualEnd = item.endedAt ?? actualStart + Math.max(item.durationMs || 1, 1);
		const layoutStart = Math.max(actualStart, cursor);
		const layoutEnd = Math.max(actualEnd, layoutStart + 1);
		cursor = layoutEnd;
		return { key, kind, item, roundKey, layoutStart, layoutEnd };
	});
	const timelineEnd = Math.max(cursor, timelineStart + 1);
	const timelineSpan = timelineEnd - timelineStart;

	return positioned.map(({ key, kind, item, roundKey, layoutStart, layoutEnd }) => ({
		key,
		kind,
		item,
		roundKey,
		left: ((layoutStart - timelineStart) / timelineSpan) * 100,
		width: Math.max(((layoutEnd - layoutStart) / timelineSpan) * 100, 0.8),
		timeStart: ((layoutStart - timelineStart) / timelineSpan) * 100,
		timeEnd: ((layoutEnd - timelineStart) / timelineSpan) * 100,
		title: traceTimingTitle(item),
	}));
});

const timelineViewport = computed(() => {
	const width = 100 / timelineZoom.value;
	const start = Math.min(
		Math.max(timelineCenter.value - width / 2, 0),
		100 - width,
	);
	return { start, end: start + width };
});

const timelineClock = computed(() => {
	const timestamps = timelineBars.value.flatMap(({ item }) =>
		item.startedAt === undefined
			? []
			: [item.startedAt, item.endedAt ?? item.startedAt],
	);
	if (timestamps.length === 0) return null;
	return { start: Math.min(...timestamps), end: Math.max(...timestamps) };
});

const overviewBars = computed<OverviewBar[]>(() => {
	const viewport = timelineViewport.value;
	const width = viewport.end - viewport.start;
	return timelineBars.value.map((bar) => ({
		...bar,
		left: ((bar.left - viewport.start) / width) * 100,
		width: (bar.width / width) * 100,
	}));
});

const activeTimeRange = computed(() => {
	const range = draggingTimeSelection.value || timeSelection.value;
	if (!range) return null;
	const start = Math.min(range.start, 'current' in range ? range.current : range.end);
	const end = Math.max(range.start, 'current' in range ? range.current : range.end);
	const viewport = timelineViewport.value;
	const width = viewport.end - viewport.start;
	return {
		start: ((start - viewport.start) / width) * 100,
		end: ((end - viewport.start) / width) * 100,
	};
});

const hasTimeSelection = computed(() => Boolean(timeSelection.value));

const hoverDisplayPercent = computed(() => {
	if (hoverAxisPercent.value === null) return null;
	const viewport = timelineViewport.value;
	return ((hoverAxisPercent.value - viewport.start) / (viewport.end - viewport.start)) * 100;
});

const hoverTimeLabel = computed(() => {
	const clock = timelineClock.value;
	if (!clock || hoverAxisPercent.value === null) return '';
	const timestamp =
		clock.start + (hoverAxisPercent.value / 100) * Math.max(clock.end - clock.start, 1);
	return formatTraceTime(timestamp);
});

const selectedItem = computed(() =>
	traceMessages.value.find(
		(item) => item.stepIds.includes(selection.value?.selectedStepId || ''),
	),
);

const visibleDetailTabs = computed(() =>
	detailTabs.filter((tab) => isDetailTabAvailable(tab.value)),
);

const detailContent = computed(() => {
	const item = selectedItem.value;
	if (!item) return { content: '', textType: 'TEXT' };
	if (activeDetailTab.value === 'params') {
		return item.kind === 'TOOL_CALL'
			? { content: item.parameterContent, textType: 'JSON' }
			: { content: '', textType: 'TEXT' };
	}
	if (activeDetailTab.value === 'result') {
		return item.kind === 'TOOL_CALL' || item.kind === 'TOOL_RESULT'
			? { content: item.resultContent, textType: item.resultTextType }
			: { content: '', textType: 'TEXT' };
	}
	return { content: item.content, textType: item.textType };
});

const detailEmptyMessage = computed(() => {
	if (activeDetailTab.value === 'params') return '当前消息没有工具调用参数';
	if (activeDetailTab.value === 'result') return '当前消息没有工具返回结果';
	return '当前消息没有可展示内容';
});

const contentView = computed<ContentView>(() => {
	const source = detailContent.value;

	const text = normalizeContent(source.content);
	const type = normalizeTextType(source.textType);
	if (type === 'markdown' || (type === 'text' && looksLikeMarkdown(text))) {
		return {
			kind: 'markdown',
			text,
			html: sanitizeMarkdown(text),
			languageLabel: 'MARKDOWN',
			code: text,
		};
	}
	if (type === 'sql' || type === 'json' || type === 'python') {
		const language = type === 'json' ? 'json' : type;
		const code = extractCode(text);
		return {
			kind: 'code',
			text,
			html: highlightCode(code, language),
			languageLabel: language.toUpperCase(),
			code,
		};
	}
	return { kind: 'text', text, html: '', languageLabel: 'TEXT', code: text };
});

const selectedMessageIndex = computed(() => {
	const index = traceMessages.value.findIndex(
		(item) => item.stepIds.includes(selection.value?.selectedStepId || ''),
	);
	return index >= 0 ? index + 1 : '-';
});

const schemaContent = computed(() => {
	if (selectedItem.value?.kind === 'TOOL_RESULT') return '工具结果未提供 Schema';
	return '当前消息没有 Schema 信息';
});

const selectedRoundIndex = computed(() => {
	const index = rounds.value.findIndex(
		(round) => roundKey(round) === selectedRoundKey.value,
	);
	return index >= 0 ? index : 0;
});

const allRoundsExpanded = computed(
	() => rounds.value.length > 0 && rounds.value.every((round) => isExpanded(round)),
);

function roundKey(round: ReActTraceSelection) {
	return round.threadId || `round-${round.messageId || round.roundIndex || 0}`;
}

function messagesForRound(round: ReActTraceSelection): TraceMessage[] {
	const messages = round.blocks.map((block, index) => {
		const first = block[0];
		const kind = first?.eventType || 'ASSISTANT';
		const stepId = first?.stepId || `message-${index}`;
		const content = block.map((event) => event.text || '').join('');
		const callEvents = block.filter((event) => event.eventType === 'TOOL_CALL');
		const resultEvents = block.filter((event) => event.eventType === 'TOOL_RESULT');
		const parameterContent =
			kind === 'TOOL_CALL'
				? callEvents
						.filter((event) => normalizeTextType(event.textType) === 'json')
						.map((event) => event.text || '')
						.join('')
				: '';
		const resultPayloadEvents = resultEvents.filter(
			(event) => event.text?.trim() !== '工具返回结果',
		);
		const resultContent = resultPayloadEvents.map((event) => event.text || '').join('');
		const resultTextType =
			resultPayloadEvents.find((event) => event.textType && event.textType !== 'TEXT')?.textType ||
			'TEXT';
		const timestamps = block
			.map((event) => event.timestamp)
			.filter((timestamp): timestamp is number => typeof timestamp === 'number');
		return {
			key: `${roundKey(round)}-${stepId}-${index}`,
			stepId,
			kind,
			title: messageTitle(kind),
			source: messageSource(first, kind),
			content,
			textType: first?.textType || 'TEXT',
			parameterContent,
			resultContent,
			resultTextType,
			stepIds: Array.from(
				new Set([
					stepId,
					...block.map((event) => event.stepId).filter((id): id is string => Boolean(id)),
				]),
			),
			startedAt: timestamps.length > 0 ? Math.min(...timestamps) : undefined,
			endedAt: timestamps.length > 0 ? Math.max(...timestamps) : undefined,
			durationMs:
				timestamps.length > 1 ? Math.max(...timestamps) - Math.min(...timestamps) : undefined,
			attempt: first?.attempt,
		};
	});
	if (round.finalText) {
		messages.push({
			key: `${roundKey(round)}-final`,
			stepId: 'final',
			kind: 'FINAL_ANSWER',
			title: messageTitle('FINAL_ANSWER'),
			source: 'Agent',
			content: round.finalText,
			textType: 'MARK_DOWN',
			parameterContent: '',
			resultContent: '',
			resultTextType: 'MARK_DOWN',
			stepIds: ['final'],
			startedAt: round.finalTimestamp,
			endedAt: round.finalTimestamp,
		});
	}
	return messages;
}

function filteredMessagesForRound(round: ReActTraceSelection) {
	const query = searchQuery.value.trim().toLowerCase();
	if (!query) return messagesForRound(round);
	return messagesForRound(round).filter((item) =>
		`${item.title} ${item.source} ${item.content}`.toLowerCase().includes(query),
	);
}

function messageTitle(eventType?: string) {
	const labels: Record<string, string> = {
		CONTEXT: '上下文消息',
		USER_MESSAGE: '用户消息',
		ASSISTANT: '助手消息',
		THINKING: '思考消息',
		TOOL_CALL: '工具调用消息',
		TOOL_RESULT: '工具结果消息',
		FINAL_ANSWER: '助手最终输出',
		ERROR: '错误消息',
	};
	return labels[eventType || ''] || '助手消息';
}

function messageTypeLabel(eventType?: string) {
	const labels: Record<string, string> = {
		CONTEXT: '上下文',
		USER_MESSAGE: '用户',
		ASSISTANT: '助手',
		THINKING: '思考',
		TOOL_CALL: '工具',
		TOOL_RESULT: '结果',
		FINAL_ANSWER: '输出',
		ERROR: '错误',
	};
	return labels[eventType || ''] || '消息';
}

function messageTypeClass(eventType?: string) {
	const classes: Record<string, string> = {
		CONTEXT: 'context',
		USER_MESSAGE: 'user',
		ASSISTANT: 'assistant',
		THINKING: 'thinking',
		TOOL_CALL: 'tool',
		TOOL_RESULT: 'result',
		FINAL_ANSWER: 'final',
		ERROR: 'error',
	};
	return classes[eventType || ''] || 'default';
}

function messageSource(event: ReActStreamEvent | undefined, kind: string) {
	if (kind === 'CONTEXT') return '运行上下文';
	if (kind === 'USER_MESSAGE') return '用户';
	if (kind === 'TOOL_CALL' || kind === 'TOOL_RESULT')
		return event?.nodeName || '工具';
	return event?.nodeName || 'Agent';
}

function preview(content: string) {
	const value = content.replace(/\s+/g, ' ').trim();
	return value.length > 72 ? `${value.slice(0, 72)}...` : value || '空消息';
}

function normalizeTextType(value?: string) {
	const type = value?.toUpperCase().replace(/[-_ ]/g, '') || 'TEXT';
	if (type.includes('MARKDOWN') || type.includes('MARKDOWNREPORT')) return 'markdown';
	if (type.includes('SQL')) return 'sql';
	if (type.includes('JSON') || type.includes('RESULTSET')) return 'json';
	if (type.includes('PYTHON')) return 'python';
	return 'text';
}

function normalizeContent(content: string) {
	return content
		.replace(/\\r\\n/g, '\n')
		.replace(/\\n/g, '\n')
		.replace(/\\t/g, '\t');
}

function looksLikeMarkdown(content: string) {
	return /```[\s\S]*```/.test(content) || /^#{1,6}\s/m.test(content) || /^\s*\|.+\|\s*$/m.test(content);
}

function extractCode(content: string) {
	const fence = /^\s*```(?:[a-z0-9_-]+)?\s*([\s\S]*?)\s*```\s*$/i.exec(content);
	return fence?.[1]?.trim() || content.trim();
}

function highlightCode(content: string, language: string) {
	const lang = hljs.getLanguage(language) ? language : undefined;
	return lang
		? hljs.highlight(content, { language: lang }).value
		: hljs.highlightAuto(content).value;
}

function sanitizeMarkdown(content: string) {
	return DOMPurify.sanitize(renderMarkdownContent(content), {
		ADD_TAGS: ['div'],
		ADD_ATTR: ['class', 'data-echarts-config'],
	});
}

async function copyContent() {
	if (!contentView.value.code || !navigator.clipboard) return;
	try {
		await navigator.clipboard.writeText(contentView.value.code);
		copiedContent.value = true;
		window.setTimeout(() => {
			copiedContent.value = false;
		}, 1600);
	}
	catch {
		copiedContent.value = false;
	}
}

function isExpanded(round: ReActTraceSelection) {
	return expandedRounds.value[roundKey(round)] ?? false;
}

function toggleRound(round: ReActTraceSelection) {
	const key = roundKey(round);
	const next = !isExpanded(round);
	expandedRounds.value[key] = next;
	if (next && selectedRoundKey.value !== key) {
		const first = messagesForRound(round)[0];
		if (first) {
			activeDetailTab.value = 'overview';
			store.selectTraceStep(round, first.stepId);
		}
	}
}

function toggleAllRounds() {
	const shouldExpand = !allRoundsExpanded.value;
	const nextState: Record<string, boolean> = {};
	for (const round of rounds.value) {
		nextState[roundKey(round)] = shouldExpand;
	}
	expandedRounds.value = nextState;
}

function selectItem(round: ReActTraceSelection, item: TraceMessage) {
	expandedRounds.value[roundKey(round)] = true;
	activeDetailTab.value = 'overview';
	detailOpen.value = true;
	store.selectTraceStep(round, item.stepId);
	void nextTick(() => {
		messageRowRefs.get(item.key)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
	});
}

function selectTimelineBar(bar: OverviewBar) {
	const round = rounds.value.find((candidate) => roundKey(candidate) === bar.roundKey);
	if (!round) return;
	timeSelection.value = { start: bar.timeStart, end: bar.timeEnd };
	selectItem(round, bar.item);
}

function closeDetail() {
	detailOpen.value = false;
}

function formatDuration(durationMs?: number) {
	if (durationMs === undefined) return '-';
	if (durationMs < 1000) return `${durationMs} ms`;
	return `${(durationMs / 1000).toFixed(1)} s`;
}

function traceTimingTitle(item: TraceMessage) {
	if (item.startedAt === undefined || item.endedAt === undefined) {
		return '时间数据不可用';
	}
	const duration = Math.max(item.endedAt - item.startedAt, 0);
	return `${formatTraceTime(item.startedAt)} → ${formatTraceTime(item.endedAt)}\n总计 ${duration.toLocaleString('zh-CN')} 毫秒`;
}

function formatTraceTime(timestamp: number) {
	const date = new Date(timestamp);
	const pad = (value: number, length = 2) => String(value).padStart(length, '0');
	return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
}

function pointerPercent(event: PointerEvent) {
	const track =
		timeSelectionTrack ||
		(event.currentTarget instanceof HTMLElement ? event.currentTarget : null);
	if (!track) return 0;
	const bounds = track.getBoundingClientRect();
	if (bounds.width <= 0) return 0;
	const displayPercent = Math.min(
		100,
		Math.max(0, ((event.clientX - bounds.left) / bounds.width) * 100),
	);
	const viewport = timelineViewport.value;
	return viewport.start + (displayPercent / 100) * (viewport.end - viewport.start);
}

function zoomTimeline(event: WheelEvent) {
	if (timelineBars.value.length === 0) return;
	const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
	if (bounds.width <= 0) return;
	const pointer = Math.min(
		1,
		Math.max(0, (event.clientX - bounds.left) / bounds.width),
	);
	const currentViewport = timelineViewport.value;
	const pointerPosition =
		currentViewport.start + pointer * (currentViewport.end - currentViewport.start);
	const nextZoom = Math.min(
		12,
		Math.max(1, timelineZoom.value * (event.deltaY < 0 ? 1.25 : 0.8)),
	);
	const nextWidth = 100 / nextZoom;
	const nextStart = Math.min(
		Math.max(pointerPosition - pointer * nextWidth, 0),
		100 - nextWidth,
	);
	timelineZoom.value = nextZoom;
	timelineCenter.value = nextStart + nextWidth / 2;
}

function beginTimeSelection(event: PointerEvent) {
	if (event.button !== 0) return;
	timeSelectionTrack = event.currentTarget as HTMLElement;
	clickedTimelineBarKey = (event.target as Element | null)
		?.closest<HTMLElement>('[data-trace-bar-key]')
		?.dataset.traceBarKey || null;
	const start = pointerPercent(event);
	draggingTimeSelection.value = { start, current: start };
	timeSelectionTrack.setPointerCapture?.(event.pointerId);
}

function setMessageRowRef(key: string, element: unknown) {
	if (element instanceof HTMLElement) {
		messageRowRefs.set(key, element);
	}
	else {
		messageRowRefs.delete(key);
	}
}

function beginResize(event: PointerEvent) {
	if (event.button !== 0) return;
	resizeStartX = event.clientX;
	resizeStartWidth = detailWidthPercent.value;
	isResizing.value = true;
	(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
}

function updateResize(event: PointerEvent) {
	if (!isResizing.value) return;
	const workspace = event.currentTarget instanceof HTMLElement
		? event.currentTarget.parentElement
		: null;
	const width = workspace?.getBoundingClientRect().width || 0;
	if (width <= 0) return;
	const deltaPercent = ((event.clientX - resizeStartX) / width) * 100;
	detailWidthPercent.value = Math.min(60, Math.max(25, resizeStartWidth - deltaPercent));
}

function endResize(event: PointerEvent) {
	if (!isResizing.value) return;
	isResizing.value = false;
	(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId);
}

function resizeWithKeyboard(event: KeyboardEvent) {
	const step = event.shiftKey ? 5 : 2;
	if (event.key === 'ArrowLeft') {
		detailWidthPercent.value = Math.min(60, detailWidthPercent.value + step);
		event.preventDefault();
	}
	else if (event.key === 'ArrowRight') {
		detailWidthPercent.value = Math.max(25, detailWidthPercent.value - step);
		event.preventDefault();
	}
}

function updateTimeSelection(event: PointerEvent) {
	if (!draggingTimeSelection.value) return;
	draggingTimeSelection.value.current = pointerPercent(event);
}

function updateHoverPosition(event: PointerEvent) {
	hoverAxisPercent.value = pointerPercent(event);
	updateTimeSelection(event);
}

function finishTimeSelection(event: PointerEvent) {
	const dragging = draggingTimeSelection.value;
	if (!dragging) return;
	const current = pointerPercent(event);
	const start = Math.min(dragging.start, current);
	const end = Math.max(dragging.start, current);
	const clickedBarKey = clickedTimelineBarKey;
	clickedTimelineBarKey = null;
	if (end - start > 0.5) {
		timeSelection.value = { start, end };
	}
	else if (clickedBarKey) {
		const clickedBar = overviewBars.value.find((bar) => bar.key === clickedBarKey);
		if (clickedBar) {
			const isSameSelection =
				timeSelection.value !== null &&
				Math.abs(timeSelection.value.start - clickedBar.timeStart) < 0.001 &&
				Math.abs(timeSelection.value.end - clickedBar.timeEnd) < 0.001;
			if (isSameSelection) {
				timeSelection.value = null;
			}
			else {
				selectTimelineBar(clickedBar);
			}
		}
		else {
			timeSelection.value = null;
		}
	}
	else {
		timeSelection.value = null;
	}
	draggingTimeSelection.value = null;
	timeSelectionTrack?.releasePointerCapture?.(event.pointerId);
	if (timeSelection.value) void nextTick(scrollToFirstTimeSelectedMessage);
}

function cancelTimeSelection() {
	draggingTimeSelection.value = null;
	clickedTimelineBarKey = null;
}

function isBarSelected(bar: OverviewBar) {
	const range = activeTimeRange.value;
	if (!range) return false;
	return bar.left <= range.end && bar.left + bar.width >= range.start;
}

function isMessageTimeSelected(item: TraceMessage) {
	if (!hasTimeSelection.value) return false;
	return overviewBars.value.some(
		(bar) => bar.item.key === item.key && isBarSelected(bar),
	);
}

function scrollToFirstTimeSelectedMessage() {
	if (!timeSelection.value) return;
	for (const round of rounds.value) {
		const item = filteredMessagesForRound(round).find(isMessageTimeSelected);
		if (!item) continue;
		expandedRounds.value[roundKey(round)] = true;
		void nextTick(() => {
			messageRowRefs.get(item.key)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
		});
		return;
	}
}

onMounted(() => {
	window.addEventListener('pointermove', updateTimeSelection);
	window.addEventListener('pointerup', finishTimeSelection);
	window.addEventListener('pointercancel', cancelTimeSelection);
});

onBeforeUnmount(() => {
	window.removeEventListener('pointermove', updateTimeSelection);
	window.removeEventListener('pointerup', finishTimeSelection);
	window.removeEventListener('pointercancel', cancelTimeSelection);
});

function shortId(value: string) {
	return value.length > 16
		? `${value.slice(0, 8)}...${value.slice(-6)}`
		: value;
}

function isDetailTabAvailable(tab: DetailTab) {
	const item = selectedItem.value;
	if (!item) return false;
	if (tab === 'params') {
		return item.kind === 'TOOL_CALL' && Boolean(item.parameterContent);
	}
	if (tab === 'result') {
		return (
			(item.kind === 'TOOL_CALL' || item.kind === 'TOOL_RESULT') &&
			Boolean(item.resultContent)
		);
	}
	return true;
}

watch(
	rounds,
	(value) => {
		const latest = value.at(-1);
		if (latest) expandedRounds.value[roundKey(latest)] = true;
	},
	{ immediate: true },
);

watch(selection, () => {
	detailOpen.value = true;
	activeDetailTab.value = 'overview';
	copiedContent.value = false;
});

watch(visibleDetailTabs, (tabs) => {
	if (!tabs.some((tab) => tab.value === activeDetailTab.value)) {
		activeDetailTab.value = 'overview';
	}
});
</script>

<style scoped>
.trace-panel {
	display: flex;
	min-height: 0;
	flex: 1;
	flex-direction: column;
	background: #f8fafc;
	color: #273449;
}

.trace-panel-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 24px;
	min-height: 54px;
	padding: 0 clamp(20px, 4vw, 48px);
	border-bottom: 1px solid #e7ebf2;
	background: #fff;
}

.trace-eyebrow {
	margin: 0 0 4px;
	color: #60708a;
	font-size: 11px;
	font-weight: 700;
	letter-spacing: 0.08em;
	text-transform: uppercase;
}

.trace-panel-header .trace-eyebrow,
.trace-panel-header .trace-subtitle {
	display: none;
}

.trace-panel-header h2,
.trace-detail-header h3 {
	margin: 0;
	color: #1d2939;
	font-size: 16px;
	font-weight: 700;
}

.trace-subtitle {
	margin: 3px 0 0;
	color: #7b8799;
	font-size: 12px;
}

.trace-toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	min-height: 42px;
	padding: 0 28px;
	border-bottom: 1px solid #e6e9ee;
	background: #fff;
}

.trace-toolbar-actions {
	display: flex;
	align-items: center;
	gap: 2px;
}

.trace-toolbar-stats {
	margin-right: auto;
	margin-left: auto;
}

.trace-toolbar-button {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	min-height: 30px;
	padding: 0 9px;
	border: 1px solid transparent;
	border-radius: 6px;
	background: transparent;
	color: #7a8493;
	font: inherit;
	font-size: 12px;
	cursor: pointer;
}

.trace-toolbar-button:hover,
.trace-toolbar-button--active {
	border-color: #e2e6ed;
	background: #f7f8fa;
	color: #273449;
}

.trace-search {
	display: flex;
	width: min(240px, 32vw);
	height: 30px;
	align-items: center;
	gap: 6px;
	padding: 0 9px;
	border: 1px solid #dfe3e9;
	border-radius: 7px;
	background: #fff;
	color: #a0a8b4;
}

.trace-search:focus-within {
	border-color: #8ba5f7;
	box-shadow: 0 0 0 2px rgba(49, 94, 251, 0.09);
}

.trace-search input {
	width: 100%;
	min-width: 0;
	padding: 0;
	border: 0;
	outline: 0;
	background: transparent;
	color: #334155;
	font: inherit;
	font-size: 12px;
}

.trace-search input::placeholder {
	color: #a4acb7;
}

.trace-overview {
	display: grid;
	grid-template-columns: 48px minmax(0, 1fr);
	align-items: center;
	min-height: 68px;
	padding: 5px 20px 6px;
	border-bottom: 1px solid #e3e7ed;
	background: #fbfcfd;
}

.trace-overview-labels {
	display: flex;
	align-items: flex-start;
	flex-direction: column;
	justify-content: space-between;
	padding-right: 10px;
	color: #8993a1;
	font-size: 10px;
	line-height: 15px;
}

.trace-overview-track {
	position: relative;
	display: flex;
	align-items: flex-end;
	gap: 4px;
	height: 50px;
	padding: 0 2px 3px;
	border-bottom: 1px solid #dfe4eb;
	background: repeating-linear-gradient(
		90deg,
		transparent 0,
		transparent 23px,
		rgba(210, 216, 225, 0.32) 24px
	);
	cursor: crosshair;
	touch-action: none;
	user-select: none;
}

.trace-overview-bar {
	position: absolute;
	bottom: 3px;
	display: block;
	min-width: 2px;
	height: 10px;
	border-radius: 1px;
	background: #c7b6dc;
	transition: opacity 0.16s ease, filter 0.16s ease;
	pointer-events: auto;
	cursor: pointer;
}

.trace-overview-bar--selected {
	filter: saturate(1.2) brightness(0.92);
	box-shadow: 0 0 0 1px #315efb;
}

.trace-overview-bar--faded {
	opacity: 0.18;
}

.trace-overview-selection {
	position: absolute;
	top: 0;
	bottom: 0;
	border-right: 2px solid #315efb;
	border-left: 2px solid #315efb;
	background: rgba(49, 94, 251, 0.1);
	pointer-events: none;
}

.trace-overview-cursor {
	position: absolute;
	top: 0;
	bottom: 0;
	z-index: 4;
	width: 1px;
	border-left: 1px solid #315efb;
	pointer-events: none;
}

.trace-overview-cursor-label {
	position: absolute;
	top: 2px;
	left: 4px;
	padding: 2px 5px;
	border-radius: 3px;
	background: #25344a;
	color: #fff;
	font-size: 10px;
	line-height: 1.3;
	white-space: nowrap;
}

.trace-overview-bar--context,
.trace-overview-bar--user_message {
	bottom: 34px;
	height: 8px;
	background: #60bd88;
}

.trace-overview-bar--thinking,
.trace-overview-bar--assistant {
	bottom: 19px;
	height: 8px;
	background: #b8a3d0;
}

.trace-overview-bar--tool_call,
.trace-overview-bar--tool_result {
	bottom: 3px;
	height: 8px;
	background: #e68a24;
}

.trace-overview-bar--error {
	bottom: 19px;
	height: 8px;
	background: #e64c4c;
}

.trace-overview-bar--final_answer {
	bottom: 19px;
	height: 8px;
	background: #4c7df0;
}

.trace-panel-stats {
	display: flex;
	align-items: center;
	gap: 24px;
}

.trace-panel-stats > div {
	display: flex;
	align-items: baseline;
	gap: 8px;
}

.trace-panel-stats span,
.trace-list-heading,
.trace-detail-meta,
.trace-round-meta {
	color: #8490a3;
	font-size: 11px;
}

.trace-panel-stats strong {
	color: #2f5bea;
	font-size: 15px;
}

.trace-workspace {
	position: relative;
	display: grid;
	min-height: 0;
	flex: 1;
	grid-template-columns: minmax(0, calc(100% - var(--trace-detail-width))) var(--trace-detail-width);
}

.trace-workspace--detail-closed {
	grid-template-columns: minmax(0, 1fr);
}

.trace-workspace--resizing,
.trace-workspace--resizing * {
	cursor: col-resize !important;
	user-select: none;
}

.trace-resizer {
	position: absolute;
	top: 0;
	bottom: 0;
	left: calc(100% - var(--trace-detail-width));
	z-index: 3;
	width: 12px;
	margin-left: -6px;
	border: 0;
	background: transparent;
	cursor: col-resize;
	outline: 0;
}

.trace-resizer::after {
	position: absolute;
	top: 0;
	bottom: 0;
	left: 50%;
	width: 1px;
	background: #e1e6ee;
	content: '';
	transform: translateX(-50%);
	transition: background 0.16s ease, width 0.16s ease;
}

.trace-resizer:hover::after,
.trace-resizer:focus-visible::after {
	left: 50%;
	width: 2px;
	background: #8aa8ff;
}

.trace-rounds {
	min-height: 0;
	overflow-y: auto;
	padding: 10px 10px 18px clamp(12px, 3vw, 28px);
	border-right: 1px solid #e7ebf2;
	background: #fbfcfe;
	scrollbar-color: #aeb8c6 transparent;
	scrollbar-width: thin;
}

.trace-rounds::-webkit-scrollbar,
.trace-detail::-webkit-scrollbar {
	width: 8px;
	height: 8px;
}

.trace-rounds::-webkit-scrollbar-track,
.trace-detail::-webkit-scrollbar-track {
	background: transparent;
}

.trace-rounds::-webkit-scrollbar-thumb,
.trace-detail::-webkit-scrollbar-thumb {
	border: 2px solid transparent;
	border-radius: 999px;
	background: #aeb8c6;
	background-clip: padding-box;
}

.trace-rounds::-webkit-scrollbar-thumb:hover,
.trace-detail::-webkit-scrollbar-thumb:hover {
	background: #7f8da1;
	background-clip: padding-box;
}

.trace-list-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 5px;
	padding: 0 8px;
	font-weight: 650;
}

.trace-round {
	margin-bottom: 0;
	border: 0;
	border-bottom: 1px solid #e5e9ef;
	border-radius: 0;
	background: #fff;
	transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.trace-round--selected {
	border-color: #dfe5ef;
	box-shadow: inset 3px 0 0 #315efb;
}

.trace-round--expanded .trace-round-header {
	background: #f8faff;
}

.trace-round-header,
.trace-message-row {
	width: 100%;
	border: 0;
	background: transparent;
	font: inherit;
	text-align: left;
	cursor: pointer;
}

.trace-round-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	min-height: 48px;
	padding: 7px 10px;
}

.trace-round-header:hover,
.trace-message-row:hover {
	background: #f7f9ff;
}

.trace-round-header:focus-visible,
.trace-message-row:focus-visible {
	outline: 2px solid #8aa8ff;
	outline-offset: -2px;
}

.trace-round-heading {
	display: flex;
	min-width: 0;
	align-items: center;
	gap: 9px;
}

.trace-round-index,
.trace-message-index {
	display: grid;
	flex-shrink: 0;
	place-items: center;
	border-radius: 50%;
	font-size: 11px;
	font-weight: 700;
}

.trace-round-index {
	width: 24px;
	height: 24px;
	background: #edf2ff;
	color: #315efb;
}

.trace-round-copy {
	display: flex;
	min-width: 0;
	flex-direction: column;
	gap: 3px;
}

.trace-round-copy strong {
	color: #2b3850;
	font-size: 13px;
}

.trace-round-copy span {
	overflow: hidden;
	color: #8390a4;
	font-size: 12px;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.trace-round-meta {
	display: flex;
	align-items: center;
	gap: 4px;
	flex-shrink: 0;
}

.trace-round-messages {
	padding: 0 5px 4px 10px;
	border-top: 1px solid #edf0f5;
}

.trace-message-row {
	display: flex;
	align-items: center;
	gap: 8px;
	height: 38px;
	min-height: 38px;
	padding: 5px 6px 5px 4px;
	border-radius: 6px;
}

.trace-message-row--selected {
	background: #edf1f7;
	color: #315efb;
}

.trace-message-row--time-selected {
	background: #e5edff;
	box-shadow: inset 3px 0 0 #315efb;
}

.trace-message-row--time-faded {
	opacity: 0.28;
}

.trace-message-index {
	width: 20px;
	height: 20px;
	background: #f0f3f8;
	color: #7b8799;
}

.trace-message-row--selected .trace-message-index {
	background: #dbe5ff;
	color: #315efb;
}

.trace-message-kind {
	display: inline-flex;
	min-width: 42px;
	height: 26px;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	padding: 0 7px;
	border-radius: 4px;
	font-size: 12px;
	font-weight: 600;
	line-height: 1.35;
}

.trace-message-kind--user {
	background: #e9f0ff;
	color: #3569d8;
}

.trace-message-kind--context {
	background: #e8f8ef;
	color: #15915a;
}

.trace-message-kind--assistant,
.trace-message-kind--thinking {
	background: #f0eafa;
	color: #8751b5;
}

.trace-message-kind--tool,
.trace-message-kind--result {
	background: #fff1df;
	color: #d97909;
}

.trace-message-kind--final {
	background: #e8efff;
	color: #315efb;
}

.trace-message-kind--error {
	background: #ffebeb;
	color: #d64343;
}

.trace-message-kind--default {
	background: #f0f3f8;
	color: #69778b;
}

.trace-message-row--selected .trace-message-kind {
	box-shadow: inset 0 0 0 1px currentColor;
}

.trace-message-copy {
	display: flex;
	align-items: center;
	height: 26px;
	min-width: 0;
	flex: 1;
	overflow: hidden;
}

.trace-round-empty {
	margin: 0;
	padding: 10px 6px;
	color: #9aa5b4;
	font-size: 12px;
}

.trace-message-preview {
	display: flex;
	align-items: center;
	overflow: hidden;
	min-width: 0;
	flex: 1;
	color: #7d899b;
	font-size: 12px;
	line-height: 1.35;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.trace-message-arrow {
	flex-shrink: 0;
	color: #a8b1bf;
}

.trace-detail {
	min-width: 0;
	overflow-y: auto;
	padding: 16px clamp(18px, 3vw, 40px) 24px;
	background: #fff;
	scrollbar-color: #aeb8c6 transparent;
	scrollbar-width: thin;
}

.trace-detail-header {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 18px;
	padding-bottom: 12px;
	border-bottom: 1px solid #edf0f5;
}

.trace-detail-source {
	max-width: 180px;
	overflow: hidden;
	color: #7d899b;
	font-size: 12px;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.trace-detail-actions {
	display: flex;
	align-items: center;
	gap: 16px;
}

.trace-detail-close {
	display: grid;
	width: 32px;
	height: 32px;
	padding: 0;
	place-items: center;
	border: 1px solid transparent;
	border-radius: 6px;
	background: transparent;
	color: #68768a;
	cursor: pointer;
}

.trace-detail-close:hover {
	border-color: #e1e6ed;
	background: #f5f7fa;
	color: #25344a;
}

.trace-detail-close:focus-visible {
	outline: 2px solid #8aa8ff;
	outline-offset: 2px;
}

.trace-detail-tabs {
	display: flex;
	align-items: stretch;
	gap: 0;
	min-height: 36px;
	border-bottom: 1px solid #edf0f5;
}

.trace-detail-tab {
	position: relative;
	min-width: 52px;
	flex: 1;
	padding: 0 8px;
	border: 0;
	background: transparent;
	color: #7f8998;
	font: inherit;
	font-size: 13px;
	white-space: nowrap;
	cursor: pointer;
}

.trace-detail-tab:hover,
.trace-detail-tab--active {
	color: #315efb;
}

.trace-detail-tab:disabled {
	color: #c2c9d3;
	cursor: not-allowed;
}

.trace-detail-tab:disabled:hover {
	color: #c2c9d3;
}

.trace-detail-tab--active::after {
	position: absolute;
	right: 0;
	bottom: -1px;
	left: 0;
	height: 2px;
	background: #315efb;
	content: '';
}

.trace-detail-tab:focus-visible {
	outline: 2px solid #8aa8ff;
	outline-offset: 3px;
}

.trace-detail-meta {
	display: flex;
	align-items: center;
	gap: 12px;
	padding: 10px 0 8px;
}

.trace-detail-body {
	min-width: 0;
}

.trace-detail-label {
	margin: 14px 0 10px;
	color: #758196;
	font-size: 12px;
}

.trace-timing-grid {
	display: grid;
	grid-template-columns: minmax(96px, 0.7fr) minmax(0, 1.3fr);
	gap: 13px 18px;
	padding-top: 4px;
	color: #7c8798;
	font-size: 13px;
}

.trace-timing-grid strong {
	color: #2f3d52;
	font-weight: 600;
}

.trace-detail-meta code {
	color: #69778b;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 11px;
}

.trace-detail-content {
	margin: 0;
	color: #334155;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 13px;
	line-height: 1.7;
	white-space: pre-wrap;
	word-break: break-word;
}

.trace-content-empty {
	margin: 24px 0;
	color: #9aa5b5;
	font-size: 13px;
}

.trace-content-viewer {
	min-width: 0;
}

.trace-markdown-content {
	color: #334155;
	font-size: 13px;
	line-height: 1.7;
}

.trace-markdown-content :deep(p) {
	margin: 0 0 10px;
}

.trace-markdown-content :deep(p:last-child) {
	margin-bottom: 0;
}

.trace-markdown-content :deep(h1),
.trace-markdown-content :deep(h2),
.trace-markdown-content :deep(h3) {
	margin: 14px 0 8px;
	color: #273449;
	font-weight: 700;
}

.trace-markdown-content :deep(h1) {
	font-size: 18px;
}

.trace-markdown-content :deep(h2) {
	font-size: 16px;
}

.trace-markdown-content :deep(h3) {
	font-size: 14px;
}

.trace-markdown-content :deep(ul),
.trace-markdown-content :deep(ol) {
	margin: 0 0 10px;
	padding-left: 20px;
}

.trace-markdown-content :deep(li) {
	margin: 3px 0;
}

.trace-markdown-content :deep(code:not(pre code)) {
	padding: 2px 5px;
	border-radius: 4px;
	background: #f0f3f8;
	color: #c23b61;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 12px;
}

.trace-markdown-content :deep(table) {
	width: 100%;
	margin: 8px 0 12px;
	border-collapse: collapse;
	font-size: 12px;
}

.trace-markdown-content :deep(th),
.trace-markdown-content :deep(td) {
	padding: 6px 8px;
	border: 1px solid #e1e6ee;
	text-align: left;
}

.trace-markdown-content :deep(th) {
	background: #f5f7fa;
	color: #526176;
	font-weight: 650;
}

.trace-markdown-content :deep(blockquote) {
	margin: 8px 0;
	padding: 4px 12px;
	border-left: 3px solid #b7c8f7;
	color: #69778b;
}

.trace-markdown-content :deep(.code-block-wrapper) {
	margin: 10px 0;
}

.trace-markdown-content :deep(.code-block-header) {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 6px 10px;
	border: 1px solid #dfe5ee;
	border-bottom: 0;
	border-radius: 6px 6px 0 0;
	background: #f3f5f8;
	color: #718096;
	font-size: 10px;
	font-weight: 700;
	letter-spacing: 0.04em;
}

.trace-markdown-content :deep(pre.hljs) {
	margin: 0;
	padding: 12px;
	border: 1px solid #dfe5ee;
	border-radius: 0 0 6px 6px;
	font-size: 12px;
	line-height: 1.6;
	overflow-x: auto;
}

.trace-code-panel {
	overflow: hidden;
	margin-top: 4px;
	border: 1px solid #dfe5ee;
	border-radius: 7px;
}

.trace-code-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	min-height: 30px;
	padding: 0 10px;
	border-bottom: 1px solid #dfe5ee;
	background: #f3f5f8;
	color: #68778b;
	font-size: 10px;
	font-weight: 700;
	letter-spacing: 0.05em;
}

.trace-code-copy {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	min-height: 24px;
	padding: 0 5px;
	border: 0;
	border-radius: 4px;
	background: transparent;
	color: #718096;
	font: inherit;
	font-size: 11px;
	cursor: pointer;
}

.trace-code-copy:hover {
	background: #e7ebf2;
	color: #315efb;
}

.trace-code-copy:focus-visible {
	outline: 2px solid #8aa8ff;
	outline-offset: 1px;
}

.trace-code-content {
	max-height: 520px;
	padding: 14px;
	background: #fbfcfe;
	font-size: 12px;
	line-height: 1.65;
	overflow: auto;
}

.trace-code-content code {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.trace-detail--empty,
.trace-empty {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 9px;
	flex-direction: column;
	color: #8a96a8;
	text-align: center;
}

.trace-detail--empty {
	min-height: 240px;
}

.trace-detail--empty p,
.trace-empty p {
	margin: 0;
	font-size: 13px;
}

.trace-empty {
	min-height: 0;
	flex: 1;
	padding: 32px;
	background: #fff;
}

.trace-empty strong {
	color: #526176;
	font-size: 15px;
}

@media (max-width: 900px) {
	.trace-toolbar,
	.trace-overview {
		padding-right: 16px;
		padding-left: 16px;
	}

	.trace-workspace {
		grid-template-columns: 1fr;
		overflow-y: auto;
	}

	.trace-resizer {
		display: none;
	}

	.trace-rounds {
		max-height: 46vh;
		border-right: 0;
		border-bottom: 1px solid #e7ebf2;
		padding-right: 20px;
	}

	.trace-detail {
		min-height: 280px;
	}
}

@media (max-width: 640px) {
	.trace-panel-header {
		padding: 18px 16px 14px;
	}

	.trace-panel-header h2 {
		font-size: 18px;
	}

	.trace-panel-stats {
		gap: 10px;
	}

	.trace-rounds {
		padding-left: 16px;
	}

	.trace-detail {
		padding: 22px 18px 28px;
	}
}
</style>
