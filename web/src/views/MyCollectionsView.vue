<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { postApi } from '@/api/post'
import { useUserStore } from '@/store/user'
import PostCard from '@/components/PostCard.vue'
import type { Post } from '@/types'
import { Star, ArrowLeft, Loader2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const posts = ref<Post[]>([])
const loading = ref(true)

const fetchPosts = async () => {
    if (!userStore.userInfo?.id) return;
    try {
        const res = await postApi.searchList({
            page: 1,
            pageSize: 20,
            collectedBy: userStore.userInfo.id
        })
        posts.value = res.data.records
    } catch (error) {
        console.error(error)
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    fetchPosts()
})
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-6 font-sans">
      <div class="max-w-4xl">
        <!-- Header -->
        <div class="flex items-center justify-between mb-10 pb-6 border-b border-slate-200">
            <div class="flex items-center gap-5">
                <button 
                  @click="router.back()"
                  class="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-slate-500 shadow-sm border border-slate-100 hover:bg-slate-900 hover:text-white transition-all active:scale-95"
                >
                    <ArrowLeft class="w-5 h-5" />
                </button>
                <div>
                    <h1 class="text-2xl font-black text-slate-900 tracking-tight">我的收藏</h1>
                    <p class="text-[11px] text-slate-400 font-black uppercase tracking-widest mt-1">珍藏了 {{ posts.length }} 份宝贵内容</p>
                </div>
            </div>
            <div class="w-12 h-12 bg-amber-50 text-amber-500 rounded-2xl flex items-center justify-center shadow-sm border border-amber-100">
                <Star class="w-6 h-6 fill-current" />
            </div>
        </div>

        <!-- Content -->
        <div v-if="loading" class="flex flex-col items-center justify-center py-32 bg-white rounded-[2rem] border border-slate-100 shadow-xl shadow-slate-200/20">
            <Loader2 class="w-10 h-10 text-brand-primary animate-spin mb-4" />
            <p class="text-xs font-black text-slate-300 uppercase tracking-widest">正在开启收藏宝库...</p>
        </div>
        
        <div v-else-if="posts.length > 0" class="space-y-6">
            <PostCard v-for="post in posts" :key="post.id" :post="post" />
        </div>
        
        <div v-else class="text-center py-32 bg-white rounded-[2rem] border border-slate-100 shadow-xl shadow-slate-200/30">
            <div class="w-24 h-24 bg-slate-50 rounded-full flex items-center justify-center mx-auto mb-6 text-slate-200">
                <Star class="w-12 h-12" />
            </div>
            <h3 class="text-xl font-black text-slate-800 tracking-tight">宝库目前空空如也</h3>
            <p class="text-sm text-slate-400 font-bold mt-2">遇到有用的内容别忘了点击收藏噢</p>
            <button 
              @click="router.push('/')"
              class="mt-10 px-10 py-3.5 bg-slate-900 text-white font-black rounded-2xl shadow-xl shadow-slate-900/20 hover:bg-slate-800 transition-all active:scale-95 text-xs uppercase tracking-widest"
            >
              寻找宝藏
            </button>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
