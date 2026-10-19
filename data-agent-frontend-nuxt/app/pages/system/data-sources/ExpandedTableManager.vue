<!--
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
 -->

<template>
	<div class="expand-row-content pa-6">
		<div class="manage-tables-container">
			<div class="manager-toolbar">
				<div class="d-flex align-center min-width-0">
					<v-icon size="20" class="mr-2 text-primary">mdi-table-cog</v-icon>
					<span class="text-subtitle-1 font-weight-bold">数据表管理</span>
					<span class="text-caption text-medium-emphasis ml-4 text-no-wrap">已选择 {{ selectedTables.length }} 个表 · 共 {{ allTables.length }} 个表</span>
				</div>
				<div class="ga-2 d-flex flex-wrap justify-end">
					<v-btn variant="text" size="small" @click="selectAll">全选</v-btn>
					<v-btn variant="text" size="small" @click="clearAll">清空</v-btn>
					<v-btn color="primary" size="small" elevation="0" class="px-4" :loading="updating" @click="$emit('update-tables')">更新数据表</v-btn>
				</div>
			</div>
			<v-divider class="my-4" />

			<v-text-field
				v-if="!loadingTables && !fetchError && allTables.length > 0"
				v-model="searchQuery"
				label="搜索数据表"
				placeholder="输入表名、schema 或注释"
				prepend-inner-icon="mdi-magnify"
				variant="outlined"
				density="compact"
				clearable
				hide-details
				class="mb-4 table-search"
			>
				<template #append-inner><span class="text-caption text-medium-emphasis text-no-wrap">{{ filteredTables.length }} / {{ allTables.length }}</span></template>
			</v-text-field>

			<div v-if="loadingTables" class="d-flex justify-center py-8"><v-progress-circular indeterminate color="primary" size="32" /></div>
			<div v-else-if="fetchError" class="empty-state error-state">
				<v-icon size="48" color="error" class="mb-3">mdi-database-off-outline</v-icon>
				<p class="text-body-2 text-medium-emphasis mb-3">数据获取失败</p>
				<p class="text-caption text-medium-emphasis mb-4">无法拉取数据表列表，请检查连接后重试</p>
				<v-btn color="primary" variant="outlined" size="small" @click="$emit('retry')">重试</v-btn>
			</div>

			<div v-else-if="filteredTables.length > 0" class="table-browser" aria-label="数据表列表">
				<v-data-table-virtual
					v-model:expanded="expandedTableNames"
					:headers="tableHeaders"
					:items="filteredTables"
					item-value="name"
					show-expand
					hover
					:item-height="64"
					height="420"
					class="table-virtual-table"
				>
					<template #item.data-table-expand="{ props, internalItem, isExpanded }">
						<v-btn v-bind="props" :icon="isExpanded(internalItem) ? 'mdi-chevron-up' : 'mdi-chevron-down'" aria-label="展开列信息" />
					</template>
					<template #item.selection="{ item }">
						<v-checkbox :model-value="selectedTables.includes(item.name)" density="compact" hide-details color="primary" :aria-label="`选择表 ${item.name}`" @click.stop="toggleSelection(item.name)" />
					</template>
					<template #item.schema="{ item }"><span class="cell-muted text-truncate" :title="item.schema || '—'">{{ item.schema || '—' }}</span></template>
					<template #item.name="{ item }"><div class="table-name-cell text-truncate" :title="item.name"><v-icon size="16" color="primary" class="mr-2">mdi-table</v-icon><span>{{ item.name }}</span></div></template>
					<template #item.type="{ item }"><v-chip :color="tableTypeColor(item.type)" size="small" variant="tonal" class="table-type-chip">{{ tableTypeLabel(item.type) }}</v-chip></template>
					<template #item.description="{ item }"><span class="cell-muted text-truncate" :title="item.description || '暂无注释'">{{ item.description || '暂无注释' }}</span></template>
					<template #expanded-row="{ columns, item }">
						<tr>
							<td :colspan="columns.length" class="pa-0">
								<div class="inline-column-panel" aria-live="polite">
									<div class="column-panel-header">
										<div class="min-width-0"><div class="text-subtitle-2 font-weight-bold text-truncate" :title="item.name">列信息 · {{ item.name }}</div><div class="text-caption text-medium-emphasis text-truncate">{{ item.schema || '默认 schema' }} · {{ item.description || '暂无表注释' }}</div></div>
										<v-chip size="small" variant="tonal" color="primary">{{ columnsFor(item.name).length }} 列</v-chip>
									</div>
									<v-progress-linear v-if="isColumnsLoading(item.name)" indeterminate color="primary" height="2" />
									<div v-else-if="columnsFor(item.name).length === 0" class="column-empty column-empty-small"><v-icon size="24" color="grey-lighten-1" class="mb-2">mdi-view-column-outline</v-icon><span class="text-caption text-medium-emphasis">暂无列信息</span></div>
									<div v-else class="columns-table">
										<div class="columns-header columns-grid"><div>列名</div><div>类型</div><div>约束</div><div>列注释</div></div>
										<v-virtual-scroll :items="columnsFor(item.name)" :item-height="48" height="324">
											<template #default="{ item: column }">
												<div class="column-row columns-grid"><div class="font-weight-medium text-truncate" :title="column.name">{{ column.name }}</div><div class="cell-muted text-truncate" :title="column.type || '—'">{{ column.type || '—' }}</div><div class="constraint-cell"><v-chip v-if="column.primary" size="x-small" color="warning" variant="tonal">主键</v-chip><v-chip v-if="column.notnull" size="x-small" color="info" variant="tonal">非空</v-chip><span v-if="!column.primary && !column.notnull" class="cell-muted">—</span></div><div class="cell-muted text-truncate" :title="column.description || '—'">{{ column.description || '—' }}</div></div>
											</template>
										</v-virtual-scroll>
									</div>
								</div>
							</td>
						</tr>
					</template>
				</v-data-table-virtual>
			</div>

			<div v-else-if="!loadingTables && !fetchError && allTables.length > 0" class="empty-state"><v-icon size="40" color="grey" class="mb-2">mdi-table-search</v-icon><p class="text-body-2 text-medium-emphasis">没有匹配“{{ searchQuery.trim() }}”的数据表</p></div>
			<div v-else-if="!loadingTables && !fetchError" class="empty-state"><v-icon size="40" color="grey" class="mb-2">mdi-table-off</v-icon><p class="text-body-2 text-medium-emphasis">暂无表数据，请确保数据源连接正常后刷新</p></div>
		</div>
	</div>
