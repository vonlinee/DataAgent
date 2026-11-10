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
	<section class="column-list-panel" aria-labelledby="column-list-title">
		<div v-if="table" class="panel-heading">
			<div class="panel-title-group">
				<div
					id="column-list-title"
					class="panel-title text-subtitle-1 font-weight-bold text-truncate"
				>
					列信息 · {{ table.name }}
				</div>
				<div class="panel-meta text-caption text-medium-emphasis">
					{{ table.schema || '默认 schema' }}
					<span v-if="table.description"> · {{ table.description }}</span>
				</div>
			</div>
			<v-chip
				class="column-count-chip"
				size="small"
				variant="tonal"
				color="primary"
			>
				{{ filteredColumns.length
				}}<span v-if="searchQuery"> / {{ columns.length }}</span> 列
			</v-chip>
		</div>
		<v-text-field
			v-if="table && !loading && columns.length > 0"
			v-model="searchQuery"
			label="搜索列"
			placeholder="列名、类型或注释"
			prepend-inner-icon="mdi-magnify"
			variant="outlined"
			density="compact"
			clearable
			hide-details
			class="column-search mb-3"
		/>

		<div v-if="!table" class="panel-empty">
			<v-icon size="36" color="grey-lighten-1" class="mb-2"
				>mdi-view-column-outline</v-icon
			>
			<span class="text-caption text-medium-emphasis"
				>选择左侧数据表查看列信息</span
			>
		</div>
		<template v-else-if="loading">
			<div class="panel-empty">
				<v-progress-circular indeterminate color="primary" size="32" />
				<span class="text-caption text-medium-emphasis mt-3"
					>正在加载列信息</span
				>
			</div>
		</template>
		<div v-else-if="columns.length === 0" class="panel-empty">
			<v-icon size="36" color="grey-lighten-1" class="mb-2"
				>mdi-view-column-outline</v-icon
			>
			<span class="text-caption text-medium-emphasis">暂无列信息</span>
		</div>
		<div v-else-if="filteredColumns.length === 0" class="panel-empty">
			<v-icon size="36" color="grey-lighten-1" class="mb-2"
				>mdi-view-column-search-outline</v-icon
			>
			<span class="text-caption text-medium-emphasis">没有匹配的列信息</span>
		</div>
		<template v-else>
			<div class="columns-table-scroll">
				<div class="columns-table" role="table" aria-label="列信息">
					<div class="columns-row columns-header" role="row">
						<div role="columnheader">列名</div>
						<div role="columnheader">类型</div>
						<div role="columnheader">约束</div>
						<div role="columnheader">列注释</div>
					</div>
					<div
						v-for="column in paginatedColumns"
						:key="column.name"
						class="columns-row column-item"
						role="row"
					>
						<div
							class="font-weight-medium text-truncate"
							:title="column.name"
							role="cell"
						>
							{{ column.name }}
						</div>
						<div
							class="cell-muted text-truncate"
							:title="column.type || '—'"
							role="cell"
						>
							{{ column.type || '—' }}
						</div>
						<div class="constraint-cell" role="cell">
							<v-chip
								v-if="column.primary"
								size="x-small"
								color="warning"
								variant="tonal"
							>
								主键
							</v-chip>
							<v-chip
								v-if="column.notnull"
								size="x-small"
								color="info"
								variant="tonal"
							>
								非空
							</v-chip>
							<span v-if="!column.primary && !column.notnull" class="cell-muted"
								>—</span
							>
						</div>
						<div role="cell" @click.stop>
							<v-text-field
								:model-value="column.description"
								placeholder="输入列注释"
								variant="underlined"
								density="compact"
								hide-details
								:aria-label="`列 ${column.name} 的注释`"
								@update:model-value="
									emit('update-column-description', {
										column,
										description: $event,
									})
								"
							/>
						</div>
					</div>
				</div>
			</div>

			<div class="list-footer">
				<span class="text-caption text-medium-emphasis">
					{{ (page - 1) * itemsPerPage + 1 }}-{{
						Math.min(page * itemsPerPage, filteredColumns.length)
					}}
					/ {{ filteredColumns.length }}
				</span>
				<div class="d-flex align-center ga-3">
					<v-select
						v-model="itemsPerPage"
						:items="pageSizeOptions"
						label="每页"
						variant="outlined"
						density="compact"
						hide-details
						class="page-size-select"
					/>
					<v-pagination
						v-model="page"
						:length="pageCount"
						:total-visible="5"
						density="compact"
						rounded="circle"
					/>
				</div>
			</div>
		</template>
	</section>
