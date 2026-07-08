<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, DataLine, House, Plus, Present } from '@element-plus/icons-vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import LeftNav from '@/components/layout/LeftNav.vue'
import RightRail from '@/components/layout/RightRail.vue'
import { usePostComposerStore } from '@/store/postComposer'

const route = useRoute()
const router = useRouter()
const composerStore = usePostComposerStore()

const showRightRail = ref(false)
const shellReady = ref(false)

const mobileNavItems = [
  { key: '/', label: '首页', icon: House, action: () => router.push('/') },
  { key: '/hot', label: '热榜', icon: DataLine, action: () => router.push('/hot') },
  { key: '/compose', label: '发帖', icon: Plus, action: () => composerStore.open(), compose: true },
  { key: '/benefits', label: '福利', icon: Present, action: () => router.push('/benefits') },
  { key: '/metaverse', label: '星港', icon: Connection, action: () => router.push('/metaverse') },
]

const activeMobileKey = computed(() => {
  const path = route.path
  if (path.startsWith('/hot')) return '/hot'
  if (path.startsWith('/benefits')) return '/benefits'
  if (path.startsWith('/metaverse')) return '/metaverse'
  if (path.startsWith('/t/') || path.startsWith('/s/') || path.startsWith('/tag/') || path.startsWith('/search')) return '/'
  return '/'
})

onMounted(() => {
  requestAnimationFrame(() => {
    shellReady.value = true
  })

  const win = window as Window & {
    requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number
  }

  if (typeof win.requestIdleCallback === 'function') {
    win.requestIdleCallback(
      () => {
        showRightRail.value = true
      },
      { timeout: 1500 }
    )
    return
  }

  setTimeout(() => {
    showRightRail.value = true
  }, 800)
})
</script>

<template>
  <div class="page">
    <AppTopbar />

    <div class="shell" :class="{ 'shell-ready': shellReady }">
      <div class="grid">
        <aside class="left hidden-sm-and-down">
          <LeftNav />
        </aside>

        <main class="main">
          <slot></slot>
        </main>

        <aside class="right hidden-md-and-down">
          <transition name="rail-float" mode="out-in" appear>
            <div v-if="showRightRail" key="right-rail" class="right-rail-stage">
              <slot name="right-rail">
                <RightRail />
              </slot>
            </div>
            <div v-else key="right-rail-placeholder" class="right-rail-placeholder"></div>
          </transition>
        </aside>
      </div>
    </div>
    <nav class="mobile-bottom-nav">
      <button
        v-for="item in mobileNavItems"
        :key="item.key"
        class="mobile-nav-item"
        :class="{ active: activeMobileKey === item.key, compose: item.compose }"
        @click="item.action()"
      >
        <span v-if="item.compose" class="compose-orb">
          <el-icon><component :is="item.icon" /></el-icon>
        </span>
        <el-icon v-else><component :is="item.icon" /></el-icon>
        <span class="mobile-nav-label">{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background:
    radial-gradient(1100px 400px at -120px -160px, rgba(244, 180, 0, 0.08), transparent 60%),
    var(--el-bg-color-page, #f5f7fa);
}

.shell {
  max-width: var(--cp-shell-width);
  margin: 0 auto;
  padding: var(--cp-shell-padding);
  transition: max-width 0.3s cubic-bezier(0.4, 0, 0.2, 1), padding 0.3s ease, opacity 0.32s ease, transform 0.38s cubic-bezier(0.22, 1, 0.36, 1);
  opacity: 0.94;
  transform: translate3d(0, 10px, 0);
}

.shell-ready {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.grid {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 300px;
  gap: 20px;
  align-items: start;
}

.left {
  position: sticky;
  top: calc(var(--header-height) + 16px);
  max-height: calc(100vh - var(--header-height) - 32px);
  overflow-y: auto;
  background: color-mix(in srgb, var(--el-bg-color-overlay) 78%, transparent);
  border: 1px solid color-mix(in srgb, var(--el-border-color-lighter) 70%, transparent);
  border-radius: 14px;
  padding: 10px 8px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(10px);
}

.main {
  min-width: 0;
}

.right {
  position: sticky;
  top: calc(var(--header-height) + 16px);
  max-height: calc(100vh - var(--header-height) - 32px);
  overflow-y: auto;
  overflow-x: hidden;
}

.right-rail-placeholder {
  height: 320px;
  border-radius: 14px;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, 0.28) 50%, rgba(255, 255, 255, 0) 100%),
    var(--el-fill-color-light);
  background-size: 240px 100%, auto;
  animation: rail-shimmer 1.5s ease-in-out infinite;
}

