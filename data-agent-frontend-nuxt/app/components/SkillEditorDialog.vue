<template>
	<v-dialog
		:model-value="modelValue"
		max-width="calc(100vw - 48px)"
		width="clamp(960px, 88vw, 1440px)"
		scrollable
		@update:model-value="requestClose"
	>
		<v-card class="skill-editor-dialog">
			<header class="dialog-head">
				<div class="dialog-title">
					<span class="skill-mark"><v-icon icon="mdi-puzzle-outline" size="20" /></span>
					<div>
						<h2>{{ skillName }}</h2>
						<p>文件浏览器</p>
					</div>
				</div>
				<div class="dialog-actions">
					<v-btn icon="mdi-download" variant="text" title="打包下载" @click="download" />
					<v-btn icon="mdi-close" variant="text" title="关闭" @click="requestClose(false)" />
				</div>
			</header>
			<SkillWorkspace
				:active="modelValue"
				:skill-id="skillId"
				:skill-name="skillName"
				@dirty-change="dirty = $event"
				@saved="emit('saved')"
			/>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import skillService from '~/services/skills';
import SkillWorkspace from './SkillWorkspace.vue';

const props = defineProps<{ modelValue: boolean; skillId: number; skillName: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>();
const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();
const dirty = ref(false);

async function download() {
	try {
		const blob = await skillService.download(props.skillId);
		const url = URL.createObjectURL(blob);
		const link = document.createElement('a');
		link.href = url;
		link.download = `${props.skillName}.zip`;
		link.click();
		URL.revokeObjectURL(url);
	} catch {
		$tip('打包下载失败，请稍后重试', { color: 'error', icon: 'mdi-alert-circle' });
	}
}

function requestClose(value: boolean) {
	if (!value && dirty.value) {
		showConfirm({
			title: '未保存的更改',
			message: '关闭将丢弃当前文件的未保存更改。',
			confirmText: '放弃并关闭',
			icon: 'mdi-content-save-alert-outline',
			onConfirm: () => emit('update:modelValue', false),
		});
		return;
	}
	emit('update:modelValue', value);
}
</script>

<style scoped>
:global(.skill-editor-dialog) {
	height: min(84vh, 920px);
	min-height: min(84vh, 640px);
	max-height: calc(100vh - 48px);
	flex: 0 0 auto;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	border-radius: 8px;
}

.dialog-head {
	min-height: 72px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 12px 20px;
	border-bottom: 1px solid #e4e9ed;
}

.dialog-title {
	min-width: 0;
	display: flex;
	align-items: center;
	gap: 12px;
}

.skill-mark {
	width: 40px;
	height: 40px;
	display: grid;
	place-items: center;
	border-radius: 6px;
	background: #e8f3f0;
	color: #176b5c;
}

.dialog-title h2 {
	margin: 0;
	color: #243746;
	font-size: 17px;
	font-weight: 650;
}

.dialog-title p {
	margin: 3px 0 0;
	color: #74818c;
	font-size: 12px;
}

.dialog-actions {
	display: flex;
	align-items: center;
}

@media (max-width: 700px) {
	:global(.skill-editor-dialog) {
		height: 94vh;
		min-height: 0;
		max-height: calc(100vh - 24px);
	}
}
</style>
