<template>
	<section class="react-chat" aria-label="ReAct 对话">
		<header class="react-header">
			<div class="react-title-wrap">
				<v-avatar
					:image="store.currentAgentAvatar || undefined"
					:color="store.currentAgentAvatar ? undefined : '#e8efff'"
					size="32"
					rounded="lg"
				>
					<v-icon v-if="!store.currentAgentAvatar" color="#315efb" size="18"
						>mdi-robot-outline</v-icon
					>
				</v-avatar>
				<div>
					<h1>{{ store.currentAgentName || '智能助手' }}</h1>
					<p><span class="status-dot" /> ReAct 智能体</p>
				</div>
			</div>
			<div class="react-header-actions">
				<v-btn
					icon
					variant="text"
					size="small"
					title="切换历史会话"
					aria-label="切换历史会话"
					@click="store.chatSidebarCollapsed = !store.chatSidebarCollapsed"
				>
					<v-icon size="20">mdi-view-sidebar-outline</v-icon>
				</v-btn>
			</div>
		</header>

		<ReActMessageList />
		<ReActInputArea />
	</section>
</template>

<script setup lang="ts">
import { useReActChatStore } from '~/stores/reactChat';
import ReActMessageList from './ReActMessageList.vue';
import ReActInputArea from './ReActInputArea.vue';

const store = useReActChatStore();
</script>

<style scoped>
.react-chat {
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

@media (max-width: 640px) {
	.react-header {
		padding: 0 14px;
	}
}
</style>
