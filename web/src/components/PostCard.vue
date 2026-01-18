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
  <div class="group relative p-6 cursor-pointer overflow-hidden transform hover:-translate-y-0.5 transition-all duration-200">
    
    <!-- Trending Badge -->
    <div v-if="post.trendLevel === 'hot'" class="absolute top-0 right-0 p-4 z-10">
      <div class="px-2 py-0.5 bg-rose-500 text-white text-[10px] font-bold uppercase tracking-wider rounded flex items-center gap-1 shadow-sm">
        <Sparkles class="w-3 h-3 fill-white" />
        HOT
      </div>
    </div>

    <div class="flex flex-col gap-4 relative z-10">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center text-sm font-bold text-slate-500 overflow-hidden">
            <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
            <span v-else>{{ post.authorName.charAt(0) }}</span>
          </div>
          <div class="flex flex-col">
            <span class="text-sm font-bold text-slate-900">{{ post.authorName }}</span>
            <div class="flex items-center gap-1.5 text-xs text-slate-500 font-medium font-mono">
              <span>{{ formatDate(post.createTime) }}</span>
            </div>
          </div>
        </div>
        
        <span v-if="post.categoryName" class="px-2.5 py-0.5 bg-slate-100 text-slate-600 text-xs font-medium rounded border border-slate-200">
          {{ post.categoryName }}
        </span>
      </div>

      <!-- Content -->
      <router-link :to="'/post/' + post.id" class="space-y-2 block">
        <h3 class="text-lg font-bold text-slate-900 leading-snug group-hover:text-blue-600 transition-colors">
          {{ post.title }}
        </h3>
        <p class="text-sm text-slate-600 line-clamp-3 leading-relaxed">
          {{ post.content }}
        </p>
      </router-link>

      <!-- Footer Actions -->
      <div class="flex items-center justify-between pt-4 border-t border-slate-100 mt-2">
        <div class="flex items-center gap-6">
          <button 
            @click.stop="handleLike"
            :class="['flex items-center gap-2 transition-colors', post.isLiked ? 'text-pink-600' : 'text-slate-500 hover:text-pink-600']"
          >
            <ThumbsUp class="w-4 h-4" :class="{ 'fill-current': post.isLiked }" />
            <span class="text-xs font-bold font-mono">{{ post.likeCount }}</span>
          </button>
          
          <button class="flex items-center gap-2 text-slate-500 hover:text-blue-600 transition-colors">
            <MessageSquare class="w-4 h-4" />
            <span class="text-xs font-bold font-mono">{{ post.commentCount }}</span>
          </button>
          
          <button 
            @click.stop="handleCollect"
            :class="['flex items-center gap-2 transition-colors', post.isCollected ? 'text-amber-500' : 'text-slate-500 hover:text-amber-500']"
          >
            <Star class="w-4 h-4" :class="{ 'fill-current': post.isCollected }" />
            <span class="text-xs font-bold font-mono">{{ post.collectCount }}</span>
          </button>
        </div>
        
        <div class="flex items-center gap-1.5 text-slate-400 text-xs font-mono">
          <Eye class="w-4 h-4" />
          <span>{{ post.viewCount }}</span>
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
