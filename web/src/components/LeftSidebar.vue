<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Home, TrendingUp, BookOpen, Tag, Settings, Users, ArrowRight } from 'lucide-vue-next'
import { categoryApi, type Category } from '@/api/category'
import { tagApi } from '@/api/tag'

const menuItems = [
  { id: 'feed', name: '探索发现', icon: Home, path: '/' },
  { id: 'trends', name: '大趋势', icon: TrendingUp, path: '/trends' },
]

const categories = ref<Category[]>([])
const tags = ref<any[]>([])

const fetchData = async () => {
  try {
    const [catRes, tagRes] = await Promise.all([
        categoryApi.getList(),
        tagApi.getHotTags(8)
    ])
    categories.value = catRes.data || []
    tags.value = tagRes.data || []
  } catch (error) {
    console.error('Failed to fetch sidebar data:', error)
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <aside class="w-72 h-[calc(100vh-64px)] overflow-y-auto py-6 flex flex-col gap-8 flex-shrink-0">
    <!-- Main Menu -->
    <div class="flex flex-col gap-1 px-4">
      <h3 class="px-4 text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-2">主菜单</h3>
      <router-link 
        v-for="item in menuItems" 
        :key="item.id"
        :to="item.path"
        class="flex items-center gap-3 px-4 py-3 rounded-xl text-slate-600 hover:bg-white hover:text-brand-primary hover:shadow-sm transition-all group"
        active-class="bg-white text-brand-primary shadow-sm font-semibold"
      >
        <component :is="item.icon" class="w-5 h-5 group-hover:scale-110 transition-transform" />
        <span>{{ item.name }}</span>
      </router-link>
    </div>

    <!-- Categories -->
    <div class="flex flex-col gap-1 px-4">
      <div class="flex items-center justify-between px-4 mb-2">
        <h3 class="text-[11px] font-bold text-slate-400 uppercase tracking-widest">热门分类</h3>
        <button class="text-[10px] text-brand-primary font-bold hover:underline">查看全部</button>
      </div>
      <router-link 
        v-for="cat in categories" 
        :key="cat.id"
        :to="{ path: '/', query: { category: cat.id } }"
        class="flex items-center justify-between px-4 py-2.5 rounded-xl text-slate-600 hover:bg-white hover:text-slate-900 hover:shadow-sm transition-all text-sm group text-left"
        active-class="bg-white text-brand-primary shadow-sm font-semibold"
      >
        <div class="flex items-center gap-3">
          <div class="w-1.5 h-1.5 rounded-full bg-slate-300 group-hover:bg-brand-primary transition-colors" :class="{ 'bg-brand-primary': $route.query.category === cat.id }"></div>
          <span>{{ cat.name }}</span>
        </div>
        <span class="text-xs text-slate-400 font-medium">{{ cat.postCount }}</span>
      </router-link>
    </div>

    <!-- Recommended Tags -->
    <div class="flex flex-col gap-1 px-4">
       <h3 class="px-4 text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-2">推荐话题</h3>
       <div class="flex flex-wrap gap-2 px-4">
          <router-link
             v-for="tag in tags"
             :key="tag.id"
             :to="{ path: '/', query: { tag: tag.name } }"
             class="relative px-3 py-1.5 bg-slate-100/50 hover:bg-white border border-slate-200/50 hover:border-slate-200 rounded-lg text-xs font-bold text-slate-600 hover:text-brand-primary hover:shadow-sm transition-all active:scale-95 group/tag"
             active-class="!bg-brand-primary !text-white !border-brand-primary !shadow-md"
          >
             #{{ tag.name }}
             <!-- Heat Dot for popular tags -->
             <span v-if="tag.heat > 500" class="absolute -top-1 -right-1 w-2 h-2 bg-red-500 rounded-full border border-white animate-pulse"></span>
          </router-link>
       </div>
    </div>

    <!-- Community Section -->
    <div class="mt-auto px-6 py-6 border-t border-slate-200/60 mx-4">
      <div class="bg-gradient-to-br from-blue-600 to-blue-400 rounded-2xl p-4 text-white relative overflow-hidden shadow-xl shadow-blue-500/10">
        <Users class="absolute -right-4 -bottom-4 w-24 h-24 opacity-20 rotate-12" />
        <h4 class="font-bold text-sm relative z-10">加入学术社区</h4>
        <p class="text-[11px] text-blue-50 mt-1 mb-3 opacity-90 relative z-10">与更多优秀的同学探讨专业领域知识</p>
        <button class="w-full flex items-center justify-center gap-2 py-2 bg-white/20 hover:bg-white/30 rounded-lg text-xs font-bold transition-colors border border-white/20">
          立即加入 <ArrowRight class="w-3 h-3" />
        </button>
      </div>
    </div>
  </aside>
</template>
