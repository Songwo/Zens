<script setup lang="ts">
import { MessageSquare, ThumbsUp, Eye, Clock, Share2, Star, Sparkles } from 'lucide-vue-next'

interface Post {
  id: string
  title: string
  content: string
  authorName: string
  authorAvatar?: string
  createTime: string
  viewCount: number
  likeCount: number
  commentCount: number
  categoryName?: string
  sentimentLabel?: string // positive/neutral/negative
  trendLevel?: string    // hot/trending/normal
  recommendReason?: string
  isLiked?: boolean
  isCollected?: boolean
  collectCount: number
}

const props = defineProps<{
  post: Post
}>()

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

import { postApi } from '@/api/post'
import { toast } from 'vue-sonner'

const handleLike = async (e: Event) => {
  e.stopPropagation()
  try {
    await postApi.like(props.post.id)
    props.post.isLiked = !props.post.isLiked
    props.post.likeCount += props.post.isLiked ? 1 : -1
    toast.success(props.post.isLiked ? '点赞成功' : '取消点赞')
  } catch (error) {
    toast.error('操作失败')
  }
}

const handleCollect = async (e: Event) => {
  e.stopPropagation()
  try {
    await postApi.collect(props.post.id)
    props.post.isCollected = !props.post.isCollected
    props.post.collectCount += props.post.isCollected ? 1 : -1
    toast.success(props.post.isCollected ? '收藏成功' : '取消收藏')
  } catch (error) {
    toast.error('操作失败')
  }
}
</script>

<template>
  <div class="post-card group relative bg-white rounded-3xl p-6 cursor-pointer border border-slate-100 hover:border-slate-300 hover:shadow-2xl hover:shadow-slate-200/50 transition-all duration-300">
    <!-- Recommendation Reason Badge -->
    <div v-if="post.recommendReason" class="absolute -top-3 left-8 z-10 px-3 py-1 bg-slate-900 text-white text-[10px] font-black uppercase tracking-widest rounded-lg shadow-lg shadow-slate-200 flex items-center gap-1.5">
      <Sparkles class="w-3 h-3 text-amber-400" />
      {{ post.recommendReason }}
    </div>

    <!-- Sentiment Indicator (Left Stripe) -->
    <div 
      class="absolute left-0 top-6 bottom-6 w-1 rounded-r-full transition-all duration-300 group-hover:w-1.5"
      :class="[
        post.sentimentLabel === 'positive' ? 'bg-emerald-400' : 
        post.sentimentLabel === 'negative' ? 'bg-rose-400' : 
        'bg-slate-200'
      ]"
    ></div>

    <div class="flex flex-col gap-4">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-slate-50 border border-slate-100 flex items-center justify-center overflow-hidden shadow-sm">
            <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
            <span v-else class="text-xs font-black text-slate-400 uppercase">{{ post.authorName.charAt(0) }}</span>
          </div>
          <div class="flex flex-col">
            <span class="text-sm font-black text-slate-900 tracking-tight">{{ post.authorName }}</span>
            <div class="flex items-center gap-1.5 text-[10px] text-slate-400 font-black uppercase tracking-widest mt-0.5">
              <span>{{ formatDate(post.createTime) }}</span>
            </div>
          </div>
        </div>
        
        <div class="flex items-center gap-2">
          <span v-if="post.trendLevel === 'hot'" class="px-2 py-0.5 bg-rose-50 text-rose-600 text-[9px] font-black uppercase tracking-widest rounded-lg border border-rose-100">
            HOT
          </span>
          <span v-if="post.categoryName" class="px-2.5 py-1 bg-slate-50 text-slate-500 text-[10px] font-black uppercase tracking-widest rounded-lg border border-slate-100">
            {{ post.categoryName }}
          </span>
        </div>
      </div>

      <!-- Content -->
      <router-link :to="'/post/' + post.id" class="space-y-2 block">
        <h3 class="text-lg font-black text-slate-900 leading-tight group-hover:text-slate-800 transition-colors tracking-tight">
          {{ post.title }}
        </h3>
        <p class="text-sm text-slate-500 line-clamp-2 leading-relaxed font-medium">
          {{ post.content }}
        </p>
      </router-link>

      <!-- Footer Actions -->
      <div class="flex items-center justify-between pt-4 border-t border-slate-50 mt-2">
        <div class="flex items-center gap-6">
          <button 
            @click.stop="handleLike"
            :class="['flex items-center gap-2 transition-all duration-300 group/btn', post.isLiked ? 'text-pink-500 scale-105' : 'text-slate-400 hover:text-pink-500']"
          >
            <ThumbsUp class="w-4 h-4" :class="{ 'fill-current': post.isLiked }" />
            <span class="text-[10px] font-black tracking-widest">{{ post.likeCount }}</span>
          </button>
          
          <button class="flex items-center gap-2 text-slate-400 hover:text-slate-900 transition-colors group/btn">
            <MessageSquare class="w-4 h-4" />
            <span class="text-[10px] font-black tracking-widest">{{ post.commentCount }}</span>
          </button>
          
          <button 
            @click.stop="handleCollect"
            :class="['flex items-center gap-2 transition-all duration-300 group/btn', post.isCollected ? 'text-amber-500 scale-105' : 'text-slate-400 hover:text-amber-500']"
          >
            <Star class="w-4 h-4" :class="{ 'fill-current': post.isCollected }" />
            <span class="text-[10px] font-black tracking-widest">{{ post.collectCount }}</span>
          </button>
        </div>
        
        <div class="flex items-center gap-1.5 text-slate-300">
          <Eye class="w-4 h-4" />
          <span class="text-[10px] font-black uppercase tracking-widest">{{ post.viewCount }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.group:hover .animate-shine {
  animation: shine 0.75s;
}

@keyframes shine {
  100% {
    left: 125%;
  }
}
</style>
