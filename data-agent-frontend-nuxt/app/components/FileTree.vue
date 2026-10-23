<template>
	<aside class="file-panel">
		<div class="panel-title">
			<span class="panel-name">{{ rootLabel || '文件' }}</span>
			<div v-if="rootLabel" class="file-actions">
				<v-btn icon="mdi-file-plus-outline" size="small" variant="text" title="新建文件" @click="startCreateFromSelection('file')" />
				<v-btn icon="mdi-folder-plus-outline" size="small" variant="text" title="新建目录" @click="startCreateFromSelection('directory')" />
				<v-btn icon="mdi-cloud-upload-outline" size="small" variant="text" title="上传文件" @click="uploadToRoot" />
				<input ref="uploadPicker" hidden type="file" @change="uploadFile" />
			</div>
		</div>
		<div
			class="tree-scroll"
			@contextmenu.prevent="openRootMenu"
			@click.self="selectRoot"
			@dragover.prevent="dragOverRoot = true"
			@dragleave="dragOverRoot = false"
			@drop.prevent="dropOnRoot"
			:class="{ 'drop-root': dragOverRoot }"
		>
			<div v-if="!rootLabel" class="file-empty">选择一个位置</div>
			<template v-else>
				<div v-if="pendingCreate && !pendingCreate.afterPath" class="tree-create" :style="{ '--depth': pendingCreate.depth }">
					<v-icon :icon="pendingCreate.type === 'directory' ? 'mdi-folder-plus-outline' : 'mdi-file-plus-outline'" size="17" />
					<input :ref="setCreateInput" v-model="createName" :aria-label="pendingCreate.type === 'directory' ? '目录名称' : '文件名称'" :aria-invalid="createError" :placeholder="pendingCreate.type === 'directory' ? '新建文件夹' : '新建文件'" @input="createError = false" @keydown.enter.prevent="submitCreate" @keydown.esc="cancelCreate" />
					<button class="mini-action" title="确认" @click="submitCreate"><v-icon icon="mdi-check" size="17" /></button>
					<button class="mini-action" title="取消" @click="cancelCreate"><v-icon icon="mdi-close" size="17" /></button>
				</div>
				<template v-for="node in visibleNodes" :key="node.path">
					<button
						class="tree-row"
						:class="{ active: selectedNode === node.path, 'context-target': contextSelected === node.path, 'drop-target': dropTarget === node.path }"
						:style="{ '--depth': node.depth }"
						:draggable="true"
						:aria-expanded="node.directory ? !collapsed.has(node.path) : undefined"
						@click="selectNode(node)"
						@contextmenu.prevent.stop="openNodeMenu($event, node)"
						@dragstart="startDrag($event, node)"
						@dragend="endDrag"
						@dragover.prevent.stop="node.directory && setDropTarget(node.path)"
						@dragleave.stop="clearDropTarget(node.path)"
						@drop.prevent.stop="node.directory && dropOnFolder(node.path)"
					>
						<v-icon v-if="node.directory" :icon="collapsed.has(node.path) ? 'mdi-chevron-right' : 'mdi-chevron-down'" class="expand-icon" size="16" />
						<span v-else class="expand-spacer" />
						<v-icon :icon="node.directory ? (collapsed.has(node.path) ? 'mdi-folder' : 'mdi-folder-open') : fileIcon(node.path)" size="17" />
						<span class="node-name">{{ node.name }}</span>
						<span v-if="!node.directory" class="file-size">{{ formatSize(node.size) }}</span>
					</button>
					<div v-if="pendingCreate?.afterPath === node.path" class="tree-create" :style="{ '--depth': pendingCreate.depth }">
						<v-icon :icon="pendingCreate.type === 'directory' ? 'mdi-folder-plus-outline' : 'mdi-file-plus-outline'" size="17" />
						<input :ref="setCreateInput" v-model="createName" :aria-label="pendingCreate.type === 'directory' ? '目录名称' : '文件名称'" :aria-invalid="createError" :placeholder="pendingCreate.type === 'directory' ? '新建文件夹' : '新建文件'" @input="createError = false" @keydown.enter.prevent="submitCreate" @keydown.esc="cancelCreate" />
						<button class="mini-action" title="确认" @click="submitCreate"><v-icon icon="mdi-check" size="17" /></button>
						<button class="mini-action" title="取消" @click="cancelCreate"><v-icon icon="mdi-close" size="17" /></button>
					</div>
				</template>
				<div v-if="!files.length && !pendingCreate" class="file-empty">此位置尚无文件</div>
			</template>
		</div>

		<Teleport to="body">
		<div v-if="menu.open" class="tree-context-menu" :style="{ left: `${menu.x}px`, top: `${menu.y}px` }" role="menu" @click.stop>
			<button role="menuitem" @click="startCreateFromMenu('directory')"><v-icon icon="mdi-folder-plus-outline" size="17" />新建文件夹</button>
			<button role="menuitem" @click="startCreateFromMenu('file')"><v-icon icon="mdi-file-plus-outline" size="17" />新建文件</button>
			<button role="menuitem" @click="uploadTo(menu.parent)"><v-icon icon="mdi-upload-outline" size="17" />上传文件</button>
			<div v-if="menu.path" class="menu-separator" />
			<button v-if="menu.path" class="danger-action" role="menuitem" @click="$emit('remove', menu.path); closeMenu()"><v-icon icon="mdi-delete-outline" size="17" />删除{{ menu.directory ? '文件夹' : '文件' }}</button>
		</div>
		</Teleport>
	</aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import type { FileEntry } from '~/types/file-entry';

