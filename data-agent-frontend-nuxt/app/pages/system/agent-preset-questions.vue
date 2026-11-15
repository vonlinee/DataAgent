<!--
  Copyright 2026 the original author or authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

<template>
	<v-container fluid class="preset-page pa-4 pa-md-8">
		<div
			class="page-heading d-flex flex-wrap align-center justify-space-between ga-4 mb-6"
		>
			<div>
				<h1 class="text-h5 font-weight-bold text-slate-900">预设问题管理</h1>
				<p class="text-body-2 text-medium-emphasis mt-1">
					管理智能体欢迎页展示的问题建议
				</p>
			</div>
			<v-btn
				color="primary"
				prepend-icon="mdi-plus"
				:disabled="!selectedAgentId"
				@click="openCreateDialog"
			>
				新增问题
			</v-btn>
		</div>

		<div class="toolbar d-flex flex-wrap align-center ga-4 mb-5">
			<v-select
				v-model="selectedAgentId"
				:items="agents"
				:item-title="(agent) => agent.name || `Agent ${agent.id}`"
				item-value="id"
				label="选择智能体"
				variant="outlined"
				density="compact"
				hide-details
				class="agent-select"
				:loading="agentsLoading"
				:disabled="agentsLoading || !agents.length"
			/>
			<div class="text-body-2 text-medium-emphasis">
				{{ questions.length }} 个问题，{{ activeCount }} 个已启用
			</div>
		</div>

		<v-alert v-if="loadError" type="error" variant="tonal" class="mb-4">
			{{ loadError }}
		</v-alert>

		<v-data-table
			:headers="headers"
			:items="questions"
			:loading="questionsLoading"
			:item-value="(item) => item.id ?? item.question"
			:items-per-page="10"
			class="question-table rounded-lg border"
			:no-data-text="selectedAgentId ? '暂无预设问题' : '请先创建智能体'"
		>
			<template #item.question="{ item }">
				<div class="question-cell">{{ item.question }}</div>
			</template>
			<template #item.isActive="{ item }">
				<v-chip
					size="small"
					:color="item.isActive ? 'success' : 'grey'"
					variant="tonal"
				>
					{{ item.isActive ? '已启用' : '已停用' }}
				</v-chip>
			</template>
			<template #item.actions="{ item }">
				<div class="d-flex justify-end ga-1">
					<v-btn
						icon="mdi-pencil-outline"
						variant="text"
						aria-label="编辑问题"
						@click="openEditDialog(item)"
					>
						<v-tooltip activator="parent" location="top">编辑</v-tooltip>
					</v-btn>
					<v-btn
						icon="mdi-delete-outline"
						variant="text"
						color="error"
						aria-label="删除问题"
						@click="confirmDelete(item)"
					>
						<v-tooltip activator="parent" location="top">删除</v-tooltip>
					</v-btn>
				</div>
			</template>
			<template #loading>
				<v-skeleton-loader type="table-row@4" />
			</template>
			<template #no-data>
				<div class="empty-state py-12 text-center">
					<v-icon
						icon="mdi-comment-question-outline"
						size="36"
						color="blue-grey-lighten-2"
					/>
					<p class="text-body-1 font-weight-medium mt-3">
						{{ selectedAgentId ? '还没有预设问题' : '暂无可管理的智能体' }}
					</p>
					<v-btn
						v-if="selectedAgentId"
						class="mt-3"
						variant="outlined"
						prepend-icon="mdi-plus"
						@click="openCreateDialog"
					>
						新增问题
					</v-btn>
				</div>
			</template>
		</v-data-table>

		<v-dialog v-model="dialogVisible" max-width="560" persistent>
			<v-card rounded="lg">
				<v-card-title
					class="d-flex align-center justify-space-between px-6 pt-6"
				>
					<span class="text-h6 font-weight-bold">{{
						editingQuestion ? '编辑预设问题' : '新增预设问题'
					}}</span>
					<v-btn
						icon="mdi-close"
						variant="text"
						aria-label="关闭"
						@click="closeDialog"
					/>
				</v-card-title>
				<v-card-text class="px-6 pt-4">
					<v-form ref="formRef" @submit.prevent="saveQuestion">
						<v-textarea
							v-model="form.question"
							label="问题内容"
							placeholder="输入用户可以直接提出的问题"
							:rules="questionRules"
							rows="3"
							variant="outlined"
						/>
						<v-switch
							v-model="form.isActive"
							label="在智能体欢迎页展示"
							color="primary"
							inset
							hide-details
						/>
					</v-form>
				</v-card-text>
				<v-divider />
				<v-card-actions class="pa-4 justify-end ga-2">
					<v-btn variant="outlined" @click="closeDialog">取消</v-btn>
					<v-btn color="primary" :loading="saving" @click="saveQuestion">
						{{ editingQuestion ? '保存修改' : '创建问题' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</v-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import agentService, { type Agent } from '~/services/agent';
import presetQuestionService, {
	type PresetQuestion,
} from '~/services/presetQuestion';

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();
const agents = ref<Agent[]>([]);
const questions = ref<PresetQuestion[]>([]);
const selectedAgentId = ref<number | undefined>();
const agentsLoading = ref(false);
const questionsLoading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const editingQuestion = ref<PresetQuestion | null>(null);
const loadError = ref('');
const formRef = ref();
const form = ref({ question: '', isActive: true });
let requestVersion = 0;

const headers = [
	{ title: '问题内容', key: 'question', sortable: false },
	{ title: '状态', key: 'isActive', width: '110px', sortable: false },
	{
		title: '操作',
		key: 'actions',
		width: '120px',
		align: 'end' as const,
		sortable: false,
	},
];
const questionRules = [
	(value: string) => Boolean(value?.trim()) || '请输入问题内容',
];
const activeCount = computed(
	() => questions.value.filter((question) => question.isActive).length,
);

async function loadQuestions(agentId = selectedAgentId.value) {
	const version = ++requestVersion;
	questions.value = [];
	loadError.value = '';
	if (!agentId) {
		questionsLoading.value = false;
		return;
	}
	questionsLoading.value = true;
	try {
		const result = await presetQuestionService.list(agentId);
		if (version === requestVersion) questions.value = result;
	} catch (error) {
		if (version === requestVersion) {
			loadError.value = '加载预设问题失败，请检查服务连接后重试。';
			console.error('加载预设问题失败:', error);
		}
	} finally {
		if (version === requestVersion) questionsLoading.value = false;
	}
}

function openCreateDialog() {
	editingQuestion.value = null;
	form.value = { question: '', isActive: true };
	dialogVisible.value = true;
}

function openEditDialog(question: PresetQuestion) {
	editingQuestion.value = question;
	form.value = {
		question: question.question,
		isActive: question.isActive ?? true,
	};
	dialogVisible.value = true;
}

function closeDialog() {
	dialogVisible.value = false;
	editingQuestion.value = null;
}

async function saveQuestion() {
	const validation = await formRef.value?.validate();
	if (!validation?.valid || !selectedAgentId.value) return;
	const question = form.value.question.trim();

	saving.value = true;
	try {
		if (editingQuestion.value?.id) {
			await presetQuestionService.update(
				selectedAgentId.value,
				editingQuestion.value.id,
				{ question, isActive: form.value.isActive },
			);
		} else {
			await presetQuestionService.create(selectedAgentId.value, {
				question,
				isActive: form.value.isActive,
			});
		}
		$tip(editingQuestion.value ? '预设问题已更新' : '预设问题已创建');
		closeDialog();
		await loadQuestions();
	} catch (error) {
		console.error('保存预设问题失败:', error);
		$tip('保存预设问题失败，请重试', {
			icon: 'mdi-alert-circle',
			color: 'error',
		});
	} finally {
		saving.value = false;
	}
}

function confirmDelete(question: PresetQuestion) {
	if (!selectedAgentId.value || !question.id) return;
	showConfirm({
		title: '删除预设问题',
		message: '确定删除这条预设问题吗？此操作不可撤销。',
		icon: 'mdi-alert-circle-outline',
		confirmText: '确认删除',
		onConfirm: async () => {
			try {
				await presetQuestionService.delete(
					selectedAgentId.value!,
					question.id!,
				);
				questions.value = questions.value.filter(
					(item) => item.id !== question.id,
				);
				$tip('预设问题已删除');
			} catch (error) {
				console.error('删除预设问题失败:', error);
				$tip('删除预设问题失败，请重试', {
					icon: 'mdi-alert-circle',
					color: 'error',
				});
			}
		},
	});
}

watch(selectedAgentId, () => loadQuestions());

onMounted(async () => {
	agentsLoading.value = true;
	try {
		agents.value = await agentService.list();
		selectedAgentId.value = agents.value.find((agent) => agent.id)?.id;
	} catch (error) {
		loadError.value = '加载智能体列表失败，请检查服务连接后重试。';
		console.error('加载智能体失败:', error);
	} finally {
		agentsLoading.value = false;
	}
});
</script>

<style scoped>
.preset-page {
	min-height: 100%;
	background: #f8fafc;
}

.text-slate-900 {
	color: #0f172a;
}

.agent-select {
	max-width: 360px;
	min-width: min(100%, 280px);
}

.question-table {
	background: #fff;
}

.question-cell {
	max-width: 760px;
	padding: 12px 0;
	white-space: normal;
	line-height: 1.55;
	word-break: break-word;
}

@media (max-width: 600px) {
	.preset-page {
		padding: 16px !important;
	}

	.agent-select {
		max-width: none;
		width: 100%;
	}
}
</style>
