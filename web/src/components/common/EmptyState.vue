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
      :description="undefined"
      :image-size="icon ? 0 : (imageSize || 120)"
    >
      <template #description>
        <div class="empty-copy">
          <strong v-if="title" class="empty-title">{{ title }}</strong>
          <p class="empty-desc">{{ description || '暂无数据' }}</p>
        </div>
      </template>
      <slot />
    </el-empty>
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

.empty-copy {
  display: grid;
  gap: 8px;
  text-align: center;
}

.empty-title {
  color: var(--el-text-color-primary);
  font-size: 18px;
  line-height: 1.4;
}

.empty-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.7;
}
</style>
