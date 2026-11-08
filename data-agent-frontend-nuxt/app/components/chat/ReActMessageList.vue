<template>
	<div ref="listRef" class="react-message-list" @scroll="handleScroll">
		<div
			v-if="!store.currentSession || messages.length === 0"
			class="react-welcome"
		>
			<div class="welcome-icon">
				<v-icon size="26" color="#315efb">mdi-sparkles</v-icon>
			</div>
			<h2>你好，我是 {{ store.currentAgentName || '智能助手' }}</h2>
			<p>
				{{
					store.currentAgentDescription ||
					'我会拆解问题、调用工具并逐步完成分析。'
				}}
			</p>
			<div class="welcome-hint">
				<v-icon size="16">mdi-lightbulb-outline</v-icon>
				<span>你可以直接描述目标，我会自主规划下一步。</span>
			</div>
		</div>

		<div v-else class="messages-column">
			<article
				v-for="message in messages"
				:key="message.id || `${message.role}-${message.createTime}`"
				class="message-block"
				:class="[
					`message-block--${message.role}`,
					{
						'message-block--timeline': message.messageType === 'timeline',
					},
				]"
			>
				<div v-if="message.role === 'user'" class="user-message">
					<div v-if="isEditing(message)" class="inline-editor">
						<textarea
							:ref="setEditTextareaRef"
							v-model="editingText"
							class="inline-editor-textarea"
							rows="1"
							aria-label="编辑用户消息"
							@keydown.esc.prevent="cancelEdit"
							@keydown.enter.exact.prevent="submitEdit"
						/>
						<div class="inline-editor-actions">
							<button
								type="button"
								class="inline-editor-button inline-editor-button--cancel"
								@click="cancelEdit"
							>
								取消
							</button>
							<button
								type="button"
								class="inline-editor-button inline-editor-button--send"
								:disabled="!editingText.trim() || store.isStreaming"
								@click="submitEdit"
							>
								发送
							</button>
						</div>
					</div>
					<div v-else class="user-bubble">{{ message.content }}</div>
				</div>
				<template v-else>
					<div
						v-if="message.messageType === 'timeline'"
						class="assistant-content"
					>
						<ReActThoughtSteps
							:blocks="parseTimeline(message.content).blocks"
							:duration-ms="parseTimeline(message.content).durationMs"
						/>
					</div>
					<div
						v-else-if="message.messageType === 'error'"
						class="inline-status inline-status--error"
					>
						<v-icon size="16">mdi-alert-circle-outline</v-icon>
						{{ message.content }}
					</div>
					<div
						v-else-if="message.messageType === 'warning'"
						class="inline-status inline-status--warning"
					>
						<v-icon size="16">mdi-information-outline</v-icon>
						{{ message.content }}
					</div>
					<div
						v-else
						class="assistant-content markdown-content"
						v-html="renderMarkdown(message.content)"
					/>
				</template>
				<div
					v-if="
						(shouldShowActions(message) || store.hasTrace(message)) &&
						!isEditing(message)
					"
					class="message-actions"
					:class="{ 'message-actions--user': message.role === 'user' }"
				>
					<time v-if="formatTime(message.createTime)" class="message-time">
						{{ formatTime(message.createTime) }}
					</time>
					<button
						v-if="store.hasTrace(message)"
						type="button"
						class="message-action"
						title="查看消息轨迹"
						aria-label="查看消息轨迹"
						@click="store.openTraceForMessage(message)"
					>
						<v-icon size="17">mdi-timeline-text-outline</v-icon>
					</button>
					<button
						type="button"
						class="message-action"
						:title="isCopied(message) ? '已复制' : '复制消息'"
						:aria-label="isCopied(message) ? '已复制' : '复制消息'"
						@click="copyMessage(message)"
					>
						<v-icon size="17">
							{{ isCopied(message) ? 'mdi-check' : 'mdi-content-copy' }}
						</v-icon>
					</button>
					<button
						v-if="message.role === 'user'"
						type="button"
						class="message-action"
						title="编辑消息"
						aria-label="编辑消息"
						@click="startEdit(message)"
					>
						<v-icon size="18">mdi-pencil-outline</v-icon>
					</button>
				</div>
			</article>

			<div
				v-if="store.isStreaming && store.stepBlocks.length"
				class="streaming-block"
			>
				<ReActThoughtSteps
					:blocks="store.stepBlocks"
					:duration-ms="store.stepDurationMs"
					active
				/>
			</div>
			<div v-else-if="store.isStreaming" class="thinking-line">
				<span class="thinking-spinner"><i /><i /><i /></span>
				<span>正在思考...</span>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import {
	computed,
	nextTick,
	onUnmounted,
	ref,
	watch,
	type ComponentPublicInstance,
} from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useReActChatStore } from '~/stores/reactChat';
import type { ChatMessage } from '~/services/chat';
import type { ReActStreamEvent } from '~/services/react';
import ReActThoughtSteps from './ReActThoughtSteps.vue';

