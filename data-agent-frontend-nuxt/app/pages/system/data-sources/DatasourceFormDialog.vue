/*
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
 */

<template>
	<v-dialog :model-value="modelValue" max-width="800" persistent @update:model-value="$emit('update:modelValue', $event)">
		<v-card rounded="xl" class="pa-2">
			<v-card-title class="d-flex align-center justify-space-between px-4 pt-4">
				<span class="text-h6 font-weight-bold">{{ isEdit ? '编辑数据源' : '添加数据源' }}</span>
				<v-btn icon="mdi-close" variant="text" size="small" @click="$emit('update:modelValue', false)" />
			</v-card-title>

			<v-card-text class="pt-4">
				<v-form ref="formRef" v-model="formValid">
					<v-row dense>
						<v-col cols="12" md="6">
							<span class="custom-label">数据源名称 *</span>
							<v-text-field v-model="form.name" placeholder="请输入名称" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col cols="12" md="6">
							<span class="custom-label">数据库类型 *</span>
							<v-select v-model="form.type" :items="['mysql', 'postgresql', 'sqlserver', 'dameng', 'oracle']" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>

						<v-col cols="12" md="8">
							<span class="custom-label">主机地址 *</span>
							<v-text-field v-model="form.host" placeholder="localhost 或 IP 地址" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col cols="12" md="4">
							<span class="custom-label">端口号 *</span>
							<v-text-field v-model.number="form.port" type="number" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>

						<v-col cols="12" :md="['postgresql', 'oracle'].includes(form.type || '') ? 6 : 12">
							<span class="custom-label">数据库名 *</span>
							<v-text-field v-model="form.databaseName" placeholder="Database Name" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col v-if="['postgresql', 'oracle'].includes(form.type || '')" cols="12" md="6">
							<span class="custom-label">Schema 名</span>
							<v-text-field v-model="form.schemaName" placeholder="如 public" variant="outlined" density="compact" />
						</v-col>

						<v-col cols="12">
							<span class="custom-label">JDBC 连接地址 (可选)</span>
							<v-text-field v-model="form.connectionUrl" placeholder="若不填则自动生成" variant="outlined" density="compact" />
						</v-col>

						<v-col cols="12" md="6">
							<span class="custom-label">用户名 *</span>
							<v-text-field v-model="form.username" placeholder="Username" variant="outlined" density="compact" :rules="[rules.required]" />
						</v-col>
						<v-col cols="12" md="6">
							<span class="custom-label">密码 *</span>
							<v-text-field
								v-model="form.password"
								:type="showPassword ? 'text' : 'password'"
								:append-inner-icon="form.password ? (showPassword ? 'mdi-eye-off' : 'mdi-eye') : undefined"
								placeholder="Password"
								variant="outlined"
								density="compact"
								autocomplete="new-password"
								:rules="[rules.required]"
								@click:append-inner="form.password && (showPassword = !showPassword)"
							/>
						</v-col>

						<v-col cols="12">
							<span class="custom-label">描述信息</span>
							<v-textarea v-model="form.description" rows="2" placeholder="可选描述" variant="outlined" density="compact" />
						</v-col>
					</v-row>
				</v-form>
				<div class="d-flex align-center justify-space-between flex-wrap mt-4 ga-3">
					<v-menu v-model="testResultVisible" location="top start" :close-on-content-click="false" offset="8" max-width="560" class="connection-test-menu">
						<template #activator="{ props: menuProps }">
							<v-btn
								v-bind="menuProps"
								variant="outlined"
								color="primary"
								class="text-none"
								prepend-icon="mdi-lan-connect"
								:loading="testingConnection"
								:disabled="saving"
								@click="handleTestConnection"
							>
								测试连接
							</v-btn>
						</template>

						<v-card class="connection-test-card" rounded="lg" elevation="8">
							<v-card-title class="d-flex align-center justify-space-between text-subtitle-1 font-weight-bold">
								<span class="d-flex align-center ga-2">
									<v-icon :color="testingConnection ? 'primary' : connectionTestError ? 'error' : 'success'" size="20">
										{{ testingConnection ? 'mdi-loading mdi-spin' : connectionTestError ? 'mdi-close-circle-outline' : 'mdi-check-circle-outline' }}
									</v-icon>
									{{ testingConnection ? '正在测试连接' : connectionTestError ? '连接失败' : '连接成功' }}
								</span>
								<v-btn icon="mdi-close" variant="text" size="small" @click="testResultVisible = false" />
							</v-card-title>
							<v-divider />
							<v-card-text v-if="testingConnection" class="py-8 text-center text-medium-emphasis">
								<v-progress-circular indeterminate color="primary" size="24" class="mb-3" />
								<div>正在连接数据库并读取服务器信息...</div>
							</v-card-text>
							<v-card-text v-else-if="connectionTestError" class="connection-test-error text-body-2">
								{{ connectionTestError }}
							</v-card-text>
							<v-card-text v-else-if="connectionTestResult" class="connection-test-details">
								<div class="connection-test-summary text-success mb-4">服务器已响应</div>
								<div class="connection-test-grid">
									<div><span>数据库</span><strong>{{ connectionTestResult.databaseProductName || '未知' }}</strong></div>
									<div><span>版本</span><strong>{{ connectionTestResult.databaseProductVersion || '未知' }}</strong></div>
									<div><span>驱动</span><strong>{{ connectionTestResult.driverName || '未知' }}</strong></div>
									<div><span>驱动版本</span><strong>{{ connectionTestResult.driverVersion || '未知' }}</strong></div>
									<div><span>JDBC</span><strong>{{ connectionTestResult.jdbcVersion || '未知' }}</strong></div>
									<div><span>大小写敏感性</span><strong>{{ connectionTestResult.caseSensitivity || '未知' }}</strong></div>
									<div><span>响应时间</span><strong>{{ connectionTestResult.latencyMs ?? '-' }} ms</strong></div>
									<div><span>Catalog</span><strong>{{ connectionTestResult.catalog || '未知' }}</strong></div>
									<div><span>Schema</span><strong>{{ connectionTestResult.schema || '未知' }}</strong></div>
									<div><span>SSL</span><strong>{{ formatSsl(connectionTestResult.sslEnabled) }}</strong></div>
								</div>
								<div class="d-flex justify-end mt-4">
									<v-btn variant="text" size="small" prepend-icon="mdi-content-copy" @click="copyConnectionUrl">复制 JDBC 地址</v-btn>
								</div>
							</v-card-text>
						</v-card>
					</v-menu>

					<div class="d-flex ga-3 ml-auto">
						<v-btn variant="text" class="text-none" @click="$emit('update:modelValue', false)">取消</v-btn>
						<v-btn color="primary" class="text-none px-8" elevation="0" :loading="saving" @click="handleSubmit">
							{{ isEdit ? '保存' : '创建' }}
						</v-btn>
					</div>
				</div>
			</v-card-text>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import type { VForm } from 'vuetify/components';
