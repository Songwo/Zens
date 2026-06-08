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
        <!-- Background light pulse glow effect -->
        <div class="toast-glow"></div>

        <!-- Custom SVG animated icons based on type -->
        <div class="toast-icon-wrap">
          <!-- LIKE: Beating Neon Heart -->
          <svg v-if="item.type === 'like'" class="toast-icon svg-heart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>

          <!-- COMMENT: Typing Speech Bubble -->
          <svg v-else-if="item.type === 'comment'" class="toast-icon svg-comment" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
            <circle cx="9" cy="12" r="1" fill="currentColor" />
            <circle cx="12" cy="12" r="1" fill="currentColor" />
            <circle cx="15" cy="12" r="1" fill="currentColor" />
          </svg>

          <!-- POST: Flying Paper Airplane -->
          <svg v-else-if="item.type === 'post'" class="toast-icon svg-post" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="22" y1="2" x2="11" y2="13" />
            <polygon points="22 2 15 22 11 13 2 9 22 2" />
          </svg>

          <!-- SUCCESS: Check Circle -->
          <svg v-else-if="item.type === 'success'" class="toast-icon svg-success" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>

          <!-- WARNING: Danger Triangle -->
          <svg v-else-if="item.type === 'warning'" class="toast-icon svg-warning" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>

          <!-- ERROR: Close Hexagon -->
          <svg v-else-if="item.type === 'error'" class="toast-icon svg-error" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
            <line x1="9" y1="9" x2="15" y2="15" />
            <line x1="15" y1="9" x2="9" y2="15" />
          </svg>

          <!-- INFO / DEFAULT: Pulsing Radar -->
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

        <!-- Dynamic glowing progress bar -->
        <div class="toast-progress"></div>
      </div>
    </transition-group>
  </div>
</template>

<style scoped>
.pulse-notification-container {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 99999;
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: none;
  max-width: 360px;
  width: calc(100vw - 48px);
}

.pulse-toast-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 10px 30px -5px rgba(0, 0, 0, 0.08), 
              inset 0 1px 0 rgba(255, 255, 255, 0.6);
  pointer-events: auto;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  will-change: transform, opacity;
}

