<template>
	<main class="skills-page">
		<header class="page-head">
			<div>
				<div class="eyebrow">通用设置 / SKILLS</div>
				<h1>Skill 管理</h1>
				<p>浏览 Skill，并在文件工作区中维护内容</p>
			</div>
			<div class="head-actions">
				<v-btn prepend-icon="mdi-upload" @click="importDialog = true">导入 ZIP</v-btn>
				<v-btn color="primary" prepend-icon="mdi-plus" @click="openCreate">新建 Skill</v-btn>
			</div>
		</header>

		<section class="library" style="width:100%;max-width:1480px;margin:0 auto;padding:24px 32px 40px;">
			<div class="library-toolbar" style="display:flex;align-items:center;gap:12px;margin-bottom:20px;min-height:40px;">
				<div class="result-count" style="flex:1;min-width:0;color:#52616d;font-size:13px;font-weight:600;">{{ filteredSkills.length }} 个 Skill</div>
				<v-text-field v-model="keyword" aria-label="搜索 Skill" placeholder="搜索 Skill" prepend-inner-icon="mdi-magnify" variant="outlined" density="compact" clearable hide-details class="search-field" style="width:300px;max-width:42vw;flex:none;" />
				<v-btn icon="mdi-refresh" variant="text" title="刷新 Skill 列表" :loading="loading" @click="loadSkills" />
			</div>

			<div v-if="loading && !skills.length" class="loading-state" style="min-height:300px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#74818c;"><v-progress-circular indeterminate color="primary"/><span>正在加载 Skill</span></div>
			<div v-else-if="filteredSkills.length" class="skill-grid">
				<SkillCard
					v-for="skill in filteredSkills"
					:key="skill.id"
					:skill="skill"
					:deleting="deletingSkillIds.has(skill.id)"
					@open-workspace="openEditor"
					@edit-info="openEditInfo"
					@delete-skill="confirmDeleteSkill"
				/>
			</div>
			<div v-else class="empty-state" style="min-height:320px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:32px;text-align:center;color:#84919b;">
				<v-icon :icon="keyword ? 'mdi-magnify' : 'mdi-puzzle-outline'" size="34" />
				<strong>{{ keyword ? '没有匹配的 Skill' : '还没有 Skill' }}</strong>
				<span>{{ keyword ? '尝试其他名称' : '创建 Skill，开始添加说明和文件' }}</span>
				<v-btn v-if="!keyword" color="primary" prepend-icon="mdi-plus" @click="openCreate">新建 Skill</v-btn>
			</div>
		</section>

		<v-dialog v-model="infoDialog" width="clamp(420px, 36vw, 640px)" max-width="calc(100vw - 32px)" @after-leave="resetInfo">
			<v-card class="form-dialog">
				<header class="form-head">
					<h2>{{ infoMode === 'create' ? '新建 Skill' : '编辑 Skill 基本信息' }}</h2>
					<v-btn icon="mdi-close" variant="text" title="关闭" @click="infoDialog = false" />
				</header>
				<v-card-text>
					<v-text-field
						v-model="infoName"
						autofocus
						label="Skill 名称"
						placeholder="例如 data-analysis"
						hint="支持字母、数字、点、下划线和连字符"
						persistent-hint
						maxlength="80"
						variant="outlined"
					/>
					<v-textarea
						v-model="infoDescription"
						label="描述"
						placeholder="简要说明这个 Skill 的用途"
						rows="4"
						maxlength="2000"
						counter
						variant="outlined"
					/>
					<p v-if="infoError" class="form-error" role="alert">{{ infoError }}</p>
				</v-card-text>
				<v-card-actions class="form-actions">
					<v-spacer />
					<v-btn @click="infoDialog = false">取消</v-btn>
					<v-btn color="primary" :disabled="!infoName.trim()" :loading="saving" @click="saveSkillInfo">
						{{ infoMode === 'create' ? '创建 Skill' : '保存更改' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>

		<v-dialog v-model="importDialog" width="clamp(460px, 42vw, 720px)" max-width="calc(100vw - 32px)" @after-leave="importFile = undefined">
			<v-card class="form-dialog">
				<header class="form-head"><h2>导入 Skill</h2><v-btn icon="mdi-close" variant="text" title="关闭" @click="importDialog = false" /></header>
				<v-card-text>
					<v-file-input v-model="importFile" accept=".zip,application/zip" label="Skill 压缩包" prepend-icon="mdi-archive-outline" variant="outlined" show-size hide-details />
					<p v-if="importError" class="form-error" role="alert">{{ importError }}</p>
				</v-card-text>
				<v-card-actions class="form-actions"><v-spacer/><v-btn @click="importDialog = false">取消</v-btn><v-btn color="primary" :disabled="!importFile" :loading="saving" @click="importSkill">导入 Skill</v-btn></v-card-actions>
			</v-card>
		</v-dialog>

		<SkillEditorDialog v-model="editorOpen" :skill-id="activeSkillId" :skill-name="activeSkillName" @saved="loadSkills" />
	</main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import SkillCard from '~/components/SkillCard.vue';
import SkillEditorDialog from '~/components/SkillEditorDialog.vue';
import skillService, { type SkillItem } from '~/services/skills';
import { useConfirm } from '~/composables/useConfirm';

type SkillSummary = SkillItem & { fileCount: number; updatedAt: string };
const skills = ref<SkillSummary[]>([]);
const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();
const keyword = ref('');
const loading = ref(false);
const saving = ref(false);
const deletingSkillIds = ref(new Set<number>());
const infoDialog = ref(false);
const infoMode = ref<'create' | 'edit'>('create');
const importDialog = ref(false);
const editorOpen = ref(false);
const activeSkillId = ref(0);
const activeSkillName = ref('');
const editingSkillId = ref<number | null>(null);
const infoName = ref('');
const infoDescription = ref('');
const infoError = ref('');
const importError = ref('');
const importFile = ref<File>();
const filteredSkills = computed(() => skills.value.filter(skill => skill.name.toLowerCase().includes(keyword.value.trim().toLowerCase())));

async function loadSkills() {
	loading.value = true;
	try {
		const list = await skillService.list();
		skills.value = await Promise.all(list.map(async item => {
			const files = await skillService.tree(item.id).catch(() => []);
			return { ...item, fileCount: files.filter(file => !file.directory).length, updatedAt: formatDate(item.modified) };
		}));
	} catch { notify('加载 Skill 列表失败', true); }
	finally { loading.value = false; }
}

function formatDate(timestamp: number) { return timestamp ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(timestamp) : '未知'; }
function openCreate() {
	infoMode.value = 'create';
	editingSkillId.value = null;
	infoError.value = '';
	infoDialog.value = true;
}
function openEditInfo(skill: SkillItem) {
	infoMode.value = 'edit';
	editingSkillId.value = skill.id;
	infoName.value = skill.name;
	infoDescription.value = skill.description ?? '';
	infoError.value = '';
	infoDialog.value = true;
}
function resetInfo() {
	infoName.value = '';
	infoDescription.value = '';
	infoError.value = '';
	editingSkillId.value = null;
}
async function saveSkillInfo() {
	const name = infoName.value.trim();
	if (!name) return;
	saving.value = true;
	try {
		if (infoMode.value === 'create') {
			const created = await skillService.create(name, infoDescription.value.trim());
			infoDialog.value = false;
			await loadSkills();
			openEditor(created);
		} else if (editingSkillId.value !== null) {
			await skillService.update(editingSkillId.value, name, infoDescription.value.trim());
			infoDialog.value = false;
			await loadSkills();
			notify('Skill 信息已更新');
		}
	} catch {
		infoError.value = infoMode.value === 'create'
			? '创建失败，请检查名称格式或是否已存在'
			: '保存失败，请检查名称是否已被占用';
	}
	finally { saving.value = false; }
}
async function importSkill() {
	if (!importFile.value) return;
	saving.value = true;
	try { const result = await skillService.import(importFile.value); importDialog.value = false; await loadSkills(); if (result.id) openEditor(result); notify('Skill 已导入'); }
	catch { importError.value = '导入失败，请确认压缩包结构有效'; }
	finally { saving.value = false; }
}
function confirmDeleteSkill(skill: SkillItem) {
	showConfirm({
		title: '删除 Skill',
		message: `确定删除 Skill「${skill.name}」吗？删除后将从列表中移除，但不会删除磁盘上的文件。`,
		icon: 'mdi-delete-outline',
		confirmText: '确认删除',
		onConfirm: async () => {
			deletingSkillIds.value.add(skill.id);
			try {
				await skillService.delete(skill.id);
				skills.value = skills.value.filter(item => item.id !== skill.id);
				notify('Skill 已删除');
			} catch {
				notify('删除 Skill 失败，请稍后重试', true);
			} finally {
				deletingSkillIds.value.delete(skill.id);
			}
		},
	});
}
function openEditor(skill: Pick<SkillItem, 'id' | 'name'>) { activeSkillId.value = skill.id; activeSkillName.value = skill.name; editorOpen.value = true; }
function notify(text: string, error = false) { $tip(text, error ? { color: 'error', icon: 'mdi-alert-circle' } : undefined); }
onMounted(loadSkills);
</script>

<style scoped>
.skills-page { min-height:calc(100vh - 64px); background:#f7f9fa; color:#243746; }
.page-head { min-height:118px; display:flex; align-items:center; justify-content:space-between; gap:20px; padding:22px 30px; border-bottom:1px solid #e2e8ec; background:#fff; }
.eyebrow { color:#71808a; font-size:11px; font-weight:700; letter-spacing:1px; }
h1 { margin:6px 0 3px; font-size:23px; line-height:30px; font-weight:650; }
.page-head p { margin:0; color:#697782; font-size:13px; }
.head-actions { display:flex; align-items:center; gap:8px; }
.library { max-width:1480px; margin:0 auto; padding:22px 30px 40px; }
.library-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:18px; }
.result-count { flex:1; color:#52616d; font-size:13px; font-weight:600; }
.search-field { max-width:300px; }
.skill-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); gap:14px; }
.empty-state,.loading-state { min-height:320px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:10px; color:#84919b; text-align:center; }
.empty-state strong { margin-top:4px; color:#344451; font-size:16px; font-weight:600; }
.empty-state span,.loading-state span { color:#74818c; font-size:13px; }
.empty-state :deep(.v-btn) { margin-top:8px; }
.form-dialog { border-radius:8px; }
.form-head { display:flex; align-items:center; justify-content:space-between; padding:12px 18px 8px 24px; }
.form-head h2 { margin:0; color:#263746; font-size:17px; font-weight:650; }
.form-actions { padding:8px 24px 20px; }
.form-error { margin:9px 0 0; color:#b42318; font-size:13px; }
@media(max-width:650px) { .page-head { align-items:flex-start; flex-direction:column; padding:18px 16px; } .head-actions { width:100%; } .library { padding:16px; } .library-toolbar { gap:6px; } .search-field { max-width:210px; } .skill-grid { grid-template-columns:1fr; } }
</style>
