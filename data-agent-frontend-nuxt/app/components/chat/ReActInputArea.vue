<template>
	<div class="react-composer-shell">
		<form
			class="react-composer"
			@submit.prevent="handleSend"
			@pointerdown="handleComposerPointerDown"
		>
			<textarea
				ref="textareaRef"
				v-model="inputText"
				class="composer-textarea"
				rows="2"
				:disabled="store.isStreaming"
				placeholder="给智能助手发送消息"
				aria-label="消息内容"
				@keydown="handleKeydown"
				@input="autoResize"
			/>
			<div class="composer-toolbar">
				<div class="toolbar-left">
					<span
						class="reasoning-mode"
						title="当前对话使用自主推理模式"
						aria-label="当前对话使用自主推理模式"
					>
						<v-icon size="15">mdi-brain</v-icon>
						<span>自主推理模式</span>
					</span>
					<div class="datasource-picker-wrap" @click.stop>
						<button
							type="button"
							class="toolbar-chip datasource-chip"
							:disabled="store.isStreaming"
							:aria-expanded="showDatasources"
							aria-haspopup="listbox"
							title="选择数据源"
							@click="toggleDatasources"
						>
							<v-icon size="15">mdi-database-outline</v-icon>
							<span>{{ store.activeDatasource?.name || '选择数据源' }}</span>
							<v-icon size="15">{{
								showDatasources ? 'mdi-chevron-up' : 'mdi-chevron-down'
							}}</v-icon>
						</button>
						<div v-if="showDatasources" class="datasource-menu" role="listbox">
							<button
								v-for="datasource in store.allDatasources"
								:key="datasource.id"
								type="button"
								class="datasource-option"
								:class="{
									active: store.activeDatasource?.id === datasource.id,
								}"
								:aria-selected="store.activeDatasource?.id === datasource.id"
								role="option"
								@click="selectDatasource(datasource)"
							>
								<span class="datasource-option-main">
									<span class="datasource-option-name">{{
										datasource.name || '未命名数据源'
									}}</span>
									<span class="datasource-option-type">{{
										datasource.type?.toUpperCase() || '数据库'
									}}</span>
								</span>
								<v-icon
									v-if="store.activeDatasource?.id === datasource.id"
									size="14"
								>
									mdi-check
								</v-icon>
							</button>
							<div v-if="!store.allDatasources.length" class="datasource-empty">
								暂无可用数据源
							</div>
						</div>
					</div>
				</div>
				<div class="toolbar-right">
					<div class="model-picker-wrap" @click.stop>
						<button
							type="button"
							class="toolbar-chip"
							:disabled="store.isStreaming || !store.chatModels.length"
							@click="toggleModels"
						>
							<span>{{
								store.activeModelConfig?.modelName || '选择模型'
							}}</span>
							<v-icon size="15">{{
								showModels ? 'mdi-chevron-up' : 'mdi-chevron-down'
							}}</v-icon>
						</button>
						<div v-if="showModels" class="model-menu">
							<button
								v-for="model in store.chatModels"
								:key="model.id"
								type="button"
								class="model-option"
								:class="{ active: model.id === store.activeModelConfig?.id }"
								@click="selectModel(model.id)"
							>
								<span>{{ model.modelName }}</span>
								<v-icon
									v-if="model.id === store.activeModelConfig?.id"
									size="14"
									>mdi-check</v-icon
								>
							</button>
						</div>
					</div>
					<span v-if="store.isStreaming" class="streaming-label">正在生成</span>
					<button
						v-if="store.isStreaming"
						type="button"
						class="send-button stop-button"
						title="停止生成"
						aria-label="停止生成"
						@click="handleStop"
					>
						<v-icon size="18">mdi-stop</v-icon>
					</button>
					<button
						v-else
						type="submit"
						class="send-button"
						:disabled="!inputText.trim() || !store.currentSession"
						title="发送消息"
						aria-label="发送消息"
					>
						<v-icon size="19">mdi-arrow-up</v-icon>
					</button>
				</div>
			</div>
		</form>
		<p class="composer-note">AI 生成的内容可能存在错误，请核验重要信息</p>
	</div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useReActChatStore } from '~/stores/reactChat';

