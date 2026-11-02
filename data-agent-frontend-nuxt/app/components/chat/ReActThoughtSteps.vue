<template>
	<div class="thoughts" :class="{ 'thoughts--active': active }">
		<button
			class="thoughts-toggle"
			type="button"
			:aria-expanded="expanded"
			:aria-label="expanded ? '收起思考过程' : '展开思考过程'"
			@click="expanded = !expanded"
		>
			<span class="thoughts-mark">
				<v-icon
					size="18"
					class="thoughts-icon"
					:class="{ 'pulse-icon': active }"
				>
					mdi-atom
				</v-icon>
			</span>
			<span class="thoughts-title">
				{{ active ? '正在思考' : '已思考'
				}}<span v-if="durationLabel" class="thoughts-duration">
					（用时 {{ durationLabel }}）
				</span>
			</span>
			<v-icon size="16" class="chevron" :class="{ open: expanded }"
				>mdi-chevron-down</v-icon
			>
		</button>

		<Transition name="thoughts-expand">
			<div v-if="expanded" class="steps-list">
				<div
					v-for="(step, index) in steps"
					:key="step.key"
					class="step-item"
					:class="{
						'step-item--tool': step.isToolCall,
						'step-item--thought-collapsed':
							!step.isToolCall && !isThoughtExpanded(step.key),
					}"
				>
					<div class="step-rail">
						<v-icon v-if="step.icon" size="18" class="step-icon">{{
							step.icon
						}}</v-icon>
						<span
							v-else
							class="step-dot"
							:class="{
								'step-dot--active': active && index === steps.length - 1,
							}"
						/>
						<span v-if="index < steps.length - 1" class="step-line" />
					</div>
					<div class="step-content" :aria-label="step.title">
						<template v-if="step.isToolCall">
							<button
								type="button"
								class="tool-step-toggle"
								:aria-expanded="isToolExpanded(step.key)"
								@mousedown="clearTextSelection"
								@click="toggleToolStep(step.key, $event)"
							>
								<span>{{
									step.completed
										? `调用了工具：${step.toolName}`
										: `正在调用工具：${step.toolName}`
								}}</span>
								<v-icon
									size="15"
									class="tool-step-chevron"
									:class="{ open: isToolExpanded(step.key) }"
									>mdi-chevron-down</v-icon
								>
							</button>
							<Transition name="tool-details">
								<div v-if="isToolExpanded(step.key)" class="tool-step-details">
									<div v-if="step.toolArguments" class="tool-detail-section">
										<span class="tool-detail-label">传参</span>
										<pre>{{ step.toolArguments }}</pre>
									</div>
									<div v-if="step.toolResult" class="tool-detail-section">
										<span class="tool-detail-label">返回结果</span>
										<pre>{{ step.toolResult }}</pre>
									</div>
								</div>
							</Transition>
						</template>
						<template v-else>
							<button
								type="button"
								class="thought-step-toggle"
								:aria-expanded="isThoughtExpanded(step.key)"
								:aria-label="
									isThoughtExpanded(step.key) ? '收起思考内容' : '展开思考内容'
								"
								@mousedown="clearTextSelection"
								@click="toggleThoughtStep(step.key, $event)"
							>
								<span
									class="thought-step-text"
									:class="{
										'thought-step-text--collapsed': !isThoughtExpanded(
											step.key,
										),
									}"
								>
									{{ step.text || '已完成此步骤' }}
								</span>
								<v-icon
									size="15"
									class="thought-step-chevron"
									:class="{ open: isThoughtExpanded(step.key) }"
									>mdi-chevron-down</v-icon
								>
							</button>
						</template>
					</div>
				</div>
			</div>
		</Transition>
	</div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import type { ReActStreamEvent } from '~/services/react';
import { ReActEventType } from '~/services/react';
import { aggregateThoughtText } from '~/utils/reactThoughts';