</template>

<script setup lang="ts">
import datasourceService, { type ColumnMetadata, type TableMetadata } from '@/services/datasource';

const selectedTables = defineModel<string[]>('selectedTables', { default: () => [] });
const searchQuery = ref('');
const expandedTableNames = ref<string[]>([]);
const columnCache = ref<Record<string, ColumnMetadata[]>>({});
const columnsLoading = ref<Record<string, boolean>>({});

const props = defineProps<{ allTables: TableMetadata[]; loadingTables: boolean; fetchError: boolean; updating: boolean; datasourceId: number }>();
defineEmits<{ 'update-tables': []; retry: [] }>();

const tableHeaders = [
	{ title: '', key: 'selection', sortable: false, width: 48 },
	{ title: 'Schema', key: 'schema', sortable: false },
	{ title: '表名', key: 'name', sortable: false },
	{ title: '类型', key: 'type', sortable: false, width: 92 },
	{ title: '表注释', key: 'description', sortable: false },
];

const allTables = computed(() => props.allTables ?? []);
const filteredTables = computed(() => {
	const query = searchQuery.value.trim().toLowerCase();
	if (!query) return allTables.value;
	return allTables.value.filter((table) => [table.schema, table.name, table.type, table.description].some((value) => value?.toLowerCase().includes(query)));
});

