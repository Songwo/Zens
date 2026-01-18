<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { recommendApi } from '@/api/recommend'
import type { Post, Comment, RecommendPost } from '@/types'
import { toast } from 'vue-sonner'
import MarkdownIt from 'markdown-it'
import { Clock, Eye, ThumbsUp, MessageSquare, Share2, Star, Sparkles } from 'lucide-vue-next'
import UserCardPopover from '@/components/UserCardPopover.vue'

const route = useRoute()
const md = new MarkdownIt()

const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const similarPosts = ref<RecommendPost[]>([])
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
  <DefaultLayout wide isFluid>
    <div class="py-6 font-sans">
      <div v-if="loading" class="animate-pulse space-y-8">
        <div class="h-10 bg-slate-100 rounded-2xl w-3/4"></div>
        <div class="flex items-center gap-4">
          <div class="w-12 h-12 bg-slate-100 rounded-full"></div>
          <div class="space-y-2 flex-1">
            <div class="h-4 bg-slate-100 rounded w-1/4"></div>
            <div class="h-3 bg-slate-100 rounded w-1/6"></div>
          </div>
        </div>
        <div class="space-y-4">
          <div class="h-4 bg-slate-50 rounded w-full"></div>
          <div class="h-4 bg-slate-50 rounded w-full"></div>
          <div class="h-4 bg-slate-50 rounded w-full"></div>
          <div class="h-4 bg-slate-50 rounded w-2/3"></div>
        </div>
      </div>

      <div v-else-if="post" class="pb-20">
        <!-- Post Header -->
        <div class="bg-white border border-slate-200 rounded-[2rem] p-8 md:p-10 mb-6 shadow-sm">
          <h1 class="text-3xl font-bold text-slate-900 leading-tight mb-8 tracking-tight">
            {{ post.title }}
          </h1>
          
          <div class="flex flex-wrap items-center justify-between gap-6 pb-8 border-b border-slate-100">
            <div class="flex items-center gap-4">
              <UserCardPopover :user="{ id: post.userId, nickname: post.authorName, avatar: post.authorAvatar }">
                <div class="flex items-center gap-4 cursor-default">
                  <div class="w-12 h-12 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center overflow-hidden hover:ring-4 hover:ring-slate-900/5 transition-all">
                    <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
                    <span v-else class="text-sm font-bold text-slate-400">{{ post.authorName?.charAt(0) || 'U' }}</span>
                  </div>
                  <div class="text-left">
                    <div class="text-base font-bold text-slate-900 hover:text-slate-600 transition-colors cursor-pointer">
                      {{ post.authorName }}
                    </div>
                    <div class="flex items-center gap-2 text-xs font-bold text-slate-400 mt-1 uppercase tracking-widest">
                      <span>{{ new Date(post.createTime).toLocaleString() }}</span>
                      <span class="text-slate-200">|</span>
                      <span>{{ post.locationName || 'CAMPUS' }}</span>
                    </div>
                  </div>
                </div>
              </UserCardPopover>
            </div>
            
            <div class="flex items-center gap-3">
              <span v-if="post.categoryName" class="px-4 py-1.5 bg-slate-50 border border-slate-100 text-slate-500 text-[10px] font-black uppercase tracking-widest rounded-xl">
                {{ post.categoryName }}
              </span>
              <div class="flex items-center gap-1.5 text-slate-400 bg-slate-50 px-3 py-1.5 rounded-xl border border-slate-100">
                <Eye class="w-4 h-4" />
                <span class="text-[10px] font-black">{{ post.viewCount }}</span>
              </div>
            </div>
          </div>

          <!-- Main Content Body -->
          <div class="mt-10">
            <div class="prose prose-slate max-w-none 
              prose-headings:text-slate-900 prose-headings:font-bold 
              prose-p:text-slate-600 prose-p:leading-relaxed prose-p:text-base
              prose-strong:text-slate-900 prose-strong:font-bold
              prose-img:rounded-2xl prose-img:border prose-img:border-slate-100
              prose-a:text-slate-900 prose-a:font-bold hover:prose-a:text-slate-600 transition-colors" 
              v-html="md.render(post.content)"
            ></div>
          </div>

          <!-- Post Footer Actions -->
          <div class="flex items-center justify-between mt-12 pt-8 border-t border-slate-100">
            <div class="flex gap-4">
              <button 
                @click="handleLike"
                :class="[
                  'flex items-center gap-2.5 px-6 py-3 rounded-2xl text-sm font-bold transition-all active:scale-95', 
                  post.isLiked ? 'bg-rose-50 text-rose-500 border border-rose-100' : 'bg-slate-50 text-slate-500 border border-slate-100 hover:bg-slate-100 hover:border-slate-200'
                ]"
              >
                <ThumbsUp class="w-4 h-4" :class="{ 'fill-current': post.isLiked }" />
                {{ post.likeCount }}
              </button>
              <button 
                @click="handleCollect"
                :class="[
                  'flex items-center gap-2.5 px-6 py-3 rounded-2xl text-sm font-bold transition-all active:scale-95', 
                  post.isCollected ? 'bg-amber-50 text-amber-600 border border-amber-100' : 'bg-slate-50 text-slate-500 border border-slate-100 hover:bg-slate-100 hover:border-slate-200'
                ]"
              >
                <Star class="w-4 h-4" :class="{ 'fill-current': post.isCollected }" />
                {{ post.collectCount }}
              </button>
            </div>
            
            <button class="p-3 bg-slate-50 text-slate-400 border border-slate-100 rounded-2xl hover:bg-slate-100 hover:text-slate-600 transition-all">
              <Share2 class="w-5 h-5" />
            </button>
          </div>
        </div>

        <!-- Comment Section -->
        <div class="bg-white border border-slate-200 rounded-[2rem] p-8 md:p-10 shadow-sm">
          <div class="flex items-center gap-3 mb-10">
            <MessageSquare class="w-6 h-6 text-slate-900" />
            <h3 class="text-xl font-bold text-slate-900 tracking-tight">全部评论 <span class="text-slate-400 font-mono ml-2">{{ post.commentCount }}</span></h3>
          </div>
          
          <!-- Comment Input Area -->
          <div class="flex gap-5 mb-12">
            <div class="w-12 h-12 rounded-full bg-slate-100 border border-slate-200 flex-shrink-0 overflow-hidden shadow-sm">
                <img v-if="post.authorAvatar" :src="post.authorAvatar" class="w-full h-full object-cover" />
            </div>
            <div class="flex-1 space-y-3">
              <textarea 
                v-model="commentContent"
                placeholder="尊重是交流的基础，发表你的精彩见解..."
                class="w-full bg-slate-50 border border-slate-200 rounded-2xl p-5 text-sm font-medium focus:ring-4 focus:ring-slate-900/5 focus:border-slate-900 focus:bg-white outline-none transition-all resize-none h-32 placeholder:text-slate-400"
              ></textarea>
              <div class="flex justify-end">
                <button 
                  @click="handleComment"
                  class="px-8 py-3 bg-slate-900 text-white text-sm font-bold rounded-xl hover:bg-slate-800 transition-all shadow-lg shadow-slate-200 active:scale-95"
                >
                  发表评论
                </button>
              </div>
            </div>
          </div>

          <!-- Comments List -->
          <div class="space-y-10">
            <div v-for="comment in comments" :key="comment.id" class="group/comment">
              <UserCardPopover 
                :user="{ 
                  id: comment.userId, 
                  nickname: comment.nickname, 
                  avatar: comment.userAvatar 
                }"
              >
                <div class="flex gap-5 text-left">
                  <div class="w-12 h-12 rounded-full bg-slate-50 border border-slate-200 flex-shrink-0 overflow-hidden cursor-pointer hover:ring-4 hover:ring-slate-900/5 transition-all">
                    <img v-if="comment.userAvatar" :src="comment.userAvatar" class="w-full h-full object-cover" />
                    <div v-else class="w-full h-full flex items-center justify-center text-slate-400 font-bold text-xs uppercase">{{ comment.nickname?.charAt(0) || 'A' }}</div>
                  </div>
                  
                  <div class="flex-1 pb-10 border-b border-slate-50 last:border-0 last:pb-0">
                    <div class="flex items-center justify-between mb-2">
                      <div class="flex items-center gap-3">
                        <span class="text-sm font-bold text-slate-900 cursor-pointer hover:text-slate-600 transition-colors">{{ comment.nickname }}</span>
                        <span v-if="comment.replyUserNickname" class="text-[10px] bg-slate-100 text-slate-500 px-2 py-0.5 rounded-lg font-bold">
                          回复 <span class="text-slate-900">@{{ comment.replyUserNickname }}</span>
                        </span>
                        <span class="text-[10px] font-bold text-slate-300 uppercase tracking-tighter">{{ new Date(comment.createTime).toLocaleDateString() }}</span>
                      </div>
                    </div>
                    <p class="text-sm text-slate-600 leading-relaxed font-medium mb-4">{{ comment.content }}</p>
                    
                    <div class="flex items-center gap-6">
                      <button class="flex items-center gap-1.5 text-xs text-slate-400 hover:text-rose-500 transition-colors font-bold">
                        <ThumbsUp class="w-3.5 h-3.5" /> {{ comment.likeCount }}
                      </button>
                      <button class="text-xs text-slate-400 hover:text-slate-900 transition-colors font-bold">回复</button>
                    </div>
                  </div>
                </div>
              </UserCardPopover>
            </div>
            
            <div v-if="comments.length === 0" class="text-center py-12">
              <div class="text-slate-300 text-sm font-medium italic">暂无评论，快来抢占沙发吧！</div>
            </div>
          </div>
        </div>

        <!-- Similar Content Recommendation -->
        <div v-if="similarPosts.length > 0" class="mt-12">
             <div class="flex items-center gap-3 mb-6 px-4">
                <Sparkles class="w-5 h-5 text-slate-900" />
                <h3 class="text-lg font-bold text-slate-900 tracking-tight">猜你喜欢</h3>
             </div>
             <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                <router-link 
                  v-for="post in similarPosts" 
                  :key="post.id" 
                  :to="`/post/${post.id}`"
                  class="bg-white p-6 rounded-[2rem] border border-slate-200 hover:border-slate-400 hover:shadow-xl hover:shadow-slate-200/50 transition-all group relative"
                >
                   <div v-if="post.recommendReason" class="absolute -top-2.5 left-6 px-2 py-0.5 bg-slate-900 text-white text-[9px] font-black uppercase tracking-widest rounded shadow-lg">
                      {{ post.recommendReason }}
                   </div>
                   <h4 class="font-bold text-slate-800 text-base mb-3 truncate group-hover:text-slate-900 transition-colors tracking-tight">{{ post.title }}</h4>
                   <p class="text-xs text-slate-500 line-clamp-2 mb-4 leading-relaxed">{{ post.summary || post.content.substring(0, 50) }}...</p>
                   <div class="flex items-center justify-between">
                      <div class="flex items-center gap-2">
                        <span class="px-2 py-0.5 bg-slate-50 border border-slate-100 text-[9px] font-black text-slate-400 uppercase tracking-widest rounded-lg">
                          {{ post.categoryName }}
                        </span>
                      </div>
                      <div class="flex items-center gap-1 text-[10px] font-bold text-slate-300 uppercase tracking-tighter">
                        <Eye class="w-3 h-3" />
                        <span>{{ post.viewCount }}</span>
                      </div>
                   </div>
                </router-link>
             </div>
        </div>

      </div>
    </div>
  </DefaultLayout>
</template>
