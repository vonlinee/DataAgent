<template>
	<article class="skill-card">
		<div class="card-top">
			<div class="skill-identity">
				<span class="skill-mark"><v-icon icon="mdi-puzzle-outline" size="21" /></span>
				<h2>{{ skill.name }}</h2>
			</div>
			<div class="card-actions">
				<v-btn
					icon="mdi-file-edit-outline"
					variant="text"
					size="default"
					:aria-label="`编辑 ${skill.name} 文件`"
					:title="`编辑 ${skill.name} 文件`"
					@click="$emit('open-workspace', skill)"
				/>
				<v-btn
					icon="mdi-pencil-outline"
					variant="text"
					size="default"
					:aria-label="`编辑 ${skill.name} 基本信息`"
					:title="`编辑 ${skill.name} 基本信息`"
					@click="$emit('edit-info', skill)"
				/>
				<v-btn
					icon="mdi-delete-outline"
					variant="text"
					color="error"
					size="default"
					:aria-label="`删除 ${skill.name}`"
					:title="`删除 ${skill.name}`"
					:loading="deleting"
					:disabled="deleting"
					@click="$emit('delete-skill', skill)"
				/>
			</div>
		</div>
		<div class="card-copy">
			<p>{{ skill.description || '暂无描述' }}</p>
		</div>
		<div class="card-meta">
			<span><v-icon icon="mdi-file-multiple-outline" size="16" />{{ skill.fileCount }} 个文件</span>
			<span>更新于 {{ skill.updatedAt }}</span>
		</div>
	</article>
</template>

<script setup lang="ts">
import type { SkillItem } from '~/services/skills';

defineProps<{ skill: SkillItem & { fileCount: number; updatedAt: string }; deleting?: boolean }>();
defineEmits<{
	'open-workspace': [skill: SkillItem];
	'edit-info': [skill: SkillItem];
	'delete-skill': [skill: SkillItem];
}>();
</script>

<style scoped>
.skill-card {
	display: flex;
	min-height: 184px;
	flex-direction: column;
	align-items: stretch;
	padding: 18px;
	border: 1px solid #dfe5e8;
	border-radius: 6px;
	background: #fff;
	color: #1e293b;
	transition: border-color 0.16s ease, background 0.16s ease;
}

.skill-card:hover {
	border-color: #a8b9b5;
	background: #fbfdfc;
}

.card-top,
.skill-identity,
.card-actions {
	display: flex;
	align-items: center;
}

.card-top {
	justify-content: space-between;
	gap: 12px;
}

.skill-identity {
	min-width: 0;
	gap: 12px;
}

.skill-mark {
	width: 38px;
	height: 38px;
	flex: 0 0 auto;
	display: grid;
	place-items: center;
	border-radius: 6px;
	background: #e8f3f0;
	color: #176b5c;
}

h2 {
	min-width: 0;
	margin: 0;
	overflow: hidden;
	color: #263746;
	font-size: 16px;
	line-height: 22px;
	font-weight: 650;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.card-actions {
	flex: 0 0 auto;
	gap: 2px;
	opacity: 0;
	pointer-events: none;
	transform: translateY(-2px);
	transition: opacity 0.15s ease, transform 0.15s ease;
}

.skill-card:hover .card-actions,
.skill-card:focus-within .card-actions {
	opacity: 1;
	pointer-events: auto;
	transform: translateY(0);
}

@media (hover: none) {
	.card-actions {
		opacity: 1;
		pointer-events: auto;
		transform: none;
	}
}

.card-copy {
	flex: 1;
	min-width: 0;
	padding-top: 17px;
}

p {
	margin: 0;
	overflow: hidden;
	color: #6b7884;
	font-size: 13px;
	line-height: 19px;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
}

.card-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
	padding-top: 14px;
	border-top: 1px solid #edf0f2;
	color: #7c8994;
	font-size: 11px;
}

.card-meta span:first-child {
	display: flex;
	align-items: center;
	gap: 5px;
	color: #526371;
}
</style>
