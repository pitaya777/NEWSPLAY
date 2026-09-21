<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
const router = useRouter()
onMounted(async () => { try { await api('/auth/me') } catch { router.replace('/admin/login') } })
async function logout() { await api('/auth/logout', { method: 'POST' }); router.push('/admin/login') }
</script>
<template><el-container class="admin-shell"><el-aside width="230px" class="sidebar"><div class="brand">NEWSPLAY<span>学院新闻展示</span></div><el-menu router :default-active="$route.path"><el-menu-item index="/admin/play">播放管理</el-menu-item><el-menu-item index="/admin/news">新闻管理</el-menu-item><el-menu-item index="/admin/settings">展示设置</el-menu-item></el-menu><div class="sidebar-bottom"><a href="/display" target="_blank">打开大厅展示 ↗</a><el-button text @click="logout">退出登录</el-button></div></el-aside><el-main class="main-panel"><router-view /></el-main></el-container></template>
