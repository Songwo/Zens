<script setup lang="ts">
import { ref } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import type { PostSearchRequest } from '@/types'

const timeRange = ref<'TODAY' | 'WEEK' | 'MONTH'>('TODAY')
const topicListRef = ref<any>(null)

const query = ref<PostSearchRequest>({
  orderBy: 'hot',
  timeRange: 'TODAY'
})

const handleRangeChange = (val: 'TODAY' | 'WEEK' | 'MONTH') => {
  timeRange.value = val
  query.value = {
    ...query.value,
    timeRange: val
  }
  // Song：说明
  if (topicListRef.value) {
    topicListRef.value.refreshLatest()
  }
}
</script>

<template>
  <MainLayout>
    <div class="hot-page">
      <div class="page-header">
        <div class="title-section">
          <h1 class="page-title">🔥 热门排行</h1>
          <p class="page-desc">全站最受关注的内容，根据互动指数实时计算</p>
        </div>
        
        <div class="filter-controls">
          <el-radio-group v-model="timeRange" size="large" @change="handleRangeChange">
            <el-radio-button label="TODAY">今日热榜</el-radio-button>
            <el-radio-button label="WEEK">本周最热</el-radio-button>
            <el-radio-button label="MONTH">月度精华</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <TopicList ref="topicListRef" :default-query="query" hide-filters />
    </div>
  </MainLayout>
</template>

<style scoped>
.hot-page {
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 24px;
  background: var(--el-bg-color-overlay);
  padding: 24px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 20px;
}

.page-title {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.page-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