const store = useReActChatStore();
const inputText = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const showModels = ref(false);
const showDatasources = ref(false);
const MIN_TEXTAREA_HEIGHT = 52;
const AUTO_RESIZE_MAX_HEIGHT = 280;
const MAX_TEXTAREA_HEIGHT = AUTO_RESIZE_MAX_HEIGHT + 100;
let isManualHeight = false;
let resizeStartY = 0;
let resizeStartHeight = 0;

function autoResize() {
	const textarea = textareaRef.value;
	if (!textarea) return;
	if (!inputText.value) isManualHeight = false;
	const currentHeight = textarea.offsetHeight;
	textarea.style.height = 'auto';
	const contentHeight = textarea.scrollHeight;
	const targetHeight = isManualHeight
		? Math.min(
				Math.max(currentHeight, contentHeight, MIN_TEXTAREA_HEIGHT),
				MAX_TEXTAREA_HEIGHT,
			)
		: Math.min(
				Math.max(contentHeight, MIN_TEXTAREA_HEIGHT),
				AUTO_RESIZE_MAX_HEIGHT,
			);
	textarea.style.height = `${targetHeight}px`;
}

function handleComposerPointerDown(event: PointerEvent) {
	const composer = event.currentTarget as HTMLElement;
	const distanceFromTop = event.clientY - composer.getBoundingClientRect().top;
	if (distanceFromTop > 8) return;
	startResize(event);
}

function startResize(event: PointerEvent) {
	if (store.isStreaming || !textareaRef.value) return;
	event.preventDefault();
	isManualHeight = true;
	resizeStartY = event.clientY;
	resizeStartHeight = textareaRef.value.offsetHeight;
	document.body.style.userSelect = 'none';
	document.addEventListener('pointermove', resizeTextarea);
	document.addEventListener('pointerup', stopResize, { once: true });
	document.addEventListener('pointercancel', stopResize, { once: true });
}

function resizeTextarea(event: PointerEvent) {
	const textarea = textareaRef.value;
	if (!textarea) return;
	const nextHeight = resizeStartHeight + resizeStartY - event.clientY;
	textarea.style.height = `${Math.min(
		Math.max(nextHeight, MIN_TEXTAREA_HEIGHT),
		MAX_TEXTAREA_HEIGHT,
	)}px`;
}

function stopResize() {
	document.removeEventListener('pointermove', resizeTextarea);
	document.body.style.userSelect = '';
}

function toggleModels() {
	if (store.isStreaming || !store.chatModels.length) return;
	showModels.value = !showModels.value;
	showDatasources.value = false;
}

function toggleDatasources() {
	if (store.isStreaming) return;
	showDatasources.value = !showDatasources.value;
	showModels.value = false;
}

async function selectDatasource(
	datasource: (typeof store.allDatasources)[number],
) {
	showDatasources.value = false;
	await store.switchDatasource(datasource);
}

async function selectModel(id?: number) {
	showModels.value = false;
	if (id !== undefined) await store.switchModel(id);
}

async function handleSend() {
	const query = inputText.value.trim();
	if (!query || !store.currentSession || store.isStreaming) return;
	inputText.value = '';
	isManualHeight = false;
	await nextTick();
	if (textareaRef.value)
		textareaRef.value.style.height = `${MIN_TEXTAREA_HEIGHT}px`;
	try {
		await store.sendMessage(query);
	} catch (error) {
		console.error('发送失败', error);
	}
}

