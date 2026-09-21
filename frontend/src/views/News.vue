<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
type FileRow = { id: number; original_name: string; page_count: number; convert_status: string; error_message: string; created_at: string }
type NewsRow = { id: number; source_file_id: number; original_name: string; page_number: number; play_status: string; image_url: string; thumbnail_url: string }
const files = ref<FileRow[]>([]), news = ref<NewsRow[]>([]), uploading = ref(false), previewOpen = ref(false), previewUrl = ref(''), selected = ref<File | null>(null)
function showPreview(url: string) { previewUrl.value = url; previewOpen.value = true }
let timer: number | undefined
async function load() { try { [files.value, news.value] = await Promise.all([api('/files'), api('/news')]) } catch (e: any) { ElMessage.error(e.message) } }
onMounted(() => { load(); timer = window.setInterval(() => { if (files.value.some(f => f.convert_status === 'PROCESSING')) load() }, 3000) })
onUnmounted(() => clearInterval(timer))
const offlineCount = computed(() => news.value.filter(n => n.play_status === 'OFFLINE').length)
function choose(event: Event) { selected.value = (event.target as HTMLInputElement).files?.[0] || null }
async function upload() { if (!selected.value) return; uploading.value = true; const form = new FormData(); form.append('file', selected.value); try { await api('/files/upload', { method: 'POST', body: form }); ElMessage.success('已上传，正在生成新闻页面'); selected.value = null; await load() } catch (e: any) { ElMessage.error(e.message) } finally { uploading.value = false } }
async function action(path: string, method = 'PUT') { try { await api(path, { method }); ElMessage.success('操作成功'); await load() } catch (e: any) { ElMessage.error(e.message) } }
async function removeFile(f: FileRow) { try { await ElMessageBox.confirm(`删除「${f.original_name}」及其全部新闻页面？`, '确认删除'); await action(`/files/${f.id}`, 'DELETE') } catch {} }
async function removeNews(n: NewsRow) { try { await ElMessageBox.confirm(`删除「${n.original_name}」第 ${n.page_number} 页？`, '确认删除'); await action(`/news/${n.id}`, 'DELETE') } catch {} }
</script>
<template><div class="page-heading"><div><h1>新闻管理</h1><p>上传 PDF，按页生成新闻并预览展示效果。</p></div><el-button type="primary" :disabled="!offlineCount" @click="action('/news/play-all')">全部加入播放（{{ offlineCount }}）</el-button></div>
<el-card class="block"><template #header><b>上传 PDF</b></template><div class="upload-row"><input type="file" accept=".pdf,application/pdf" @change="choose"><el-button type="primary" :loading="uploading" :disabled="!selected" @click="upload">开始上传</el-button></div><p class="hint">仅支持 PDF，每一页生成一篇独立新闻。上传后请预览确认版式。</p></el-card>
<el-card class="block"><template #header><b>上传记录</b></template><el-table :data="files" empty-text="暂无上传文件"><el-table-column prop="original_name" label="文件名" min-width="220"/><el-table-column prop="page_count" label="页数" width="80"/><el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="row.convert_status==='SUCCESS'?'success':row.convert_status==='FAILED'?'danger':'warning'">{{ row.convert_status==='SUCCESS'?'完成':row.convert_status==='FAILED'?'失败':'转换中' }}</el-tag></template></el-table-column><el-table-column prop="created_at" label="上传时间" width="190"/><el-table-column label="操作" width="110"><template #default="{row}"><el-button text type="danger" :disabled="row.convert_status==='PROCESSING'" @click="removeFile(row)">删除文件</el-button></template></el-table-column></el-table><p v-for="f in files.filter(x=>x.convert_status==='FAILED')" :key="f.id" class="error">{{ f.original_name }}：{{ f.error_message }}</p></el-card>
<el-card class="block"><template #header><b>新闻页面（{{ news.length }}）</b></template><div class="news-grid"><div v-for="n in news" :key="n.id" class="news-card"><img :src="n.thumbnail_url" :alt="n.original_name + '第' + n.page_number + '页'" @click="showPreview(n.image_url)"><div class="news-meta"><b>{{ n.original_name }}</b><span>第 {{ n.page_number }} 页 · {{ n.play_status==='PLAYING'?'播放中':'未播放' }}</span></div><div class="news-actions"><el-button size="small" @click="showPreview(n.image_url)">预览</el-button><el-button v-if="n.play_status==='OFFLINE'" size="small" type="primary" @click="action(`/news/${n.id}/play`)">加入播放</el-button><el-button v-else size="small" @click="action(`/news/${n.id}/offline`)">下架</el-button><el-button size="small" type="danger" text @click="removeNews(n)">删除</el-button></div></div></div><el-empty v-if="!news.length" description="上传 PDF 后，新闻页面会显示在这里" /></el-card>
<el-dialog v-model="previewOpen" width="80%" title="新闻预览"><img v-if="previewOpen" :src="previewUrl" class="preview-image" alt="新闻预览"></el-dialog></template>