interface TreeNode extends FileEntry { name: string; depth: number }
interface DirectoryNode { path: string; name: string; directory: boolean; size: number; children: DirectoryNode[] }

const props = defineProps<{ rootLabel: string; files: FileEntry[]; selected: string }>();
const emit = defineEmits<{
	open: [path: string];
	create: [request: { type: 'file' | 'directory'; path: string }];
	remove: [path: string];
	upload: [parent: string, file: File];
	move: [request: { source: string; target: string }];
}>();

const collapsed = ref(new Set<string>());
const uploadPicker = ref<HTMLInputElement | null>(null);
const createInput = ref<HTMLInputElement | null>(null);
const createName = ref('');
const createError = ref(false);
const pendingCreate = ref<{ type: 'file' | 'directory'; parent: string; depth: number; afterPath: string } | null>(null);
const selectedNode = ref(props.selected);
const dragSource = ref<TreeNode | null>(null);
const dropTarget = ref('');
const dragOverRoot = ref(false);
const contextSelected = ref('');
const menu = reactive({ open: false, x: 0, y: 0, path: '', parent: '', directory: false });
let uploadParent = '';

const tree = computed(() => {
	const root: DirectoryNode = { path: '', name: '', directory: true, size: 0, children: [] };
	for (const file of props.files) {
		const segments = file.path.split('/').filter(Boolean);
		let parent = root;
		segments.forEach((name, index) => {
			const path = segments.slice(0, index + 1).join('/');
			let node = parent.children.find(item => item.path === path);
			if (!node) {
				node = { path, name, directory: index < segments.length - 1 || file.directory, size: index === segments.length - 1 ? file.size : 0, children: [] };
				parent.children.push(node);
			}
			parent = node;
		});
	}
	return root.children;
});

const visibleNodes = computed(() => {
	const result: TreeNode[] = [];
	function visit(nodes: DirectoryNode[], depth: number) {
		const sorted = [...nodes].sort((a, b) => Number(b.directory) - Number(a.directory) || a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }));
		for (const node of sorted) {
			result.push({ path: node.path, name: node.name, directory: node.directory, size: node.size, depth });
			if (node.directory && !collapsed.value.has(node.path)) visit(node.children, depth + 1);
		}
	}
	visit(tree.value, 0);
	return result;
});

watch(() => props.selected, path => { selectedNode.value = path; });

function setCreateInput(element: Element | null) {
	createInput.value = element as HTMLInputElement | null;
}

function toggle(path: string) {
	const next = new Set(collapsed.value);
	if (next.has(path)) next.delete(path); else next.add(path);
	collapsed.value = next;
}

