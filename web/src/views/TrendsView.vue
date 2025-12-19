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
import { Flame, TrendingUp, BarChart2 } from 'lucide-vue-next'

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

    // Configure Pie Chart
    const pieData = Object.entries(pieRes.data).map(([name, value]) => ({ name, value }))
    pieOptions.value = {
      tooltip: { trigger: 'item' },
      legend: { bottom: '0%', left: 'center' },
      series: [
        {
          name: '分区占比',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
          label: { show: false, position: 'center' },
          emphasis: { label: { show: true, fontSize: 20, fontWeight: 'bold' } },
          data: pieData
        }
      ]
    }

    // Configure Line Chart
    // Assuming trendRes.data is [{ date: '...', count: 10 }]
    const dates = trendRes.data.map((item: any) => item.date || item.statDate)
    const counts = trendRes.data.map((item: any) => item.count || item.totalPosts)
    
    lineOptions.value = {
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: dates },
      yAxis: { type: 'value' },
      series: [
        {
          name: '发帖量',
          type: 'line',
          stack: 'Total',
          smooth: true,
          lineStyle: { width: 4, color: '#3b82f6' },
          areaStyle: { opacity: 0.1, color: '#3b82f6' },
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
  <DefaultLayout wide :showRightSidebar="false">
    <div class="py-10 px-6 max-w-7xl mx-auto">
      <div class="mb-16">
        <h1 class="text-4xl font-black text-slate-900 tracking-tighter">全校趋势看板</h1>
        <p class="text-base text-slate-500 mt-2 font-medium italic opacity-80">数据驱动的校园热点分析 · 实时洞察</p>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-12 pb-20">
        <!-- Main Stats Column -->
        <div class="lg:col-span-2 space-y-12">
          
          <!-- Activity Line Chart -->
          <div class="bg-white p-8 rounded-[2.5rem] border border-slate-100 shadow-xl shadow-slate-200/40">
            <div class="flex items-center gap-3 mb-8">
              <div class="p-2.5 bg-blue-50 rounded-xl text-blue-500 shadow-sm border border-blue-100">
                <TrendingUp class="w-6 h-6" />
              </div>
              <div>
                <h3 class="font-bold text-slate-800 text-lg">7日话题活跃度</h3>
                <p class="text-[11px] text-slate-400 font-bold uppercase tracking-widest mt-0.5">Campus Pulse Velocity</p>
              </div>
            </div>
            <div class="h-[400px] w-full px-2">
               <VChart v-if="lineOptions" :option="lineOptions" autoresize class="w-full h-full" />
               <div v-else class="h-full flex items-center justify-center text-slate-300">加载中...</div>
            </div>
          </div>

          <!-- Category Pie & Prediction -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-12">
            <div class="bg-white p-8 rounded-[2.5rem] border border-slate-100 shadow-xl shadow-slate-200/40">
               <div class="flex items-center gap-3 mb-8">
                <div class="p-2.5 bg-purple-50 rounded-xl text-purple-500 shadow-sm border border-purple-100">
                  <BarChart2 class="w-6 h-6" />
                </div>
                <div>
                  <h3 class="font-bold text-slate-800 text-lg">话题分布</h3>
                  <p class="text-[11px] text-slate-400 font-bold uppercase tracking-widest mt-0.5">Topic Distribution</p>
                </div>
              </div>
              <div class="h-80 w-full">
                <VChart v-if="pieOptions" :option="pieOptions" autoresize class="w-full h-full" />
              </div>
            </div>

            <!-- AI Insight Card -->
            <div class="bg-white p-8 rounded-[2.5rem] border border-slate-100 shadow-xl shadow-slate-200/40 relative overflow-hidden group">
               <div class="absolute top-0 right-0 w-48 h-48 bg-blue-50/50 rounded-full blur-3xl -mr-16 -mt-16 group-hover:scale-110 transition-transform duration-1000"></div>
               <div class="absolute -left-10 -bottom-10 w-32 h-32 bg-purple-50/50 rounded-full blur-2xl"></div>
               
               <h3 class="font-bold text-xl text-slate-800 mb-6 flex items-center gap-2 relative z-10">
                 <span class="bg-brand-primary/10 text-brand-primary px-2 py-0.5 rounded text-[10px] font-black uppercase tracking-tighter backdrop-blur-md">Next-Gen AI</span> 
                 趋势预测
               </h3>
               
               <div class="space-y-4 relative z-10">
                 <div v-for="(pred, idx) in predictions.slice(0, 2)" :key="idx" class="bg-slate-50 p-5 rounded-3xl border border-slate-100 hover:bg-white hover:shadow-md transition-all group/item">
                   <div class="flex justify-between items-center mb-3">
                     <span class="text-[10px] font-black uppercase tracking-widest text-slate-400 px-2 py-0.5 bg-slate-100 rounded-lg group-hover/item:bg-white transition-colors">🔥 Rising Star</span>
                     <span class="text-sm font-black text-green-500 bg-green-50 px-2.5 py-1 rounded-full">+{{ pred.growthRate }}%</span>
                   </div>
                   <p class="text-lg font-bold text-slate-800 leading-tight">{{ pred.topic }}</p>
                   <p class="text-sm text-slate-500 mt-3 leading-relaxed line-clamp-3 font-medium">{{ pred.insight }}</p>
                 </div>
               </div>
            </div>
          </div>
        </div>

        <!-- Right Side: Heat Rank -->
        <div class="bg-white p-8 rounded-[2.5rem] border border-slate-100 shadow-xl shadow-slate-200/40 h-fit">
          <div class="flex items-center gap-3 mb-8">
            <div class="p-2.5 bg-orange-50 rounded-xl text-orange-500 shadow-sm border border-orange-100">
              <Flame class="w-6 h-6" />
            </div>
            <div>
              <h3 class="font-bold text-slate-800 text-xl">实时热榜</h3>
              <p class="text-[11px] text-slate-400 font-bold uppercase tracking-widest mt-0.5">Top 10 Trending</p>
            </div>
          </div>
          
          <div class="space-y-6">
            <router-link 
              v-for="(item, index) in heatRank" 
              :key="item.postId" 
              :to="`/post/${item.postId}`"
              class="flex items-center gap-6 group cursor-pointer p-4 hover:bg-slate-50 rounded-[1.5rem] transition-all duration-300 border border-transparent hover:border-slate-100"
            >
              <div 
                class="w-12 h-12 rounded-2xl flex items-center justify-center font-black text-2xl transition-all group-hover:scale-110 group-hover:rotate-3 shadow-sm"
                :class="index < 3 ? 'bg-gradient-to-br from-orange-400 to-pink-500 text-white shadow-orange-200' : 'bg-slate-100 text-slate-400'"
              >
                {{ index + 1 }}
              </div>
              <div class="flex-1 min-w-0">
                <h4 class="text-base font-bold text-slate-700 truncate group-hover:text-brand-primary transition-colors mb-2.5">
                  {{ item.title }}
                </h4>
                <div class="flex items-center gap-4">
                  <div class="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                    <div 
                      class="h-full bg-gradient-to-r from-orange-400 to-pink-500 rounded-full transition-all duration-1000" 
                      :style="{ width: Math.min(item.heatScore / 20, 100) + '%' }"
                    ></div>
                  </div>
                  <span class="text-[11px] text-slate-400 font-bold whitespace-nowrap bg-slate-50 px-2 py-0.5 rounded-md border border-slate-100">{{ item.viewCount }} 阅读</span>
                </div>
              </div>
            </router-link>
            
            <div v-if="heatRank.length === 0" class="text-center py-16 text-slate-400">
              <p class="text-sm font-medium italic">暂无热榜数据</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
