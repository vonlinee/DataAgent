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
	<v-dialog v-model="modelValue" max-width="1320">
		<v-card class="metadata-dialog">
			<v-card-title class="dialog-header">
				<div class="dialog-heading">
					<v-avatar color="blue-lighten-5" rounded="lg" size="40" class="mr-3">
						<v-icon color="primary">mdi-table-cog</v-icon>
					</v-avatar>
					<div class="dialog-title-line">
						<div class="dialog-title text-h6 font-weight-bold">
							数据表与列管理
						</div>
						<div class="dialog-title-meta text-body-2 text-medium-emphasis">
							{{ datasourceName }} · {{ tables.length }} 个表
						</div>
					</div>
				</div>
				<v-btn
					icon="mdi-close"
					variant="text"
					aria-label="关闭数据表与列管理"
					@click="modelValue = false"
				/>
			</v-card-title>

			<v-divider />
			<v-card-text class="pa-0 dialog-content">
				<div v-if="loadingTables && tables.length === 0" class="dialog-state">
					<v-progress-circular indeterminate color="primary" size="36" />
					<span class="text-body-2 text-medium-emphasis mt-3"
						>正在加载数据表</span
					>
				</div>
				<div v-else-if="fetchError && tables.length === 0" class="dialog-state">
					<v-icon size="44" color="error" class="mb-3"
						>mdi-database-off-outline</v-icon
					>
					<span class="text-body-2 text-medium-emphasis mb-1"
						>数据获取失败</span
					>
					<span class="text-caption text-medium-emphasis mb-4">
						无法拉取数据表列表，请检查连接后重试
					</span>
					<v-btn color="primary" variant="outlined" @click="emit('retry')">
						重试
					</v-btn>
				</div>
				<div v-else ref="workspaceRef" class="metadata-workspace">
					<DatasourceTableList
						v-model:selected-tables="selectedTables"
						:tables="tables"
						:selected-table-key="selectedTableKey"
						:style="{ flex: `0 0 ${tablePanePercent}%` }"
						@select-table="selectTable"
						@update-table-description="updateTableDescription"
					/>
					<div
						class="metadata-resizer"
						:class="{ 'is-resizing': isResizing }"
						role="separator"
						tabindex="0"
						aria-label="调整数据表和列信息区域宽度"
						aria-orientation="vertical"
						:aria-valuemin="MIN_TABLE_PERCENT"
						:aria-valuemax="MAX_TABLE_PERCENT"
						:aria-valuenow="Math.round(tablePanePercent)"
						@pointerdown="startResize"
						@keydown="handleResizerKeydown"
						@dblclick="resetPaneWidth"
					>
						<v-icon size="18" color="grey">mdi-drag-vertical</v-icon>
					</div>
					<DatasourceColumnList
						:table="selectedTable"
						:columns="selectedColumns"
						:loading="selectedTable ? isColumnsLoading(selectedTable) : false"
						@update-column-description="updateColumnDescription"
					/>
				</div>
			</v-card-text>

			<v-divider />
			<v-card-actions class="dialog-actions">
				<span class="text-caption text-medium-emphasis">
					勾选的表将用于当前智能体的数据查询
				</span>
				<v-spacer />
				<v-btn variant="text" @click="modelValue = false">关闭</v-btn>
				<v-btn
					color="primary"
					elevation="0"
					prepend-icon="mdi-content-save-outline"
					:loading="updating"
					@click="submitChanges"
				>
					更新数据库
				</v-btn>
			</v-card-actions>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import datasourceService, {
	type ColumnMetadata,
	type DatasourceCommentTableChange,
	type TableMetadata,
} from '@/services/datasource';
import DatasourceColumnList from './DatasourceColumnList.vue';
import DatasourceTableList from './DatasourceTableList.vue';

const modelValue = defineModel<boolean>({ default: false });
const selectedTables = defineModel<string[]>('selectedTables', {
	default: () => [],
});

const props = defineProps<{
	datasourceId: number;
	datasourceName: string;
	tables: TableMetadata[];
	loadingTables: boolean;
	fetchError: boolean;
	updating: boolean;
}>();

const emit = defineEmits<{
	'update-tables': [comments: DatasourceCommentTableChange[]];
	retry: [];
}>();

