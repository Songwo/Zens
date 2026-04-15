<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import { trendsApi } from '@/api/trends'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import { 
  Sunny, 
  TrendCharts, 
  DataAnalysis, 
  CaretTop, 
  Minus, 
  Lightning 
} from '@element-plus/icons-vue'

use([CanvasRenderer, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const loading = ref(true)
const heatRank = ref<any[]>([])
const predictions = ref<any[]>([])
const trendDays = ref<'7' | '30'>('7')

// Dashboard real metrics
const dashboardMetrics = ref({ todayPosts: 0, totalUsers: 0, todayUsers: 0 })

// Song：说明
const pieOptions = ref<any>(null)
const lineOptions = ref<any>(null)

const fetchData = async () => {
  try {
    const days = Number(trendDays.value)
    const [trendRes, pieRes, rankRes, predictRes, dashRes] = await Promise.all([
      trendsApi.getPostTrend(days),
      trendsApi.getSectionPie(),
      trendsApi.getHeatRank(),
      trendsApi.getPrediction(),
      trendsApi.getDashboard()
    ])

    heatRank.value = rankRes.data || []
    predictions.value = predictRes.data || []
    if (dashRes.data) {
      dashboardMetrics.value = {
        todayPosts: dashRes.data.todayPosts ?? 0,
        totalUsers: dashRes.data.totalUsers ?? 0,
        todayUsers: dashRes.data.todayUsers ?? 0
      }
    }

    // Song：说明
    const pieData = Object.entries(pieRes.data).map(([name, value]) => ({ name, value }))
    pieOptions.value = {
      color: ['#1e88e5', '#42a5f5', '#90caf9', '#bbdefb', '#e3f2fd'], 
      tooltip: { 
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)'
      },
      legend: { 
        bottom: '0%', 
        left: 'center',
        icon: 'circle',
        textStyle: { fontSize: 12 }
      },
      series: [
        {
          name: '分区占比',
          type: 'pie',
          radius: ['50%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { 
            borderRadius: 8, 
            borderColor: '#fff', 
            borderWidth: 2 
          },
          label: { show: false },
          data: pieData
        }
      ]
    }

    // Song：说明
    const dates = trendRes.data.map((item: any) => item.date || item.statDate)
    const counts = trendRes.data.map((item: any) => item.count || item.totalPosts)
    
    lineOptions.value = {
      tooltip: { 
        trigger: 'axis'
      },
      grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
      xAxis: { 
        type: 'category', 
        boundaryGap: false, 
        data: dates,
        axisLine: { lineStyle: { color: '#E4E7ED' } }
      },
      yAxis: { 
        type: 'value',
        splitLine: { lineStyle: { type: 'dashed', color: '#F2F6FC' } }
      },
      series: [
        {
          name: '发帖量',
          type: 'line',
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 3, color: '#1e88e5' },
          areaStyle: { 
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(30, 136, 229, 0.2)' },
                { offset: 1, color: 'rgba(30, 136, 229, 0)' }
              ]
            }
          },
          data: counts
        }
      ]
    }

  } catch (error) {
    ElMessage.error('获取趋势数据失败')
  } finally {
    loading.value = false
  }
}

watch(trendDays, () => fetchData())

onMounted(() => {
  fetchData()
})
</script>