function toggleSelection(tableName: string) {
	selectedTables.value = selectedTables.value.includes(tableName) ? selectedTables.value.filter((name) => name !== tableName) : [...selectedTables.value, tableName];
}
function selectAll() { selectedTables.value = [...new Set([...selectedTables.value, ...filteredTables.value.map((table) => table.name)])]; }
function clearAll() { selectedTables.value = []; }

function tableTypeLabel(type?: string): string {
	const normalizedType = type?.trim().toUpperCase();
	if (normalizedType?.includes('VIEW')) return '视图';
	if (normalizedType === 'TABLE' || normalizedType === 'BASE TABLE' || normalizedType === 'SYSTEM TABLE' || normalizedType === 'FOREIGN TABLE') return '表';
	return type?.trim() || '未知';
}

function tableTypeColor(type?: string): string {
	if (!type?.trim()) return 'grey';
	return type.trim().toUpperCase().includes('VIEW') ? 'info' : 'primary';
}

async function selectTable(table: TableMetadata) {
	if (columnCache.value[table.name] || columnsLoading.value[table.name]) return;
	columnsLoading.value = { ...columnsLoading.value, [table.name]: true };
	try {
		const columns = await datasourceService.getTableColumnDetails(props.datasourceId, table.name);
		columnCache.value = { ...columnCache.value, [table.name]: columns };
	} finally {
		columnsLoading.value = { ...columnsLoading.value, [table.name]: false };
	}
}

watch(expandedTableNames, (names) => {
	for (const tableName of names) {
		const table = allTables.value.find((candidate) => candidate.name === tableName);
		if (table) void selectTable(table);
	}
}, { deep: true });

watch(() => props.allTables, () => {
	if (expandedTableNames.value.some((name) => !props.allTables.some((table) => table.name === name))) expandedTableNames.value = [];
});

function columnsFor(tableName: string): ColumnMetadata[] {
	return columnCache.value[tableName] ?? [];
}

function isColumnsLoading(tableName: string): boolean {
	return columnsLoading.value[tableName] === true;
}
</script>

<style scoped>
.manage-tables-container { background: white; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.02); padding: 24px; }
.manager-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.table-browser { min-width: 0; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; background: #fff; }
.table-virtual-table { border: 0 !important; }
.table-virtual-table :deep(.v-table__wrapper) { overflow-x: hidden; }
.table-virtual-table :deep(table) { width: 100%; table-layout: fixed; }
.table-name-cell { display: flex; align-items: center; font-weight: 600; color: #0f172a; min-width: 0; }
.table-type-chip { min-width: 48px; justify-content: center; }
.cell-muted { color: #64748b; font-size: 13px; }
.inline-column-panel { margin: 0 16px 16px 48px; border: 1px solid #dbeafe; border-radius: 8px; overflow: hidden; background: #f8fbff; }
.column-panel-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 64px; padding: 12px 16px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
.column-empty { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; padding: 24px; text-align: center; }
.column-empty-small { min-height: 360px; }
.columns-header, .columns-grid { display: grid; grid-template-columns: minmax(120px, 1.1fr) minmax(80px, 0.8fr) minmax(92px, 0.9fr) minmax(110px, 1fr); align-items: center; gap: 12px; }
.columns-header { min-height: 40px; padding: 0 16px; color: #64748b; font-size: 12px; font-weight: 600; background: #fff; border-bottom: 1px solid #e2e8f0; }
.column-row { min-height: 48px; padding: 0 16px; border-bottom: 1px solid #f1f5f9; font-size: 13px; }
.constraint-cell { display: flex; flex-wrap: wrap; gap: 4px; }
.empty-state, .error-state { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 160px; padding: 24px; }
.expand-row-content { animation: expandSlideDown 0.3s ease-out; }
@keyframes expandSlideDown { from { max-height: 0; opacity: 0; overflow: hidden; } to { max-height: 900px; opacity: 1; overflow: visible; } }
@media (max-width: 640px) { .manager-toolbar { align-items: flex-start; flex-direction: column; } .columns-header, .columns-grid { grid-template-columns: minmax(110px, 1fr) minmax(76px, 0.8fr) minmax(80px, 0.8fr); } .columns-grid > :last-child { display: none; } }
</style>