const store = useReActChatStore();
const listRef = ref<HTMLElement | null>(null);
const { renderECharts } = useEchartsRenderer();
const copiedMessageId = ref<number | undefined>();
const editingMessage = ref<ChatMessage | null>(null);
const editingText = ref('');
const editTextareaRef = ref<HTMLTextAreaElement | null>(null);
const shouldAutoScroll = ref(true);
const AUTO_SCROLL_THRESHOLD = 64;
let copyResetTimer: ReturnType<typeof setTimeout> | null = null;
let scrollRafId: number | null = null;

const messages = computed<ChatMessage[]>(() => store.currentMessages);

function parseTimeline(content: string) {
	try {
		const parsed = JSON.parse(content) as
			| ReActStreamEvent[][]
			| { blocks?: ReActStreamEvent[][]; durationMs?: number };
		if (Array.isArray(parsed)) return { blocks: parsed, durationMs: undefined };
		return {
			blocks: Array.isArray(parsed.blocks) ? parsed.blocks : [],
			durationMs: parsed.durationMs,
		};
	} catch {
		return { blocks: [], durationMs: undefined };
	}
}

function renderMarkdown(content: string) {
	if (!content) return '';
	return DOMPurify.sanitize(renderMarkdownContent(content), {
		ADD_TAGS: ['div'],
		ADD_ATTR: ['style', 'class'],
		RETURN_TRUSTED_TYPE: false,
	});
}

function shouldShowActions(message: ChatMessage) {
	return message.role === 'user' || message.messageType === 'text';
}

function isCopied(message: ChatMessage) {
	return message.id !== undefined && copiedMessageId.value === message.id;
}

function isEditing(message: ChatMessage) {
	return (
		editingMessage.value === message ||
		(message.id !== undefined && editingMessage.value?.id === message.id)
	);
}

function setEditTextareaRef(element: Element | ComponentPublicInstance | null) {
	editTextareaRef.value =
		element instanceof HTMLTextAreaElement ? element : null;
}

function startEdit(message: ChatMessage) {
	if (store.isStreaming || message.role !== 'user') return;
	editingMessage.value = message;
	editingText.value = message.content;
	nextTick(() => {
		const textarea = editTextareaRef.value;
		if (!textarea) return;
		textarea.focus();
		textarea.setSelectionRange(textarea.value.length, textarea.value.length);
	});
}

function cancelEdit() {
	editingMessage.value = null;
	editingText.value = '';
}

async function submitEdit() {
	const query = editingText.value.trim();
	if (!query || !editingMessage.value || store.isStreaming) return;
	cancelEdit();
	try {
		await store.sendMessage(query);
	} catch (error) {
		console.error('重新发送编辑消息失败', error);
	}
}

function formatTime(value?: Date | string) {
	if (!value) return '';
	const date = value instanceof Date ? value : new Date(value);
	if (Number.isNaN(date.getTime())) return '';
	return date.toLocaleTimeString('zh-CN', {
		hour: '2-digit',
		minute: '2-digit',
	});
}

async function copyMessage(message: ChatMessage) {
	if (!navigator.clipboard) return;
	try {
		await navigator.clipboard.writeText(message.content);
		copiedMessageId.value = message.id;
		if (copyResetTimer) clearTimeout(copyResetTimer);
		copyResetTimer = setTimeout(() => {
			copiedMessageId.value = undefined;
		}, 1500);
	} catch (error) {
		console.error('复制消息失败', error);
	}
}

