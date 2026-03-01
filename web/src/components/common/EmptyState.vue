<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
  imageSize?: number
}>()
</script>

<template>
  <div class="empty-state-wrapper">
    <el-empty 
      :description="description || '暂无数据'" 
      :image-size="imageSize || 120" 
    />
  </div>
</template>

<style scoped>
.empty-state-wrapper {
  padding: 40px 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: var(--el-bg-color);
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
}
</style>
