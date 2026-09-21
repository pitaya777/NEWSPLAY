<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, json } from '../api'
const router = useRouter()
const username = ref('admin')
const password = ref('')
const busy = ref(false)
async function login() { busy.value = true; try { await api('/auth/login', json('POST', { username: username.value, password: password.value })); router.push('/admin/play') } catch (e: any) { ElMessage.error(e.message) } finally { busy.value = false } }
</script>
<template><main class="login-page"><el-card class="login-card"><h1>学院新闻循环展示系统</h1><p>管理员登录</p><el-form @submit.prevent="login"><el-form-item><el-input v-model="username" placeholder="用户名" /></el-form-item><el-form-item><el-input v-model="password" type="password" show-password placeholder="密码" /></el-form-item><el-button type="primary" native-type="submit" :loading="busy" class="full">登录</el-button></el-form></el-card></main></template>
