<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { trendsApi } from '@/api/trends'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { toast } from 'vue-sonner'
import { Flame, TrendingUp, BarChart2, Activity, ArrowUp, ArrowDown, Minus, Zap } from 'lucide-vue-next'

use([CanvasRenderer, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const loading = ref(true)
const heatRank = ref<any[]>([])
const predictions = ref<any[]>([])

// Chart Options
const pieOptions = ref<any>(null)
const lineOptions = ref<any>(null)

const fetchData = async () => {
  try {
    const [cloudRes, trendRes, pieRes, rankRes, predictRes] = await Promise.all([
      trendsApi.getKeywordCloud(),
      trendsApi.getPostTrend(),
      trendsApi.getCategoryPie(),
      trendsApi.getHeatRank(),
      trendsApi.getPrediction()
    ])

    heatRank.value = rankRes.data || []
    predictions.value = predictRes.data || []

    // Configure Pie Chart (Professional Data Visualization Style)
    const pieData = Object.entries(pieRes.data).map(([name, value]) => ({ name, value }))
    pieOptions.value = {
      color: ['#3B82F6', '#60A5FA', '#93C5FD', '#BFDBFE', '#DBEAFE', '#EFF6FF'], 
      tooltip: { 
        trigger: 'item',
        backgroundColor: '#FFFFFF',
        borderColor: '#E2E8F0',
        borderWidth: 1,
        textStyle: { color: '#1E293B', fontFamily: 'Fira Sans' },
        formatter: '{b}: {c} ({d}%)'
      },
      legend: { 
        bottom: '0%', 
        left: 'center',
        icon: 'circle',
        itemGap: 20,
        textStyle: { color: '#64748B', fontFamily: 'Fira Sans', fontSize: 12 }
      },
      series: [
        {
          name: '分区占比',
          type: 'pie',
          radius: ['55%', '75%'],
          avoidLabelOverlap: false,
          itemStyle: { 
            borderRadius: 4, 
            borderColor: '#fff', 
            borderWidth: 2 
          },
          label: { show: false, position: 'center' },
          emphasis: { 
            label: { 
              show: true, 
              fontSize: 24, 
              fontWeight: 'bold',
              color: '#1E293B'
            }
          },
          data: pieData
        }
      ]
    }

    // Configure Line Chart (Clean Analytics Style)
    const dates = trendRes.data.map((item: any) => item.date || item.statDate)
    const counts = trendRes.data.map((item: any) => item.count || item.totalPosts)
    
    lineOptions.value = {
      tooltip: { 
        trigger: 'axis',
        backgroundColor: '#FFFFFF',
        borderColor: '#E2E8F0',
        borderWidth: 1,
        textStyle: { color: '#1E293B', fontFamily: 'Fira Sans' }
      },
      grid: { left: '2%', right: '2%', bottom: '5%', top: '10%', containLabel: true },
      xAxis: { 
        type: 'category', 
        boundaryGap: false, 
        data: dates,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#94A3B8', fontFamily: 'Fira Code', margin: 15 }
      },
      yAxis: { 
        type: 'value',
        splitLine: { lineStyle: { type: 'dashed', color: '#F1F5F9' } },
        axisLabel: { color: '#94A3B8', fontFamily: 'Fira Code' }
      },
      series: [
        {
          name: '发帖量',
          type: 'line',
          smooth: true,
          symbol: 'none',
          lineStyle: { width: 3, color: '#3B82F6' },
          areaStyle: { 
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(59, 130, 246, 0.1)' },
                { offset: 1, color: 'rgba(59, 130, 246, 0)' }
              ]
            }
          },
          data: counts
        }
      ]
    }

  } catch (error) {
    console.error(error)
    toast.error('获取趋势数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-6 font-sans space-y-6">
      
      <!-- Top Metrics Row -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div class="dashboard-card p-5 flex flex-col justify-between h-28 relative overflow-hidden group">
          <div class="flex justify-between items-start z-10">
            <span class="text-xs font-bold text-slate-500 uppercase tracking-wider">今日发帖</span>
            <div class="p-1 bg-blue-50 rounded text-blue-600">
              <Activity class="w-3.5 h-3.5" />
            </div>
          </div>
          <div class="z-10">
            <div class="text-2xl font-mono font-bold text-slate-900 tracking-tight">1,248</div>
            <div class="flex items-center gap-1.5 mt-1">
              <span class="text-xs font-bold text-emerald-600 flex items-center gap-0.5">
                <ArrowUp class="w-3 h-3" /> 12.5%
              </span>
              <span class="text-[10px] text-slate-400">较昨日</span>
            </div>
          </div>
        </div>

        <div class="dashboard-card p-5 flex flex-col justify-between h-28 relative overflow-hidden group">
          <div class="flex justify-between items-start z-10">
            <span class="text-xs font-bold text-slate-500 uppercase tracking-wider">活跃用户</span>
            <div class="p-1 bg-indigo-50 rounded text-indigo-600">
              <Zap class="w-3.5 h-3.5" />
            </div>
          </div>
          <div class="z-10">
            <div class="text-2xl font-mono font-bold text-slate-900 tracking-tight">8,932</div>
            <div class="flex items-center gap-1.5 mt-1">
              <span class="text-xs font-bold text-emerald-600 flex items-center gap-0.5">
                <ArrowUp class="w-3 h-3" /> 5.2%
              </span>
              <span class="text-[10px] text-slate-400">较上周</span>
            </div>
          </div>
        </div>

        <div class="dashboard-card p-5 flex flex-col justify-between h-28 relative overflow-hidden group">
          <div class="flex justify-between items-start z-10">
            <span class="text-xs font-bold text-slate-500 uppercase tracking-wider">互动率</span>
            <div class="p-1 bg-orange-50 rounded text-orange-600">
              <Flame class="w-3.5 h-3.5" />
            </div>
          </div>
          <div class="z-10">
            <div class="text-2xl font-mono font-bold text-slate-900 tracking-tight">4.8%</div>
            <div class="flex items-center gap-1.5 mt-1">
              <span class="text-xs font-bold text-slate-500 flex items-center gap-0.5">
                <Minus class="w-3 h-3" /> 0.0%
              </span>
              <span class="text-[10px] text-slate-400">持平</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Traffic Trend Chart -->
      <div class="dashboard-card p-0 overflow-hidden">
        <div class="px-5 py-3 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
          <h3 class="font-bold text-slate-800 text-sm flex items-center gap-2">
            <TrendingUp class="w-4 h-4 text-blue-600" />
            流量趋势监控
          </h3>
          <div class="flex gap-1">
            <button class="text-[10px] font-bold text-blue-600 bg-blue-50 border border-blue-100 px-2 py-1 rounded transition-colors">7天</button>
            <button class="text-[10px] font-bold text-slate-400 bg-transparent border border-transparent px-2 py-1 rounded hover:text-slate-600 transition-colors">30天</button>
          </div>
        </div>
        <div class="p-4 h-[300px]">
           <VChart v-if="lineOptions" :option="lineOptions" autoresize class="w-full h-full" />
        </div>
      </div>

      <!-- Prediction & Distribution Grid -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Prediction Table -->
        <div class="dashboard-card overflow-hidden">
           <div class="px-5 py-3 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
             <div class="flex items-center gap-2">
               <Activity class="w-4 h-4 text-emerald-600" />
               <h3 class="font-bold text-slate-800 text-sm">智能趋势预测</h3>
             </div>
             <span class="text-[10px] font-mono text-slate-400 border border-slate-200 px-1.5 py-0.5 rounded bg-white">LSTM-V2</span>
           </div>
           <div class="overflow-x-auto">
             <table class="w-full text-left border-collapse">
               <thead>
                 <tr class="bg-slate-50/50">
                   <th class="px-4 py-2 text-[10px] font-bold text-slate-400 uppercase tracking-wider border-b border-slate-100">话题</th>
                   <th class="px-4 py-2 text-[10px] font-bold text-slate-400 uppercase tracking-wider border-b border-slate-100">增长</th>
                   <th class="px-4 py-2 text-[10px] font-bold text-slate-400 uppercase tracking-wider border-b border-slate-100">建议</th>
                 </tr>
               </thead>
               <tbody class="divide-y divide-slate-50">
                 <tr v-for="(pred, idx) in predictions.slice(0, 5)" :key="idx" class="hover:bg-slate-50/80 transition-colors group">
                   <td class="px-4 py-3 text-xs font-bold text-slate-700 font-mono">{{ pred.topic }}</td>
                   <td class="px-4 py-3">
                     <span class="font-mono text-xs font-bold text-emerald-600 bg-emerald-50 px-1.5 py-0.5 rounded">+{{ pred.growthRate }}%</span>
                   </td>
                   <td class="px-4 py-3 text-xs text-slate-500 truncate max-w-[150px]">
                     {{ pred.insight }}
                   </td>
                 </tr>
               </tbody>
             </table>
           </div>
        </div>

        <!-- Category Pie -->
        <div class="dashboard-card p-0 overflow-hidden">
           <div class="px-5 py-3 border-b border-slate-100">
             <h3 class="font-bold text-slate-800 text-sm">内容分布</h3>
           </div>
           <div class="h-[250px] w-full p-2">
             <VChart v-if="pieOptions" :option="pieOptions" autoresize class="w-full h-full" />
           </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
