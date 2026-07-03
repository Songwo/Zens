<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Connection, WarningFilled } from '@element-plus/icons-vue'
import { readNetworkConnection, type NetworkConnectionInfo } from '@/utils/network'

const online = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
const recentlyRestored = ref(false)
const connectionInfo = ref<NetworkConnectionInfo>({})
let restoreTimer: number | null = null

const readConnectionInfo = () => {
  connectionInfo.value = readNetworkConnection()
}

const slowNetwork = computed(() => {
  const type = connectionInfo.value.effectiveType || ''
  return online.value && Boolean(connectionInfo.value.saveData || /(^slow-2g$|^2g$)/i.test(type))
})

const visible = computed(() => !online.value || recentlyRestored.value || slowNetwork.value)

const bannerClass = computed(() => ({
  offline: !online.value,
  restored: online.value && recentlyRestored.value,
  slow: slowNetwork.value && !recentlyRestored.value,
}))

const icon = computed(() => (!online.value || slowNetwork.value ? WarningFilled : Connection))

const text = computed(() => {
  if (!online.value) return '当前离线，已展示可用缓存'
  if (recentlyRestored.value) return '网络已恢复，内容会继续同步'
  return '网络较慢，已减少后台加载'
})

const handleOnline = () => {
  online.value = true
  recentlyRestored.value = true
  if (restoreTimer !== null) {
    window.clearTimeout(restoreTimer)
  }
  restoreTimer = window.setTimeout(() => {
    recentlyRestored.value = false
    restoreTimer = null
  }, 3200)
}

const handleOffline = () => {
  online.value = false
  recentlyRestored.value = false
  if (restoreTimer !== null) {
    window.clearTimeout(restoreTimer)
    restoreTimer = null
  }
}

onMounted(() => {
  readConnectionInfo()
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  ;(navigator as Navigator & { connection?: EventTarget }).connection?.addEventListener?.('change', readConnectionInfo)
})

onUnmounted(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  ;(navigator as Navigator & { connection?: EventTarget }).connection?.removeEventListener?.('change', readConnectionInfo)
  if (restoreTimer !== null) {
    window.clearTimeout(restoreTimer)
  }
})
</script>

<template>
  <transition name="network-banner">
    <div v-if="visible" class="network-status-banner" :class="bannerClass" role="status" aria-live="polite">
      <el-icon><component :is="icon" /></el-icon>
      <span>{{ text }}</span>
    </div>
  </transition>
</template>

<style scoped>
.network-status-banner {
  position: fixed;
  left: 50%;
  bottom: 18px;
  z-index: 3200;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(420px, calc(100vw - 24px));
  min-height: 38px;
  padding: 8px 13px;
  border-radius: 999px;
  border: 1px solid var(--el-border-color-light);
  background: color-mix(in srgb, var(--el-bg-color-overlay) 94%, transparent);
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(16px) saturate(128%);
  transform: translateX(-50%);
}

.network-status-banner.offline {
  color: #9a3412;
  border-color: #fed7aa;
  background: color-mix(in srgb, #fff7ed 94%, transparent);
}

.network-status-banner.restored {
  color: #047857;
  border-color: #bbf7d0;
  background: color-mix(in srgb, #f0fdf4 94%, transparent);
}

.network-status-banner.slow {
  color: #8a5a00;
  border-color: var(--accept-border);
  background: color-mix(in srgb, var(--accept-bg-soft) 94%, transparent);
}

.network-banner-enter-active,
.network-banner-leave-active {
  transition: opacity 0.2s ease, transform 0.22s ease;
}

.network-banner-enter-from,
.network-banner-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

@media (max-width: 900px) {
  .network-status-banner {
    bottom: calc(var(--cp-mobile-nav-height, 62px) + env(safe-area-inset-bottom, 0px) + 22px);
    min-height: 34px;
    padding: 7px 11px;
    font-size: 11px;
  }
}
</style>
