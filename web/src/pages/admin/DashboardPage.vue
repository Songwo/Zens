<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  User,
  Document,
  TrendCharts,
  ChatLineRound,
  CaretTop
} from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { statsApi } from '@/api/stats'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent])

const stats = ref({
  totalUsers: 0,
  totalPosts: 0,
  totalComments: 0,
  todayPosts: 0,
})

const loading = ref(true)
const userChartOptions = ref<any>(null)
const postChartOptions = ref<any>(null)

const fetchStats = async () => {
  loading.value = true
  try {
    const res = await statsApi.getSiteStats()
    const d = res.data || res
    stats.value = {
      totalUsers: d.totalUsers ?? 0,
      totalPosts: d.totalPosts ?? 0,
      totalComments: d.totalComments ?? 0,
      todayPosts: d.todayPosts ?? 0,
    }

    // Song：说明
    const [postTrendRes, userTrendRes] = await Promise.all([
      statsApi.getPostTrend().catch(() => ({ data: [] })),
      statsApi.getUserTrend().catch(() => ({ data: [] }))
    ])

    const postData = postTrendRes.data || []
    const userData = userTrendRes.data || []

    // Song：说明
    const extractTrend = (data: {date: string, count: number}[]) => {
      if (!data || data.length === 0) {
        return { dates: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], counts: [0, 0, 0, 0, 0, 0, 0] }
      }
      return {
        dates: data.map(v => v.date.substring(5)), // Song：月-日
        counts: data.map(v => v.count)
      }
    }

    const pTrend = extractTrend(postData)
    const uTrend = extractTrend(userData)

    // Song：说明
    userChartOptions.value = {
      grid: { left: 40, right: 20, top: 20, bottom: 20 },
      xAxis: { type: 'category', show: true, data: uTrend.dates, axisLine: { lineStyle: { color: '#E5E7EB' } }, axisLabel: { color: '#6B7280' } },
      yAxis: { show: true, type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed', color: '#E5E7EB' } }, axisLabel: { color: '#6B7280' } },
      tooltip: { trigger: 'axis' },
      series: [{
        name: '新增用户',
        type: 'line',
        smooth: true,
        showSymbol: true,
        data: uTrend.counts,
        lineStyle: { color: '#F4B400', width: 3 },
        itemStyle: { color: '#F4B400' },
        areaStyle: { color: 'rgba(244, 180, 0, 0.1)' }
      }]
    }

    postChartOptions.value = {
      grid: { left: 40, right: 20, top: 20, bottom: 20 },
      xAxis: { type: 'category', show: true, data: pTrend.dates, axisLine: { lineStyle: { color: '#E5E7EB' } }, axisLabel: { color: '#6B7280' } },
      yAxis: { show: true, type: 'value', minInterval: 1, splitLine: { lineStyle: { type: 'dashed', color: '#E5E7EB' } }, axisLabel: { color: '#6B7280' } },
      tooltip: { trigger: 'axis' },
      series: [{
        name: '发布内容',
        type: 'bar',
        barMaxWidth: 32,
        data: pTrend.counts,
        itemStyle: { color: '#F59E0B', borderRadius: [4, 4, 0, 0] }
      }]
    }

  } catch (error) {
    console.error('Failed to fetch stats:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<template>
  <div class="dashboard-page" v-loading="loading">
    <div class="page-header">
      <h1 class="title">数据看板</h1>
      <p class="subtitle">站点运营数据概览</p>
    </div>

    <!-- Stats Matrix -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" :xs="12">
        <el-card shadow="never" class="stat-card">
          <div class="stat-top">
            <div class="stat-icon blue"><el-icon><User /></el-icon></div>
            <div class="stat-trend up"><el-icon><CaretTop /></el-icon></div>
          </div>
          <div class="stat-bottom">
            <div class="stat-value">{{ stats.totalUsers }}</div>
            <div class="stat-label">总用户数</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6" :xs="12">
        <el-card shadow="never" class="stat-card">
          <div class="stat-top">
            <div class="stat-icon purple"><el-icon><Document /></el-icon></div>
            <div class="stat-trend up"><el-icon><CaretTop /></el-icon></div>
          </div>
          <div class="stat-bottom">
            <div class="stat-value">{{ stats.totalPosts }}</div>
            <div class="stat-label">总帖子数</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6" :xs="12">
        <el-card shadow="never" class="stat-card">
          <div class="stat-top">
            <div class="stat-icon green"><el-icon><ChatLineRound /></el-icon></div>
            <div class="stat-trend up"><el-icon><CaretTop /></el-icon></div>
          </div>
          <div class="stat-bottom">
            <div class="stat-value">{{ stats.totalComments }}</div>
            <div class="stat-label">总评论数</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6" :xs="12">
        <el-card shadow="never" class="stat-card">
          <div class="stat-top">
            <div class="stat-icon orange"><el-icon><TrendCharts /></el-icon></div>
          </div>
          <div class="stat-bottom">
            <div class="stat-value">{{ stats.todayPosts }}</div>
            <div class="stat-label">今日发帖</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Visual Analytics -->
    <el-row :gutter="20" class="charts-row">
      <el-col :span="12" :xs="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-header">
              <el-icon><TrendCharts /></el-icon>
              <span>用户增长趋势</span>
            </div>
          </template>
          <div class="chart-box">
            <VChart v-if="userChartOptions" :option="userChartOptions" autoresize />
            <el-skeleton v-else :rows="4" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" :xs="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="chart-header">
              <el-icon><Document /></el-icon>
              <span>内容发布趋势</span>
            </div>
          </template>
          <div class="chart-box">
            <VChart v-if="postChartOptions" :option="postChartOptions" autoresize />
            <el-skeleton v-else :rows="4" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 0;
}

.page-header {
  margin-bottom: 32px;
}

.page-header .title {
  margin: 0;
  font-size: 28px;
  font-weight: 900;
  color: var(--el-text-color-primary);
  letter-spacing: -0.02em;
}

.page-header .subtitle {
  margin: 4px 0 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
}

.stat-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.blue { background-color: var(--el-color-primary-light-9); color: var(--el-color-primary-dark-2); }
.stat-icon.purple { background-color: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.stat-icon.green { background-color: var(--el-color-success-light-9); color: var(--el-color-success); }
.stat-icon.orange { background-color: #FFF7ED; color: #F59E0B; }

.stat-trend {
  font-size: 12px;
  font-weight: 800;
  padding: 4px 8px;
  border-radius: 6px;
}

.stat-trend.up { background-color: var(--el-color-success-light-9); color: var(--el-color-success); }

.stat-value {
  font-size: 32px;
  font-weight: 900;
  color: var(--el-text-color-primary);
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.chart-card {
  border-radius: 12px;
}

.chart-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 14px;
}

.chart-box {
  height: 200px;
  width: 100%;
}

@media (max-width: 768px) {
  .stat-card {
    margin-bottom: 12px;
  }
}
</style>
