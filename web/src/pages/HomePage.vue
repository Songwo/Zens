<script setup lang="ts">
import { onMounted, ref } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import { publicDataApi, type PublicSiteStats } from '@/api/publicData'

const siteStats = ref<PublicSiteStats>({
  totalPosts: 0,
  totalUsers: 0,
  totalComments: 0,
  todayPosts: 0,
})

onMounted(async () => {
  try {
    const res = await publicDataApi.getHomeBootstrapCached(10, 5, 'WEEK')
    if (res.code === 2000 && res.data?.siteStats) {
      siteStats.value = res.data.siteStats
    }
  } catch {
    // ignore bootstrap failure on hero
  }
})
</script>

<template>
  <MainLayout>
    <div class="page-content">
      <section class="hero-panel">
        <div class="hero-copy">
          <span class="hero-kicker">技术社区智能分析决策论坛系统</span>
          <h1 class="hero-title">基于社区行为数据，识别热点、辅助治理、支持运营决策</h1>
          <p class="hero-desc">
            系统结合帖子浏览、评论回复、趋势变化与内容治理流程，对社区活跃主题进行动态分析，
            为用户发现高价值内容，也为管理员提供决策依据。
          </p>
        </div>
        <div class="hero-metrics">
          <div class="metric-pill">
            <span class="metric-label">累计帖子</span>
            <strong class="metric-value">{{ siteStats.totalPosts }}</strong>
          </div>
          <div class="metric-pill">
            <span class="metric-label">活跃用户</span>
            <strong class="metric-value">{{ siteStats.totalUsers }}</strong>
          </div>
          <div class="metric-pill">
            <span class="metric-label">累计互动</span>
            <strong class="metric-value">{{ siteStats.totalComments }}</strong>
          </div>
          <div class="metric-pill">
            <span class="metric-label">今日新帖</span>
            <strong class="metric-value">{{ siteStats.todayPosts }}</strong>
          </div>
        </div>
      </section>

      <TopicList />
    </div>
  </MainLayout>
</template>

<style scoped>
.page-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(260px, 0.9fr);
  gap: 20px;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid var(--el-border-color-lighter);
  background:
    radial-gradient(circle at top right, var(--el-color-primary-light-9), transparent 38%),
    linear-gradient(180deg, var(--el-bg-color-overlay), var(--el-fill-color-extra-light));
}

.hero-copy {
  min-width: 0;
}

.hero-kicker {
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary-dark-2);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.hero-title {
  margin: 14px 0 10px;
  font-size: 28px;
  line-height: 1.35;
  color: var(--el-text-color-primary);
}

.hero-desc {
  margin: 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}

.hero-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  align-content: start;
}

.metric-pill {
  min-height: 88px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--el-border-color-lighter);
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: var(--el-text-color-primary);
}

.metric-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-value {
  font-size: 24px;
  line-height: 1;
}

@media (max-width: 900px) {
  .hero-panel {
    grid-template-columns: 1fr;
  }

  .hero-title {
    font-size: 24px;
  }
}
</style>