function fileIcon(path: string) {
	const extension = path.split('.').pop()?.toLowerCase();
	const icons: Record<string, string> = {
		md: 'mdi-language-markdown-outline', txt: 'mdi-file-document-outline', rst: 'mdi-file-document-outline',
		json: 'mdi-code-json', yaml: 'mdi-code-json', yml: 'mdi-code-json', toml: 'mdi-code-json',
		js: 'mdi-language-javascript', mjs: 'mdi-language-javascript', cjs: 'mdi-language-javascript',
		ts: 'mdi-language-typescript', tsx: 'mdi-react', jsx: 'mdi-react', vue: 'mdi-vuejs',
		py: 'mdi-language-python', java: 'mdi-language-java', kt: 'mdi-language-kotlin', go: 'mdi-language-go',
		rs: 'mdi-language-rust', html: 'mdi-language-html5', css: 'mdi-language-css3', scss: 'mdi-sass',
		sql: 'mdi-database-outline', sh: 'mdi-console', bash: 'mdi-console', xml: 'mdi-xml',
		png: 'mdi-file-image-outline', jpg: 'mdi-file-image-outline', jpeg: 'mdi-file-image-outline', svg: 'mdi-svg',
		pdf: 'mdi-file-pdf-box', zip: 'mdi-folder-zip-outline', gz: 'mdi-folder-zip-outline',
	};
	return icons[extension || ''] || 'mdi-file-outline';
}