import datasourceService, {
	type Datasource,
	type DatasourceConnectionTestResult,
} from '@/services/datasource';
import { buildDatasourceConnectionUrl } from '@/utils/datasourceConnection';

const props = defineProps<{
	modelValue: boolean;
	isEdit: boolean;
	datasource: Datasource | null;
	saving: boolean;
}>();

const emit = defineEmits<{
	'update:modelValue': [value: boolean];
	submit: [data: Datasource];
}>();

const formRef = ref<VForm | null>(null);
const formValid = ref(false);
const showPassword = ref(false);
const autoConnectionUrl = ref(true);
const lastGeneratedConnectionUrl = ref('');
const initializingForm = ref(false);
const updatingConnectionUrl = ref(false);
const testingConnection = ref(false);
const testResultVisible = ref(false);
const connectionTestResult = ref<DatasourceConnectionTestResult | null>(null);
const connectionTestError = ref('');

const defaultForm = (): Datasource => ({
	name: '', type: 'mysql', host: '', port: 3306,
	databaseName: '', schemaName: '', username: '', password: '',
	description: '', connectionUrl: '',
});

const form = reactive<Datasource>(defaultForm());

const rules = { required: (v: unknown) => !!v || '此项必填' };

watch(() => props.modelValue, (visible) => {
	if (!visible) return;
	showPassword.value = false;
	testResultVisible.value = false;
	connectionTestResult.value = null;
	connectionTestError.value = '';
	initializingForm.value = true;
	if (props.isEdit && props.datasource) {
		Object.assign(form, props.datasource);
	} else {
		Object.assign(form, defaultForm());
	}
	const generatedUrl = buildConnectionUrl();
	lastGeneratedConnectionUrl.value = generatedUrl;
	autoConnectionUrl.value = !form.connectionUrl || form.connectionUrl === generatedUrl;
	if (autoConnectionUrl.value && generatedUrl) form.connectionUrl = generatedUrl;
	initializingForm.value = false;
});

