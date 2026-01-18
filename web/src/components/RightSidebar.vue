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
    <div class="bg-gradient-to-br from-white to-blue-50 border border-blue-100 rounded-xl p-4 shadow-sm relative overflow-hidden group">
      <div class="flex items-center gap-2 mb-3">
        <div class="p-1.5 bg-blue-600 rounded text-white">
          <Sparkles class="w-3.5 h-3.5" />
        </div>
        <span class="text-xs font-bold text-blue-900 uppercase tracking-wider">趋势预测 (BETA)</span>
      </div>
      
      <p class="text-[11px] text-blue-700 leading-relaxed mb-4">
        AI 基于深度学习模型，实时预测校园内即将爆发的热点话题。
      </p>
      
      <router-link to="/trends" class="flex items-center justify-between group/btn py-2 px-3 bg-white border border-blue-200 rounded-lg text-xs font-bold text-blue-600 hover:border-blue-400 transition-all shadow-sm">
        前往分析中心
        <ArrowUpRight class="w-3.5 h-3.5 group-hover/btn:translate-x-0.5 group-hover/btn:-translate-y-0.5 transition-transform" />
      </router-link>
    </div>

    <!-- Hot Topics List -->
    <div class="bg-white border border-slate-200 rounded-xl p-0 shadow-sm overflow-hidden">
      <div class="px-4 py-3 border-b border-slate-100 flex items-center gap-2">
        <TrendingUp class="w-4 h-4 text-orange-500" />
        <h3 class="text-sm font-bold text-slate-900 tracking-tight">24小时热门排行</h3>
      </div>
      
      <div class="flex flex-col divide-y divide-slate-50">
        <router-link 
          v-for="(topic, index) in hotTopics" 
          :key="topic.postId"
          :to="`/post/${topic.postId}`"
          class="flex items-center gap-3 px-4 py-3 hover:bg-slate-50 transition-colors group"
        >
          <span class="text-sm font-black font-mono w-5 text-center" :class="index < 3 ? 'text-orange-500' : 'text-slate-300'">
            {{ index + 1 }}
          </span>
          <div class="flex-1 min-w-0">
            <h4 class="text-xs font-medium text-slate-700 truncate group-hover:text-blue-600 transition-colors mb-0.5">
              {{ topic.title }}
            </h4>
            <div class="flex items-center gap-2">
              <span class="text-[10px] text-slate-400 font-mono">{{ topic.viewCount }} 阅读</span>
            </div>
          </div>
        </router-link>
        
        <div v-if="hotTopics.length === 0" class="text-center py-6 text-slate-400 text-xs">
          暂无热门内容
        </div>
      </div>
    </div>

    <!-- Stats Card -->
    <div class="bg-slate-900 rounded-2xl p-5 text-white shadow-xl shadow-slate-900/10">
      <h3 class="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4">全站实时概览</h3>
      <div class="grid grid-cols-2 gap-4">
        <div>
          <span class="block text-xl font-bold italic">1.2k</span>
          <span class="text-[10px] text-slate-500 uppercase font-semibold">今日动态</span>
        </div>
        <div>
          <span class="block text-xl font-bold italic">45k</span>
          <span class="text-[10px] text-slate-500 uppercase font-semibold">互动总量</span>
        </div>
      </div>
      <div class="mt-4 pt-4 border-t border-white/10">
        <div class="flex items-center justify-between text-[10px] text-slate-400 mb-1.5">
          <span>系统状态: 极度活跃</span>
          <span>98%</span>
        </div>
        <div class="w-full h-1 bg-white/5 rounded-full overflow-hidden">
          <div class="w-[98%] h-full bg-blue-500 rounded-full shadow-[0_0_8px_rgba(59,130,246,0.5)]"></div>
        </div>
      </div>
    </div>
  </aside>
</template>
