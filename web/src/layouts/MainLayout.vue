<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataLine, House, Plus, StarFilled, User } from '@element-plus/icons-vue'
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
  { key: '/compose', label: '发帖', icon: Plus, action: () => composerStore.open() },
  { key: '/featured', label: '精华', icon: StarFilled, action: () => router.push('/featured') },
  { key: '/me', label: '我的', icon: User, action: () => router.push('/me') },
]

const activeMobileKey = computed(() => {
  const path = route.path
  if (path.startsWith('/hot')) return '/hot'
  if (path.startsWith('/featured')) return '/featured'
  if (path.startsWith('/me') || path.startsWith('/settings')) return '/me'
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
        :class="{ active: activeMobileKey === item.key, compose: item.key === '/compose' }"
        @click="item.action()"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
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
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 12px 8px;
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
    padding: 12px 10px calc(var(--cp-mobile-nav-height) + 14px);
  }

  .mobile-bottom-nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: var(--cp-mobile-nav-height);
    background: rgba(255, 255, 255, 0.96);
    border-top: 1px solid var(--el-border-color-light);
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    z-index: 120;
    backdrop-filter: blur(10px);
  }

  .mobile-nav-item {
    border: none;
    background: transparent;
    color: var(--el-text-color-secondary);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    font-size: 11px;
    font-weight: 600;
    cursor: pointer;
    transition: color 0.2s ease, transform 0.2s ease;
  }

  .mobile-nav-item :deep(.el-icon) {
    font-size: 18px;
  }

  .mobile-nav-item.active {
    color: #7a5700;
  }

  .mobile-nav-item.compose {
    color: #7a5700;
    transform: translateY(-2px);
  }
}
</style>
