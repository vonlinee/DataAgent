/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

<template>
	<div class="welcome-wrap">
		<!-- Agent Avatar -->
		<div class="agent-avatar-wrap">
			<v-avatar
				:image="store.currentAgentAvatar || undefined"
				:color="store.currentAgentAvatar ? undefined : 'grey-lighten-3'"
				size="80"
				rounded="xl"
				class="agent-avatar"
			>
				<span v-if="!store.currentAgentAvatar" class="agent-avatar-emoji">🤖</span>
			</v-avatar>
		</div>

		<!-- Agent Name -->
		<h2 class="welcome-title">
			您好，我是 <span class="agent-name">{{ store.currentAgentName || '数据助手' }}</span>
		</h2>

		<!-- Agent Description -->
		<p class="welcome-desc">
			{{ store.currentAgentDescription || '我可以为您分析数据库中的表结构、生成 SQL 或可视化图表。' }}
		</p>
		<div v-if="presetQuestions.length" class="preset-questions" aria-label="预设问题">
			<button
				v-for="question in visiblePresetQuestions"
				:key="question.id"
				type="button"
				class="preset-question"
				@click="selectQuestion(question.question)"
			>
				<v-icon icon="mdi-lightbulb-outline" size="16" />
				<span>{{ question.question }}</span>
			</button>
		</div>
		<v-btn
			v-if="pageCount > 1"
			class="preset-questions-more"
			variant="text"
			color="primary"
			prepend-icon="mdi-refresh"
			@click="showNextPresetPage"
		>
			换一批
		</v-btn>
	</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useChatStore } from '~/stores/chat';
import presetQuestionService, { type PresetQuestion } from '~/services/presetQuestion';

const store = useChatStore();
const presetQuestions = ref<PresetQuestion[]>([]);
const presetPage = ref(0);
const pageSize = 6;
const pageCount = computed(() =>
	Math.ceil(presetQuestions.value.length / pageSize),
);
const visiblePresetQuestions = computed(() =>
	presetQuestions.value.slice(
		presetPage.value * pageSize,
		(presetPage.value + 1) * pageSize,
	),
);
let requestVersion = 0;

async function loadPresetQuestions(agentId?: number) {
	const version = ++requestVersion;
	presetPage.value = 0;
	presetQuestions.value = [];
	if (!agentId) return;
	try {
		const questions = (await presetQuestionService.list(agentId))
			.filter((question) => question.isActive !== false);
		if (version === requestVersion) presetQuestions.value = questions;
	} catch (error) {
		console.error('获取预设问题失败:', error);
	}
}

function showNextPresetPage() {
	if (pageCount.value < 2) return;
	presetPage.value = (presetPage.value + 1) % pageCount.value;
}

function selectQuestion(question: string) {
	store.pendingPresetQuestion = question;
}

watch(() => store.currentAgentId, loadPresetQuestions);
onMounted(() => loadPresetQuestions(store.currentAgentId));
</script>

<style scoped>
.welcome-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 1;
	padding: 40px 24px;
	text-align: center;
}

.agent-avatar-wrap {
	margin-bottom: 24px;
}

.agent-avatar {
	box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.agent-avatar-emoji {
	font-size: 36px;
	line-height: 1;
}

.welcome-title {
	font-size: 26px;
	font-weight: 700;
	color: #0f172a;
	margin-bottom: 12px;
	letter-spacing: -0.3px;
}

.agent-name {
	color: #1e293b;
}

.welcome-desc {
	font-size: 14.5px;
	color: #64748b;
	max-width: 480px;
	line-height: 1.7;
}

.preset-questions {
	display: flex;
	flex-wrap: wrap;
	justify-content: center;
	gap: 10px;
	width: min(720px, 100%);
	margin-top: 30px;
}

.preset-questions-more {
	margin-top: 8px;
}

.preset-question {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10px;
	flex: 0 1 calc(50% - 5px);
	max-width: 355px;
	min-height: 48px;
	padding: 12px 14px;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
	background: #fff;
	color: #334155;
	font: inherit;
	text-align: left;
	cursor: pointer;
	transition: border-color 0.15s, background-color 0.15s;
}

.preset-question:hover,
.preset-question:focus-visible {
	border-color: #60a5fa;
	background: #f8fbff;
	outline: none;
}

.preset-question span {
	min-width: 0;
	line-height: 1.5;
	overflow-wrap: anywhere;
}

@media (max-width: 600px) {
	.preset-questions {
		margin-top: 22px;
	}

	.preset-question {
		flex-basis: 100%;
	}
}
</style>
