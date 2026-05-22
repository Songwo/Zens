<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ArrowDownBold, ArrowUpBold } from '@element-plus/icons-vue'

const canScroll = ref(false)
const atTop = ref(true)
const atBottom = ref(false)
const progress = ref(0)
const dragging = ref(false)
const railRef = ref<HTMLElement | null>(null)

let frameId: number | null = null
let resizeObserver: ResizeObserver | null = null
let activePointerId: number | null = null

const getScrollMetrics = () => {
  const root = document.documentElement
  const body = document.body
  const scrollTop = window.scrollY || root.scrollTop || body.scrollTop || 0
  const viewportHeight = window.innerHeight || root.clientHeight || 0
  const fullHeight = Math.max(root.scrollHeight, body.scrollHeight, root.offsetHeight, body.offsetHeight)

  return {
    scrollTop,
    viewportHeight,
    fullHeight,
  }
}

const updateState = () => {
  frameId = null
  const { scrollTop, viewportHeight, fullHeight } = getScrollMetrics()
  const remaining = Math.max(fullHeight - viewportHeight, 0)

  canScroll.value = remaining > 240
  atTop.value = scrollTop <= 24
  atBottom.value = remaining - scrollTop <= 24
  progress.value = remaining > 0 ? Math.min(Math.max(scrollTop / remaining, 0), 1) : 0
}

const scheduleUpdate = () => {
  if (frameId !== null) {
    return
  }
  frameId = window.requestAnimationFrame(updateState)
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const scrollToBottom = () => {
  const { fullHeight, viewportHeight } = getScrollMetrics()
  window.scrollTo({ top: Math.max(fullHeight - viewportHeight, 0), behavior: 'smooth' })
}

const thumbStyle = computed(() => ({
  top: `calc(${progress.value * 100}% - 7px)`,
}))

const progressStyle = computed(() => ({
  transform: `translateX(-50%) scaleY(${Math.max(progress.value, 0.02)})`,
}))

const jumpToRatio = (ratio: number, smooth: boolean) => {
  const normalizedRatio = Math.min(Math.max(ratio, 0), 1)
  const { fullHeight, viewportHeight } = getScrollMetrics()
  const maxScrollTop = Math.max(fullHeight - viewportHeight, 0)
  window.scrollTo({
    top: maxScrollTop * normalizedRatio,
    behavior: smooth ? 'smooth' : 'auto',
  })
}

const getRatioFromClientY = (clientY: number) => {
  const railEl = railRef.value
  if (!railEl) {
    return 0
  }
  const rect = railEl.getBoundingClientRect()
  if (!rect.height) {
    return 0
  }
  return (clientY - rect.top) / rect.height
}

const handleRailPointerMove = (event: PointerEvent) => {
  if (!dragging.value || activePointerId !== event.pointerId) {
    return
  }
  jumpToRatio(getRatioFromClientY(event.clientY), false)
}

const stopRailDragging = (pointerId?: number) => {
  if (pointerId !== undefined && activePointerId !== pointerId) {
    return
  }
  dragging.value = false
  activePointerId = null
}

const handleRailPointerDown = (event: PointerEvent) => {
  activePointerId = event.pointerId
  dragging.value = true
  jumpToRatio(getRatioFromClientY(event.clientY), false)
  railRef.value?.setPointerCapture?.(event.pointerId)
}

const handlePointerUp = (event: PointerEvent) => {
  stopRailDragging(event.pointerId)
}

const handlePointerCancel = (event: PointerEvent) => {
  stopRailDragging(event.pointerId)
}

onMounted(() => {
  updateState()

  window.addEventListener('scroll', scheduleUpdate, { passive: true })
  window.addEventListener('resize', scheduleUpdate, { passive: true })
  window.addEventListener('pointermove', handleRailPointerMove, { passive: true })
  window.addEventListener('pointerup', handlePointerUp, { passive: true })
  window.addEventListener('pointercancel', handlePointerCancel, { passive: true })

  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(scheduleUpdate)
    resizeObserver.observe(document.body)
    resizeObserver.observe(document.documentElement)
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', scheduleUpdate)
  window.removeEventListener('resize', scheduleUpdate)
  window.removeEventListener('pointermove', handleRailPointerMove)
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('pointercancel', handlePointerCancel)
  resizeObserver?.disconnect()
  resizeObserver = null

  if (frameId !== null) {
    window.cancelAnimationFrame(frameId)
    frameId = null
  }
})
</script>

<template>
  <Teleport to="body">
    <transition name="edge-bar-fade">
      <div v-if="canScroll" class="scroll-edge-bar" aria-label="页面快捷滚动">
        <button type="button" class="edge-action" :class="{ disabled: atTop }" :disabled="atTop" aria-label="回到顶部" @click="scrollToTop">
          <el-icon><ArrowUpBold /></el-icon>
        </button>
        <button
          ref="railRef"
          type="button"
          class="edge-rail"
          :class="{ dragging }"
          aria-label="快速定位阅读进度"
          @pointerdown.prevent="handleRailPointerDown"
        >
          <span class="edge-rail-line"></span>
          <span class="edge-rail-progress" :style="progressStyle"></span>
          <span class="edge-rail-thumb" :style="thumbStyle"></span>
        </button>
        <button type="button" class="edge-action" :class="{ disabled: atBottom }" :disabled="atBottom" aria-label="跳到末尾" @click="scrollToBottom">
          <el-icon><ArrowDownBold /></el-icon>
        </button>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.scroll-edge-bar {
  position: fixed;
  right: max(18px, calc((100vw - var(--cp-shell-width, 1440px)) / 2 + 336px));
  bottom: 30px;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  width: 48px;
  border-radius: 999px;
  border: 1px solid color-mix(in oklab, var(--el-border-color) 82%, white 18%);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(14px);
  z-index: 118;
}

.edge-action {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--el-text-color-regular);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.edge-action :deep(.el-icon) {
  font-size: 14px;
}

.edge-action:hover:not(.disabled) {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  transform: translateY(-1px);
}

.edge-action.disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}

