<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChatDotRound, CollectionTag, DataAnalysis, Refresh, Timer, View } from '@element-plus/icons-vue'
import { supporterInsightsApi, type SupporterCreatorInsights } from '@/api/supporterInsights'

const props = withDefaults(defineProps<{ days?: number }>(), { days: 30 })

const loading = ref(false)
const errorMessage = ref('')
const insights = ref<SupporterCreatorInsights | null>(null)

const hasData = computed(() => {
  const summary = insights.value?.summary
  return Boolean(summary && (
    summary.publishedPosts || summary.totalViews || summary.totalLikes ||
    summary.totalCollects || summary.totalComments
  ))
})

const maxDailyActivity = computed(() => Math.max(1, ...(insights.value?.trend || []).map(item =>
  item.views + item.likes + item.collects + item.comments)))

const recentTrend = computed(() => (insights.value?.trend || []).slice(-14))

const metrics = computed(() => {
  const summary = insights.value?.summary
  return [
    { label: '已发布内容', value: summary?.publishedPosts ?? 0, suffix: '篇', icon: DataAnalysis },
    { label: '累计浏览', value: summary?.totalViews ?? 0, suffix: '次', icon: View },
    { label: '收到点赞', value: summary?.totalLikes ?? 0, suffix: '次', icon: ChatDotRound },
    { label: '收到收藏', value: summary?.totalCollects ?? 0, suffix: '次', icon: CollectionTag },
    { label: '收到评论', value: summary?.totalComments ?? 0, suffix: '条', icon: ChatDotRound },
    { label: '平均停留', value: summary?.avgDwellSec ?? 0, suffix: '秒', icon: Timer },
  ]
})

const activityHeight = (item: SupporterCreatorInsights['trend'][number]) => {
  const activity = item.views + item.likes + item.collects + item.comments
  return `${Math.max(activity ? 8 : 2, Math.round(activity / maxDailyActivity.value * 100))}%`
}

const formatDate = (date: string) => date.slice(5).replace('-', '/')
const formatNumber = (value: number) => Number(value || 0).toLocaleString('zh-CN')

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await supporterInsightsApi.get(props.days)
    if (response.code !== 2000 || !response.data) {
      throw new Error(response.message || '创作数据简报加载失败')
    }
    insights.value = response.data
  } catch (error: any) {
    insights.value = null
    errorMessage.value = error?.response?.data?.message || error?.message || '创作数据简报加载失败'
  } finally {
    loading.value = false
  }
}

defineExpose({ refresh: load })
onMounted(load)
</script>

<template>
  <section class="insights-panel" aria-labelledby="supporter-insights-title">
    <header class="panel-header">
      <div>
        <p class="eyebrow">private creator brief</p>
        <h2 id="supporter-insights-title">我的创作数据简报</h2>
        <p>只统计你本人已公开内容的汇总表现，不参与推荐排序或审核决策。</p>
      </div>
      <el-button :icon="Refresh" round :loading="loading" @click="load">刷新</el-button>
    </header>

    <div v-if="errorMessage && !loading" class="state-card is-error">
      <strong>暂时无法读取简报</strong>
      <span>{{ errorMessage }}</span>
      <el-button type="primary" link @click="load">重新加载</el-button>
    </div>

    <template v-else>
      <div class="metric-grid" v-loading="loading">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <el-icon><component :is="metric.icon" /></el-icon>
          <span>{{ metric.label }}</span>
          <strong>{{ formatNumber(metric.value) }}<small>{{ metric.suffix }}</small></strong>
        </article>
      </div>

      <div v-if="insights && hasData" class="trend-card">
        <div class="trend-head">
          <div>
            <strong>最近 {{ Math.min(14, insights.trend.length) }} 天互动趋势</strong>
            <span>{{ insights.fromDate }} 至 {{ insights.toDate }} · 统计周期 {{ insights.days }} 天</span>
          </div>
          <span class="privacy-chip">仅自己可见</span>
        </div>
        <div class="bars" role="img" aria-label="最近创作互动趋势柱状图">
          <div v-for="item in recentTrend" :key="item.date" class="bar-column">
            <el-tooltip
              :content="`${item.date}：浏览 ${item.views}、点赞 ${item.likes}、收藏 ${item.collects}、评论 ${item.comments}、平均停留 ${item.avgDwellSec} 秒`"
              placement="top"
            >
              <span class="bar-track"><span class="bar-fill" :style="{ height: activityHeight(item) }" /></span>
            </el-tooltip>
            <small>{{ formatDate(item.date) }}</small>
          </div>
        </div>
      </div>

      <div v-else-if="insights && !loading" class="state-card">
        <el-icon><DataAnalysis /></el-icon>
        <strong>还没有可汇总的公开内容</strong>
        <span>发布第一篇内容并产生真实互动后，这里会自动形成你的私有创作简报。</span>
      </div>
    </template>
  </section>
