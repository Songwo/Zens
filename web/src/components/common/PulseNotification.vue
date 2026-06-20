<script setup lang="ts">
import { notificationQueue, pulseNotification } from '@/utils/pulseNotification'

const handleClose = (id: string) => {
  pulseNotification.close(id)
}
</script>

<template>
  <div class="pulse-notification-container">
    <transition-group name="pulse-toast-list">
      <div
        v-for="item in notificationQueue"
        :key="item.id"
        class="pulse-toast-card"
        :class="[`is-${item.type}`]"
        :style="{ '--duration': item.duration + 'ms' }"
        role="alert"
      >
        <div class="toast-icon-wrap">
          <svg v-if="item.type === 'like'" class="toast-icon svg-heart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>

          <svg v-else-if="item.type === 'comment'" class="toast-icon svg-comment" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
            <circle cx="9" cy="12" r="1" fill="currentColor" />
            <circle cx="12" cy="12" r="1" fill="currentColor" />
            <circle cx="15" cy="12" r="1" fill="currentColor" />
          </svg>

          <svg v-else-if="item.type === 'post'" class="toast-icon svg-post" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>

          <svg v-else-if="item.type === 'success'" class="toast-icon svg-success" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>

          <svg v-else-if="item.type === 'warning'" class="toast-icon svg-warning" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>

          <svg v-else-if="item.type === 'error'" class="toast-icon svg-error" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
            <line x1="9" y1="9" x2="15" y2="15" />
            <line x1="15" y1="9" x2="9" y2="15" />
          </svg>

          <svg v-else class="toast-icon svg-info" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
        </div>

        <div class="toast-content">
          <h4 class="toast-title">{{ item.title }}</h4>
          <p class="toast-desc">{{ item.message }}</p>
        </div>

        <button class="toast-close-btn" @click="handleClose(item.id)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>

        <div class="toast-progress"></div>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.pulse-notification-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 99999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
  width: min(360px, calc(100vw - 32px));
}

.pulse-toast-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
  pointer-events: auto;
  overflow: hidden;
  transition: opacity 0.18s ease, transform 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
  will-change: transform, opacity;
}

:global(html.dark) .pulse-toast-card {
  background: rgba(17, 24, 39, 0.96);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.32);
}

.toast-content {
  flex: 1;
  min-width: 0;
  z-index: 1;
}

.toast-title {
  margin: 0 0 4px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.35;
}

.toast-desc {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  word-break: break-word;
}

.toast-icon-wrap {
  position: relative;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.toast-icon {
  width: 18px;
  height: 18px;
}

.toast-close-btn {
  background: none;
  border: none;
  outline: none;
  padding: 4px;
  margin-top: -1px;
  color: var(--el-text-color-placeholder);
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.15s ease, color 0.15s ease;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-close-btn:hover {
  background: rgba(148, 163, 184, 0.12);
  color: var(--el-text-color-regular);
}

:global(html.dark) .toast-close-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #e5e7eb;
}

.toast-close-btn svg {
  width: 14px;
  height: 14px;
}

.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 2px;
  width: 100%;
  transform-origin: left;
  animation: progress-shrink var(--duration) linear forwards;
  z-index: 2;
}

@keyframes progress-shrink {
  from {
    transform: scaleX(1);
  }
  to {
    transform: scaleX(0);
  }
}

.pulse-toast-card.is-like {
  border-left: 4px solid #ec4899;
}
.pulse-toast-card.is-like .toast-icon-wrap {
  background: rgba(236, 72, 153, 0.1);
  color: #ec4899;
}
.pulse-toast-card.is-like .toast-progress {
  background: #ec4899;
}

.pulse-toast-card.is-comment {
  border-left: 4px solid #8b5cf6;
}
.pulse-toast-card.is-comment .toast-icon-wrap {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}
.pulse-toast-card.is-comment .toast-progress {
  background: #8b5cf6;
}

.pulse-toast-card.is-post {
  border-left: 4px solid #06b6d4;
}
.pulse-toast-card.is-post .toast-icon-wrap {
  background: rgba(6, 182, 212, 0.1);
  color: #06b6d4;
}
.pulse-toast-card.is-post .toast-progress {
  background: #06b6d4;
}

.pulse-toast-card.is-success {
  border-left: 4px solid #10b981;
}
.pulse-toast-card.is-success .toast-icon-wrap {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
.pulse-toast-card.is-success .toast-progress {
  background: #10b981;
}

.pulse-toast-card.is-warning {
  border-left: 4px solid #f59e0b;
}
.pulse-toast-card.is-warning .toast-icon-wrap {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
}
.pulse-toast-card.is-warning .toast-progress {
  background: #f59e0b;
}

.pulse-toast-card.is-error {
  border-left: 4px solid #ef4444;
}
.pulse-toast-card.is-error .toast-icon-wrap {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}
.pulse-toast-card.is-error .toast-progress {
  background: #ef4444;
}

.pulse-toast-card.is-info {
  border-left: 4px solid #3b82f6;
}
.pulse-toast-card.is-info .toast-icon-wrap {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}
.pulse-toast-card.is-info .toast-progress {
  background: #3b82f6;
}

.pulse-toast-list-enter-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.pulse-toast-list-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
  position: absolute; /* for smooth moving transitions of adjacent items */
}
.pulse-toast-list-enter-from {
  opacity: 0;
  transform: translate3d(0, -8px, 0);
}
.pulse-toast-list-leave-to {
  opacity: 0;
  transform: translate3d(0, -6px, 0);
}
.pulse-toast-list-move {
  transition: transform 0.18s ease;
}
</style>
