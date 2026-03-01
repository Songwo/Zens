<script setup lang="ts">
import { User } from '@element-plus/icons-vue'
import { computed } from 'vue'

interface Props {
  src?: string
  alt?: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | number
}

const props = withDefaults(defineProps<Props>(), {
  size: 'md',
  alt: 'User avatar'
})

const avatarSize = computed(() => {
  if (typeof props.size === 'number') return props.size
  const sizeMap: Record<string, number> = {
    xs: 24,
    sm: 32,
    md: 40,
    lg: 48,
    xl: 64
  }
  return sizeMap[props.size] || 40
})
</script>

<template>
  <el-avatar
    :src="src"
    :size="avatarSize"
    class="custom-avatar"
  >
    <el-icon :size="avatarSize / 2"><User /></el-icon>
  </el-avatar>
</template>

<style scoped>
.custom-avatar {
  background-color: var(--el-fill-color-darker);
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}

:deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