/* Custom dark mode adjustment via HTML dark class */
:global(html.dark) .pulse-toast-card {
  background: rgba(20, 24, 33, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 12px 40px -10px rgba(0, 0, 0, 0.4), 
              inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

/* Light background glows based on type */
.toast-glow {
  position: absolute;
  top: -30px;
  left: -30px;
  width: 100px;
  height: 100px;
  border-radius: 50%;
  filter: blur(35px);
  opacity: 0.15;
  pointer-events: none;
  z-index: 0;
  transition: background 0.3s;
}

/* Typography styles */
.toast-content {
  flex: 1;
  min-width: 0;
  z-index: 1;
}

.toast-title {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.3;
}

:global(html.dark) .toast-title {
  color: #f1f5f9;
}

.toast-desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.4;
  word-break: break-all;
}

:global(html.dark) .toast-desc {
  color: #94a3b8;
}

/* Custom icon wrapping */
.toast-icon-wrap {
  position: relative;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.toast-icon {
  width: 20px;
  height: 20px;
}

/* Toast Close Button */
.toast-close-btn {
  background: none;
  border: none;
  outline: none;
  padding: 4px;
  margin-top: -2px;
  color: #94a3b8;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-close-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #475569;
}

:global(html.dark) .toast-close-btn:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #cbd5e1;
}

.toast-close-btn svg {
  width: 14px;
  height: 14px;
}

/* Toast Neon progress bar */
.toast-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  height: 3px;
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

/* Types Styling (gradients, glows, borders, active effects) */

/* 1. LIKE Notification (Neon Pink Pulse) */
.pulse-toast-card.is-like {
  border-left: 4px solid #ec4899;
}
.pulse-toast-card.is-like .toast-glow {
  background: #ec4899;
}
.pulse-toast-card.is-like .toast-icon-wrap {
  background: rgba(236, 72, 153, 0.1);
  color: #ec4899;
  border: 1px solid rgba(236, 72, 153, 0.2);
}
.pulse-toast-card.is-like .toast-progress {
  background: linear-gradient(90deg, #ec4899, #f43f5e);
}
.pulse-toast-card.is-like .svg-heart {
  animation: heart-beat 1.2s infinite cubic-bezier(0.215, 0.61, 0.355, 1);
}
@keyframes heart-beat {
  0% { transform: scale(1); }
  14% { transform: scale(1.25); }
  28% { transform: scale(1); }
  42% { transform: scale(1.25); }
  70% { transform: scale(1); }
}

/* 2. COMMENT Notification (Neon Purple Typing) */
.pulse-toast-card.is-comment {
  border-left: 4px solid #8b5cf6;
}
.pulse-toast-card.is-comment .toast-glow {
  background: #8b5cf6;
}
.pulse-toast-card.is-comment .toast-icon-wrap {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
  border: 1px solid rgba(139, 92, 246, 0.2);
}
.pulse-toast-card.is-comment .toast-progress {
  background: linear-gradient(90deg, #8b5cf6, #a78bfa);
}
.pulse-toast-card.is-comment .svg-comment circle {
  animation: typing-dots 1.4s infinite both;
}
.pulse-toast-card.is-comment .svg-comment circle:nth-child(2) {
  animation-delay: 0.2s;
}
.pulse-toast-card.is-comment .svg-comment circle:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes typing-dots {
  0%, 80%, 100% { opacity: 0.2; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-1px); }
}

/* 3. POST Notification (Neon Cyan Flying Jet) */
.pulse-toast-card.is-post {
  border-left: 4px solid #06b6d4;
}
.pulse-toast-card.is-post .toast-glow {
  background: #06b6d4;
}
.pulse-toast-card.is-post .toast-icon-wrap {
  background: rgba(6, 182, 212, 0.1);
  color: #06b6d4;
  border: 1px solid rgba(6, 182, 212, 0.2);
}
.pulse-toast-card.is-post .toast-progress {
  background: linear-gradient(90deg, #06b6d4, #3b82f6);
}
.pulse-toast-card.is-post .svg-post {
  animation: rocket-launch 1.5s ease-in-out infinite;
}
@keyframes rocket-launch {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(2px, -2px) scale(1.05); }
}

/* 4. SUCCESS Notification (Emerald Green Pulse) */
.pulse-toast-card.is-success {
  border-left: 4px solid #10b981;
}
.pulse-toast-card.is-success .toast-glow {
  background: #10b981;
}
.pulse-toast-card.is-success .toast-icon-wrap {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.2);
}
.pulse-toast-card.is-success .toast-progress {
  background: linear-gradient(90deg, #10b981, #059669);
}
.pulse-toast-card.is-success .svg-success {
  animation: success-pulse 1.4s ease-out infinite;
}
@keyframes success-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

/* 5. WARNING Notification (Amber Glow) */
.pulse-toast-card.is-warning {
  border-left: 4px solid #f59e0b;
}
.pulse-toast-card.is-warning .toast-glow {
  background: #f59e0b;
}
.pulse-toast-card.is-warning .toast-icon-wrap {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.2);
}
.pulse-toast-card.is-warning .toast-progress {
  background: linear-gradient(90deg, #f59e0b, #d97706);
}
.pulse-toast-card.is-warning .svg-warning {
  animation: warning-flash 1.5s ease-in-out infinite;
}
@keyframes warning-flash {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* 6. ERROR Notification (Red Flame) */
.pulse-toast-card.is-error {
  border-left: 4px solid #ef4444;
}
.pulse-toast-card.is-error .toast-glow {
  background: #ef4444;
}
.pulse-toast-card.is-error .toast-icon-wrap {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
}
.pulse-toast-card.is-error .toast-progress {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}
.pulse-toast-card.is-error .svg-error {
  animation: error-shake 0.5s ease-in-out infinite alternate;
}
@keyframes error-shake {
  0% { transform: rotate(-3deg); }
  100% { transform: rotate(3deg); }
}

/* 7. INFO / DEFAULT Notification (Classic Pulse Blue) */
.pulse-toast-card.is-info {
  border-left: 4px solid #3b82f6;
}
.pulse-toast-card.is-info .toast-glow {
  background: #3b82f6;
}
.pulse-toast-card.is-info .toast-icon-wrap {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  border: 1px solid rgba(59, 130, 246, 0.2);
}
.pulse-toast-card.is-info .toast-progress {
  background: linear-gradient(90deg, #3b82f6, #1d4ed8);
}
.pulse-toast-card.is-info .svg-info {
  animation: info-pulse 2s infinite ease-in-out;
}
@keyframes info-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.06); opacity: 0.8; }
}

/* High-performance Transition Animations with Scale + Bounce spring */
.pulse-toast-list-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.pulse-toast-list-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1);
  position: absolute; /* for smooth moving transitions of adjacent items */
}
.pulse-toast-list-enter-from {
  opacity: 0;
  transform: scale(0.85) translate3d(80px, 0, 0);
}
.pulse-toast-list-leave-to {
  opacity: 0;
  transform: scale(0.9) translate3d(0, -20px, 0);
}
.pulse-toast-list-move {
  transition: transform 0.3s ease;
}
</style>
