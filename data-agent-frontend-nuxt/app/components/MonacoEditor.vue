<template><div ref="host" class="monaco-host" /></template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type * as Monaco from 'monaco-editor';

const props = defineProps<{ modelValue: string; path: string }>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; save: [] }>();
const host = ref<HTMLElement | null>(null);
let editor: Monaco.editor.IStandaloneCodeEditor | undefined;
let monacoApi: typeof Monaco | undefined;

function languageForPath(path: string) {
	const ext = path.split('.').pop()?.toLowerCase();
	const languages: Record<string, string> = { md: 'markdown', json: 'json', yml: 'yaml', yaml: 'yaml', js: 'javascript', mjs: 'javascript', ts: 'typescript', vue: 'html', html: 'html', css: 'css', scss: 'scss', java: 'java', py: 'python', sql: 'sql', sh: 'shell', xml: 'xml', go: 'go', rs: 'rust', kt: 'kotlin', txt: 'plaintext' };
	return languages[ext || ''] || 'plaintext';
}

onMounted(async () => {
	const monaco = await import('monaco-editor');
	monacoApi = monaco;
	if (!host.value) return;
	editor = monaco.editor.create(host.value, {
		value: props.modelValue,
		language: languageForPath(props.path),
		theme: 'vs',
		automaticLayout: true,
		minimap: { enabled: false },
		fontSize: 13,
		lineNumbers: 'on',
		scrollBeyondLastLine: false,
		wordWrap: 'on',
		padding: { top: 12 },
	});
	editor.onDidChangeModelContent(() => emit('update:modelValue', editor?.getValue() || ''));
	editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => emit('save'));
});

watch(() => props.modelValue, value => { if (editor && editor.getValue() !== value) editor.setValue(value); });
watch(() => props.path, value => { const model = editor?.getModel(); if (model && monacoApi) monacoApi.editor.setModelLanguage(model, languageForPath(value)); });
onBeforeUnmount(() => editor?.dispose());
</script>

<style scoped>
.monaco-host { width: 100%; height: 100%; min-height: 320px; }
</style>
