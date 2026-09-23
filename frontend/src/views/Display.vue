<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api'
type Item = { id: number; image_url: string; sort_order: number }
const items=ref<Item[]>([]), config=ref<any>({ display_seconds:10, animation_enabled:true, animation_duration:0.8 }), index=ref(0), logoPosition=computed(() => config.value.logo_position || 'top-left')
const currentDate = ref('')
const isFullscreen = ref(false)
let rotateTimer: number | undefined, syncTimer: number | undefined, dateTimer: number | undefined
const lunarDays = ['', '初一', '初二', '初三', '初四', '初五', '初六', '初七', '初八', '初九', '初十', '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十', '廿一', '廿二', '廿三', '廿四', '廿五', '廿六', '廿七', '廿八', '廿九', '三十']
function updateDate() {
  const now = new Date()
  const solar = new Intl.DateTimeFormat('zh-CN', { timeZone:'Asia/Shanghai', year:'numeric', month:'long', day:'numeric', weekday:'long' }).format(now)
  const lunarParts = new Intl.DateTimeFormat('zh-CN-u-ca-chinese', { timeZone:'Asia/Shanghai', month:'long', day:'numeric' }).formatToParts(now)
  const lunarMonth = lunarParts.find(part => part.type === 'month')?.value || ''
  const lunarDay = Number(lunarParts.find(part => part.type === 'day')?.value || 0)
  currentDate.value = `${solar}　农历${lunarMonth}${lunarDays[lunarDay] || lunarDay}`
}
async function sync() { try { const [next,cfg]=await Promise.all([api<Item[]>('/display/news'),api('/display/config')]); await Promise.all(next.map(n=>new Promise<void>(resolve=>{ const img=new Image(); img.onload=()=>resolve(); img.onerror=()=>resolve(); img.src=n.image_url }))); const old=items.value[index.value]?.id; items.value=next; config.value=cfg; const found=next.findIndex(n=>n.id===old); index.value=found>=0?found:0; schedule() } catch { schedule() } }
function schedule() { clearTimeout(rotateTimer); if(items.value.length>1) rotateTimer=window.setTimeout(()=>{ index.value=(index.value+1)%items.value.length; schedule() }, Math.max(1,Number(config.value.display_seconds)||10)*1000) }
function updateFullscreenState() {
  const safariDocument = document as Document & { webkitFullscreenElement?: Element }
  isFullscreen.value = Boolean(document.fullscreenElement || safariDocument.webkitFullscreenElement)
}
async function enterFullscreen() {
  const element = document.documentElement as HTMLElement & { webkitRequestFullscreen?: () => void }
  try {
    if (element.requestFullscreen) await element.requestFullscreen()
    else element.webkitRequestFullscreen?.()
  } finally {
    updateFullscreenState()
  }
}
onMounted(()=>{
  updateDate(); sync()
  syncTimer=window.setInterval(sync,30000)
  dateTimer=window.setInterval(updateDate,60000)
  document.addEventListener('fullscreenchange',updateFullscreenState)
  document.addEventListener('webkitfullscreenchange',updateFullscreenState)
})
onUnmounted(()=>{
  clearInterval(syncTimer); clearInterval(dateTimer); clearTimeout(rotateTimer)
  document.removeEventListener('fullscreenchange',updateFullscreenState)
  document.removeEventListener('webkitfullscreenchange',updateFullscreenState)
})
</script>
<template><main class="display" :style="{ '--fade-duration': `${Number(config.animation_duration ?? 0.8)}s` }"><transition :name="config.animation_enabled?'fade':'none'" mode="out-in" :duration="Number(config.animation_duration ?? 0.8)*1000"><img v-if="items.length" :key="items[index]?.id" :src="items[index]?.image_url" class="display-image" alt="学院新闻"><img v-else-if="config.default_image_url" :key="config.default_image_url" :src="config.default_image_url" class="display-image" alt="默认宣传画面"><div v-else key="fallback" class="display-fallback"><strong>数字艺术与设计学院</strong><span>新闻展示</span></div></transition><img v-if="config.logo_enabled && config.logo_url" :src="config.logo_url" :class="['display-logo',logoPosition]" alt="学院 Logo"><div class="display-date">{{ currentDate }}</div><button v-if="!isFullscreen" class="fullscreen-button" type="button" @click="enterFullscreen">进入全屏展示</button></main></template>
