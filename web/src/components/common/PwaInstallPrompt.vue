<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Close, Download } from '@element-plus/icons-vue'

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

const STORAGE_KEY = 'zens:pwa-install-dismissed'
const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null)
const visible = ref(false)

const install = async () => {
  if (!deferredPrompt.value) return
  await deferredPrompt.value.prompt()
  const choice = await deferredPrompt.value.userChoice
  if (choice.outcome === 'accepted') {
    visible.value = false
    deferredPrompt.value = null
  }
}

const dismiss = () => {
  visible.value = false
  localStorage.setItem(STORAGE_KEY, String(Date.now()))
}

onMounted(() => {
  if (localStorage.getItem(STORAGE_KEY)) return
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault()
    deferredPrompt.value = event as BeforeInstallPromptEvent
    visible.value = true
  })
})
</script>

<template>
  <div v-if="visible" class="pwa-install">
    <div class="pwa-copy">
      <strong>安装 Zens</strong>
      <span>添加到主屏，消息和内容访问更快。</span>
    </div>
    <el-button size="small" type="primary" :icon="Download" @click="install">安装</el-button>
    <el-button size="small" text circle :icon="Close" @click="dismiss" />
  </div>
</template>

<style scoped>
.pwa-install {
  position: fixed;
  left: 50%;
  bottom: 18px;
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(420px, calc(100vw - 24px));
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
  box-shadow: var(--el-box-shadow-light);
  transform: translateX(-50%);
}

.pwa-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.pwa-copy strong {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.pwa-copy span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
