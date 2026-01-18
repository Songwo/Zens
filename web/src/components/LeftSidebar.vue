<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Home, TrendingUp, BookOpen, Tag, Settings, Users, ArrowRight, UserCircle, GraduationCap, ClipboardCheck, MessageSquarePlus } from 'lucide-vue-next'
import { categoryApi, type Category } from '@/api/category'
import { tagApi } from '@/api/tag'

const menuItems = [
  { id: 'feed', name: '探索发现', icon: Home, path: '/' },
  { id: 'trends', name: '大趋势', icon: TrendingUp, path: '/trends' },
]

const studentModules = [
  { id: 'profile', name: '学籍档案', icon: UserCircle, path: '/student/profile' },
  { id: 'academic', name: '学业中心', icon: GraduationCap, path: '/academic' },
  { id: 'selection', name: '选课系统', icon: ClipboardCheck, path: '/course-selection' },
  { id: 'leave', name: '请假管理', icon: MessageSquarePlus, path: '/leave' },
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
        class="flex items-center gap-3 px-4 py-3 rounded-xl text-slate-500 hover:bg-white hover:text-slate-900 transition-all group"
        active-class="bg-white text-slate-900 shadow-sm font-bold border border-slate-100"
      >
        <component :is="item.icon" class="w-5 h-5 group-hover:scale-110 transition-transform" />
        <span>{{ item.name }}</span>
      </router-link>
    </div>

    <!-- Student Management -->
    <div class="flex flex-col gap-1 px-4">
      <h3 class="px-4 text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-2">教务与学工</h3>
      <router-link 
        v-for="item in studentModules" 
        :key="item.id"
        :to="item.path"
        class="flex items-center gap-3 px-4 py-3 rounded-xl text-slate-500 hover:bg-white hover:text-slate-900 transition-all group"
        active-class="bg-white text-slate-900 shadow-sm font-bold border border-slate-100"
      >
        <component :is="item.icon" class="w-5 h-5 group-hover:scale-110 transition-transform" />
        <span>{{ item.name }}</span>
      </router-link>
    </div>

    <!-- Categories -->
    <div class="flex flex-col gap-1 px-4">
      <div class="flex items-center justify-between px-4 mb-2">
        <h3 class="text-[11px] font-bold text-slate-400 uppercase tracking-widest">分类导航</h3>
        <button class="text-[10px] text-slate-900 font-bold hover:underline">全部</button>
      </div>
      <router-link 
        v-for="cat in categories" 
        :key="cat.id"
        :to="{ path: '/', query: { category: cat.id } }"
        class="flex items-center justify-between px-4 py-2.5 rounded-xl text-slate-500 hover:bg-white hover:text-slate-900 transition-all text-sm group text-left"
        active-class="bg-white text-slate-900 shadow-sm font-bold border border-slate-100"
      >
        <div class="flex items-center gap-3">
          <div class="w-1.5 h-1.5 rounded-full bg-slate-200 group-hover:bg-slate-900 transition-colors" :class="{ 'bg-slate-900': $route.query.category === cat.id }"></div>
          <span>{{ cat.name }}</span>
        </div>
        <span class="text-[10px] bg-slate-50 text-slate-400 px-1.5 py-0.5 rounded font-mono">{{ cat.postCount }}</span>
      </router-link>
    </div>

    <!-- Recommended Tags -->
    <div class="flex flex-col gap-1 px-4">
       <h3 class="px-4 text-[11px] font-bold text-slate-400 uppercase tracking-widest mb-2">话题标签</h3>
       <div class="flex flex-wrap gap-2 px-4">
          <router-link
             v-for="tag in tags"
             :key="tag.id"
             :to="{ path: '/', query: { tag: tag.name } }"
             class="relative px-3 py-1.5 bg-slate-50 hover:bg-white border border-slate-200/50 hover:border-slate-300 rounded-lg text-[11px] font-bold text-slate-500 hover:text-slate-900 transition-all active:scale-95"
             active-class="!bg-slate-900 !text-white !border-slate-900 !shadow-md"
          >
             #{{ tag.name }}
          </router-link>
       </div>
    </div>

    <!-- Community Section -->
    <div class="mt-auto px-4 py-6 border-t border-slate-100 mx-4">
      <div class="bg-slate-900 rounded-2xl p-5 text-white relative overflow-hidden shadow-xl shadow-slate-200">
        <Users class="absolute -right-4 -bottom-4 w-20 h-20 opacity-10 rotate-12" />
        <h4 class="font-bold text-sm relative z-10">加入学术社区</h4>
        <p class="text-[10px] text-slate-400 mt-1 mb-4 relative z-10 leading-relaxed">连接校园内的每一个智慧节点，共同探讨前沿知识</p>
        <button class="w-full flex items-center justify-center gap-2 py-2.5 bg-white text-slate-900 hover:bg-slate-100 rounded-xl text-[11px] font-bold transition-all shadow-lg shadow-black/20">
          立即加入 <ArrowRight class="w-3 h-3" />
        </button>
      </div>
    </div>
  </aside>
</template>