interface ThoughtStep {
	key: string;
	title: string;
	icon: string;
	text: string;
	isToolCall?: boolean;
	toolName?: string;
	toolArguments?: string;
	toolResult?: string;
	completed?: boolean;
}

const props = withDefaults(
	defineProps<{
		blocks: ReActStreamEvent[][];
		active?: boolean;
		durationMs?: number;
	}>(),
	{ active: false, durationMs: undefined },
);

const expanded = ref(props.active);
const expandedTools = ref<Record<string, boolean>>({});
const expandedThoughts = ref<Record<string, boolean>>({});
const elapsedMs = ref(props.durationMs || 0);
let startedAt: number | null = null;
let elapsedTimer: ReturnType<typeof setInterval> | null = null;

const durationLabel = computed(() => {
	if (!props.active && props.durationMs === undefined) return '';
	return `${Math.max(1, Math.ceil(elapsedMs.value / 1000))} 秒`;
});

function updateElapsed() {
	if (startedAt !== null) elapsedMs.value = Date.now() - startedAt;
}

function startTimer() {
	if (!props.active || elapsedTimer) return;
	startedAt = Date.now() - (props.durationMs || 0);
	updateElapsed();
	elapsedTimer = setInterval(updateElapsed, 1000);
}

function stopTimer() {
	if (elapsedTimer) clearInterval(elapsedTimer);
	elapsedTimer = null;
	updateElapsed();
	startedAt = null;
}

watch(
	() => props.active,
	(activeValue) => {
		if (activeValue) startTimer();
		else stopTimer();
	},
);
watch(
	() => props.durationMs,
	(duration) => {
		if (duration !== undefined) elapsedMs.value = duration;
	},
);
onMounted(startTimer);
onUnmounted(stopTimer);

const steps = computed<ThoughtStep[]>(() => {
	const normalizedSteps: ThoughtStep[] = [];
	const toolSteps = new Map<string, ThoughtStep>();

	props.blocks.forEach((block, index) => {
		const first = block[0];
		if (first?.eventType === ReActEventType.TOOL_CALL) {
			const text = aggregateThoughtText(block);
			const toolStep: ThoughtStep = {
				key: `${first.stepId || first.eventType || 'step'}-${index}`,
				title: formatStepTitle(first.eventType),
				icon: formatStepIcon(first.eventType),
				text,
				isToolCall: true,
				toolName: extractToolName(text),
				toolArguments: formatToolPayload(extractToolArguments(text)),
				completed: false,
			};
			normalizedSteps.push(toolStep);
			toolSteps.set(normalizeToolStepId(first.stepId), toolStep);
			return;
		}

		if (first?.eventType === ReActEventType.TOOL_RESULT) {
			const toolStep = toolSteps.get(normalizeToolStepId(first.stepId));
			if (toolStep) {
				toolStep.toolResult = formatToolPayload(
					stripToolResultPrefix(aggregateThoughtText(block)),
				);
				toolStep.completed = true;
				return;
			}
		}

		normalizedSteps.push({
			key: `${first?.stepId || first?.eventType || 'step'}-${index}`,
			title: formatStepTitle(first?.eventType),
			icon: formatStepIcon(first?.eventType),
			text: aggregateThoughtText(block),
		});
	});

	return normalizedSteps;
});

function normalizeToolStepId(stepId?: string) {
	return (stepId || '').replace(/^tool-(?:call|result):/, '');
}

function extractToolName(text: string) {
	const prefix = '正在调用工具：';
	if (!text.startsWith(prefix)) return '未知工具';
	const toolText = text.slice(prefix.length).trim();
	const argumentStart = toolText.indexOf('{');
	return (
		(argumentStart === -1
			? toolText
			: toolText.slice(0, argumentStart)
		).trim() || '未知工具'
	);
}

function extractToolArguments(text: string) {
	const argumentStart = text.indexOf('{');
	return argumentStart === -1 ? '' : text.slice(argumentStart).trim();
}

function stripToolResultPrefix(text: string) {
	return text.replace(/^工具返回结果\s*/, '').trim();
}

