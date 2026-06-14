<script setup lang="ts">
import { computed } from 'vue'
import { TRUST_LEVELS, trustLevelLabel } from '@/utils/trustLevel'

const props = defineProps({
  /** 信任等级 0-4 */
  trustLevel: {
    type: Number,
    default: 0,
  },
  /** 是否显示文字（默认显示） */
  showText: {
    type: Boolean,
    default: true,
  },
})

const visible = computed(() => (props.trustLevel ?? 0) > 0)
const spec = computed(() => {
  const level = Math.max(0, Math.min(4, props.trustLevel ?? 0))
  return TRUST_LEVELS[level]!
})
const tooltip = computed(() => `信任等级 TL${spec.value.level} · ${trustLevelLabel(props.trustLevel)}`)
</script>

<template>
  <el-tooltip
    v-if="visible"
    :content="tooltip"
    placement="top"
    effect="dark"
  >
    <div class="trust-badge" :style="{ '--badge-color': spec!.color }">
      <span class="badge-text">{{ showText ? `TL${spec!.level}` : `TL${spec!.level}` }}</span>
    </div>
  </el-tooltip>
</template>

<style scoped>
.trust-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.3px;
  cursor: default;
  margin-left: 6px;
  user-select: none;
  vertical-align: middle;
  background: var(--badge-color);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.2);
}

:deep(html.dark) .trust-badge {
  border-color: rgba(255, 255, 255, 0.1);
  filter: brightness(0.9);
}
</style>