.right-rail-stage {
  min-height: 160px;
}

.left::-webkit-scrollbar,
.right::-webkit-scrollbar {
  width: 6px;
}

.left::-webkit-scrollbar-track,
.right::-webkit-scrollbar-track {
  background: transparent;
}

.left::-webkit-scrollbar-thumb,
.right::-webkit-scrollbar-thumb {
  background: var(--el-border-color-light);
  border-radius: 3px;
}

.left::-webkit-scrollbar-thumb:hover,
.right::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color);
}

.mobile-bottom-nav {
  display: none;
}

.rail-float-enter-active,
.rail-float-leave-active {
  transition: opacity 0.28s ease, transform 0.34s cubic-bezier(0.22, 1, 0.36, 1);
}

.rail-float-enter-from,
.rail-float-leave-to {
  opacity: 0;
  transform: translate3d(0, 14px, 0);
}

@keyframes rail-shimmer {
  0% {
    background-position: -220px 0, 0 0;
  }
  100% {
    background-position: calc(100% + 220px) 0, 0 0;
  }
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 220px minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: 1fr;
  }

  .shell {
    padding: 10px 10px calc(var(--cp-mobile-nav-height) + env(safe-area-inset-bottom, 0px) + 18px);
  }

  .mobile-bottom-nav {
    position: fixed;
    bottom: max(8px, env(safe-area-inset-bottom, 0px));
    left: max(10px, env(safe-area-inset-left, 0px));
    right: max(10px, env(safe-area-inset-right, 0px));
    height: var(--cp-mobile-nav-height);
    padding: 6px;
    background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
    border: 1px solid color-mix(in srgb, var(--el-border-color-light) 82%, transparent);
    border-radius: 24px;
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    z-index: 120;
    box-shadow: 0 14px 34px rgba(15, 23, 42, 0.16);
    backdrop-filter: blur(18px) saturate(128%);
    -webkit-tap-highlight-color: transparent;
  }

  .mobile-nav-item {
    border: none;
    background: transparent;
    color: var(--el-text-color-secondary);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    min-width: 0;
    min-height: 50px;
    border-radius: 18px;
    font-size: 10px;
    line-height: 1;
    font-weight: 700;
    cursor: pointer;
    -webkit-tap-highlight-color: transparent;
    transition: color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
  }

  .mobile-nav-item:active {
    transform: scale(0.94);
  }

  .mobile-nav-item :deep(.el-icon) {
    font-size: 19px;
  }

  .mobile-nav-item.active {
    color: #7a5700;
    background: var(--accept-bg-soft);
  }

  .mobile-nav-item.compose {
    position: relative;
    color: #7a5700;
    transform: translateY(-12px);
  }

  .mobile-nav-item.compose:active {
    transform: translateY(-12px) scale(0.94);
  }

  .mobile-nav-item.compose .mobile-nav-label {
    margin-top: 1px;
    color: var(--accept-text);
  }

  .compose-orb {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    background: linear-gradient(135deg, #f6b800 0%, #f29b24 100%);
    box-shadow: 0 12px 24px rgba(230, 154, 0, 0.34);
  }

  .compose-orb :deep(.el-icon) {
    font-size: 23px;
  }

  .mobile-nav-label {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 380px) {
  .mobile-bottom-nav {
    left: max(8px, env(safe-area-inset-left, 0px));
    right: max(8px, env(safe-area-inset-right, 0px));
    border-radius: 22px;
  }

  .mobile-nav-item {
    font-size: 9px;
  }
}
</style>
