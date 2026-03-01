<script setup lang="ts">
import { MagicStick, Close } from '@element-plus/icons-vue'
import { ref } from 'vue'

const isVisible = ref(true)

const closeCard = () => {
  isVisible.value = false
}
</script>

<template>
  <el-card v-if="isVisible" class="explain-card" shadow="never">
    <div class="card-top">
      <div class="title-wrap">
        <el-icon class="magic-icon"><MagicStick /></el-icon>
        <span class="title-text">智能推荐引擎</span>
      </div>
      <el-button text circle size="small" @click="closeCard" class="close-btn">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>
    
    <div class="explain-body">
      <p class="desc">当前内容流基于你的浏览偏好与近期互动生成。</p>
      
      <div class="tags-row">
        <span class="label">你的核心兴趣：</span>
        <div class="tags">
          <el-tag size="small" type="primary" effect="light">#Vue3</el-tag>
          <el-tag size="small" type="primary" effect="light">#职场吐槽</el-tag>
        </div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.explain-card {
  background: linear-gradient(145deg, var(--el-color-primary-light-9) 0%, #ffffff 100%);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: var(--el-border-radius-base);
}

.explain-card :deep(.el-card__body) {
  padding: 16px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.magic-icon {
  color: var(--el-color-primary);
  font-size: 18px;
}

.title-text {
  font-size: 14px;
  font-weight: bold;
  color: var(--el-color-primary);
}

.close-btn {
  margin-top: -4px;
  margin-right: -8px;
  color: var(--el-text-color-placeholder);
}

.close-btn:hover {
  color: var(--el-text-color-primary);
}

.desc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  margin: 0 0 12px 0;
}

.tags-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.label {
  color: var(--el-text-color-secondary);
}

.tags {
  display: flex;
  gap: 6px;
}
</style>