function handleKeydown(event: KeyboardEvent) {
	if (event.key === 'Enter' && event.altKey) {
		event.preventDefault();
		const textarea = event.currentTarget as HTMLTextAreaElement;
		const start = textarea.selectionStart;
		const end = textarea.selectionEnd;
		inputText.value = `${inputText.value.slice(0, start)}\n${inputText.value.slice(end)}`;
		nextTick(() => {
			textarea.selectionStart = start + 1;
			textarea.selectionEnd = start + 1;
			autoResize();
		});
		return;
	}
	if (
		event.key !== 'Enter' ||
		event.altKey ||
		event.shiftKey ||
		event.ctrlKey ||
		event.metaKey
	)
		return;
	event.preventDefault();
	void handleSend();
}

async function handleStop() {
	try {
		await store.stopStreaming();
	} catch (error) {
		console.error('停止失败', error);
	}
}

function closeMenus() {
	showModels.value = false;
	showDatasources.value = false;
}

onMounted(() => document.addEventListener('click', closeMenus));
onUnmounted(() => {
	document.removeEventListener('click', closeMenus);
	document.removeEventListener('pointermove', resizeTextarea);
	document.removeEventListener('pointerup', stopResize);
	document.removeEventListener('pointercancel', stopResize);
	document.body.style.userSelect = '';
});
</script>

<style scoped>
.react-composer-shell {
	position: relative;
	z-index: 2;
	flex-shrink: 0;
	padding: 0 24px 12px;
	background: linear-gradient(to bottom, rgba(255, 255, 255, 0), #fff 18px);
}

.react-composer {
	position: relative;
	width: min(820px, 100%);
	margin: 0 auto;
	padding: 12px 14px 10px;
	border: 1px solid #dfe4ec;
	border-radius: 14px;
	background: #fff;
	box-shadow: 0 5px 20px rgba(39, 55, 86, 0.08);
	transition:
		border-color 0.15s ease,
		box-shadow 0.15s ease;
}

.react-composer::before {
	position: absolute;
	top: 0;
	right: 0;
	left: 0;
	height: 8px;
	content: '';
	cursor: ns-resize;
}

.react-composer:focus-within {
	border-color: #b9caff;
	box-shadow: 0 6px 24px rgba(49, 94, 251, 0.1);
}

.composer-textarea {
	display: block;
	width: 100%;
	height: 52px;
	min-height: 52px;
	padding: 2px 2px 4px;
	border: 0;
	outline: 0;
	resize: none;
	overflow-y: auto;
	background: transparent;
	font: inherit;
	font-size: 14px;
	line-height: 1.65;
	color: #293449;
	scrollbar-width: thin;
	scrollbar-color: #d9dde4 transparent;
}

.composer-textarea::-webkit-scrollbar {
	width: 7px;
}

.composer-textarea::-webkit-scrollbar-track {
	background: transparent;
}

.composer-textarea::-webkit-scrollbar-thumb {
	border: 2px solid transparent;
	border-radius: 8px;
	background: #d9dde4;
	background-clip: content-box;
}

.composer-textarea::-webkit-scrollbar-thumb:hover {
	background: #c4c9d2;
	background-clip: content-box;
}

.composer-textarea::placeholder {
	color: #a0a8b6;
}

.composer-textarea:disabled {
	opacity: 0.65;
}

.composer-toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	min-height: 32px;
	margin-top: 5px;
}

.toolbar-left,
.toolbar-right {
	display: flex;
	align-items: center;
	gap: 10px;
	min-width: 0;
}

.toolbar-left {
	margin-right: 12px;
}

.reasoning-mode {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	min-height: 28px;
	padding: 0 2px;
	font-size: 12px;
	color: #69758a;
	white-space: nowrap;
}

.reasoning-mode .v-icon {
	color: #5572db;
}

.toolbar-chip,
.react-chip {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	min-height: 28px;
	padding: 0 2px;
	border: 0;
	border-radius: 0;
	background: transparent;
	font-size: 13px;
	color: #596273;
}

.toolbar-chip {
	cursor: pointer;
}

.toolbar-chip:hover:not(:disabled) {
	color: #315efb;
}