function formatToolPayload(text: string) {
	if (!text) return '';
	try {
		return JSON.stringify(JSON.parse(text), null, 2);
	} catch {
		return text;
	}
}

function isToolExpanded(key: string) {
	return expandedTools.value[key] === true;
}

function clearTextSelection() {
	if (typeof window !== 'undefined') window.getSelection()?.removeAllRanges();
}

function isSelectionClick(event: MouseEvent) {
	return (
		event.detail > 0 &&
		typeof window !== 'undefined' &&
		Boolean(window.getSelection()?.toString().trim())
	);
}

function toggleToolStep(key: string, event: MouseEvent) {
	if (isSelectionClick(event)) return;
	expandedTools.value[key] = !isToolExpanded(key);
}

function isThoughtExpanded(key: string) {
	return expandedThoughts.value[key] !== false;
}

function toggleThoughtStep(key: string, event: MouseEvent) {
	if (isSelectionClick(event)) return;
	expandedThoughts.value[key] = !isThoughtExpanded(key);
}

function formatStepTitle(eventType?: string) {
	const labels: Record<string, string> = {
		[ReActEventType.THINKING]: '思考',
		[ReActEventType.TOOL_CALL]: '工具调用',
		[ReActEventType.TOOL_RESULT]: '工具结果',
		[ReActEventType.FINAL_ANSWER]: '最终回答',
		[ReActEventType.ERROR]: '错误',
	};
	if (eventType && labels[eventType]) return labels[eventType];
	return '智能体步骤';
}

function formatStepIcon(eventType?: string) {
	const icons: Record<string, string> = {
		[ReActEventType.TOOL_CALL]: 'mdi-magnify',
		[ReActEventType.TOOL_RESULT]: 'mdi-file-document-outline',
		[ReActEventType.FINAL_ANSWER]: 'mdi-check-circle-outline',
		[ReActEventType.ERROR]: 'mdi-alert-circle-outline',
	};
	return icons[eventType || ''] || '';
}
</script>

<style scoped>
.thoughts {
	width: 100%;
	color: #4b5567;
	user-select: text;
	-webkit-user-select: text;
}

.thoughts--active {
	color: #526078;
}

.thoughts-toggle {
	display: inline-flex;
	align-items: center;
	align-self: flex-start;
	min-height: 34px;
	gap: 4px;
	padding: 0;
	border: 0;
	background: transparent;
	color: #475569;
	font: inherit;
	text-align: left;
	cursor: pointer;
}

.thoughts-toggle:focus-visible {
	outline: 2px solid #86a8ff;
	outline-offset: -2px;
}

.thoughts-mark {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	color: #5a7cf5;
}

.thoughts-icon {
	color: #3f6ff5;
}

.pulse-icon {
	animation: pulse 1.4s ease-in-out infinite;
}

.thoughts-title {
	white-space: nowrap;
	font-size: 16px;
	font-weight: 600;
}

.thoughts-duration {
	font-weight: 500;
	color: #697386;
}

.chevron {
	margin-left: 0;
	color: #a0a9b8;
	transition: transform 0.2s ease;
}

.chevron.open {
	transform: rotate(180deg);
}

.steps-list {
	padding: 7px 0 12px 5px;
}

.step-item {
	display: flex;
	gap: 10px;
	min-height: 36px;
}

.step-item--tool {
	min-height: 36px;
}

.step-item--thought-collapsed {
	min-height: 30px;
}

.step-rail {
	position: relative;
	width: 18px;
	flex-shrink: 0;
	display: flex;
	align-items: flex-start;
	justify-content: center;
	padding-top: 3px;
}

.step-item--tool .step-rail {
	padding-top: 6px;
}

.step-icon {
	color: #687486;
}

.step-dot {
	z-index: 1;
	margin-top: 3px;
	width: 7px;
	height: 7px;
	border: 2px solid #aab6ca;
	border-radius: 50%;
	background: #fff;
}

