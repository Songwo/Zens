<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { useUserStore } from '@/store/user'
import { useUiStore } from '@/store/ui'
import { usePostComposerStore } from '@/store/postComposer'
import GlobalProgressBar from '@/components/common/GlobalProgressBar.vue'
import { initSessionResilience } from '@/utils/sessionResilience'
import { ensureCurrentUserProfile } from '@/utils/sessionProfile'
import { wsClient } from '@/utils/websocket'
import { setRouteMeta } from '@/utils/seo'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const uiStore = useUiStore()
const composerStore = usePostComposerStore()
const router = useRouter()
let unsubForceLogout: (() => void) | null = null

const AsyncPostComposerModal = defineAsyncComponent(() => import('@/components/compose/PostComposerModal.vue'))
const AsyncAppearanceDock = defineAsyncComponent(() => import('@/components/ui/AppearanceDock.vue'))
const AsyncPulseNotification = defineAsyncComponent(() => import('@/components/common/PulseNotification.vue'))
const AsyncPwaInstallPrompt = defineAsyncComponent(() => import('@/components/common/PwaInstallPrompt.vue'))
const AsyncNetworkStatusBanner = defineAsyncComponent(() => import('@/components/common/NetworkStatusBanner.vue'))
const shouldMountComposer = ref(false)
const shouldMountDeferredUi = ref(false)
const showComposer = computed(() => shouldMountComposer.value)
let stopSessionResilience: (() => void) | null = null
const scheduleDeferredUiMount = () => {
  const win = window as Window & {
    requestIdleCallback?: (callback: IdleRequestCallback, options?: { timeout: number }) => number
  }

  const mount = () => {
    shouldMountDeferredUi.value = true
  }

  const scheduleWhenIdle = () => {
    window.setTimeout(() => {
      if (typeof win.requestIdleCallback === 'function') {
        win.requestIdleCallback(mount, { timeout: 2500 })
        return
      }
      mount()
    }, 1200)
  }

  if (document.readyState === 'complete') {
    scheduleWhenIdle()
  } else {
    window.addEventListener('load', scheduleWhenIdle, { once: true })
  }
}

watch(
  () => composerStore.isOpen,
  (open) => {
    if (open) {
      shouldMountComposer.value = true
    }
  },
  { immediate: true }
)

onMounted(async () => {
  // Song：说明
  uiStore.applyUiSettings()
  stopSessionResilience = initSessionResilience()
  scheduleDeferredUiMount()

  if ((userStore.accessToken || userStore.refreshToken) && !userStore.userInfo) {
    try {
      await ensureCurrentUserProfile()
    } catch (err: any) {
      console.error('Failed to auto-login:', err)
      const status = err?.response?.status
      if (status === 401 || status === 403) {
        userStore.logout()
      }
    }
  }

  // 订阅强制下线通知（单设备策略）
  const uid = userStore.userId
  if (uid) {
    unsubForceLogout = wsClient.subscribeForceLogout(uid, () => {
      userStore.logout()
      ElMessage.warning('您的账号已在其他设备登录，当前会话已退出')
      router.push('/auth?type=login')
    })
  }
})

watch(
  () => router.currentRoute.value.fullPath,
  () => setRouteMeta(router.currentRoute.value),
  { immediate: true }
)

// 用户登录后动态订阅
watch(() => userStore.userId, (uid, oldUid) => {
  if (oldUid && unsubForceLogout) {
    unsubForceLogout()
    unsubForceLogout = null
  }
  if (uid) {
    unsubForceLogout = wsClient.subscribeForceLogout(uid, () => {
      userStore.logout()
      ElMessage.warning('您的账号已在其他设备登录，当前会话已退出')
      router.push('/auth?type=login')
    })
  }
})

onUnmounted(() => {
  stopSessionResilience?.()
  stopSessionResilience = null
  unsubForceLogout?.()
  unsubForceLogout = null
})
</script>

<template>
  <div class="app-container">
    <GlobalProgressBar />

    <router-view v-slot="{ Component, route }">
      <div class="route-stage">
      <transition name="route-shell" mode="out-in" appear>
        <keep-alive>
          <component :is="Component" v-if="route.meta.keepAlive" :key="route.path" class="route-page" />
        </keep-alive>
      </transition>
      <transition name="route-shell" mode="out-in" appear>
        <component :is="Component" v-if="!route.meta.keepAlive" :key="route.fullPath" class="route-page" />
      </transition>
      </div>
    </router-view>

    <AsyncAppearanceDock v-if="shouldMountDeferredUi" />

    <AsyncPulseNotification v-if="shouldMountDeferredUi" />

    <AsyncPwaInstallPrompt v-if="shouldMountDeferredUi" />

    <AsyncNetworkStatusBanner v-if="shouldMountDeferredUi" />

    <AsyncPostComposerModal v-if="showComposer" />
  </div>
</template>

<style>
.app-container {
  min-height: 100vh;
}

.route-stage {
  position: relative;
  isolation: isolate;
}

.route-page {
  transform-origin: 50% 0;
}

.route-shell-enter-active {
  transition: opacity 0.28s ease, transform 0.34s cubic-bezier(0.22, 1, 0.36, 1), filter 0.28s ease;
}

.route-shell-leave-active {
  transition: opacity 0.18s ease, transform 0.2s ease, filter 0.2s ease;
}

.route-shell-enter-from {
  opacity: 0;
  transform: translate3d(0, 16px, 0) scale(0.988);
  filter: blur(6px);
}

.route-shell-leave-to {
  opacity: 0;
  transform: translate3d(0, -10px, 0) scale(0.995);
  filter: blur(3px);
}

@media (prefers-reduced-motion: reduce) {
  .route-shell-enter-active,
  .route-shell-leave-active {
    transition-duration: 0.01ms;
  }
}
</style>
