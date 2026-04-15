<script setup lang="ts">
import { computed, ref } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import type { PostSearchRequest } from '@/types'

const timeRange = ref<'TODAY' | 'WEEK' | 'MONTH'>('TODAY')
const topicListRef = ref<any>(null)

const query = ref<PostSearchRequest>({
  orderBy: 'hot',
  timeRange: 'TODAY'
})

const rangeInsightMap = {
  TODAY: {
    title: '今日行为热榜',
    desc: '聚焦今天发生真实互动的帖子，只统计当日浏览与评论回复活跃内容。',
    decision: '适合快速判断社区当下最受关注的问题与技术议题。'
  },
  WEEK: {
    title: '本周决策热榜',
    desc: '按自然周汇总近期持续活跃的话题，识别更稳定的讨论趋势。',
    decision: '适合观察一周内的技术热点演化和内容运营重点。'
  },
  MONTH: {
    title: '本月趋势洞察',
    desc: '按自然月追踪高活跃主题，筛出长期具备讨论价值的内容。',
    decision: '适合用于专题复盘、知识沉淀和社区治理决策。'
  }
} as const

const currentInsight = computed(() => rangeInsightMap[timeRange.value])

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
          <div class="page-kicker">智能分析决策</div>
          <h1 class="page-title">{{ currentInsight.title }}</h1>
          <p class="page-desc">{{ currentInsight.desc }}</p>
        </div>
        
        <div class="filter-controls">
          <el-radio-group v-model="timeRange" size="large" @change="handleRangeChange">
            <el-radio-button label="TODAY">今日热榜</el-radio-button>
            <el-radio-button label="WEEK">本周最热</el-radio-button>
            <el-radio-button label="MONTH">月度精华</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div class="insight-panel">
        <div class="insight-card">
          <span class="insight-label">分析口径</span>
          <strong>仅统计当前周期内发生行为的帖子</strong>
        </div>
        <div class="insight-card">
          <span class="insight-label">行为信号</span>
          <strong>浏览 + 评论/回复 联合驱动热度排序</strong>
        </div>
        <div class="insight-card">
          <span class="insight-label">决策价值</span>
          <strong>{{ currentInsight.decision }}</strong>
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

.page-kicker {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  margin-bottom: 12px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
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

.insight-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.insight-card {
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(180deg, var(--el-fill-color-extra-light), var(--el-bg-color-overlay));
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.insight-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  letter-spacing: 0.04em;
}

.insight-card strong {
  color: var(--el-text-color-primary);
  line-height: 1.6;
  font-size: 14px;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .insight-panel {
    grid-template-columns: 1fr;
  }
}
</style>