.step-dot--active {
	border-color: #5a7cf5;
	background: #dbe5ff;
}

.step-line {
	position: absolute;
	top: 13px;
	bottom: -2px;
	width: 1px;
	background: #dfe5ee;
}

.step-content {
	min-width: 0;
	padding: 0 0 4px;
}

.step-item--tool .step-content {
	padding-bottom: 4px;
}

.step-item--thought-collapsed .step-content {
	padding-bottom: 2px;
}

.step-content p {
	margin: 0;
	font-size: 15px;
	line-height: 1.55;
	color: #68717e;
	word-break: break-word;
}

.thought-step-toggle {
	display: flex;
	align-items: flex-start;
	width: 100%;
	min-height: 26px;
	gap: 5px;
	padding: 0;
	border: 0;
	background: transparent;
	font: inherit;
	font-size: 15px;
	line-height: 1.55;
	text-align: left;
	color: #68717e;
	user-select: text;
	-webkit-user-select: text;
	cursor: pointer;
}

.thought-step-toggle:hover {
	color: #315efb;
}

.thought-step-toggle:focus-visible {
	outline: 2px solid #86a8ff;
	outline-offset: 3px;
	border-radius: 3px;
}

.thought-step-text {
	min-width: 0;
	flex: 1;
	word-break: break-word;
}

.thought-step-text--collapsed {
	display: -webkit-box;
	overflow: hidden;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 1;
}

.thought-step-chevron {
	flex-shrink: 0;
	margin-top: 3px;
	color: #a0a9b8;
	transition: transform 0.2s ease;
}

.thought-step-chevron.open {
	transform: rotate(180deg);
}

.tool-step-toggle {
	display: inline-flex;
	align-items: center;
	max-width: 100%;
	gap: 5px;
	min-height: 26px;
	padding: 0;
	border: 0;
	background: transparent;
	font: inherit;
	font-size: 15px;
	line-height: 1.55;
	text-align: left;
	color: #68717e;
	user-select: text;
	-webkit-user-select: text;
	cursor: pointer;
}

.tool-step-toggle:hover {
	color: #315efb;
}

.tool-step-toggle:focus-visible {
	outline: 2px solid #86a8ff;
	outline-offset: 3px;
	border-radius: 3px;
}

.tool-step-chevron {
	flex-shrink: 0;
	color: #a0a9b8;
	transition: transform 0.2s ease;
}

.tool-step-chevron.open {
	transform: rotate(180deg);
}

.tool-step-details {
	max-width: 680px;
	margin-top: 5px;
	padding: 9px 11px;
	border: 1px solid #e5e9f0;
	border-radius: 7px;
	background: #f8faff;
}

.tool-detail-section + .tool-detail-section {
	margin-top: 9px;
}

.tool-detail-label {
	display: block;
	margin-bottom: 4px;
	font-size: 11px;
	font-weight: 600;
	line-height: 1.4;
	color: #7b879a;
}

.tool-detail-section pre {
	margin: 0;
	font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
	font-size: 12px;
	line-height: 1.55;
	color: #46536a;
	white-space: pre-wrap;
	word-break: break-word;
}

.tool-details-enter-active,
.tool-details-leave-active {
	max-height: 460px;
	overflow: hidden;
	transition:
		max-height 0.2s ease,
		opacity 0.2s ease;
}

.tool-details-enter-from,
.tool-details-leave-to {
	max-height: 0;
	opacity: 0;
}

.thoughts-expand-enter-active,
.thoughts-expand-leave-active {
	max-height: 450px;
	overflow: hidden;
	transition:
		max-height 0.2s ease,
		opacity 0.2s ease;
}

.thoughts-expand-enter-from,
.thoughts-expand-leave-to {
	max-height: 0;
	opacity: 0;
}

@keyframes pulse {
	0%,
	100% {
		opacity: 0.55;
		transform: scale(0.92);
	}
	50% {
		opacity: 1;
		transform: scale(1);
	}
}
</style>