</template>

<script setup lang="ts">
import type { ColumnMetadata, TableMetadata } from '@/services/datasource';

const props = defineProps<{
	table: TableMetadata | null;
	columns: ColumnMetadata[];
	loading: boolean;
}>();

const emit = defineEmits<{
	'update-column-description': [
		payload: { column: ColumnMetadata; description: string },
	];
}>();

const page = ref(1);
const itemsPerPage = ref(10);
const pageSizeOptions = [10, 20, 50, 100];
const searchQuery = ref('');
const columns = computed(() => props.columns ?? []);
const filteredColumns = computed(() => {
	const query = searchQuery.value.trim().toLowerCase();
	if (!query) return columns.value;
	return columns.value.filter((column) =>
		[column.name, column.type, column.description].some((value) =>
			value?.toLowerCase().includes(query),
		),
	);
});
const pageCount = computed(() =>
	Math.max(1, Math.ceil(filteredColumns.value.length / itemsPerPage.value)),
);
const paginatedColumns = computed(() => {
	const start = (page.value - 1) * itemsPerPage.value;
	return filteredColumns.value.slice(start, start + itemsPerPage.value);
});

watch(
	[() => props.table, () => props.columns, searchQuery, itemsPerPage],
	() => {
		page.value = 1;
	},
);
watch(pageCount, (count) => {
	if (page.value > count) page.value = count;
});
</script>

<style scoped>
.column-list-panel {
	display: flex;
	min-width: 0;
	min-height: 0;
	flex: 1 1 auto;
	flex-direction: column;
	padding: 20px;
	overflow: hidden;
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
	min-width: 0;
	flex: 0 1 auto;
	white-space: nowrap;
}
.panel-meta {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.column-count-chip {
	flex: 0 0 auto;
}
.column-search {
	flex: 0 0 auto;
	min-height: 0;
}
.column-search :deep(.v-input__control),
.column-search :deep(.v-field) {
	min-height: 42px;
}
.panel-empty {
	display: flex;
	min-height: 390px;
	flex: 1;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	border: 1px dashed #cbd5e1;
	border-radius: 8px;
}
.columns-table-scroll {
	min-width: 0;
	min-height: 0;
	flex: 1 1 auto;
	overflow: auto;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
}
.columns-table {
	min-width: 620px;
}
.columns-row {
	display: grid;
	grid-template-columns:
		minmax(110px, 1fr) minmax(100px, 0.9fr) minmax(92px, 0.85fr)
		minmax(150px, 1.2fr);
	align-items: center;
	gap: 12px;
	min-height: 56px;
	padding: 0 16px;
}
.columns-header {
	position: sticky;
	top: 0;
	z-index: 1;
	min-height: 42px;
	color: #475569;
	font-size: 12px;
	font-weight: 600;
	background: #f8fafc;
	border-bottom: 1px solid #e2e8f0;
}
.column-item {
	border-bottom: 1px solid #f1f5f9;
	font-size: 13px;
}
.column-item:last-child {
	border-bottom: 0;
}
.constraint-cell {
	display: flex;
	flex-wrap: wrap;
	gap: 4px;
}
.cell-muted {
	color: #64748b;
	font-size: 13px;
}
.list-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	margin-top: 16px;
}
.page-size-select {
	max-width: 92px;
}
@media (max-width: 700px) {
	.columns-row {
		grid-template-columns: minmax(100px, 1fr) minmax(82px, 0.9fr) minmax(
				80px,
				0.8fr
			);
	}
	.columns-row > :last-child {
		display: none;
	}
	.list-footer {
		align-items: flex-start;
		flex-direction: column;
	}
}
</style>