function buildConnectionUrl() {
	return buildDatasourceConnectionUrl(form);
}

function syncConnectionUrl() {
	const generatedUrl = buildConnectionUrl();
	if (!generatedUrl) {
		if (autoConnectionUrl.value && form.connectionUrl) {
			updatingConnectionUrl.value = true;
			form.connectionUrl = '';
			updatingConnectionUrl.value = false;
		}
		lastGeneratedConnectionUrl.value = '';
		return;
	}
	if (autoConnectionUrl.value || !form.connectionUrl || form.connectionUrl === lastGeneratedConnectionUrl.value) {
		updatingConnectionUrl.value = true;
		form.connectionUrl = generatedUrl;
		updatingConnectionUrl.value = false;
		autoConnectionUrl.value = true;
	}
	lastGeneratedConnectionUrl.value = generatedUrl;
}

watch(
	() => [form.type, form.host, form.port, form.databaseName],
	() => {
		if (!initializingForm.value) {
			syncConnectionUrl();
			invalidateConnectionTest();
		}
	},
);

watch(
	() => [form.username, form.password, form.connectionUrl],
	() => invalidateConnectionTest(),
);

watch(
	() => form.connectionUrl,
	(value) => {
		if (initializingForm.value || updatingConnectionUrl.value) return;
		autoConnectionUrl.value = !value || value === lastGeneratedConnectionUrl.value;
	},
);

async function handleSubmit() {
	const { valid } = (await formRef.value?.validate()) || { valid: false };
	if (!valid) return;
	emit('submit', { ...form });
}

function invalidateConnectionTest() {
	if (testingConnection.value) return;
	connectionTestResult.value = null;
	connectionTestError.value = '';
	testResultVisible.value = false;
}

function formatSsl(value: boolean | undefined) {
	if (value === true) return '是';
	if (value === false) return '否';
	return '未知';
}

async function handleTestConnection() {
	testResultVisible.value = true;
	connectionTestResult.value = null;
	connectionTestError.value = '';
	if (!form.type || !form.host || !form.port || !form.databaseName || !form.username) {
		connectionTestError.value = '请先填写数据库类型、主机、端口、数据库名和用户名';
		return;
	}

	testingConnection.value = true;
	try {
		const response = await datasourceService.testConnectionDetails({ ...form });
		if (response.success && response.data) {
			connectionTestResult.value = response.data;
		}
		else {
			connectionTestError.value = response.message || '连接测试失败';
		}
	}
	catch (error: unknown) {
		connectionTestError.value = error instanceof Error ? error.message : '连接测试请求失败';
	}
	finally {
		testingConnection.value = false;
	}
}

async function copyConnectionUrl() {
	if (!form.connectionUrl || !navigator.clipboard) return;
	await navigator.clipboard.writeText(form.connectionUrl);
}
</script>

<style scoped>
.connection-test-card {
	width: min(560px, calc(100vw - 32px));
}

.connection-test-summary {
	font-size: 14px;
	font-weight: 600;
}

.connection-test-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 12px 24px;
}

.connection-test-grid > div {
	display: flex;
	flex-direction: column;
	gap: 2px;
	min-width: 0;
}

.connection-test-grid span {
	color: rgba(var(--v-theme-on-surface), var(--v-medium-emphasis-opacity));
	font-size: 12px;
}

.connection-test-grid strong {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.connection-test-error {
	color: rgb(var(--v-theme-error));
	white-space: pre-wrap;
}

@media (max-width: 600px) {
	.connection-test-grid {
		grid-template-columns: 1fr;
	}
}
</style>
