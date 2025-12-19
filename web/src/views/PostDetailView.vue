<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { recommendApi } from '@/api/recommend'
import type { Post, Comment } from '@/types'
import { toast } from 'vue-sonner'
import MarkdownIt from 'markdown-it'
import { Clock, Eye, ThumbsUp, MessageSquare, Share2, Star } from 'lucide-vue-next'
import UserCardPopover from '@/components/UserCardPopover.vue'

const route = useRoute()
const md = new MarkdownIt()

const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const similarPosts = ref<Post[]>([])
const loading = ref(true)
const commentContent = ref('')

const fetchPost = async () => {
  try {
    const id = route.params.id as string
    const res = await postApi.getDetail(id)
    post.value = res.data
    
    // Fetch comments
    const commentRes = await commentApi.getByPostId(id)
    comments.value = commentRes.data.records
    
    // Fetch Similar Posts
    const similarRes = await recommendApi.getSimilar(id)
    similarPosts.value = similarRes.data || []
  } catch (error) {
    toast.error('获取详情失败')
  } finally {
    loading.value = false
  }
}

const handleLike = async () => {
  if (!post.value) return
  try {
    await postApi.like(post.value.id)
    post.value.isLiked = !post.value.isLiked
    post.value.likeCount += post.value.isLiked ? 1 : -1
    toast.success(post.value.isLiked ? '点赞成功' : '取消点赞')
  } catch (error) {
    toast.error('操作失败')
  }
}

const handleCollect = async () => {
  if (!post.value) return
  try {
    await postApi.collect(post.value.id)
    post.value.isCollected = !post.value.isCollected
    post.value.collectCount += post.value.isCollected ? 1 : -1
    toast.success(post.value.isCollected ? '收藏成功' : '取消收藏')
  } catch (error) {
    toast.error('操作失败')
  }
}

const handleComment = async () => {
  if (!post.value || !commentContent.value.trim()) return
  try {
    await commentApi.add({
      postId: post.value.id,
      content: commentContent.value,
      isAnonymous: 0
    })
    toast.success('评论成功')
    commentContent.value = ''
    // Refresh comments
    const commentRes = await commentApi.getByPostId(post.value.id)
    comments.value = commentRes.data.records
    post.value.commentCount++
  } catch (error) {
    toast.error('评论失败')
  }
}

onMounted(() => {
  fetchPost()
})
</script>

