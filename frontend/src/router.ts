import { createRouter, createWebHistory } from 'vue-router'
import Login from './views/Login.vue'
import Shell from './views/Shell.vue'
import News from './views/News.vue'
import Play from './views/Play.vue'
import Settings from './views/Settings.vue'
import Display from './views/Display.vue'
export default createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/admin/play' },
  { path: '/admin/login', component: Login },
  { path: '/admin', component: Shell, children: [
    { path: '', redirect: '/admin/play' },
    { path: 'play', component: Play }, { path: 'news', component: News }, { path: 'settings', component: Settings }
  ] },
  { path: '/display', component: Display }
] })
