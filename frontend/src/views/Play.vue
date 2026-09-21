<script setup lang="ts">
import { onMounted, ref } from 'vue'
import draggable from 'vuedraggable'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, json } from '../api'
type Item = { id: number; image_url: string; sort_order: number }
const items = ref<Item[]>([]), previewOpen = ref(false), previewUrl = ref('')
function showPreview(url: string) { previewUrl.value = url; previewOpen.value = true }
async function load() { try { items.value = await api('/display/news') } catch (e: any) { ElMessage.error(e.message) } }
onMounted(load)
async function save() { try { await api('/news/sort', json('PUT', { ids: items.value.map(x => x.id) })); ElMessage.success('播放顺序已保存'); await load() } catch (e: any) { ElMessage.error(e.message); await load() } }
async function offline(id: number) { try { await api(`/news/${id}/offline`, { method: 'PUT' }); await load() } catch (e: any) { ElMessage.error(e.message) } }
async function allOffline() { try { await ElMessageBox.confirm('确定下架当前全部新闻？大厅将显示默认宣传画面。', '全部下架'); await api('/news/offline-all', { method: 'PUT' }); await load() } catch {} }
</script>
<template><div class="page-heading"><div><h1>播放管理</h1><p>当前播放 {{ items.length }} 篇。拖动卡片后自动保存顺序。</p></div><el-button type="danger" plain :disabled="!items.length" @click="allOffline">全部下架</el-button></div><el-card class="block"><draggable v-model="items" item-key="id" handle=".drag-handle" @end="save"><template #item="{element,index}"><div class="play-row"><span class="drag-handle">☰</span><span class="order">{{ index+1 }}</span><img :src="element.image_url" alt="新闻缩略图"><span class="grow">新闻 #{{ element.id }}</span><el-button @click="showPreview(element.image_url)">预览</el-button><el-button type="danger" plain @click="offline(element.id)">下架</el-button></div></template></draggable><el-empty v-if="!items.length" description="当前没有播放中的新闻，大厅显示默认宣传画面" /></el-card><el-dialog v-model="previewOpen" width="80%" title="新闻预览"><img v-if="previewOpen" :src="previewUrl" class="preview-image" alt="新闻预览"></el-dialog></template>
