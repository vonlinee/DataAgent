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
	<section class="table-list-panel" aria-labelledby="table-list-title">
		<div class="panel-heading">
			<div class="panel-title-group">
				<div
					id="table-list-title"
					class="panel-title text-subtitle-1 font-weight-bold"
				>
					数据表
				</div>
				<div class="panel-meta text-caption text-medium-emphasis">
					{{ selectedTables.length }} / {{ tables.length }} 个表已选择
				</div>
			</div>
			<div class="d-flex ga-1">
				<v-btn variant="text" size="small" @click="selectAll">全选</v-btn>
				<v-btn variant="text" size="small" @click="clearAll">清空</v-btn>
			</div>
		</div>

		<v-text-field
			v-model="searchQuery"
			label="搜索数据表"
			placeholder="表名、schema 或注释"
			prepend-inner-icon="mdi-magnify"
			variant="outlined"
			density="compact"
			clearable
			hide-details
			class="table-search mb-3"
		/>

		<div v-if="filteredTables.length === 0" class="panel-empty">
			<v-icon size="32" color="grey-lighten-1" class="mb-2"
				>mdi-table-search</v-icon
			>
			<span class="text-caption text-medium-emphasis">
				{{ tables.length ? '没有匹配的数据表' : '暂无表数据' }}
			</span>
		</div>
		<v-data-table
			v-else
			v-model:page="page"
			v-model:items-per-page="itemsPerPage"
			:headers="tableHeaders"
			:items="filteredTables"
			:item-value="tableKey"
			:items-per-page-options="pageSizeOptions"
			:footer-props="{
				'items-per-page-text': '每页显示：',
				'page-text': '{0}-{1} 共 {2} 条',
			}"
			fixed-header
			density="comfortable"
			class="table-grid"
			@click:row="handleRowClick"
		>
			<template #item.selection="{ item }">
				<v-checkbox-btn
					:model-value="selectedTables.includes(item.name)"
					color="primary"
					:aria-label="`选择表 ${item.name}`"
					@click.stop="toggleSelection(item.name)"
				/>
			</template>
			<template #item.schema="{ item }">
				<span class="cell-muted text-truncate" :title="item.schema || '—'">
					{{ item.schema || '—' }}
				</span>
			</template>
			<template #item.name="{ item }">
				<div
					class="table-name-cell text-truncate"
					:class="{
						'selected-table-name': tableKey(item) === selectedTableKey,
					}"
					:title="item.name"
				>
					<v-icon size="16" color="primary" class="mr-2">mdi-table</v-icon>
					<span>{{ item.name }}</span>
				</div>
			</template>
			<template #item.type="{ item }">
				<v-chip size="small" variant="tonal" :color="tableTypeColor(item.type)">
					{{ tableTypeLabel(item.type) }}
				</v-chip>
			</template>
			<template #item.description="{ item }">
				<v-text-field
					:model-value="item.description"
					placeholder="输入表注释"
					variant="underlined"
					density="compact"
					hide-details
					:aria-label="`表 ${item.name} 的注释`"
					@click.stop
					@update:model-value="
						emit('update-table-description', {
							table: item,
							description: $event,
						})
					"
				/>
			</template>
		</v-data-table>
	</section>
</template>

<script setup lang="ts">
import type { TableMetadata } from '@/services/datasource';

const selectedTables = defineModel<string[]>('selectedTables', {
	default: () => [],
});

const props = defineProps<{
	tables: TableMetadata[];
	selectedTableKey: string;
}>();

const emit = defineEmits<{
	'select-table': [table: TableMetadata];
	'update-table-description': [
		payload: { table: TableMetadata; description: string },
	];
}>();

const searchQuery = ref('');
const page = ref(1);
const itemsPerPage = ref(10);
const pageSizeOptions = [10, 20, 50, 100];
const tableHeaders = [
	{ title: '', key: 'selection', sortable: false, width: 48 },
	{ title: 'Schema名', key: 'schema', sortable: false },
	{ title: '表名', key: 'name', sortable: false },
	{ title: '表类型', key: 'type', sortable: false, width: 92 },
	{ title: '表注释', key: 'description', sortable: false },
];

