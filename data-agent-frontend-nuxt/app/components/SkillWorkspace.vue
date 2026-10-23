<template>
	<div class="editor-layout">
		<FileTree
			:root-label="skillName"
			:files="files"
			:selected="selectedPath"
			@open="openFile"
			@create="createEntry"
			@remove="removeEntry"
			@upload="uploadFile"
			@move="moveEntry"
		/>
		<FileEditor
			v-model="content"
			:path="selectedPath"
			:dirty="dirty"
			:binary-file="binaryFile"
			@save="saveFile"
		/>
	</div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import FileEditor from './FileEditor.vue';
import FileTree from './FileTree.vue';
import skillService, { type SkillFile } from '~/services/skills';

const props = defineProps<{ active: boolean; skillId: number; skillName: string }>();
const emit = defineEmits<{ saved: []; dirtyChange: [dirty: boolean] }>();
const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

const files = ref<SkillFile[]>([]);
const selectedPath = ref('');
const content = ref('');
const originalContent = ref('');
const dirty = computed(() => content.value !== originalContent.value);
const binaryFile = computed(() => !/\.(md|txt|json|ya?ml|js|mjs|ts|vue|html|css|scss|java|py|sql|sh|xml|go|rs|kt|properties|toml)$/i.test(selectedPath.value));

watch(dirty, value => emit('dirtyChange', value), { immediate: true });
watch(() => [props.active, props.skillId] as const, ([active]) => {
	if (active) void openWorkspace();
}, { immediate: true });

async function openWorkspace() {
	selectedPath.value = '';
	content.value = '';
	originalContent.value = '';
	try {
		files.value = await skillService.tree(props.skillId);
	} catch {
		notify('加载文件列表失败，请稍后重试', true);
	}
}

async function loadFile(path: string) {
	try {
		const bytes = await skillService.read(props.skillId, path);
		selectedPath.value = path;
		content.value = new TextDecoder().decode(bytes);
		originalContent.value = content.value;
	} catch {
		notify('文件读取失败，请稍后重试', true);
	}
}

async function openFile(path: string) {
	if (dirty.value) {
		showConfirm({
			title: '未保存的更改',
			message: '切换文件将丢弃当前文件的未保存更改。',
			confirmText: '放弃并切换',
			icon: 'mdi-content-save-alert-outline',
			onConfirm: () => void loadFile(path),
		});
		return;
	}
	await loadFile(path);
}

async function saveFile() {
	if (!selectedPath.value || binaryFile.value || !dirty.value) return;
	try {
		await skillService.save(props.skillId, selectedPath.value, content.value);
		originalContent.value = content.value;
		emit('saved');
		notify('文件已保存');
	} catch {
		notify('保存失败，请稍后重试', true);
	}
}

async function createEntry(request: { type: 'file' | 'directory'; path: string }) {
	try {
		if (request.type === 'directory') await skillService.mkdir(props.skillId, request.path);
		else await skillService.save(props.skillId, request.path, '');
		await refreshTree();
		emit('saved');
		if (request.type === 'file') await openFile(request.path);
		notify(request.type === 'directory' ? '文件夹已创建' : '文件已创建');
	} catch {
		notify('创建失败，请检查名称或权限', true);
	}
}

async function uploadFile(parent: string, file: File) {
	const path = parent ? `${parent}/${file.name}` : file.name;
	try {
		await skillService.upload(props.skillId, path, file);
		await refreshTree();
		emit('saved');
		notify('文件已上传');
	} catch {
		notify('上传失败，请检查文件或权限', true);
	}
}

function removeEntry(path: string) {
	const isDirectory = files.value.some(file => file.path === path && file.directory);
	const removesCurrent = selectedPath.value === path || selectedPath.value.startsWith(`${path}/`);
	const warning = [
		isDirectory && '此操作会删除文件夹内的所有内容。',
		removesCurrent && dirty.value && '当前文件有未保存的更改。',
	].filter(Boolean).join('\n');

	showConfirm({
		title: '删除确认',
		message: [`确定删除 ${path}？`, warning].filter(Boolean).join('\n'),
		confirmText: '删除',
		icon: 'mdi-delete-outline',
		onConfirm: async () => {
			try {
				await skillService.remove(props.skillId, path);
				if (removesCurrent) {
					selectedPath.value = '';
					content.value = '';
					originalContent.value = '';
				}
				await refreshTree();
				emit('saved');
				notify('已删除');
			} catch {
				notify('删除失败，请稍后重试', true);
			}
		},
	});
}

async function moveEntry(request: { source: string; target: string }) {
	try {
		await skillService.move(props.skillId, request.source, request.target);
		if (selectedPath.value === request.source || selectedPath.value.startsWith(`${request.source}/`)) {
			selectedPath.value = `${request.target}${selectedPath.value.slice(request.source.length)}`;
		}
		await refreshTree();
		emit('saved');
		notify('文件位置已更新');
	} catch {
		notify('移动失败，目标可能已存在或位置无效', true);
	}
}

async function refreshTree() {
	files.value = await skillService.tree(props.skillId);
}

function notify(message: string, error = false) {
	$tip(message, error ? { color: 'error', icon: 'mdi-alert-circle' } : undefined);
}
</script>

<style scoped>
.editor-layout {
	flex: 1;
	min-height: 0;
	display: grid;
	grid-template-columns: 250px minmax(0, 1fr);
}

.editor-layout :deep(.file-panel) {
	overflow: hidden;
	border-right: 1px solid #e3e8ec;
}

@media (max-width: 700px) {
	.editor-layout {
		display: flex;
		flex-direction: column;
	}

	.editor-layout :deep(.file-panel) {
		max-height: 190px;
		border-right: 0;
		border-bottom: 1px solid #e3e8ec;
	}
}
</style>