function formatSize(bytes: number) { return bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`; }
function lastPathInDirectory(parent: string) {
	const items = parent ? visibleNodes.value.filter(node => node.path.startsWith(`${parent}/`)) : visibleNodes.value;
	return items.at(-1)?.path || '';
}
function startCreate(type: 'file' | 'directory', parent: string, afterPath = lastPathInDirectory(parent), depth = parent ? parent.split('/').length : 0) {
	closeMenu();
	pendingCreate.value = { type, parent, depth, afterPath };
	createName.value = '';
	createError.value = false;
	nextTick(() => createInput.value?.focus());
}
function startCreateFromSelection(type: 'file' | 'directory') {
	const selected = visibleNodes.value.find(node => node.path === selectedNode.value);
	if (!selected) return startCreate(type, '');
	if (selected.directory) {
		if (collapsed.value.has(selected.path)) toggle(selected.path);
		return startCreate(type, selected.path, selected.path, selected.depth + 1);
	}
	const parent = selected.path.includes('/') ? selected.path.slice(0, selected.path.lastIndexOf('/')) : '';
	return startCreate(type, parent, lastPathInDirectory(parent), parent ? parent.split('/').length : 0);
}
function startCreateFromMenu(type: 'file' | 'directory') {
	if (menu.path && menu.directory) return startCreate(type, menu.path, menu.path, menu.path.split('/').length);
	return startCreate(type, menu.parent);
}
function selectNode(node: TreeNode) {
	selectedNode.value = node.path;
	if (node.directory) toggle(node.path);
	else emit('open', node.path);
}
function selectRoot() { selectedNode.value = ''; contextSelected.value = ''; }
function cancelCreate() { pendingCreate.value = null; createName.value = ''; createError.value = false; }
function submitCreate() {
	const pending = pendingCreate.value;
	const name = createName.value.trim();
	if (!pending || !name) return;
	if (name === '.' || name === '..' || /[/\\]/.test(name)) { createError.value = true; return; }
	emit('create', { type: pending.type, path: pending.parent ? `${pending.parent}/${name}` : name });
	cancelCreate();
}
function openNodeMenu(event: MouseEvent, node: TreeNode) {
	contextSelected.value = node.path;
	selectedNode.value = node.path;
	menu.path = node.path;
	menu.parent = node.directory ? node.path : node.path.includes('/') ? node.path.slice(0, node.path.lastIndexOf('/')) : '';
	menu.directory = node.directory;
	showMenu(event);
}
function openRootMenu(event: MouseEvent) { selectRoot(); contextSelected.value = ''; menu.path = ''; menu.parent = ''; menu.directory = true; showMenu(event); }
function showMenu(event: MouseEvent) {
	const menuWidth = 196;
	const menuHeight = menu.path ? 166 : 126;
	menu.x = Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8));
	menu.y = Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8));
	menu.open = true;
}
function closeMenu() { menu.open = false; }
function handleOutsideClick() { closeMenu(); }
function uploadTo(parent: string) { uploadParent = parent; closeMenu(); uploadPicker.value?.click(); }
function uploadToRoot() { uploadParent = ''; uploadPicker.value?.click(); }
function uploadFile(event: Event) { const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (file) emit('upload', uploadParent, file); input.value = ''; }

function startDrag(event: DragEvent, node: TreeNode) {
	dragSource.value = node;
	if (event.dataTransfer) { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', node.path); }
}
function endDrag() { dragSource.value = null; dropTarget.value = ''; dragOverRoot.value = false; }
function setDropTarget(path: string) { if (dragSource.value?.path !== path && !path.startsWith(`${dragSource.value?.path}/`)) dropTarget.value = path; }
function clearDropTarget(path: string) { if (dropTarget.value === path) dropTarget.value = ''; }
function dropOnFolder(path: string) { if (!dragSource.value) return; moveTo(path); }
function dropOnRoot() { dragOverRoot.value = false; if (dragSource.value) moveTo(''); }
function moveTo(parent: string) {
	const source = dragSource.value;
	if (!source) return;
	const target = parent ? `${parent}/${source.name}` : source.name;
	if (target !== source.path && !target.startsWith(`${source.path}/`)) emit('move', { source: source.path, target });
	endDrag();
}

onMounted(() => window.addEventListener('click', handleOutsideClick));
onBeforeUnmount(() => window.removeEventListener('click', handleOutsideClick));
</script>

<style scoped>
.file-panel { display:flex; min-width:0; min-height:0; flex-direction:column; overflow:hidden; border-right:1px solid #e2e8ec; background:#fff; }
.panel-title { display:flex; flex:0 0 48px; align-items:center; justify-content:space-between; gap:8px; padding:0 8px 0 14px; border-bottom:1px solid #edf1f4; background:#fff; color:#637282; font-size:12px; font-weight:650; }
.panel-name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file-actions { display:flex; flex:0 0 auto; align-items:center; }
.tree-scroll { position:relative; flex:1; min-height:0; overflow:auto; overscroll-behavior:contain; padding:6px 0; scrollbar-width:thin; scrollbar-color:#b9c3ca transparent; }
.tree-scroll::-webkit-scrollbar { width:10px; }
.tree-scroll::-webkit-scrollbar-track { background:transparent; }
.tree-scroll::-webkit-scrollbar-thumb { min-height:32px; border:3px solid #fff; border-radius:8px; background:#bdc7ce; }
.tree-scroll.drop-root { background:#f0f7f5; outline:2px dashed #4b9b8d; outline-offset:-3px; }
.tree-row { position:relative; width:100%; min-height:34px; display:flex; align-items:center; gap:6px; padding:0 9px 0 calc(7px + var(--depth) * 18px); border:0; background:transparent; color:#344451; text-align:left; cursor:default; font-size:13px; }
.tree-row:hover { background:#f3f6f7; }
.tree-row.active { background:#eaf4f1; color:#145c53; }
.tree-row.context-target { outline:1px solid #83b7ad; outline-offset:-1px; }
.tree-row:focus-visible { z-index:1; outline:2px solid #418b7d; outline-offset:-2px; }
.tree-row.drop-target { background:#e2f1ec; box-shadow:inset 0 0 0 1px #4b9b8d; }
.expand-icon { flex:0 0 16px; color:#778691; }
.expand-spacer { flex:0 0 16px; }
.node-name { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.file-size { flex:0 0 auto; color:#91a0aa; font-size:11px; }
.tree-create { display:flex; align-items:center; gap:6px; min-height:34px; padding:0 4px 0 calc(7px + var(--depth) * 18px); color:#176b5c; }
.tree-create input { width:0; min-width:0; flex:1; height:26px; padding:0 5px; border:1px solid #82b9ae; border-radius:3px; outline:none; color:#263746; font-size:12px; }
.tree-create input[aria-invalid="true"] { border-color:#c24136; }
.mini-action { width:22px; height:24px; display:grid; place-items:center; padding:0; border:0; background:transparent; color:#61727d; cursor:pointer; }
.mini-action:hover { color:#176b5c; }
.tree-context-menu { position:fixed; z-index:20000; width:188px; padding:4px; border:1px solid #dce3e7; border-radius:5px; background:#fff; box-shadow:0 8px 24px #12212d2b; }
.tree-context-menu button { width:100%; min-height:36px; display:flex; align-items:center; gap:9px; padding:0 9px; border:0; border-radius:3px; background:transparent; color:#344451; text-align:left; cursor:pointer; font-size:13px; }
.tree-context-menu button:hover { background:#f1f5f6; }
.tree-context-menu .danger-action { color:#b42318; }
.menu-separator { height:1px; margin:4px 2px; background:#e8ecef; }
.file-empty { padding:18px 12px; color:#929da6; font-size:12px; text-align:center; }
</style>
