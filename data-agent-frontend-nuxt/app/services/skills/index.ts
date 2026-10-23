import axios from 'axios';

const http = axios.create({ baseURL: '/api/skills' });

export interface SkillFile { path: string; directory: boolean; size: number }
export interface SkillItem { id: number; name: string; description: string | null; modified: number }

export default {
	async list() { return (await http.get<SkillItem[]>('')).data; },
	async create(name: string, description: string) { return (await http.post<{ id: number; name: string }>('', { name, description })).data; },
	async update(id: number, name: string, description: string) { await http.put(`/${id}`, { name, description }); },
	async delete(id: number) { await http.delete(`/${id}`); },
	async tree(id: number) { return (await http.get<SkillFile[]>(`/${id}/tree`)).data; },
	async read(id: number, path: string) { return (await http.get(`/${id}/file`, { params: { path }, responseType: 'arraybuffer' })).data as ArrayBuffer; },
	async save(id: number, path: string, content: string) { await http.put(`/${id}/file`, new TextEncoder().encode(content), { params: { path }, headers: { 'Content-Type': 'application/octet-stream' } }); },
	async mkdir(id: number, path: string) { await http.post(`/${id}/directory`, { path }); },
	async remove(id: number, path: string) { await http.delete(`/${id}/file`, { params: { path } }); },
	async move(id: number, source: string, target: string) { await http.post(`/${id}/move`, { source, target }); },
	async upload(id: number, path: string, file: File) { const form = new FormData(); form.append('file', file); await http.post(`/${id}/upload`, form, { params: { path } }); },
	async import(file: File) { const form = new FormData(); form.append('file', file); return (await http.post<{ id: number; name: string }>('/upload', form)).data; },
	async download(id: number) { return (await http.get(`/${id}/download`, { responseType: 'blob' })).data as Blob; },
};
