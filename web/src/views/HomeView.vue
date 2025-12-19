<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import PostCard from '@/components/PostCard.vue'
import { postApi } from '@/api/post'
import type { Post } from '@/types'
import { toast } from 'vue-sonner'
import { Loader2 } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const orderBy = ref<'new' | 'hot'>('new')
const currentCategory = ref<string | undefined>(undefined)

const fetchPosts = async (reset = false) => {
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
  }
  
  if (!hasMore.value || loading.value) return

  loading.value = true
  try {
    const categoryID = route.query.category as string
    const tag = route.query.tag as string
    const search = route.query.search as string

    const res = await postApi.searchList({
      page: page.value,
      pageSize: 10,
      orderBy: orderBy.value,
      categoryID: categoryID || undefined,
      tag: tag || undefined,
      keyword: search || undefined,
      status: 1 // Published posts only
    })
    
    if (res.data.records.length > 0) {
      posts.value.push(...res.data.records)
      page.value++
    } else {
      hasMore.value = false
    }
    
    if (posts.value.length >= res.data.total) {
      hasMore.value = false
    }
  } catch (error) {
    toast.error('获取内容失败')
  } finally {
    loading.value = false
  }
}

const toggleOrder = (newOrder: 'new' | 'hot') => {
  if (orderBy.value === newOrder) return
  orderBy.value = newOrder
  fetchPosts(true)
}

const clearFilters = () => {
    router.push('/')
}

// Watch for query changes in route
watch(() => [route.query.category, route.query.tag, route.query.search], () => {
  fetchPosts(true)
})

onMounted(() => {
  fetchPosts(true)
})
</script>

<template>
  <DefaultLayout>
    <div class="py-8 px-6">
      <div class="flex items-center justify-between mb-10">
        <div>
          <h1 class="text-3xl font-black text-slate-900 tracking-tighter">探索发现</h1>
          <p class="text-sm text-slate-500 mt-1 font-medium">捕捉校园每一个精彩瞬间 · 全校动态实时更新</p>
        </div>
        
        <div class="flex items-center gap-3">
          <button 
            @click="toggleOrder('new')"
            :class="[
              'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-colors',
              orderBy === 'new' ? 'bg-slate-100 text-slate-600' : 'bg-white border border-slate-200 text-slate-400 hover:border-slate-400'
            ]"
          >
            最新发布
          </button>
          <button 
            @click="toggleOrder('hot')"
            :class="[
              'flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-colors',
              orderBy === 'hot' ? 'bg-slate-100 text-slate-600' : 'bg-white border border-slate-200 text-slate-400 hover:border-slate-400'
            ]"
          >
            热度优先
          </button>
        </div>
      </div>

      <!-- Filter Status Indicator -->
      <div v-if="route.query.tag || route.query.search || route.query.category" class="mb-6 flex items-center flex-wrap gap-3 animate-in slide-in-from-top-2 duration-300">
        <div class="px-4 py-2 bg-brand-primary/10 border border-brand-primary/20 rounded-xl flex items-center gap-3">
          <span class="text-xs font-bold text-brand-primary">
            正在显示: 
            <span v-if="route.query.search" class="text-slate-900">搜索 "{{ route.query.search }}"</span>
            <span v-else-if="route.query.tag" class="text-slate-900">#{{ route.query.tag }} 话题</span>
            <span v-else-if="route.query.category" class="text-slate-900">分类检索</span>
          </span>
          <button 
            @click="clearFilters"
            class="w-5 h-5 flex items-center justify-center bg-brand-primary text-white rounded-full hover:bg-blue-600 transition-colors"
          >
            <span class="text-[14px] leading-none">×</span>
          </button>
        </div>
        <span class="text-[10px] font-black text-slate-300 uppercase tracking-widest">找到 {{ posts.length }} 条相关结果</span>
      </div>

      <!-- Feed Container -->
      <div class="space-y-8 pb-20">
        <!-- Post List -->
        <PostCard 
          v-for="post in posts" 
          :key="post.id" 
          :post="post" 
        />
        
        <!-- Loading State -->
        <div v-if="loading" class="flex justify-center py-8">
          <Loader2 class="w-8 h-8 text-brand-primary animate-spin" />
        </div>
        
        <!-- Empty State -->
        <div v-if="!loading && posts.length === 0" class="text-center py-20">
          <p class="text-slate-400 font-medium">暂无内容，快来发布第一条动态吧！</p>
        </div>

        <!-- Load More Button -->
        <div v-if="!loading && hasMore && posts.length > 0" class="pt-8 text-center">
          <button 
            @click="fetchPosts(false)"
            class="px-8 py-3 bg-white border border-slate-200 text-slate-500 rounded-2xl text-xs font-bold hover:border-brand-primary hover:text-brand-primary transition-all shadow-sm"
          >
            加载更多内容
          </button>
        </div>
        
        <!-- End of Feed -->
        <div v-if="!hasMore && posts.length > 0" class="py-8 text-center">
          <span class="px-4 py-1.5 bg-slate-50 text-slate-400 text-[10px] rounded-full font-bold uppercase tracking-widest">
            THE END
          </span>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
