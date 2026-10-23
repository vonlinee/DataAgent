<template>
	<aside class="skills-list">
		<div class="panel-title"><span>SKILLS</span><v-btn icon="mdi-refresh" size="small" variant="text" title="刷新列表" @click="$emit('refresh')" /></div>
		<button v-for="skill in skills" :key="skill.name" class="skill-row" :class="{ selected: selected === skill.name }" @click="$emit('select', skill.name)">
			<v-icon icon="mdi-puzzle-outline" size="18" /><span>{{ skill.name }}</span><v-icon v-if="selected === skill.name" icon="mdi-chevron-right" size="18" />
		</button>
		<div v-if="!skills.length" class="empty-list">尚无 Skill</div>
	</aside>
</template>
<script setup lang="ts">
import type { SkillItem } from '~/services/skills';
defineProps<{ skills: SkillItem[]; selected: string }>();
defineEmits<{ select: [name: string]; refresh: [] }>();
</script>
<style scoped>
.skills-list { min-width:0; overflow:auto; border-right:1px solid #e2e8f0; }
.panel-title { position:sticky; top:0; z-index:1; min-height:48px; display:flex; align-items:center; justify-content:space-between; padding:0 12px 0 16px; background:#fff; border-bottom:1px solid #edf1f4; color:#637282; font-size:11px; font-weight:700; letter-spacing:.7px; }
.skill-row { width:100%; border:0; display:flex; align-items:center; gap:9px; min-height:38px; padding:0 14px; background:transparent; color:#334155; text-align:left; cursor:pointer; }
.skill-row span { flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.skill-row:hover { background:#f1f5f9; }
.skill-row.selected { color:#145c53; background:#eaf4f1; }
.empty-list { padding:20px 14px; color:#94a3b8; font-size:13px; text-align:center; }
</style>
