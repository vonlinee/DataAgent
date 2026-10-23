<template>
	<div class="edit-area">
		<div class="edit-toolbar">
			<div class="path-label">
				<span>{{ path || '未选择文件' }}</span>
				<span v-if="path" :class="['save-state', { dirty }]">{{ dirty ? '未保存' : '已保存' }}</span>
			</div>
			<div class="edit-actions">
				<v-btn
					color="primary"
					prepend-icon="mdi-content-save-outline"
					:disabled="!path || binaryFile || !dirty"
					@click="$emit('save')"
				>
					保存
				</v-btn>
			</div>
		</div>
		<div v-if="!path" class="editor-empty">
			<v-icon icon="mdi-file-document-outline" size="34" />
			<span>从文件树中选择文件</span>
		</div>
		<div v-else-if="binaryFile" class="binary-view">
			<v-icon icon="mdi-file-download-outline" size="38" />
			<strong>{{ path.split('/').pop() }}</strong>
			<span>二进制文件无法在编辑器中显示</span>
		</div>
		<LazyMonacoEditor v-else v-model="content" :path="path" @save="$emit('save')" />
	</div>
</template>

<script setup lang="ts">
const content = defineModel<string>({ required: true });

defineProps<{
	path: string;
	dirty: boolean;
	binaryFile: boolean;
}>();

defineEmits<{ save: [] }>();
</script>

<style scoped>
.edit-area {
	display: flex;
	min-width: 0;
	min-height: 0;
	flex-direction: column;
}

.edit-toolbar {
	min-height: 48px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	padding: 0 16px;
	border-bottom: 1px solid #e8edf1;
}

.path-label {
	min-width: 0;
	display: flex;
	align-items: center;
	gap: 12px;
	color: #506070;
	font-size: 13px;
}

.path-label span:first-child {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.edit-actions {
	display: flex;
	align-items: center;
	gap: 6px;
}

.save-state {
	color: #788691;
	font-size: 11px;
	white-space: nowrap;
}

.save-state.dirty {
	color: #a15b17;
}

.editor-empty,
.binary-view {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-direction: column;
	gap: 12px;
	color: #84919b;
	font-size: 13px;
}

.binary-view strong {
	color: #344451;
	font-size: 14px;
}
</style>
