<template>
	<section class="react-chat" aria-label="ReAct 对话">
		<nav class="react-tabs" role="tablist" aria-label="ReAct 视图">
			<button
				type="button"
				role="tab"
				:aria-selected="activeView === 'chat'"
				:class="{ 'react-tab--active': activeView === 'chat' }"
				@click="selectView('chat')"
			>
				<v-icon size="16">mdi-message-text-outline</v-icon>
				<span>对话</span>
			</button>
			<button
				type="button"
				role="tab"
				:aria-selected="activeView === 'trace'"
				:class="{ 'react-tab--active': activeView === 'trace' }"
				@click="selectView('trace')"
			>
				<v-icon size="16">mdi-timeline-text-outline</v-icon>
				<span>轨迹</span>
				<span v-if="store.traceRounds.length" class="react-tab-count">
					{{ store.traceRounds.length }}
				</span>
			</button>
		</nav>

		<div class="react-content">
			<ReActMessageList v-if="activeView === 'chat'" />
			<ReActTraceDrawer v-else />
		</div>
		<ReActInputArea />
	</section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useReActChatStore } from '~/stores/reactChat';
import ReActMessageList from './ReActMessageList.vue';
import ReActInputArea from './ReActInputArea.vue';
import ReActTraceDrawer from './ReActTraceDrawer.vue';

const store = useReActChatStore();
type ReactView = 'chat' | 'trace';

const activeView = computed<ReactView>({
	get: () => (store.traceDrawerOpen ? 'trace' : 'chat'),
	set: (value) => {
		if (value === 'trace') store.openTraceOverview();
		else store.closeTrace();
	},
});

function selectView(view: ReactView) {
	activeView.value = view;
}
</script>

<style scoped>
.react-chat {
	position: relative;
	min-width: 0;
	min-height: 0;
	height: 100%;
	display: flex;
	flex-direction: column;
	background: #fff;
}

.react-header {
	height: 64px;
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 28px;
	border-bottom: 1px solid #edf0f5;
	background: rgba(255, 255, 255, 0.96);
}

.react-title-wrap,
.react-header-actions {
	display: flex;
	align-items: center;
}

.react-title-wrap {
	gap: 10px;
}

.react-title-wrap h1 {
	margin: 0;
	font-size: 15px;
	font-weight: 650;
	line-height: 1.25;
	color: #1f2937;
}

.react-title-wrap p {
	display: flex;
	align-items: center;
	gap: 5px;
	margin: 3px 0 0;
	font-size: 11px;
	color: #8b95a7;
}

.status-dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: #22c55e;
}

.react-header-actions {
	gap: 10px;
	color: #7c8799;
}

.react-tabs {
	display: flex;
	align-items: stretch;
	height: 46px;
	flex-shrink: 0;
	gap: 4px;
	padding: 0 28px;
	border-bottom: 1px solid #edf0f5;
	background: #fff;
}

.react-tabs button {
	position: relative;
	display: inline-flex;
	align-items: center;
	gap: 6px;
	min-width: 76px;
	justify-content: center;
	padding: 0 10px;
	border: 0;
	background: transparent;
	color: #8590a1;
	font: inherit;
	font-size: 13px;
	cursor: pointer;
}

.react-tabs button:hover {
	color: #315efb;
	background: #f8faff;
}

.react-tabs button:focus-visible {
	outline: 2px solid #8aa8ff;
	outline-offset: -2px;
}

.react-tabs button::after {
	position: absolute;
	right: 10px;
	bottom: -1px;
	left: 10px;
	height: 2px;
	background: transparent;
	content: '';
}

.react-tabs button.react-tab--active {
	color: #315efb;
	font-weight: 650;
}

.react-tabs button.react-tab--active::after {
	background: #315efb;
}

.react-tab-count {
	display: inline-grid;
	min-width: 18px;
	height: 18px;
	padding: 0 5px;
	place-items: center;
	border-radius: 9px;
	background: #edf2ff;
	color: #315efb;
	font-size: 10px;
	font-weight: 700;
}

.react-content {
	display: flex;
	min-height: 0;
	flex: 1;
	flex-direction: column;
}

@media (max-width: 640px) {
	.react-header {
		padding: 0 14px;
	}

	.react-tabs {
		padding: 0 14px;
	}
}
</style>
