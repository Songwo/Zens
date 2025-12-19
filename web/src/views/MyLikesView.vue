<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { postApi } from '@/api/post'
import { useUserStore } from '@/store/user'
import PostCard from '@/components/PostCard.vue'
import type { Post } from '@/types'
import { Heart, ArrowLeft, Loader2 } from 'lucide-vue-next'
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
            likedBy: userStore.userInfo.id
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
  <DefaultLayout :showLeftSidebar="false" :showRightSidebar="false" wide>
    <div class="min-h-screen bg-slate-50/50 py-10 px-6">
      <div class="max-w-2xl mx-auto">
        <!-- Header -->
        <div class="flex items-center justify-between mb-8">
            <div class="flex items-center gap-4">
                <button 
                  @click="router.back()"
                  class="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-slate-500 shadow-sm border border-slate-100 hover:bg-slate-50 transition-colors"
                >
                    <ArrowLeft class="w-5 h-5" />
                </button>
                <div>
                    <h1 class="text-2xl font-black text-slate-900">我的喜欢</h1>
                    <p class="text-xs text-slate-400 font-bold mt-1">共收藏了 {{ posts.length }} 篇心动内容</p>
                </div>
            </div>
            <div class="p-3 bg-pink-50 text-pink-500 rounded-2xl shadow-sm">
                <Heart class="w-6 h-6 fill-current" />
            </div>
        </div>

        <!-- Content -->
        <div v-if="loading" class="flex flex-col items-center justify-center py-20">
            <Loader2 class="w-10 h-10 text-brand-primary animate-spin mb-4" />
            <p class="text-sm text-slate-400 font-bold">正在加载你的心动内容...</p>
        </div>
        
        <div v-else-if="posts.length > 0" class="space-y-6">
            <PostCard v-for="post in posts" :key="post.id" :post="post" class="shadow-xl shadow-slate-200/40" />
        </div>
        
        <div v-else class="text-center py-32 bg-white rounded-[2.5rem] border border-slate-100 shadow-xl shadow-slate-200/30">
            <div class="w-24 h-24 bg-slate-50 rounded-full flex items-center justify-center mx-auto mb-6 text-slate-200">
                <Heart class="w-12 h-12" />
            </div>
            <h3 class="text-lg font-black text-slate-800">这里空空如也</h3>
            <p class="text-sm text-slate-400 font-bold mt-2">看到喜欢的内容记得点个赞呀～</p>
            <button 
              @click="router.push('/')"
              class="mt-8 px-8 py-3 bg-brand-primary text-white font-black rounded-xl shadow-lg shadow-blue-500/20 hover:bg-blue-600 transition-all active:scale-95"
            >
              去广场逛逛
            </button>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
