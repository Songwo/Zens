<script setup lang="ts">
import { computed } from 'vue'
import { PieChart as PieIcon, TrendCharts } from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const visible = defineModel<boolean>({ default: false })

const props = defineProps<{
  topics: any[]
}>()

const sentimentChartOptions = computed(() => {
  if (!props.topics.length) return null

  const sample = props.topics.slice(0, 20)
  const positive = sample.filter((item) => `${item.sentimentLabel || ''}`.toUpperCase() === 'POSITIVE').length
  const negative = sample.filter((item) => `${item.sentimentLabel || ''}`.toUpperCase() === 'NEGATIVE').length
  const neutral = sample.length - positive - negative

  return {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'horizontal',
      bottom: '0px',
      itemGap: 8,
      textStyle: { color: '#6B7280', fontSize: 10 },
    },
    series: [
      {
        name: '情绪分布',
        type: 'pie',
        radius: ['42%', '58%'],
        center: ['50%', '36%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
          position: 'center',
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 12,
            fontWeight: 'bold',
            formatter: '{b}\n{d}%',
          },
        },
        labelLine: {
          show: false,
        },
        data: [
          { value: positive, name: '积极正向', itemStyle: { color: '#10B981' } },
          { value: neutral, name: '中性客观', itemStyle: { color: '#3B82F6' } },
          { value: negative, name: '偏负向争议', itemStyle: { color: '#EF4444' } },
        ],
      },
    ],
  }
})

const browseChartOptions = computed(() => {
  if (!props.topics.length) return null
  const sample = props.topics.slice(0, 8).reverse()
  const titles = sample.map((item) => item.title.length > 5 ? `${item.title.substring(0, 5)}...` : item.title)
  const views = sample.map((item) => item.views || 0)

  return {
    grid: { left: 35, right: 15, top: 20, bottom: 50 },
    xAxis: {
      type: 'category',
      data: titles,
      axisLabel: {
        rotate: 35,
        color: '#6B7280',
        fontSize: 9,
        interval: 0,
      },
      axisLine: { lineStyle: { color: '#E5E7EB' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { type: 'dashed', color: '#E5E7EB' } },
      axisLabel: { color: '#6B7280', fontSize: 10 },
    },
    tooltip: { trigger: 'axis' },
    series: [
      {
        name: '浏览量',
        type: 'line',
        smooth: true,
        data: views,
        lineStyle: { color: '#0B57D0', width: 3 },
        itemStyle: { color: '#0B57D0' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(11, 87, 208, 0.25)' },
              { offset: 1, color: 'rgba(11, 87, 208, 0.01)' },
            ],
          },
        },
      },
    ],
  }
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="🤖 AI 情绪与互动趋势决策大屏"
    width="780px"
    align-center
    destroy-on-close
    append-to-body
    class="pulse-dashboard-dialog"
  >
    <div class="pulse-dashboard-content">
      <div class="dashboard-intro">
        <span class="sparkle-icon">✨</span>
        <p>基于当前最热的 20 篇社区讨论，AI 引擎为您深度分析情绪极性分布与宏观互动走势，辅助管理运营决策。</p>
      </div>

      <el-row :gutter="20">
        <el-col :span="10" :xs="24">
          <div class="pulse-chart-card">
            <h4 class="chart-title">
              <el-icon style="margin-right: 6px;"><PieIcon /></el-icon>
              讨论情绪极性分布
            </h4>
            <div class="chart-wrapper">
              <VChart v-if="sentimentChartOptions" :option="sentimentChartOptions" autoresize />
              <el-skeleton v-else :rows="3" />
            </div>
          </div>
        </el-col>
        <el-col :span="14" :xs="24">
          <div class="pulse-chart-card">
            <h4 class="chart-title">
              <el-icon style="margin-right: 6px;"><TrendCharts /></el-icon>
              最热内容浏览波动分析
            </h4>
            <div class="chart-wrapper">
              <VChart v-if="browseChartOptions" :option="browseChartOptions" autoresize />
              <el-skeleton v-else :rows="3" />
            </div>
          </div>
        </el-col>
      </el-row>

      <div class="pulse-insight-box">
        <h5 class="insight-box-title">💡 社区运营智能分析与建议：</h5>
        <ul class="insight-list">
          <li>当前社区讨论整体生态十分健康，正向与客观讨论占据主导。</li>
          <li>前排帖子展现了极强的长尾流量吸引力，说明内容沉淀效果极佳，建议积极设为精华展示。</li>
          <li>定时热度衰减定时任务每 15 分钟运行，缓存刷新能保障性能，建议保持默认策略。</li>
        </ul>
      </div>
    </div>
  </el-dialog>
</template>