<template>
  <MainLayout>
    <div class="trends-container">
      <div class="trends-header">
        <h1 class="page-title">社区趋势分析与决策支持</h1>
        <p class="page-subtitle">BEHAVIOR-DRIVEN INSIGHT · TREND ANALYSIS · DECISION SUPPORT</p>
      </div>

      <!-- Metrics Widgets -->
      <div class="metrics-grid">
        <el-card shadow="never" class="metric-card">
          <div class="metric-content">
            <div class="metric-label">今日发帖</div>
            <div class="metric-value">{{ dashboardMetrics.todayPosts.toLocaleString() }}</div>
            <div class="metric-trend up">
              <el-icon><CaretTop /></el-icon> <small>内容供给实时</small>
            </div>
          </div>
          <el-icon class="metric-icon blue"><DataAnalysis /></el-icon>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-content">
            <div class="metric-label">总注册用户</div>
            <div class="metric-value">{{ dashboardMetrics.totalUsers.toLocaleString() }}</div>
            <div class="metric-trend up">
              <el-icon><CaretTop /></el-icon> <small>今日新增 {{ dashboardMetrics.todayUsers }}</small>
            </div>
          </div>
          <el-icon class="metric-icon indigo"><Lightning /></el-icon>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-content">
            <div class="metric-label">今日新增用户</div>
            <div class="metric-value">{{ dashboardMetrics.todayUsers.toLocaleString() }}</div>
            <div class="metric-trend flat">
              <el-icon><Minus /></el-icon> <small>用户增长实时</small>
            </div>
          </div>
          <el-icon class="metric-icon orange"><Sunny /></el-icon>
        </el-card>
      </div>

      <!-- Main Charts -->
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <div class="header-left">
              <el-icon><TrendCharts /></el-icon>
              <span>内容供给趋势分析</span>
            </div>
            <el-radio-group size="small" v-model="trendDays">
              <el-radio-button label="7" value="7">近 7 天</el-radio-button>
              <el-radio-button label="30" value="30">近 30 天</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div class="chart-wrapper line-chart">
          <VChart v-if="lineOptions" :option="lineOptions" autoresize />
          <el-skeleton v-else :rows="5" animated />
        </div>
      </el-card>

      <div class="trends-grid">
        <!-- Prediction Table -->
        <el-card shadow="never" class="grid-card">
          <template #header>
             <div class="card-header">
               <div class="header-left">
                 <el-icon><Lightning /></el-icon>
                 <span>智能话题预测</span>
               </div>
               <el-tag size="small" effect="dark" type="success">决策辅助</el-tag>
             </div>
          </template>
          <el-table :data="predictions.slice(0, 5)" style="width: 100%" size="small">
            <el-table-column prop="topic" label="潜在话题" width="120">
              <template #default="{ row }">
                <span class="topic-name">#{{ row.topic }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="growthRate" label="热度增长" width="100">
              <template #default="{ row }">
                <span class="growth-plus">+{{ row.growthRate }}%</span>
              </template>
            </el-table-column>
            <el-table-column prop="insight" label="趋势洞察" />
          </el-table>
        </el-card>

        <!-- Category Distribution -->
        <el-card shadow="never" class="grid-card">
          <template #header>
            <div class="card-header">
              <span>板块内容结构分布</span>
            </div>
          </template>
          <div class="chart-wrapper pie-chart">
            <VChart v-if="pieOptions" :option="pieOptions" autoresize />
            <el-skeleton v-else :rows="5" animated />
          </div>
        </el-card>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.trends-container {
  max-width: 1000px;
  margin: 0 auto;
}

.trends-header {
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 4px 0 0 0;
  font-size: 10px;
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  letter-spacing: 0.1em;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.metric-card {
  border-radius: var(--el-border-radius-base);
}

.metric-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
}

.metric-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 28px;
  font-family: var(--el-font-family-mono);
  font-weight: 800;
  color: var(--el-text-color-primary);
  line-height: 1;
}

.metric-trend {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  margin-top: 8px;
}

.metric-trend.up { color: var(--el-color-success); }
.metric-trend.flat { color: var(--el-text-color-placeholder); }

.metric-trend small {
  color: var(--el-text-color-placeholder);
  font-weight: normal;
  margin-left: 4px;
}

.metric-icon {
  font-size: 32px;
  opacity: 0.8;
}
.metric-icon.blue { color: var(--el-color-primary); }
.metric-icon.indigo { color: #6366f1; }
.metric-icon.orange { color: var(--el-color-warning); }

.chart-card {
  margin-bottom: 24px;
  border-radius: var(--el-border-radius-base);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 14px;
}

.chart-wrapper {
  width: 100%;
}

.line-chart {
  height: 350px;
}

.trends-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 24px;
}

.grid-card {
  border-radius: var(--el-border-radius-base);
}

.topic-name {
  font-family: var(--el-font-family-mono);
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.growth-plus {
  font-family: var(--el-font-family-mono);
  font-weight: 700;
  color: var(--el-color-success);
  background-color: var(--el-color-success-light-9);
  padding: 2px 6px;
  border-radius: 4px;
}

.pie-chart {
  height: 280px;
}

@media (max-width: 992px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
  .trends-grid {
    grid-template-columns: 1fr;
  }
}
</style>