const tables = computed(() => props.tables ?? []);
const filteredTables = computed(() => {
	const query = searchQuery.value.trim().toLowerCase();
	if (!query) return tables.value;
	return tables.value.filter((table) =>
		[table.schema, table.name, table.type, table.description].some((value) =>
			value?.toLowerCase().includes(query),
		),
	);
});

watch([searchQuery, itemsPerPage], () => {
	page.value = 1;
});

function tableKey(table: TableMetadata): string {
	return `${table.schema?.trim().toLowerCase() ?? ''}.${table.name.trim().toLowerCase()}`;
}

function handleRowClick(
	_: Event,
	data: { item: TableMetadata | { raw: TableMetadata } },
) {
	const table = 'raw' in data.item ? data.item.raw : data.item;
	emit('select-table', table);
}

function toggleSelection(tableName: string) {
	selectedTables.value = selectedTables.value.includes(tableName)
		? selectedTables.value.filter((name) => name !== tableName)
		: [...selectedTables.value, tableName];
}

function selectAll() {
	selectedTables.value = [
		...new Set([
			...selectedTables.value,
			...filteredTables.value.map((table) => table.name),
		]),
	];
}

function clearAll() {
	selectedTables.value = [];
}

function tableTypeLabel(type?: string): string {
	const normalizedType = type?.trim().toUpperCase();
	if (normalizedType?.includes('VIEW')) return '视图';
	if (
		normalizedType === 'TABLE' ||
		normalizedType === 'BASE TABLE' ||
		normalizedType === 'SYSTEM TABLE' ||
		normalizedType === 'FOREIGN TABLE'
	)
		return '表';
	return type?.trim() || '未知';
}

function tableTypeColor(type?: string): string {
	if (!type?.trim()) return 'grey';
	return type.trim().toUpperCase().includes('VIEW') ? 'info' : 'primary';
}
</script>

<style scoped>
.table-list-panel {
	display: flex;
	min-width: 300px;
	min-height: 0;
	flex: 0 0 38%;
	flex-direction: column;
	padding: 20px;
	border-right: 1px solid #e2e8f0;
}
.panel-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	margin-bottom: 12px;
}
.panel-title-group {
	display: flex;
	min-width: 0;
	flex: 1 1 auto;
	align-items: baseline;
	gap: 8px;
}
.panel-title {
	flex: 0 0 auto;
	white-space: nowrap;
}
.panel-meta {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.table-search {
	flex: 0 0 auto;
	min-height: 0;
}
.table-search :deep(.v-input__control),
.table-search :deep(.v-field) {
	min-height: 42px;
}
.table-grid {
	display: flex;
	min-height: 0;
	flex: 1 1 auto;
	flex-direction: column;
	height: auto !important;
	overflow: hidden;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
}
.table-grid :deep(.v-table) {
	display: flex;
	min-height: 0;
	flex: 1 1 auto;
	flex-direction: column;
}
.table-grid :deep(.v-table__wrapper) {
	min-height: 0;
	flex: 1 1 auto;
	overflow-y: auto !important;
	overflow-x: auto;
}
.table-grid :deep(.v-data-table-footer) {
	flex: 0 0 auto;
}
.table-grid :deep(table) {
	min-width: 620px;
	table-layout: fixed;
}
.table-name-cell {
	display: flex;
	align-items: center;
	font-weight: 600;
	color: #0f172a;
}
.selected-table-name {
	color: rgb(var(--v-theme-primary));
}
.cell-muted {
	color: #64748b;
	font-size: 13px;
}
.panel-empty {
	display: flex;
	min-height: 430px;
	flex: 1;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	border: 1px dashed #cbd5e1;
	border-radius: 8px;
}
@media (max-width: 900px) {
	.table-list-panel {
		border-right: 0;
		border-bottom: 1px solid #e2e8f0;
	}
	.table-grid {
		flex: 0 0 auto;
	}
}
</style>
