<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title?: string
  description?: string
  imageSize?: number
  icon?: Component
}>()
</script>

<template>
  <div class="empty-state-wrapper">
    <div v-if="icon" class="empty-icon">
      <el-icon :size="48" class="empty-icon-el"><component :is="icon" /></el-icon>
    </div>
    <el-empty
      :description="description || '暂无数据'"
      :image-size="icon ? 0 : (imageSize || 120)"
    />
  </div>
</template>

<style scoped>
.empty-state-wrapper {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: var(--el-bg-color);
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
}
.empty-icon {
  margin-bottom: 8px;
}
.empty-icon-el {
  color: var(--el-text-color-placeholder);
}
</style>