const selectedTableKey = ref('');
const workspaceRef = ref<HTMLElement | null>(null);
const tablePanePercent = ref(38);
const isResizing = ref(false);
const MIN_TABLE_PERCENT = 25;
const MAX_TABLE_PERCENT = 62;
let previousBodyCursor = '';
let previousBodyUserSelect = '';
const columnCache = ref<Record<string, ColumnMetadata[]>>({});
const columnsLoading = ref<Record<string, boolean>>({});
const initialComments = ref<
	Record<string, { table: string; columns: Record<string, string> }>
>({});

const tables = computed(() => props.tables ?? []);
const datasourceName = computed(() => props.datasourceName || '数据源');
const selectedTable = computed(
	() =>
		tables.value.find((table) => tableKey(table) === selectedTableKey.value) ??
		null,
);
const selectedColumns = computed(() =>
	selectedTable.value ? columnsFor(selectedTable.value) : [],
);

watch(
	() => [modelValue.value, tables.value] as const,
	([visible]) => {
		if (!visible) return;
		initializeTableSelection();
	},
	{ immediate: true, deep: true },
);

watch(
	() => props.datasourceId,
	() => {
		tablePanePercent.value = 38;
		selectedTableKey.value = '';
		columnCache.value = {};
		columnsLoading.value = {};
		initialComments.value = {};
	},
);

function startResize(event: PointerEvent) {
	if (isResizing.value || window.innerWidth <= 900) return;
	event.preventDefault();
	isResizing.value = true;
	previousBodyCursor = document.body.style.cursor;
	previousBodyUserSelect = document.body.style.userSelect;
	document.body.style.cursor = 'col-resize';
	document.body.style.userSelect = 'none';
	(window as Window).addEventListener('pointermove', handleResize);
	(window as Window).addEventListener('pointerup', stopResize);
	(window as Window).addEventListener('pointercancel', stopResize);
}

function handleResize(event: PointerEvent) {
	if (!isResizing.value) return;
	const workspace = workspaceRef.value;
	if (!workspace || workspace.clientWidth <= 0) return;
	const bounds = workspace.getBoundingClientRect();
	const nextPercent = ((event.clientX - bounds.left) / bounds.width) * 100;
	tablePanePercent.value = Math.min(
		MAX_TABLE_PERCENT,
		Math.max(MIN_TABLE_PERCENT, nextPercent),
	);
}

function stopResize() {
	if (!isResizing.value) return;
	isResizing.value = false;
	document.body.style.cursor = previousBodyCursor;
	document.body.style.userSelect = previousBodyUserSelect;
	window.removeEventListener('pointermove', handleResize);
	window.removeEventListener('pointerup', stopResize);
	window.removeEventListener('pointercancel', stopResize);
}

function handleResizerKeydown(event: KeyboardEvent) {
	const step = event.shiftKey ? 5 : 2;
	let nextPercent: number | null = null;
	if (event.key === 'ArrowLeft') nextPercent = tablePanePercent.value - step;
	if (event.key === 'ArrowRight') nextPercent = tablePanePercent.value + step;
	if (event.key === 'Home') nextPercent = MIN_TABLE_PERCENT;
	if (event.key === 'End') nextPercent = MAX_TABLE_PERCENT;
	if (nextPercent === null) return;
	event.preventDefault();
	tablePanePercent.value = Math.min(
		MAX_TABLE_PERCENT,
		Math.max(MIN_TABLE_PERCENT, nextPercent),
	);
}

function resetPaneWidth() {
	tablePanePercent.value = 38;
}

onBeforeUnmount(stopResize);

function tableKey(table: TableMetadata): string {
	return `${table.schema?.trim().toLowerCase() ?? ''}.${table.name.trim().toLowerCase()}`;
}

function initializeTableSelection() {
	for (const table of tables.value) {
		const key = tableKey(table);
		if (!initialComments.value[key]) {
			initialComments.value[key] = {
				table: itemDescription(table),
				columns: {},
			};
		}
	}
	if (
		!tables.value.some((table) => tableKey(table) === selectedTableKey.value)
	) {
		const firstTable = tables.value[0];
		selectedTableKey.value = firstTable ? tableKey(firstTable) : '';
	}
	if (selectedTable.value) void loadColumns(selectedTable.value);
}

function selectTable(table: TableMetadata) {
	selectedTableKey.value = tableKey(table);
	void loadColumns(table);
}

