/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<div class="chat-page">
		<ChatSidebar />
		<div class="chat-body">
			<template v-if="currentAgentType === 'agentic'">
				<ReActChat />
			</template>
			<template v-else-if="!isAgentLoading">
				<ChatMessageList />
				<ChatInputArea />
			</template>
			<div v-else class="chat-loading" aria-label="正在加载智能体">
				<v-progress-circular
					indeterminate
					size="24"
					width="2"
					color="primary"
				/>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed, ref } from 'vue';
import { useChatStore } from '~/stores/chat';
import { useReActChatStore } from '~/stores/reactChat';
import agentService from '~/services/agent/index';
import ChatSidebar from '~/components/chat/ChatSidebar.vue';
import ChatMessageList from '~/components/chat/ChatMessageList.vue';
import ChatInputArea from '~/components/chat/ChatInputArea.vue';
import ReActChat from '~/components/chat/ReActChat.vue';

const route = useRoute();
const store = useChatStore();
const reactStore = useReActChatStore();
const currentAgentType = ref<'workflow' | 'agentic' | undefined>();
const isAgentLoading = ref(true);

const currentAgentId = computed(() => {
	const q = route.query.agentId;
	return q ? Number(q) : undefined;
});

async function init(agentId: number) {
	isAgentLoading.value = true;
	store.currentAgentId = agentId;
	store.currentAgentType = 'workflow';

	// Load agent info
	try {
		const agent = await agentService.get(agentId);
		if (agent) {
			currentAgentType.value = agent.agentType || 'workflow';
			store.currentAgentType = currentAgentType.value;
			store.currentAgentName = agent.name || '';
			store.currentAgentAvatar = agent.avatar || '';
			store.currentAgentDescription = agent.description || '';
		}
	} catch {
		currentAgentType.value = 'workflow';
		store.currentAgentType = 'workflow';
	} finally {
		isAgentLoading.value = false;
	}

	// Load active model — handled by store.loadSessions

	store.connectSessionStream(agentId);
	await store.loadSessions(agentId);
}

onMounted(async () => {
	if (currentAgentId.value) await init(currentAgentId.value);
});

watch(currentAgentId, async (newId, oldId) => {
	if (newId && newId !== oldId) {
		reactStore.clearRuntimeState(true);
		currentAgentType.value = undefined;
		store.currentAgentType = 'workflow';
		isAgentLoading.value = true;
		store.sessions = [];
		store.currentSession = null;
		store.currentMessages = [];
		store.isStreaming = false;
		store.nodeBlocks = [];
		await init(newId);
	}
});

onUnmounted(() => {
	store.disconnectSessionStream();
	reactStore.clearRuntimeState(true);
});
</script>

<style scoped>
.chat-page {
	display: flex;
	height: calc(100vh - 64px);
	overflow: hidden;
	background: #f1f5f9;
}

.chat-body {
	flex: 1;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	min-width: 0;
	background: #fff;
}

.chat-loading {
	flex: 1;
	display: grid;
	place-items: center;
	background: #fff;
}
</style>