<template>
  <DefaultLayout>
    <div class="py-6 px-4">
      <div v-if="loading" class="animate-pulse space-y-6">
        <div class="h-10 bg-slate-100 rounded-xl w-3/4"></div>
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 bg-slate-100 rounded-full"></div>
          <div class="h-4 bg-slate-100 rounded w-1/4"></div>
        </div>
        <div class="space-y-3">
          <div class="h-4 bg-slate-50 rounded w-full"></div>
          <div class="h-4 bg-slate-50 rounded w-full"></div>
          <div class="h-4 bg-slate-50 rounded w-5/6"></div>
        </div>
      </div>

      <div v-else-if="post" class="pb-20">
        <!-- Header -->
        <h1 class="text-2xl font-black text-slate-900 mb-4">{{ post.title }}</h1>
        
        <div class="flex items-center justify-between mb-8 pb-6 border-b border-slate-100">
            <div class="flex items-center gap-3">
              <UserCardPopover :user="{ id: post.userId, nickname: post.authorName, avatar: post.authorAvatar }">
                <div class="flex items-center gap-3 cursor-default">
                  <div class="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center overflow-hidden cursor-pointer hover:ring-2 hover:ring-brand-primary/20 transition-all">
                    <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
                    <span v-else class="text-xs font-bold text-slate-500">{{ post.authorName?.charAt(0) || 'U' }}</span>
                  </div>
                  <div class="text-left">
                    <div class="text-sm font-bold text-slate-900 cursor-pointer hover:text-brand-primary transition-colors">{{ post.authorName }}</div>
                    <div class="flex items-center gap-2 text-xs text-slate-400 mt-0.5">
                      <span>{{ new Date(post.createTime).toLocaleString() }}</span>
                      <span>·</span>
                      <span>{{ post.locationName || '未知地点' }}</span>
                    </div>
                  </div>
                </div>
              </UserCardPopover>
            </div>
          
          <div class="flex gap-2">
            <span v-if="post.categoryName" class="px-3 py-1 bg-slate-50 text-slate-500 text-xs font-bold rounded-full">
              {{ post.categoryName }}
            </span>
          </div>
        </div>

        <!-- Content -->
        <div class="prose prose-slate max-w-none mb-10" v-html="md.render(post.content)"></div>

        <!-- Actions -->
        <div class="flex items-center justify-between">
          <div class="flex gap-4">
            <button 
              @click="handleLike"
              :class="['flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all', post.isLiked ? 'bg-pink-50 text-pink-500' : 'bg-slate-50 text-slate-500 hover:bg-slate-100']"
            >
              <ThumbsUp class="w-4 h-4" :class="{ 'fill-current': post.isLiked }" />
              {{ post.likeCount }}
            </button>
            <button 
              @click="handleCollect"
              :class="['flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all', post.isCollected ? 'bg-yellow-50 text-yellow-500' : 'bg-slate-50 text-slate-500 hover:bg-slate-100']"
            >
              <Star class="w-4 h-4" :class="{ 'fill-current': post.isCollected }" />
              {{ post.collectCount }}
            </button>
          </div>
          <span class="text-xs font-bold text-slate-400">{{ post.viewCount }} 阅读</span>
        </div>

        <!-- Comment Section -->
        <div class="mt-12 pt-8 border-t border-slate-100">
          <h3 class="text-lg font-bold text-slate-900 mb-6">评论 ({{ post.commentCount }})</h3>
          
          <!-- Input -->
          <div class="flex gap-4 mb-10">
            <div class="w-10 h-10 rounded-full bg-slate-100 flex-shrink-0 overflow-hidden">
                <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
            </div>
            <div class="flex-1">
              <textarea 
                v-model="commentContent"
                placeholder="写下你的想法..."
                class="w-full bg-slate-50 border border-slate-200 rounded-xl p-4 text-sm focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary outline-none transition-all resize-none h-24"
              ></textarea>
              <div class="flex justify-end mt-2">
                <button 
                  @click="handleComment"
                  class="px-6 py-2 bg-brand-primary text-white text-xs font-bold rounded-xl hover:bg-blue-700 transition-colors"
                >
                  发表评论
                </button>
              </div>
            </div>
          </div>

          <!-- List -->
          <div class="space-y-6">
            <div v-for="comment in comments" :key="comment.id" class="flex gap-4 group/comment">
              <UserCardPopover 
                :user="{ 
                  id: comment.userId, 
                  nickname: comment.nickname, 
                  avatar: comment.userAvatar 
                }"
              >
                <div class="flex gap-4 text-left">
                  <div class="w-10 h-10 rounded-full bg-slate-100 flex-shrink-0 overflow-hidden cursor-pointer hover:ring-2 hover:ring-brand-primary/20 transition-all">
                    <img v-if="comment.userAvatar" :src="comment.userAvatar" class="w-full h-full object-cover" />
                    <div v-else class="w-full h-full flex items-center justify-center bg-slate-100 text-slate-400 font-bold text-xs uppercase">{{ comment.nickname?.charAt(0) || 'A' }}</div>
                  </div>
                  
                  <div class="flex-1">
                    <div class="flex items-center justify-between mb-1">
                      <div class="flex items-center gap-2">
                        <span class="text-sm font-bold text-slate-900 cursor-pointer hover:text-brand-primary transition-colors">{{ comment.nickname }}</span>
                        <span v-if="comment.replyUserNickname" class="text-xs text-slate-400">回复 <span class="text-slate-600 font-bold">@{{ comment.replyUserNickname }}</span></span>
                      </div>
                      <span class="text-xs text-slate-400">{{ new Date(comment.createTime).toLocaleDateString() }}</span>
                    </div>
                    <p class="text-sm text-slate-600 leading-relaxed">{{ comment.content }}</p>
                    
                    <div class="flex items-center gap-4 mt-2">
                      <button class="flex items-center gap-1 text-xs text-slate-400 hover:text-brand-primary transition-colors font-bold">
                        <ThumbsUp class="w-3 h-3" /> {{ comment.likeCount }}
                      </button>
                      <button class="text-xs text-slate-400 hover:text-brand-primary transition-colors font-bold">回复</button>
                    </div>
                  </div>
                </div>
              </UserCardPopover>
            </div>
          </div>
        </div>

        <!-- Similar Posts Recommendation -->
        <div v-if="similarPosts.length > 0" class="mt-16 pt-8 border-t border-slate-100">
             <h3 class="text-lg font-bold text-slate-900 mb-6 flex items-center gap-2">
                <div class="w-1 h-5 bg-brand-primary rounded-full"></div>
                猜你喜欢
             </h3>
             <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <router-link 
                  v-for="post in similarPosts" 
                  :key="post.id" 
                  :to="`/post/${post.id}`"
                  class="bg-white p-5 rounded-2xl border border-slate-100 hover:shadow-lg hover:shadow-slate-200/40 transition-all group"
                >
                   <h4 class="font-bold text-slate-800 mb-2 truncate group-hover:text-brand-primary transition-colors">{{ post.title }}</h4>
                   <p class="text-xs text-slate-400 line-clamp-2 mb-3">{{ post.summary || post.content.substring(0, 50) }}...</p>
                   <div class="flex items-center gap-2 text-[10px] text-slate-400 font-bold">
                      <span class="bg-slate-50 px-2 py-0.5 rounded">{{ post.categoryName }}</span>
                      <span>{{ post.viewCount }} 阅读</span>
                   </div>
                </router-link>
             </div>
        </div>

      </div>
    </div>
  </DefaultLayout>
</template>