async function loadColumns(table: TableMetadata) {
	const key = tableKey(table);
	if (columnCache.value[key] || columnsLoading.value[key]) return;
	columnsLoading.value = { ...columnsLoading.value, [key]: true };
	try {
		const columns = await datasourceService.getInitializedTableColumnDetails(
			props.datasourceId,
			table.name,
			table.schema,
		);
		columnCache.value = { ...columnCache.value, [key]: columns };
		initialComments.value = {
			...initialComments.value,
			[key]: {
				table: initialComments.value[key]?.table ?? itemDescription(table),
				columns: Object.fromEntries(
					columns.map((column) => [column.name, itemDescription(column)]),
				),
			},
		};
	} finally {
		columnsLoading.value = { ...columnsLoading.value, [key]: false };
	}
}

function columnsFor(table: TableMetadata): ColumnMetadata[] {
	return columnCache.value[tableKey(table)] ?? [];
}

function isColumnsLoading(table: TableMetadata): boolean {
	return columnsLoading.value[tableKey(table)] === true;
}

function itemDescription(item: { description?: string }): string {
	return item.description ?? '';
}

function updateTableDescription({
	table,
	description,
}: {
	table: TableMetadata;
	description: string;
}) {
	table.description = description;
}

function updateColumnDescription({
	column,
	description,
}: {
	column: ColumnMetadata;
	description: string;
}) {
	column.description = description;
}

function collectCommentChanges(): DatasourceCommentTableChange[] {
	return tables.value.flatMap((table) => {
		const initial = initialComments.value[tableKey(table)];
		const columns = columnsFor(table)
			.filter(
				(column) => initial?.columns[column.name] !== itemDescription(column),
			)
			.map((column) => ({
				name: column.name,
				description: itemDescription(column),
			}));
		const description = itemDescription(table);
		if (!initial || (initial.table === description && columns.length === 0))
			return [];
		return [{ schema: table.schema, name: table.name, description, columns }];
	});
}

function submitChanges() {
	const changes = collectCommentChanges();
	emit('update-tables', changes);
	// Keep the local baseline aligned with the values just submitted so reopening
	// the dialog does not resend unchanged comments before the parent refreshes.
	if (changes.length > 0) {
		for (const table of tables.value) {
			const key = tableKey(table);
			initialComments.value[key] = {
				table: itemDescription(table),
				columns: Object.fromEntries(
					columnsFor(table).map((column) => [
						column.name,
						itemDescription(column),
					]),
				),
			};
		}
	}
}
</script>

<style scoped>
.metadata-dialog {
	display: flex;
	height: min(800px, calc(100vh - 64px));
	max-height: calc(100vh - 32px);
	flex-direction: column;
	overflow: hidden;
}
.dialog-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	padding: 18px 24px;
}
.dialog-heading {
	display: flex;
	min-width: 0;
	flex: 1 1 auto;
	align-items: center;
}
.dialog-title-line {
	display: flex;
	min-width: 0;
	flex: 1 1 auto;
	align-items: baseline;
	gap: 12px;
}
.dialog-title {
	flex: 0 0 auto;
	white-space: nowrap;
}
.dialog-title-meta {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.dialog-content {
	display: flex;
	min-height: 0;
	flex: 1 1 auto;
	flex-direction: column;
	overflow: hidden;
}
.metadata-workspace {
	display: flex;
	min-height: 0;
	flex: 1 1 auto;
	overflow: hidden;
}
.metadata-resizer {
	position: relative;
	display: flex;
	width: 10px;
	min-width: 10px;
	flex: 0 0 10px;
	align-items: center;
	justify-content: center;
	cursor: col-resize;
	touch-action: none;
	user-select: none;
}
.metadata-resizer::before {
	position: absolute;
	width: 1px;
	height: 100%;
	background: #e2e8f0;
	content: '';
	transition:
		width 120ms ease,
		background-color 120ms ease;
}
.metadata-resizer:hover::before,
.metadata-resizer.is-resizing::before {
	width: 2px;
	background: rgb(var(--v-theme-primary));
}
.metadata-resizer:focus-visible {
	border-radius: 3px;
	outline: 2px solid rgba(var(--v-theme-primary), 0.45);
	outline-offset: -1px;
}
.dialog-state {
	display: flex;
	min-height: 0;
	flex: 1 1 auto;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 32px;
	text-align: center;
}
.dialog-actions {
	min-height: 68px;
	padding: 12px 24px;
}
@media (max-width: 900px) {
	.metadata-dialog {
		height: calc(100vh - 32px);
	}
	.metadata-workspace {
		flex-direction: column;
	}
	.metadata-resizer {
		display: none;
	}
}
</style>