.toolbar-chip:disabled {
	cursor: not-allowed;
	opacity: 0.55;
}

.react-chip {
	border-color: #dbe4ff;
	background: #f4f6ff;
	color: #5572db;
}

.model-picker-wrap {
	position: relative;
}

.datasource-picker-wrap {
	position: relative;
}

.datasource-chip {
	max-width: 220px;
	color: #596273;
}

.datasource-chip .v-icon:first-child {
	color: #5572db;
}

.datasource-chip > span {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.datasource-menu {
	position: absolute;
	bottom: calc(100% + 6px);
	left: 0;
	z-index: 10;
	min-width: 220px;
	max-width: min(280px, calc(100vw - 32px));
	max-height: 260px;
	overflow-y: auto;
	padding: 4px;
	border: 1px solid #e5e9f0;
	border-radius: 8px;
	background: #fff;
	box-shadow: 0 8px 24px rgba(40, 52, 78, 0.14);
}

.datasource-option {
	display: flex;
	align-items: center;
	justify-content: space-between;
	width: 100%;
	min-height: 36px;
	padding: 6px 9px;
	border: 0;
	border-radius: 5px;
	background: transparent;
	font-size: 12px;
	text-align: left;
	color: #475569;
	cursor: pointer;
}

.datasource-option:hover,
.datasource-option.active {
	background: #f2f5ff;
	color: #315efb;
}

.datasource-option-main {
	display: flex;
	align-items: baseline;
	gap: 8px;
	min-width: 0;
}

.datasource-option-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.datasource-option-type {
	flex-shrink: 0;
	font-size: 10px;
	color: #94a3b8;
}

.datasource-empty {
	padding: 10px 9px;
	font-size: 12px;
	color: #8b95a7;
	text-align: center;
}

.model-menu {
	position: absolute;
	bottom: calc(100% + 6px);
	right: 0;
	min-width: 180px;
	max-width: min(260px, calc(100vw - 40px));
	padding: 4px;
	border: 1px solid #e5e9f0;
	border-radius: 8px;
	background: #fff;
	box-shadow: 0 8px 24px rgba(40, 52, 78, 0.14);
}

.model-option {
	display: flex;
	align-items: center;
	justify-content: space-between;
	width: 100%;
	padding: 8px 9px;
	border: 0;
	border-radius: 5px;
	background: transparent;
	font-size: 12px;
	text-align: left;
	color: #475569;
	cursor: pointer;
}

.model-option:hover,
.model-option.active {
	background: #f2f5ff;
	color: #315efb;
}

.streaming-label {
	font-size: 11px;
	color: #8893a5;
}

.send-button {
	display: grid;
	place-items: center;
	width: 31px;
	height: 31px;
	padding: 0;
	border: 0;
	border-radius: 50%;
	background: #929292;
	color: #fff;
	cursor: pointer;
	transition:
		background 0.15s ease,
		opacity 0.15s ease;
}

.send-button:hover:not(:disabled) {
	background: #7c7c7c;
}

.send-button:disabled {
	background: #d6d8dc;
	cursor: not-allowed;
}

.stop-button {
	background: #f2f4f8;
	color: #566176;
}

.stop-button:hover:not(:disabled) {
	background: #e6eaf2;
}

.composer-note {
	margin: 7px 0 0;
	font-size: 10px;
	line-height: 1.4;
	text-align: center;
	color: #a2a9b5;
}

@media (max-width: 640px) {
	.react-composer-shell {
		padding: 0 10px 10px;
	}

	.react-composer {
		padding: 10px 11px 8px;
	}

	.reasoning-mode {
		font-size: 11px;
	}

	.datasource-chip {
		max-width: 150px;
	}
}

@media (max-width: 520px) {
	.composer-toolbar {
		flex-wrap: wrap;
		row-gap: 4px;
	}

	.toolbar-left {
		flex: 1 1 100%;
		margin-right: 0;
	}

	.toolbar-right {
		margin-left: auto;
	}
}
</style>
