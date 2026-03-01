<script setup lang="ts">
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import type { PostSearchRequest } from '@/types'

const query: PostSearchRequest = {
  isFeatured: true,
  orderBy: 'new'
}
</script>

<template>
  <MainLayout>
    <div class="featured-page">
      <div class="page-header">
        <div class="title-section">
          <h1 class="page-title">✨ 精华汇总</h1>
          <p class="page-desc">经过社区管理员精挑细选的深度好文与技术干货</p>
        </div>
      </div>

      <TopicList :default-query="query" hide-filters />
    </div>
  </MainLayout>
</template>

<style scoped>
.featured-page {
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-bg-color-overlay) 100%);
  padding: 32px 24px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  position: relative;
  overflow: hidden;
}

.page-header::after {
  content: 'BEST';
  position: absolute;
  right: -20px;
  bottom: -20px;
  font-size: 120px;
  font-weight: 900;
  color: var(--el-color-primary-light-8);
  opacity: 0.3;
  pointer-events: none;
  font-family: inherit;
  font-style: italic;
}

.page-title {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  position: relative;
  z-index: 1;
}

.page-desc {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 16px;
  position: relative;
  z-index: 1;
}
</style>