</template>

<style scoped>
.insights-panel { margin-top: 24px; padding: 26px; border: 1px solid var(--el-border-color-lighter); border-radius: 22px; background: var(--el-bg-color-overlay); }
.panel-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
.eyebrow { margin: 0 0 6px; color: #9a6b00; font-size: 11px; font-weight: 900; letter-spacing: .1em; text-transform: uppercase; }
.panel-header h2 { margin: 0; color: var(--el-text-color-primary); font-size: 22px; }
.panel-header p:last-child { margin: 8px 0 0; color: var(--el-text-color-secondary); line-height: 1.65; }
.metric-grid { min-height: 116px; margin-top: 20px; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.metric-card { min-width: 0; padding: 16px; border-radius: 16px; background: var(--el-fill-color-light); display: grid; grid-template-columns: 24px 1fr; gap: 7px 9px; align-items: center; }
.metric-card > .el-icon { color: #a87300; font-size: 19px; }
.metric-card > span { color: var(--el-text-color-secondary); font-size: 13px; }
.metric-card strong { grid-column: 1 / -1; color: var(--el-text-color-primary); font-size: 26px; line-height: 1; }
.metric-card small { margin-left: 4px; color: var(--el-text-color-secondary); font-size: 12px; font-weight: 600; }
.trend-card { margin-top: 18px; padding: 18px; border: 1px solid var(--el-border-color-lighter); border-radius: 17px; }
.trend-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.trend-head div { display: flex; flex-direction: column; gap: 5px; }
.trend-head strong { color: var(--el-text-color-primary); }
.trend-head span { color: var(--el-text-color-secondary); font-size: 12px; }
.privacy-chip { flex: 0 0 auto; padding: 5px 9px; border-radius: 999px; background: #fff4cf; color: #765500 !important; font-weight: 800; }
.bars { height: 150px; margin-top: 18px; display: grid; grid-template-columns: repeat(14, minmax(12px, 1fr)); gap: 8px; align-items: end; overflow: hidden; }
.bar-column { min-width: 0; height: 100%; display: flex; flex-direction: column; align-items: center; gap: 7px; }
.bar-track { width: min(18px, 80%); flex: 1; display: flex; align-items: end; border-radius: 999px; background: var(--el-fill-color-light); overflow: hidden; }
.bar-fill { display: block; width: 100%; min-height: 2px; border-radius: inherit; background: linear-gradient(180deg, #f4b400, #d97706); transition: height .25s ease; }
.bar-column small { color: var(--el-text-color-placeholder); font-size: 10px; white-space: nowrap; }
.state-card { margin-top: 20px; min-height: 132px; padding: 24px; border-radius: 17px; background: var(--el-fill-color-light); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px; text-align: center; color: var(--el-text-color-secondary); }
.state-card > .el-icon { font-size: 28px; color: #a87300; }.state-card strong { color: var(--el-text-color-primary); }.state-card.is-error { background: var(--el-color-danger-light-9); }
@media (max-width: 760px) { .insights-panel { padding: 20px 16px; }.panel-header { flex-direction: column; }.metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.bars { gap: 4px; }.bar-column small { transform: rotate(-45deg); transform-origin: center; margin-top: 4px; } }
@media (max-width: 420px) { .metric-grid { grid-template-columns: 1fr; }.trend-head { flex-direction: column; } }
</style>