function isNearBottom(element: HTMLElement) {
	return (
		element.scrollHeight - element.scrollTop - element.clientHeight <=
		AUTO_SCROLL_THRESHOLD
	);
}

function handleScroll() {
	const element = listRef.value;
	if (!element) return;
	shouldAutoScroll.value = isNearBottom(element);
}

function scrollToBottom() {
	nextTick(() => {
		if (scrollRafId !== null) cancelAnimationFrame(scrollRafId);
		scrollRafId = requestAnimationFrame(() => {
			const element = listRef.value;
			if (element && shouldAutoScroll.value) {
				element.scrollTop = element.scrollHeight;
			}
			renderECharts(element);
			scrollRafId = null;
		});
	});
}

watch(
	() => [store.currentMessages, store.stepBlocks, store.isStreaming],
	scrollToBottom,
	{ deep: true },
);

onUnmounted(() => {
	if (copyResetTimer) clearTimeout(copyResetTimer);
	if (scrollRafId !== null) cancelAnimationFrame(scrollRafId);
});
</script>

<style scoped>
.react-message-list {
	flex: 1;
	min-height: 0;
	overflow-y: auto;
	scroll-behavior: smooth;
	background: #fff;
	scrollbar-color: #aeb8c6 transparent;
	scrollbar-width: thin;
}

.react-message-list::-webkit-scrollbar {
	width: 8px;
}

.react-message-list::-webkit-scrollbar-track {
	background: transparent;
}

.react-message-list::-webkit-scrollbar-thumb {
	border: 2px solid transparent;
	border-radius: 999px;
	background: #aeb8c6;
	background-clip: padding-box;
}

.react-message-list::-webkit-scrollbar-thumb:hover {
	background: #7f8da1;
	background-clip: padding-box;
}

.messages-column {
	width: min(820px, calc(100% - 40px));
	margin: 0 auto;
	padding: 36px 0 150px;
}

.message-block {
	margin-bottom: 30px;
}

.message-block--timeline {
	margin-bottom: 8px;
}

.message-block--user {
	display: flex;
	flex-direction: column;
	align-items: flex-end;
}

.user-message {
	width: fit-content;
	max-width: min(600px, 78%);
}

.user-bubble {
	display: block;
	width: fit-content;
	max-width: 100%;
	padding: 11px 16px;
	border-radius: 14px 14px 4px 14px;
	background: #eef3ff;
	color: #263d87;
	font-size: 14px;
	line-height: 1.7;
	white-space: pre-wrap;
	user-select: text;
	-webkit-user-select: text;
	overflow-wrap: anywhere;
	word-break: normal;
}

.inline-editor {
	width: min(600px, 78vw);
	max-width: 100%;
	padding: 12px 14px 10px;
	border: 2px solid #4169f5;
	border-radius: 16px;
	background: #fff;
	box-sizing: border-box;
	box-shadow: 0 3px 12px rgba(65, 105, 245, 0.08);
}

.inline-editor-textarea {
	display: block;
	width: 100%;
	min-height: 58px;
	max-height: 180px;
	padding: 0;
	border: 0;
	outline: 0;
	resize: vertical;
	background: transparent;
	font: inherit;
	font-size: 16px;
	line-height: 1.6;
	color: #202938;
}

.inline-editor-actions {
	display: flex;
	justify-content: flex-end;
	gap: 10px;
	margin-top: 8px;
}

.inline-editor-button {
	min-width: 62px;
	min-height: 34px;
	padding: 0 15px;
	border: 1px solid #e0e4eb;
	border-radius: 18px;
	background: #fff;
	font-size: 13px;
	cursor: pointer;
}

.inline-editor-button--cancel {
	color: #526078;
}

.inline-editor-button--cancel:hover {
	background: #f5f6f8;
}

.inline-editor-button--send {
	border-color: #4169f5;
	background: #4169f5;
	color: #fff;
}

.inline-editor-button--send:hover:not(:disabled) {
	border-color: #3158e5;
	background: #3158e5;
}

.inline-editor-button:disabled {
	opacity: 0.5;
	cursor: not-allowed;
}

