/* * Copyright 2026 the original author or authors. * * Licensed under the
Apache License, Version 2.0 (the "License"); * you may not use this file except
in compliance with the License. * You may obtain a copy of the License at * *
https://www.apache.org/licenses/LICENSE-2.0 * * Unless required by applicable
law or agreed to in writing, software * distributed under the License is
distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied. * See the License for the specific language governing
permissions and * limitations under the License. */

<template>
	<section class="page-shell">
		<KnowledgePageHeader
			title="语义模型配置"
			subtitle="维护字段语义映射，统一业务口径，提升 SQL 生成准确性。"
		>
			<template #actions>
				<v-btn
					color="blue-darken-1"
					prepend-icon="mdi-upload"
					class="text-none px-6"
					elevation="0"
					@click="openBatchImportDialog"
				>
					批量导入
				</v-btn>
				<v-btn
					color="blue-darken-3"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openCreateDialog"
				>
					添加语义模型
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-4 pa-4">
			<v-row dense align="center">
				<v-col cols="12" sm="6" lg="3">
					<v-autocomplete
						v-model="queryForm.datasourceId"
						:items="datasourceList"
						item-title="name"
						item-value="id"
						label="数据源"
						placeholder="全部数据源"
						variant="outlined"
						density="compact"
						clearable
						hide-details
						@update:model-value="handleFilterDatasourceChange"
					/>
				</v-col>
				<v-col cols="12" sm="6" lg="3">
					<v-autocomplete
						v-model="queryForm.tableName"
						:items="filterTableOptions"
						item-title="name"
						item-value="name"
						label="表名"
						placeholder="全部表名"
						variant="outlined"
						density="compact"
						:loading="filterMetadataLoading"
						clearable
						hide-details
						no-data-text="暂无匹配的表名"
						@update:model-value="handleFilterTableChange"
					>
						<template #item="{ props, item }">
							<v-list-item
								v-bind="props"
								:title="item.raw.name"
								:subtitle="
									[
										item.raw.description || '暂无表注释',
										item.raw.datasourceName,
									]
										.filter(Boolean)
										.join(' · ')
								"
							/>
						</template>
					</v-autocomplete>
				</v-col>
				<v-col cols="12" sm="6" lg="3">
					<v-autocomplete
						v-model="queryForm.columnName"
						:items="filterColumnOptions"
						item-title="name"
						item-value="name"
						label="字段名"
						placeholder="全部字段"
						variant="outlined"
						density="compact"
						:loading="filterMetadataLoading"
						clearable
						hide-details
						no-data-text="暂无匹配的字段"
					>
						<template #item="{ props, item }">
							<v-list-item
								v-bind="props"
								:title="item.raw.name"
								:subtitle="
									[
										item.raw.description || '暂无列注释',
										item.raw.datasourceName,
										item.raw.tableName,
									]
										.filter(Boolean)
										.join(' · ')
								"
							/>
						</template>
					</v-autocomplete>
				</v-col>
				<v-col cols="12" sm="6" lg="3">
					<v-text-field
						v-model="queryForm.businessName"
						label="业务名称"
						placeholder="模糊搜索"
						prepend-inner-icon="mdi-magnify"
						variant="outlined"
						density="compact"
						clearable
						hide-details
						@keyup.enter="loadSemanticModels"
					/>
				</v-col>
				<v-col cols="12" sm="6" lg="3">
					<v-text-field
						v-model="queryForm.synonyms"
						label="同义词"
						placeholder="模糊搜索"
						variant="outlined"
						density="compact"
						clearable
						hide-details
						@keyup.enter="loadSemanticModels"
					/>
				</v-col>
				<v-col cols="12" sm="6" lg="3">
					<v-menu
						v-model="createdDateMenu"
						:close-on-content-click="false"
						location="bottom start"
						min-width="328"
					>
						<template #activator="{ props }">
							<v-text-field
								v-bind="props"
								:model-value="
									formatDateRange(
										queryForm.createdTimeStart,
										queryForm.createdTimeEnd,
									)
								"
								label="创建时间范围"
								placeholder="选择日期范围"
								prepend-inner-icon="mdi-calendar-range"
								variant="outlined"
								density="compact"
								clearable
								readonly
								hide-details
								@click:clear="clearCreatedDateRange"
							/>
						</template>
						<v-date-picker
							v-model="createdDateRange"
							multiple="range"
							color="blue-darken-2"
							show-adjacent-months
							hide-header
							@update:model-value="closeDateRangeMenu($event, 'created')"
						/>
					</v-menu>
				</v-col>
				<v-col cols="12" sm="6" lg="3">
					<v-menu
						v-model="updatedDateMenu"
						:close-on-content-click="false"
						location="bottom start"
						min-width="328"
					>
						<template #activator="{ props }">
							<v-text-field
								v-bind="props"
								:model-value="
									formatDateRange(
										queryForm.updatedTimeStart,
										queryForm.updatedTimeEnd,
									)
								"
								label="更新时间范围"
								placeholder="选择日期范围"
								prepend-inner-icon="mdi-calendar-range"
								variant="outlined"
								density="compact"
								clearable
								readonly
								hide-details
								@click:clear="clearUpdatedDateRange"
							/>
						</template>
						<v-date-picker
							v-model="updatedDateRange"
							multiple="range"
							color="blue-darken-2"
							show-adjacent-months
							hide-header
							@update:model-value="closeDateRangeMenu($event, 'updated')"
						/>
					</v-menu>
				</v-col>
				<v-col cols="12" class="d-flex flex-wrap align-center ga-2 pt-1">
					<v-btn
						v-if="selectedModelIds.length > 0"
						variant="tonal"
						color="red-darken-1"
						prepend-icon="mdi-delete"
						class="text-none"
						@click="batchDeleteModels"
					>
						批量删除 ({{ selectedModelIds.length }})
					</v-btn>
					<v-spacer />
					<v-btn
						color="blue-darken-3"
						prepend-icon="mdi-magnify"
						class="text-none"
						elevation="0"
						@click="loadSemanticModels"
					>
						查询
					</v-btn>
					<v-btn
						variant="text"
						prepend-icon="mdi-filter-off-outline"
						class="text-none"
						@click="resetQuery"
					>
						重置
					</v-btn>
				</v-col>
			</v-row>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				v-model="selectedModelIds"
				:headers="headers"
				:items="semanticModelList"
				item-value="id"
				hover
				:loading="loading"
				show-select
				class="semantic-model-table"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.datasourceId="{ item }">
					<span
						class="datasource-cell text-truncate d-inline-block"
						:title="datasourceLabel(item.datasourceId)"
					>
						{{ datasourceLabel(item.datasourceId) }}
					</span>
				</template>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<v-chip
						:color="item.status === 1 ? 'success' : 'grey'"
						size="small"
						variant="tonal"
					>
						{{ item.status === 1 ? '启用' : '停用' }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.synonyms="{ item }">
					<v-tooltip
						v-if="item.synonyms && item.synonyms.length > 30"
						:text="item.synonyms"
						location="top"
					>
						<template #activator="{ props }">
							<span
								v-bind="props"
								class="text-truncate d-inline-block"
								style="max-width: 180px; cursor: help"
							>
								{{ item.synonyms }}
							</span>
						</template>
					</v-tooltip>
					<span v-else>{{ item.synonyms || '—' }}</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.createdTime="{ item }">
					{{ formatDateTime(item.createdTime) }}
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.updateTime="{ item }">
					{{ formatDateTime(item.updateTime) }}
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
						<v-btn
							size="small"
							variant="text"
							color="blue-darken-1"
							icon="mdi-pencil"
							@click="editModel(item)"
						/>
						<v-btn
							size="small"
							variant="text"
							:color="item.status === 1 ? 'warning' : 'success'"
							:aria-label="item.status === 1 ? '停用' : '启用'"
							@click="toggleStatus(item, item.status === 1 ? 0 : 1)"
						>
							<v-icon
								:icon="item.status === 1 ? 'mdi-power-off' : 'mdi-power'"
								size="20"
							/>
							<v-tooltip activator="parent" location="top">{{
								item.status === 1 ? '停用' : '启用'
							}}</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="red-darken-1"
							icon="mdi-delete"
							@click="deleteModel(item)"
						/>
					</div>
				</template>

				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon
							icon="mdi-vector-intersection"
							size="64"
							color="blue-lighten-3"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">暂无语义模型</p>
						<p class="text-body-2 text-disabled mb-6">
							点击「添加语义模型」开始配置字段语义映射
						</p>
						<v-btn
							color="blue-darken-3"
							prepend-icon="mdi-plus"
							class="text-none"
							elevation="0"
							@click="openCreateDialog"
						>
							添加语义模型
						</v-btn>
					</div>
				</template>

				<template #bottom>
					<div class="semantic-model-table__footer">
						<v-chip
							color="blue-lighten-5"
							variant="flat"
							class="font-weight-medium"
						>
							总数 {{ semanticModelList.length }}
						</v-chip>
						<v-data-table-footer />
					</div>
				</template>
			</v-data-table>
		</v-card>

		<v-dialog v-model="dialogVisible" max-width="920" persistent>
			<v-card class="semantic-model-dialog rounded-lg" rounded="lg">
				<v-card-title class="d-flex align-center pa-5 pb-4">
					<v-icon
						:icon="isEdit ? 'mdi-pencil-circle' : 'mdi-plus-circle'"
						color="blue-darken-2"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-bold">{{
						isEdit ? '编辑语义模型' : '添加语义模型'
					}}</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="closeDialog"
					/>
				</v-card-title>
				<v-divider />

				<v-card-text class="semantic-model-dialog__body pa-5 pt-4">
					<v-form ref="formRef">
						<v-row dense>
							<v-col cols="12" md="6" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									数据源 <span class="text-error">*</span>
								</p>
								<v-autocomplete
									v-model="modelForm.datasourceId"
									:items="datasourceList"
									item-title="name"
									item-value="id"
									placeholder="请选择数据源"
									variant="outlined"
									density="compact"
									:loading="datasourceLoading"
									:disabled="datasourceLoading"
									clearable
									:rules="[(v) => !!v || '数据源不能为空']"
									hide-details="auto"
									@update:model-value="handleDatasourceChange"
								>
									<template #item="{ props, item }">
										<v-list-item
											v-bind="props"
											:title="item.raw.name || '未命名数据源'"
											:subtitle="datasourceSubtitle(item.raw)"
										/>
									</template>
								</v-autocomplete>
							</v-col>
							<v-col cols="12" md="6" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									表名 <span class="text-error">*</span>
								</p>
								<v-autocomplete
									v-model="modelForm.tableName"
									:items="tableOptions"
									item-title="name"
									item-value="name"
									placeholder="请选择表名"
									variant="outlined"
									density="compact"
									:loading="tableLoading"
									:disabled="!modelForm.datasourceId || tableLoading"
									clearable
									:rules="[(v) => !!v || '表名不能为空']"
									hide-details="auto"
									@update:model-value="handleTableChange"
								>
									<template #item="{ props, item }">
										<v-list-item
											v-bind="props"
											:title="item.raw.name"
											:subtitle="
												[
													item.raw.description || '暂无表注释',
													item.raw.schema || '默认 schema',
												]
													.join(' · ')
											"
										/>
									</template>
								</v-autocomplete>
							</v-col>
							<v-col cols="12" md="6" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									字段名 <span class="text-error">*</span>
								</p>
								<v-autocomplete
									v-model="modelForm.columnName"
									:items="columnOptions"
									item-title="name"
									item-value="name"
									placeholder="请选择字段名"
									variant="outlined"
									density="compact"
									:loading="columnLoading"
									:disabled="!modelForm.tableName || columnLoading"
									clearable
									:rules="[(v) => !!v || '字段名不能为空']"
									hide-details="auto"
									@update:model-value="handleColumnChange"
								>
									<template #item="{ props, item }">
										<v-list-item
											v-bind="props"
											:title="item.raw.name"
											:subtitle="
												[
													item.raw.description || '暂无列注释',
													item.raw.type || '未知类型',
												]
													.join(' · ')
											"
										/>
									</template>
								</v-autocomplete>
							</v-col>
							<v-col cols="12" md="6" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									数据类型 <span class="text-error">*</span>
								</p>
								<v-autocomplete
									v-model="modelForm.dataType"
									:items="dataTypeOptions"
									placeholder="请选择数据类型"
									variant="outlined"
									density="compact"
									:disabled="
										!modelForm.columnName || dataTypeOptions.length === 0
									"
									clearable
									:rules="[(v) => !!v || '数据类型不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									业务名称 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.businessName"
									placeholder="请输入业务名称"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '业务名称不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									同义词
								</p>
								<v-textarea
									v-model="modelForm.synonyms"
									placeholder="多个同义词请用逗号分隔"
									variant="outlined"
									density="compact"
									rows="2"
									class="w-100"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" class="pb-3">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									业务描述
								</p>
								<v-textarea
									v-model="modelForm.businessDescription"
									placeholder="描述该字段业务意义和口径"
									variant="outlined"
									density="compact"
									rows="2"
									class="w-100"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" class="pb-1">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									字段注释
								</p>
								<v-textarea
									v-model="modelForm.columnComment"
									placeholder="数据库原始字段注释"
									variant="outlined"
									density="compact"
									rows="2"
									class="w-100"
									hide-details="auto"
								/>
							</v-col>
						</v-row>
					</v-form>
				</v-card-text>

				<v-divider />
				<v-card-actions class="pa-4 pt-3 d-flex justify-end ga-2">
					<v-btn variant="outlined" class="text-none px-6" @click="closeDialog"
						>取消</v-btn
					>
					<v-btn
						color="blue-darken-3"
						class="text-none px-6"
						elevation="0"
						:loading="saveLoading"
						@click="saveModel"
					>
						{{ isEdit ? '保存更新' : '立即创建' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>

		<v-dialog v-model="batchImportDialogVisible" max-width="760">
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-6 pb-4">
					<v-icon
						icon="mdi-upload"
						color="blue-darken-2"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-bold">批量导入语义模型</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="batchImportDialogVisible = false"
					/>
				</v-card-title>
				<v-divider />
				<v-card-text class="pa-6">
					<v-file-input
						v-model="importFile"
						label="上传 Excel 文件"
						accept=".xlsx,.xls"
						prepend-icon="mdi-file-excel"
						variant="outlined"
						density="compact"
						show-size
						hide-details="auto"
					/>
					<div class="d-flex ga-2 mt-4">
						<v-btn
							variant="tonal"
							color="blue-darken-1"
							prepend-icon="mdi-download"
							class="text-none"
							@click="downloadExcelTemplate"
						>
							下载模板
						</v-btn>
						<v-btn
							color="blue-darken-3"
							prepend-icon="mdi-upload"
							class="text-none"
							elevation="0"
							:loading="importLoading"
							@click="executeExcelImport"
						>
							开始导入
						</v-btn>
					</div>
				</v-card-text>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import semanticModelService, {
	type SemanticModel,
	type SemanticModelAddDto,
	type SemanticModelQuery,
} from '~/services/semanticModel/index';
import { useCrudPage } from '~/composables/useCrudPage/index';
import datasourceService, {
	type ColumnMetadata,
	type Datasource,
	type TableMetadata,
} from '~/services/datasource/index';

const DEFAULT_AGENT_ID = 0;
const route = useRoute();
const agentId = computed(() => Number(route.query.agentId) || DEFAULT_AGENT_ID);

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 额外状态 ———
const importLoading = ref(false);
const batchImportDialogVisible = ref(false);
const importFile = ref<File | null>(null);
const selectedModelIds = ref<number[]>([]);
const currentEditId = ref<number | null>(null);
const createdDateMenu = ref(false);
const updatedDateMenu = ref(false);
const datasourceList = ref<Datasource[]>([]);
const datasourceLoading = ref(false);
const tableLoading = ref(false);
const columnLoading = ref(false);
const tableOptions = ref<TableMetadata[]>([]);
const columnOptions = ref<ColumnMetadata[]>([]);
interface FilterTableOption extends TableMetadata {
	datasourceId: number;
	datasourceName: string;
}

interface FilterColumnOption extends ColumnMetadata {
	datasourceId: number;
	datasourceName: string;
	tableName: string;
}

const filterTableMetadata = ref<FilterTableOption[]>([]);
const filterColumnMetadata = ref<FilterColumnOption[]>([]);
const filterMetadataLoading = ref(false);
let metadataRequestId = 0;

// ——— useCrudPage ———
const queryForm = reactive<{
	datasourceId?: number;
	tableName: string;
	columnName: string;
	businessName: string;
	synonyms: string;
	createdTimeStart: string;
	createdTimeEnd: string;
	updatedTimeStart: string;
	updatedTimeEnd: string;
}>({
	tableName: '',
	columnName: '',
	businessName: '',
	synonyms: '',
	createdTimeStart: '',
	createdTimeEnd: '',
	updatedTimeStart: '',
	updatedTimeEnd: '',
});

type DatePickerValue = string | Date;

function normalizeDatePickerValue(value: DatePickerValue): string {
	if (value instanceof Date) {
		const year = value.getFullYear();
		const month = String(value.getMonth() + 1).padStart(2, '0');
		const day = String(value.getDate()).padStart(2, '0');
		return `${year}-${month}-${day}`;
	}
	return value.slice(0, 10);
}

function applyDateRange(
	value: DatePickerValue[] | null | undefined,
	setStart: (value: string) => void,
	setEnd: (value: string) => void,
) {
	const dates = value?.filter(Boolean).map(normalizeDatePickerValue) || [];
	setStart(dates[0] || '');
	setEnd(dates.length > 1 ? dates[dates.length - 1] : '');
}

const createdDateRange = computed<DatePickerValue[]>({
	get: () =>
		[queryForm.createdTimeStart, queryForm.createdTimeEnd].filter(
			(value): value is string => Boolean(value),
		),
	set: (value) =>
		applyDateRange(
			value,
			(start) => (queryForm.createdTimeStart = start),
			(end) => (queryForm.createdTimeEnd = end),
		),
});

const updatedDateRange = computed<DatePickerValue[]>({
	get: () =>
		[queryForm.updatedTimeStart, queryForm.updatedTimeEnd].filter(
			(value): value is string => Boolean(value),
		),
	set: (value) =>
		applyDateRange(
			value,
			(start) => (queryForm.updatedTimeStart = start),
			(end) => (queryForm.updatedTimeEnd = end),
		),
});

function formatDateRange(start: string, end: string) {
	if (!start && !end) return '';
	return [start, end || start].filter(Boolean).join(' 至 ');
}

function closeDateRangeMenu(
	value: DatePickerValue[] | null,
	range: 'created' | 'updated',
) {
	if (value && value.length > 1) {
		if (range === 'created') createdDateMenu.value = false;
		else updatedDateMenu.value = false;
	}
}

function clearCreatedDateRange() {
	createdDateRange.value = [];
	createdDateMenu.value = false;
}

function clearUpdatedDateRange() {
	updatedDateRange.value = [];
	updatedDateMenu.value = false;
}

const {
	loading,
	saveLoading,
	items: semanticModelList,
	dialogVisible,
	isEdit,
	formRef,
	formData: modelForm,
	loadItems: loadSemanticModels,
	openCreateDialog: _openCreateDialog,
	openEditDialog,
	closeDialog,
	saveItem,
	deleteItem,
} = useCrudPage<SemanticModel, SemanticModelAddDto, SemanticModel>({
	loadFn: () => semanticModelService.list(agentId.value, buildQuery()),
	createFn: (data) => semanticModelService.create(data),
	updateFn: (id, data) => semanticModelService.update(id, data),
	deleteFn: (id) => semanticModelService.delete(id),
	defaultFormFactory: () => ({
		agentId: agentId.value,
		tableName: '',
		columnName: '',
		businessName: '',
		synonyms: '',
		businessDescription: '',
		columnComment: '',
		dataType: '',
		status: 1,
	}),
});

function buildQuery(): Omit<SemanticModelQuery, 'agentId'> {
	return {
		datasourceId: queryForm.datasourceId,
		tableName: queryForm.tableName || undefined,
		columnName: queryForm.columnName || undefined,
		businessName: queryForm.businessName || undefined,
		synonyms: queryForm.synonyms || undefined,
		createdTimeStart: queryForm.createdTimeStart
			? `${queryForm.createdTimeStart}T00:00:00`
			: undefined,
		createdTimeEnd: queryForm.createdTimeEnd
			? `${queryForm.createdTimeEnd}T23:59:59`
			: undefined,
		updatedTimeStart: queryForm.updatedTimeStart
			? `${queryForm.updatedTimeStart}T00:00:00`
			: undefined,
		updatedTimeEnd: queryForm.updatedTimeEnd
			? `${queryForm.updatedTimeEnd}T23:59:59`
			: undefined,
	};
}

function resetQuery() {
	Object.assign(queryForm, {
		datasourceId: undefined,
		tableName: '',
		columnName: '',
		businessName: '',
		synonyms: '',
		createdTimeStart: '',
		createdTimeEnd: '',
		updatedTimeStart: '',
		updatedTimeEnd: '',
	});
	void loadSemanticModels();
}

const filterTableOptions = computed(() => {
	if (!queryForm.datasourceId) return filterTableMetadata.value;
	return filterTableMetadata.value.filter(
		(table) => table.datasourceId === queryForm.datasourceId,
	);
});

const filterColumnOptions = computed(() => {
	return filterColumnMetadata.value.filter((column) => {
		if (
			queryForm.datasourceId &&
			column.datasourceId !== queryForm.datasourceId
		) {
			return false;
		}
		return !queryForm.tableName || column.tableName === queryForm.tableName;
	});
});

function loadFilterCandidates() {
	filterTableMetadata.value = [];
	filterColumnMetadata.value = [];
}

async function handleFilterDatasourceChange(datasourceId: number | null) {
	queryForm.tableName = '';
	queryForm.columnName = '';
	filterTableMetadata.value = [];
	filterColumnMetadata.value = [];
	if (!datasourceId) {
		void loadSemanticModels();
		return;
	}
	filterMetadataLoading.value = true;
	try {
		const datasource = datasourceList.value.find(
			(item) => item.id === datasourceId,
		);
		const tables =
			await datasourceService.getInitializedDatasourceTableDetails(
				datasourceId,
			);
		filterTableMetadata.value = tables.map((table) => ({
			...table,
			datasourceId,
			datasourceName: datasource?.name || '未命名数据源',
		}));
	} finally {
		filterMetadataLoading.value = false;
	}
	void loadSemanticModels();
}

async function handleFilterTableChange(tableName: string | null) {
	queryForm.columnName = '';
	filterColumnMetadata.value = [];
	if (!tableName || !queryForm.datasourceId) {
		void loadSemanticModels();
		return;
	}
	const table = filterTableMetadata.value.find(
		(item) => item.name === tableName,
	);
	if (!table) return;
	filterMetadataLoading.value = true;
	try {
		const columns = await datasourceService.getInitializedTableColumnDetails(
			table.datasourceId,
			table.name,
			table.schema,
		);
		filterColumnMetadata.value = columns.map((column) => ({
			...column,
			datasourceId: table.datasourceId,
			datasourceName: table.datasourceName,
			tableName: table.name,
		}));
	} finally {
		filterMetadataLoading.value = false;
	}
	void loadSemanticModels();
}

const dataTypeOptions = computed(() => {
	const selectedColumn = columnOptions.value.find(
		(column) => column.name === modelForm.value.columnName,
	);
	return selectedColumn?.type ? [selectedColumn.type] : [];
});

function openCreateDialog() {
	_openCreateDialog();
	modelForm.value.agentId = agentId.value;
	tableOptions.value = [];
	columnOptions.value = [];
}

const headers = [
	{ title: '数据源', key: 'datasourceId', width: 180, minWidth: '180px' },
	{ title: '表名', key: 'tableName', width: 220, minWidth: '220px' },
	{ title: '字段名', key: 'columnName', width: 180, minWidth: '180px' },
	{ title: '业务名称', key: 'businessName', width: 180, minWidth: '180px' },
	{
		title: '同义词',
		key: 'synonyms',
		width: 200,
		minWidth: '200px',
		sortable: false,
	},
	{ title: '数据类型', key: 'dataType', width: 130, minWidth: '130px' },
	{
		title: '状态',
		key: 'status',
		width: 100,
		minWidth: '100px',
		sortable: false,
	},
	{ title: '创建时间', key: 'createdTime', width: 190, minWidth: '190px' },
	{ title: '最后更新时间', key: 'updateTime', width: 190, minWidth: '190px' },
	{
		title: '操作',
		key: 'actions',
		width: 140,
		minWidth: '140px',
		sortable: false,
	},
];

function datasourceLabel(datasourceId?: number) {
	return (
		datasourceList.value.find((datasource) => datasource.id === datasourceId)
			?.name || '未关联数据源'
	);
}

function formatDateTime(dateTime?: string) {
	if (!dateTime) return '-';
	try {
		return new Date(dateTime).toLocaleString('zh-CN', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
			second: '2-digit',
			hour12: false,
		});
	} catch {
		return dateTime;
	}
}

function editModel(model: SemanticModel) {
	currentEditId.value = model.id || null;
	openEditDialog(model);
	void loadMetadataForModel(model);
}

function datasourceSubtitle(datasource: Datasource): string {
	const endpoint = datasource.host
		? `${datasource.host}${datasource.port ? `:${datasource.port}` : ''}`
		: '';
	return [datasource.type?.toUpperCase(), endpoint, datasource.databaseName]
		.filter(Boolean)
		.join(' · ');
}

async function loadDataSources() {
	datasourceLoading.value = true;
	try {
		datasourceList.value = await datasourceService.getAllDatasource();
		loadFilterCandidates();
	} catch {
		datasourceList.value = [];
		filterTableMetadata.value = [];
		filterColumnMetadata.value = [];
		$tip('获取数据源列表失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		datasourceLoading.value = false;
	}
}

function clearColumnSelection() {
	modelForm.value.columnName = '';
	modelForm.value.dataType = '';
	modelForm.value.columnComment = '';
	columnOptions.value = [];
	columnLoading.value = false;
}

function clearTableSelection() {
	modelForm.value.tableName = '';
	clearColumnSelection();
}

async function loadTablesForDatasource(datasourceId: number) {
	const requestId = ++metadataRequestId;
	tableLoading.value = true;
	try {
		const tables =
			await datasourceService.getInitializedDatasourceTableDetails(
				datasourceId,
			);
		if (requestId === metadataRequestId) tableOptions.value = tables;
	} catch {
		if (requestId === metadataRequestId) {
			tableOptions.value = [];
			$tip('获取数据源表列表失败', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
		}
	} finally {
		if (requestId === metadataRequestId) tableLoading.value = false;
	}
}

async function loadColumnsForTable(table: TableMetadata) {
	const datasourceId = modelForm.value.datasourceId;
	if (!datasourceId) return;
	const requestId = ++metadataRequestId;
	columnLoading.value = true;
	try {
		const columns = await datasourceService.getInitializedTableColumnDetails(
			datasourceId,
			table.name,
			table.schema,
		);
		if (requestId === metadataRequestId) columnOptions.value = columns;
	} catch {
		if (requestId === metadataRequestId) {
			columnOptions.value = [];
			$tip('获取数据源字段列表失败', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
		}
	} finally {
		if (requestId === metadataRequestId) columnLoading.value = false;
	}
}

function handleDatasourceChange(datasourceId: number | null) {
	metadataRequestId++;
	clearTableSelection();
	tableOptions.value = [];
	tableLoading.value = false;
	if (datasourceId) void loadTablesForDatasource(datasourceId);
}

function handleTableChange(tableName: string | null) {
	metadataRequestId++;
	clearColumnSelection();
	if (!tableName) return;
	const table = tableOptions.value.find((item) => item.name === tableName);
	if (table) void loadColumnsForTable(table);
}

function handleColumnChange(columnName: string | null) {
	const column = columnOptions.value.find((item) => item.name === columnName);
	modelForm.value.dataType = column?.type || '';
	modelForm.value.columnComment = column?.description || '';
}

async function loadMetadataForModel(model: SemanticModel) {
	if (!model.datasourceId) {
		tableOptions.value = [];
		columnOptions.value = [];
		return;
	}
	await loadTablesForDatasource(model.datasourceId);
	const table = tableOptions.value.find(
		(item) => item.name === model.tableName,
	);
	if (!table) return;
	await loadColumnsForTable(table);
}

function deleteModel(model: SemanticModel) {
	if (!model.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除语义模型「${model.businessName}」吗？此操作不可恢复。`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const ok = await deleteItem(model.id!);
			if (ok) {
				$tip('删除成功');
			} else {
				$tip('删除失败', { color: 'error', icon: 'mdi-alert-circle' });
			}
		},
	});
}

function toggleStatus(model: SemanticModel, status: number) {
	if (!model.id) return;
	const ids = [model.id];
	showConfirm({
		title: `${status === 1 ? '启用' : '停用'}确认`,
		message: `确定要${status === 1 ? '启用' : '停用'}语义模型「${model.businessName}」吗？`,
		confirmText: '确认',
		onConfirm: async () => {
			let result = false;
			if (status === 1) {
				result = await semanticModelService.enable(ids);
			} else {
				result = await semanticModelService.disable(ids);
			}
			if (result) {
				model.status = status;
				$tip(`${status === 1 ? '启用' : '停用'}成功`);
			} else {
				$tip(`${status === 1 ? '启用' : '停用'}失败`, {
					color: 'error',
					icon: 'mdi-alert-circle',
				});
			}
		},
	});
}

function batchDeleteModels() {
	if (selectedModelIds.value.length === 0) return;
	const ids = [...selectedModelIds.value];
	showConfirm({
		title: '批量删除确认',
		message: `确定要删除选中的 ${ids.length} 个语义模型吗？`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const result = await semanticModelService.batchDelete(ids);
			if (result) {
				$tip(`成功删除 ${ids.length} 个语义模型`);
				selectedModelIds.value = [];
				await loadSemanticModels();
			} else {
				$tip('批量删除失败', { color: 'error', icon: 'mdi-alert-circle' });
			}
		},
	});
}

async function saveModel() {
	const createData: SemanticModelAddDto = {
		agentId: agentId.value,
		datasourceId: modelForm.value.datasourceId,
		tableName: modelForm.value.tableName,
		columnName: modelForm.value.columnName,
		businessName: modelForm.value.businessName,
		synonyms: modelForm.value.synonyms,
		businessDescription: modelForm.value.businessDescription,
		columnComment: modelForm.value.columnComment,
		dataType: modelForm.value.dataType,
	};
	const updateData: SemanticModel = {
		...modelForm.value,
		id: currentEditId.value ?? undefined,
	};
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	const ok = await saveItem(createData as any, updateData, currentEditId.value);
	if (ok) {
		$tip(isEdit.value ? '更新成功' : '创建成功');
	} else {
		$tip(`${isEdit.value ? '更新' : '创建'}失败`, {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	}
}

async function downloadExcelTemplate() {
	try {
		await semanticModelService.downloadTemplate();
		$tip('模板下载成功');
	} catch {
		$tip('模板下载失败', { color: 'error', icon: 'mdi-alert-circle' });
	}
}

async function executeExcelImport() {
	if (!importFile.value) {
		$tip('请先选择 Excel 文件', { color: 'warning' });
		return;
	}
	importLoading.value = true;
	try {
		const result = await semanticModelService.importExcel(
			importFile.value,
			agentId.value,
		);
		$tip(
			`导入完成：成功 ${result.successCount} 条，失败 ${result.failCount} 条`,
		);
		if (result.errors?.length) {
			$tip(`部分失败：${result.errors[0]}`, { color: 'warning' });
		}
		batchImportDialogVisible.value = false;
		importFile.value = null;
		await loadSemanticModels();
	} catch {
		$tip('Excel 导入失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		importLoading.value = false;
	}
}

function openBatchImportDialog() {
	batchImportDialogVisible.value = true;
	importFile.value = null;
}

onMounted(() => {
	void loadDataSources();
	void loadSemanticModels();
});
</script>

<style scoped>
.semantic-model-table :deep(.v-table__wrapper) {
	overflow-x: auto;
}

.semantic-model-table :deep(table) {
	min-width: 1500px;
}

.semantic-model-table :deep(.v-data-table__th),
.semantic-model-table :deep(.v-data-table__th .v-data-table-header__content) {
	white-space: nowrap;
}

.semantic-model-table__footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	min-height: 64px;
	padding: 8px 16px;
	border-top: 1px solid rgba(0, 0, 0, 0.08);
}

.semantic-model-table__footer :deep(.v-data-table-footer) {
	flex: 1 1 auto;
	justify-content: flex-end;
	min-height: 48px;
	padding: 0;
}

.datasource-cell {
	max-width: 160px;
	vertical-align: middle;
}

.semantic-model-dialog {
	max-height: min(90vh, 860px);
	display: flex;
	flex-direction: column;
}

.semantic-model-dialog__body {
	flex: 1 1 auto;
	min-height: 0;
	overflow-y: auto;
}

@media (max-width: 600px) {
	.semantic-model-table__footer {
		align-items: stretch;
		flex-direction: column;
		gap: 4px;
	}

	.semantic-model-table__footer :deep(.v-chip) {
		align-self: flex-start;
	}

	.semantic-model-dialog {
		max-height: 100dvh;
	}
}
</style>
