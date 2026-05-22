<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    label?: string
    fallback?: string
  }>(),
  {
    label: '返回上一页',
    fallback: '/',
  }
)

const router = useRouter()

const canGoBack = computed(() => {
  if (typeof window === 'undefined') {
    return false
  }
  const state = window.history.state as { back?: string | null } | null
  return window.history.length > 1 && Boolean(state?.back)
})

const handleBack = () => {
  if (canGoBack.value) {
    router.back()
    return
  }
  router.push(props.fallback)
}
</script>

<template>
  <el-button class="page-back-button" text @click="handleBack">
    <el-icon><ArrowLeft /></el-icon>
    <span>{{ label }}</span>
  </el-button>
</template>

<style scoped>
.page-back-button {
  padding: 0;
  height: auto;
  color: var(--el-text-color-secondary);
  font-weight: 700;
  gap: 6px;
}

.page-back-button:hover {
  color: var(--el-color-primary);
  transform: translateX(-1px);
}

.page-back-button :deep(.el-icon) {
  font-size: 14px;
}
</style>