.message-actions {
	display: flex;
	align-items: center;
	justify-content: flex-start;
	gap: 3px;
	min-height: 28px;
	margin-top: 4px;
	color: #8b95a5;
	opacity: 0;
	visibility: hidden;
	pointer-events: none;
	transition:
		opacity 0.15s ease,
		visibility 0.15s ease;
}

.message-actions--user {
	justify-content: flex-end;
	width: fit-content;
	max-width: min(600px, 78%);
}

.message-block:hover .message-actions,
.message-block:focus-within .message-actions {
	opacity: 1;
	visibility: visible;
	pointer-events: auto;
}

.message-time {
	margin: 0 5px;
	font-size: 12px;
	line-height: 28px;
	color: #9aa3b1;
}

.message-action {
	display: grid;
	place-items: center;
	width: 28px;
	height: 28px;
	padding: 0;
	border: 0;
	border-radius: 6px;
	background: transparent;
	color: #8993a2;
	cursor: pointer;
}

.message-action:hover {
	background: #f1f3f6;
	color: #526078;
}

.message-action:focus-visible {
	outline: 2px solid #8aa8ff;
	outline-offset: 1px;
}

.assistant-content {
	max-width: 760px;
	color: #273244;
	font-size: 15px;
	line-height: 1.8;
	user-select: text;
	-webkit-user-select: text;
}

.markdown-content :deep(p) {
	margin: 0 0 12px;
}

.markdown-content :deep(p:last-child) {
	margin-bottom: 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
	margin: 20px 0 8px;
	color: #1b2433;
	font-weight: 700;
	line-height: 1.4;
}

.markdown-content :deep(code:not(pre code)) {
	padding: 2px 5px;
	border-radius: 4px;
	background: #f1f3f7;
	font-size: 0.9em;
	color: #3155ba;
}

.markdown-content :deep(pre) {
	max-width: 100%;
	overflow: auto;
	border-radius: 8px;
}

.markdown-content :deep(a) {
	color: #315efb;
}

.inline-status {
	display: inline-flex;
	align-items: center;
	gap: 7px;
	padding: 9px 12px;
	border-radius: 8px;
	font-size: 13px;
}

.inline-status--error {
	color: #b42318;
	background: #fff2f0;
}

.inline-status--warning {
	color: #93620b;
	background: #fff8e6;
}

.streaming-block {
	margin-top: -8px;
}

.thinking-line {
	display: flex;
	align-items: center;
	gap: 9px;
	color: #8a94a6;
	font-size: 13px;
}

.thinking-spinner {
	display: inline-flex;
	gap: 3px;
}

.thinking-spinner i {
	width: 5px;
	height: 5px;
	border-radius: 50%;
	background: #7d98f6;
	animation: bounce 1.2s infinite ease-in-out;
}

.thinking-spinner i:nth-child(2) {
	animation-delay: 0.15s;
}
.thinking-spinner i:nth-child(3) {
	animation-delay: 0.3s;
}

.react-welcome {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	min-height: 100%;
	padding: 48px 24px 150px;
	text-align: center;
}

.welcome-icon {
	display: grid;
	place-items: center;
	width: 54px;
	height: 54px;
	margin-bottom: 20px;
	border: 1px solid #dce5ff;
	border-radius: 16px;
	background: #f3f6ff;
}

.react-welcome h2 {
	margin: 0 0 8px;
	font-size: 24px;
	font-weight: 650;
	color: #202938;
}

.react-welcome p {
	max-width: 480px;
	margin: 0;
	color: #7d8798;
	font-size: 14px;
	line-height: 1.7;
}

.welcome-hint {
	display: flex;
	align-items: center;
	gap: 7px;
	margin-top: 24px;
	padding: 8px 12px;
	border-radius: 7px;
	background: #f8f9fc;
	color: #929bad;
	font-size: 12px;
}

@keyframes bounce {
	0%,
	80%,
	100% {
		transform: scale(0.6);
		opacity: 0.5;
	}
	40% {
		transform: scale(1);
		opacity: 1;
	}
}

@media (max-width: 640px) {
	.messages-column {
		width: calc(100% - 24px);
		padding-top: 22px;
	}

	.user-bubble {
		max-width: 88%;
	}

	.user-message {
		max-width: 88%;
	}

	.assistant-content {
		font-size: 14px;
	}

	.react-welcome h2 {
		font-size: 21px;
	}
}
</style>
