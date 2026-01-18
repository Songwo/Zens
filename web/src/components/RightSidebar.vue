<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { TrendingUp, Sparkles, ArrowUpRight } from 'lucide-vue-next'
import { trendsApi } from '@/api/trends'

const hotTopics = ref<any[]>([])
const loading = ref(true)

const fetchHotTopics = async () => {
  try {
    const res = await trendsApi.getHeatRank()
    hotTopics.value = res.data || []
  } catch (error) {
    console.error('Failed to fetch hot topics:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchHotTopics()
})
</script>

<template>
  <aside class="w-80 h-[calc(100vh-64px)] overflow-y-auto py-6 flex flex-col gap-6 flex-shrink-0 px-4">
    <!-- Trend Prediction (BETA) -->
    <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-sm relative overflow-hidden group">
      <div class="flex items-center gap-2 mb-3">
        <div class="p-1.5 bg-slate-900 rounded text-white shadow-sm">
          <Sparkles class="w-3.5 h-3.5" />
        </div>
        <span class="text-[10px] font-black text-slate-900 uppercase tracking-widest">趋势预测</span>
        <span class="px-1.5 py-0.5 bg-slate-100 text-[9px] text-slate-500 rounded font-bold uppercase">Beta</span>
      </div>
      
      <p class="text-[11px] text-slate-500 leading-relaxed mb-4">
        基于深度学习模型，实时发现并预测校园内即将爆发的热点话题与讨论。
      </p>
      
      <router-link to="/trends" class="flex items-center justify-between group/btn py-2.5 px-3 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900 hover:bg-white hover:border-slate-400 transition-all">
        前往分析中心
        <ArrowUpRight class="w-3.5 h-3.5 group-hover/btn:translate-x-0.5 group-hover/btn:-translate-y-0.5 transition-transform" />
      </router-link>
    </div>

    <!-- Hot Topics List -->
    <div class="bg-white border border-slate-200 rounded-xl p-0 shadow-sm overflow-hidden">
      <div class="px-5 py-4 border-b border-slate-50 flex items-center gap-2.5">
        <TrendingUp class="w-4 h-4 text-slate-900" />
        <h3 class="text-xs font-black text-slate-900 uppercase tracking-widest">24小时热门排行</h3>
      </div>
      
      <div class="flex flex-col divide-y divide-slate-50">
        <router-link 
          v-for="(topic, index) in hotTopics" 
          :key="topic.postId"
          :to="`/post/${topic.postId}`"
          class="flex items-center gap-3.5 px-5 py-3.5 hover:bg-slate-50 transition-colors group"
        >
          <span class="text-xs font-black font-mono w-5 text-center" :class="index < 3 ? 'text-slate-900' : 'text-slate-300'">
            {{ (index + 1).toString().padStart(2, '0') }}
          </span>
          <div class="flex-1 min-w-0">
            <h4 class="text-[12px] font-bold text-slate-700 truncate group-hover:text-slate-900 transition-colors mb-0.5">
              {{ topic.title }}
            </h4>
            <div class="flex items-center gap-2">
              <span class="text-[9px] text-slate-400 font-bold uppercase tracking-tighter">{{ topic.viewCount }} 次阅读</span>
            </div>
          </div>
        </router-link>
        
        <div v-if="hotTopics.length === 0" class="text-center py-8 text-slate-400 text-[11px] font-medium">
          暂无热门内容
        </div>
      </div>
    </div>

    <!-- Stats Card -->
    <div class="bg-slate-900 rounded-2xl p-6 text-white shadow-xl shadow-slate-200">
      <h3 class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-5">全站实时概览</h3>
      <div class="grid grid-cols-2 gap-6">
        <div>
          <span class="block text-2xl font-bold tracking-tighter">1,248</span>
          <span class="text-[9px] text-slate-500 uppercase font-black tracking-widest">今日动态</span>
        </div>
        <div>
          <span class="block text-2xl font-bold tracking-tighter">45.2k</span>
          <span class="text-[9px] text-slate-500 uppercase font-black tracking-widest">互动总量</span>
        </div>
      </div>
      <div class="mt-6 pt-5 border-t border-white/5">
        <div class="flex items-center justify-between text-[9px] text-slate-400 uppercase font-black tracking-widest mb-2">
          <span>系统活跃度</span>
          <span class="text-white">98%</span>
        </div>
        <div class="w-full h-1 bg-white/5 rounded-full overflow-hidden">
          <div class="w-[98%] h-full bg-white rounded-full"></div>
        </div>
      </div>
    </div>
  </aside>
</template>
