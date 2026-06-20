<script setup lang="ts">
import { computed } from 'vue'
import { globalProgressState } from '@/utils/globalProgress'

const widthStyle = computed(() => `${Math.max(0, Math.min(100, globalProgressState.percent)).toFixed(2)}%`)
</script>

<template>
  <div class="global-progress" :class="{ active: globalProgressState.active }" aria-live="polite">
    <div class="progress-track">
      <div class="progress-fill" :style="{ width: widthStyle }">
        <span class="progress-glint"></span>
      </div>
    </div>
    <div class="progress-label">{{ globalProgressState.label || '正在加载' }}</div>
  </div>
</template>

<style scoped>
.global-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  z-index: 3000;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.global-progress.active {
  opacity: 1;
}

.progress-track {
  width: 100%;
  height: 100%;
  background: color-mix(in srgb, var(--cp-primary) 10%, transparent);
  overflow: hidden;
}

.progress-fill {
  position: relative;
  width: 0;
  height: 100%;
  border-radius: 0 999px 999px 0;
  background:
    linear-gradient(90deg, var(--cp-primary-dark), var(--cp-primary), var(--cp-primary-light)),
    var(--cp-primary);
  box-shadow:
    0 0 16px color-mix(in srgb, var(--cp-primary) 46%, transparent),
    0 1px 0 rgba(255, 255, 255, 0.45) inset;
  transition: width 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

.progress-glint {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, transparent 0%, rgba(255, 255, 255, 0.55) 42%, transparent 78%);
  transform: translateX(-100%);
  animation: progress-glint 1.05s ease-in-out infinite;
}

.progress-label {
  position: absolute;
  top: 7px;
  left: 50%;
  max-width: min(280px, calc(100vw - 32px));
  transform: translateX(-50%) translateY(-6px);
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--cp-primary) 28%, var(--el-border-color-light));
  border-radius: 999px;
  background: color-mix(in srgb, var(--el-bg-color-overlay) 88%, transparent);
  color: var(--accept-text);
  font-size: 11px;
  font-weight: 800;
  line-height: 1.2;
  white-space: nowrap;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
  opacity: 0;
  transition: opacity 0.2s ease, transform 0.22s ease;
}

.global-progress.active .progress-label {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

@keyframes progress-glint {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(130%);
  }
}

@media (max-width: 760px) {
  .progress-label {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .progress-glint {
    animation: none;
  }

  .progress-fill,
  .progress-label,
  .global-progress {
    transition-duration: 0.01ms;
  }
}
</style>
