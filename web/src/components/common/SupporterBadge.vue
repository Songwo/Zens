<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ tier?: 'SUPPORTER' | 'PLUS' | string; expiresAt?: string }>()
const isPlus = computed(() => props.tier === 'PLUS')
const label = computed(() => isPlus.value ? '共建支持者' : 'Zens 支持者')
const expiryText = computed(() => props.expiresAt
  ? `有效期至 ${new Date(props.expiresAt).toLocaleDateString('zh-CN')}`
  : '有效支持者身份')
</script>

<template>
  <el-tooltip :content="expiryText" placement="top">
    <span class="supporter-badge" :class="{ plus: isPlus }">
      <span aria-hidden="true">✦</span>{{ label }}
    </span>
  </el-tooltip>
</template>

<style scoped>
.supporter-badge { display:inline-flex; align-items:center; gap:4px; padding:2px 8px; border-radius:999px;
  font-size:11px; font-weight:700; color:#76520f; background:#f8efd8; border:1px solid #dec68e; }
.supporter-badge.plus { color:#59417a; background:#eee8f6; border-color:#c7b6dc; }
</style>
