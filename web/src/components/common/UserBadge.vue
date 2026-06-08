<script setup lang="ts">
import { computed } from 'vue'

/**
 * 用户徽章：管理员授予的纯文字 flair（如「你可以访问L站」），跟随用户到处显示。
 * - effect='rainbow' → 七彩跑马动效（忽略 color）
 * - 否则纯色：用 color（hex），未给则用默认色（含暗色适配）
 * 空文本不渲染。文本经 Vue 插值自动转义，无 XSS 风险。
 */
const props = defineProps({
  text: { type: String, default: '' },
  color: { type: String, default: '' },
  effect: { type: String, default: 'solid' }
})

const display = computed(() => (props.text || '').trim())
const isRainbow = computed(() => props.effect === 'rainbow')
const colorStyle = computed(() =>
  !isRainbow.value && props.color ? { color: props.color } : {}
)
</script>

<template>
  <span
    v-if="display"
    class="user-badge"
    :class="{ 'ub-rainbow': isRainbow }"
    :style="colorStyle"
    :title="display"
  >{{ display }}</span>
</template>

<style scoped>
.user-badge {
  display: inline-flex;
  align-items: center;
  max-width: 16em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  color: #6d28d9;
  margin-left: 6px;
  vertical-align: middle;
  user-select: none;
}

html.dark .user-badge {
  color: #a78bfa;
}

/* 七彩跑马：彩虹渐变在文字里持续流动 */
.ub-rainbow {
  font-weight: 700;
  background: linear-gradient(90deg, #ef4444, #f59e0b, #eab308, #22c55e, #3b82f6, #a855f7, #ef4444);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  animation: ub-rainbow-flow 2s linear infinite;
}

@keyframes ub-rainbow-flow {
  to { background-position: 200% center; }
}

/* 尊重「减少动态效果」的系统设置 */
@media (prefers-reduced-motion: reduce) {
  .ub-rainbow { animation: none; }
}
</style>