.edge-action:disabled {
  opacity: 0.72;
}

.edge-rail {
  position: relative;
  width: 18px;
  height: 108px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  padding: 0;
  background: transparent;
  cursor: ns-resize;
  touch-action: none;
}

.edge-rail-line,
.edge-rail-progress {
  position: absolute;
  left: 50%;
  width: 2px;
  border-radius: 999px;
  transform: translateX(-50%);
}

.edge-rail-line {
  top: 8px;
  bottom: 8px;
  background: color-mix(in oklab, var(--el-border-color) 78%, white 22%);
}

.edge-rail-progress {
  top: 8px;
  bottom: 8px;
  background: linear-gradient(180deg, var(--el-color-primary-light-5), var(--el-color-primary));
  transform-origin: top center;
}

.edge-rail-thumb {
  position: absolute;
  left: 50%;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid #fff;
  background: var(--el-color-primary);
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.28);
  transform: translateX(-50%);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.edge-rail:hover .edge-rail-thumb,
.edge-rail.dragging .edge-rail-thumb {
  transform: translateX(-50%) scale(1.06);
  box-shadow: 0 6px 14px rgba(37, 99, 235, 0.34);
}

.edge-bar-fade-enter-active,
.edge-bar-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.24s ease;
}

.edge-bar-fade-enter-from,
.edge-bar-fade-leave-to {
  opacity: 0;
  transform: translate3d(0, 8px, 0);
}

@media (max-width: 1199px) {
  .scroll-edge-bar {
    right: 14px;
  }
}

@media (max-width: 900px) {
  .scroll-edge-bar {
    right: 12px;
    bottom: calc(var(--cp-mobile-nav-height) + 16px);
    width: 44px;
    padding: 8px 6px;
  }

  .edge-action {
    width: 28px;
    height: 28px;
  }

  .edge-rail {
    height: 94px;
  }
}
</style>
