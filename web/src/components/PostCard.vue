<script setup lang="ts">
import { MessageSquare, ThumbsUp, Eye, Clock, Share2, Star } from 'lucide-vue-next'

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
}

defineProps<{
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
  <div class="group relative bg-white border border-slate-100 rounded-3xl p-5 hover:shadow-xl hover:shadow-blue-500/5 hover:border-blue-100 transition-all cursor-pointer overflow-hidden">
    <!-- Sentiment/Trend Badge -->
    <div class="absolute top-0 right-0 p-4">
      <span 
        v-if="post.trendLevel === 'hot'"
        class="px-2 py-1 bg-red-500 text-white text-[10px] font-black italic rounded-lg shadow-lg shadow-red-500/20"
      >HOT</span>
    </div>

    <div class="flex flex-col gap-4">
      <!-- Author info -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div class="w-8 h-8 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-center text-xs font-bold text-slate-500 overflow-hidden">
            <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
            <span v-else>{{ post.authorName.charAt(0) }}</span>
          </div>
          <div class="flex flex-col">
            <span class="text-xs font-bold text-slate-900">{{ post.authorName }}</span>
            <div class="flex items-center gap-1 text-[10px] text-slate-400">
              <Clock class="w-3 h-3" />
              <span>{{ formatDate(post.createTime) }}</span>
            </div>
          </div>
        </div>
        
        <span v-if="post.categoryName" class="px-2 py-0.5 bg-slate-50 text-slate-400 text-[10px] font-bold rounded-lg border border-slate-100">
          {{ post.categoryName }}
        </span>
      </div>

      <!-- Content -->
      <router-link :to="'/post/' + post.id" class="flex flex-col gap-2">
        <h3 class="text-lg font-bold text-slate-900 leading-snug group-hover:text-brand-primary transition-colors tracking-tight">
          {{ post.title }}
        </h3>
        <p class="text-sm text-slate-500 line-clamp-2 leading-relaxed">
          {{ post.content }}
        </p>
      </router-link>

      <!-- Stats -->
      <div class="flex items-center justify-between pt-2">
        <div class="flex items-center gap-4">
          <button 
            @click.stop="handleLike"
            :class="['flex items-center gap-1.5 transition-colors', post.isLiked ? 'text-pink-500' : 'text-slate-400 hover:text-brand-primary']"
          >
            <ThumbsUp class="w-4 h-4" :class="{ 'fill-current': post.isLiked }" />
            <span class="text-xs font-semibold">{{ post.likeCount }}</span>
          </button>
          <button class="flex items-center gap-1.5 text-slate-400 hover:text-emerald-500 transition-colors">
            <MessageSquare class="w-4 h-4" />
            <span class="text-xs font-semibold">{{ post.commentCount }}</span>
          </button>
          <button 
            @click.stop="handleCollect"
            :class="['flex items-center gap-1.5 transition-colors', post.isCollected ? 'text-yellow-500' : 'text-slate-400 hover:text-yellow-500']"
          >
            <Star class="w-4 h-4" :class="{ 'fill-current': post.isCollected }" />
            <span class="text-xs font-semibold">{{ post.collectCount }}</span>
          </button>
          <div class="flex items-center gap-1.5 text-slate-300">
            <Eye class="w-4 h-4" />
            <span class="text-xs font-semibold">{{ post.viewCount }}</span>
          </div>
        </div>
        
        <button class="p-2 text-slate-300 hover:text-slate-600 hover:bg-slate-100 rounded-xl transition-all">
          <Share2 class="w-4 h-4" />
        </button>
      </div>
    </div>
    
    <!-- Hover Glass Reflection -->
    <div class="absolute inset-0 pointer-events-none bg-gradient-to-tr from-white/0 via-white/0 to-white/20 opacity-0 group-hover:opacity-100 transition-opacity"></div>
  </div>
</template>
