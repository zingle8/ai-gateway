<template>
  <div class="shell">
    <Sidebar :menus="menus" :brand="brand" :services="services" :health="health" />
    <div class="shell-main">
      <HeaderBar />
      <main class="shell-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Sidebar from './Sidebar.vue'
import HeaderBar from './Header.vue'

defineProps({
  menus: { type: Array, default: () => [] },
  brand: { type: String, default: 'API网关平台' },
  /** 菜单 path → 后端服务名（由 portal 按模块装配注入） */
  services: { type: Object, default: () => ({}) }
})

/**
 * 菜单健康标识（每 30 秒轮询，改配置后最迟 30 秒生效）：
 *  - /api/configs/public   → 开关 platform.menu.health.enabled
 *  - /health/menu-services → 各后端服务运行状态（proxy 直连探测）
 */
const POLL_MILLIS = 30_000
const health = ref({ enabled: false, statuses: {}, cacheMode: '' })
let timer = null

const loadHealth = async () => {
  try {
    const flags = await fetch('/api/configs/public').then((r) => (r.ok ? r.json() : null))
    const enabled = flags?.data?.['platform.menu.health.enabled'] !== 'false'
    const cacheMode = flags?.data?.['platform.auth.cache.mode'] || ''
    if (!enabled) {
      health.value = { enabled: false, statuses: {}, cacheMode }
      return
    }
    const snapshot = await fetch('/health/menu-services').then((r) => (r.ok ? r.json() : null))
    const statuses = {}
    ;(snapshot?.services || []).forEach((s) => {
      statuses[s.service] = s
    })
    health.value = { enabled: true, statuses, cacheMode }
  } catch {
    // 配置服务或网关不可用时静默隐藏标识，不影响菜单使用
    health.value = { enabled: false, statuses: {}, cacheMode: '' }
  }
}

onMounted(() => {
  loadHealth()
  timer = setInterval(loadHealth, POLL_MILLIS)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.shell {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.shell-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.shell-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
