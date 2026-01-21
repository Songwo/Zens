<script setup lang="ts">
import { ref, computed } from 'vue'
import { X, Image as ImageIcon, Loader2, Sparkles, Send } from 'lucide-vue-next'
import { postApi } from '@/api/post'
import { toast } from 'vue-sonner'
import ImageUploader from './ImageUploader.vue'
import { ResultCode } from '@/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits(['update:modelValue', 'success'])

const loading = ref(false)
const extracting = ref(false)
const title = ref('')
const content = ref('')
const categoryId = ref('')
const coverImage = ref('')
const tags = ref('')
const errorMessage = ref('')

const isValid = computed(() => {
  return title.value.trim().length > 0 && 
         content.value.trim().length > 0 && 
         categoryId.value
})

const categories = [
  { id: 'c1', name: '校园头条' },
  { id: 'c2', name: '失物招领' },
  { id: 'c3', name: '表白墙' },
  { id: 'c4', name: '学术交流' },
  { id: 'c5', name: '二手市场' },
  { id: 'c6', name: '活动组织' }
]

const close = () => {
  emit('update:modelValue', false)
  // Reset form
  setTimeout(() => {
    title.value = ''
    content.value = ''
    categoryId.value = ''
    coverImage.value = ''
    tags.value = ''
  }, 300)
}

const handleAIAnalysis = async () => {
  if (!content.value || extracting.value) return
  
  extracting.value = true
  try {
    const res = await postApi.extractTags({
      title: title.value,
      content: content.value
    })
    tags.value = res.data.tags
    toast.success('AI 分析完成')
  } catch (error) {
    // Silent fail
  } finally {
    extracting.value = false
  }
}

const handleSubmit = async () => {
  if (!isValid.value || loading.value) return

  loading.value = true
  errorMessage.value = ''
  
  try {
    const res = await postApi.create({
      title: title.value,
      content: content.value,
      categoryID: categoryId.value,
      coverImage: coverImage.value,
      tags: tags.value,
      status: 1
    })
    
    if (res.code === ResultCode.SUCCESS) {
      toast.success('发布成功')
      emit('success')
      close()
    } else {
      errorMessage.value = res.message || '发布失败'
      toast.error(res.message || '发布失败')
    }
  } catch (error) {
    errorMessage.value = '网络请求失败，请稍后重试'
    toast.error('发布失败')
  } finally {
    loading.value = false
  }
}

const addTag = (e: KeyboardEvent) => {
  const val = (e.target as HTMLInputElement).value.trim()
  if (!val) return
  
  const currentTags = tags.value ? tags.value.split(',') : []
  if (!currentTags.includes(val)) {
    currentTags.push(val)
    tags.value = currentTags.join(',')
  }
  
  (e.target as HTMLInputElement).value = ''
}

const removeTag = (tagToRemove: string) => {
  const currentTags = tags.value.split(',')
  tags.value = currentTags.filter(t => t !== tagToRemove).join(',')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-[1000] flex items-center justify-center p-4">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity" @click="close"></div>

      <!-- Modal -->
      <div class="relative w-full max-w-2xl bg-white rounded-[2rem] shadow-2xl shadow-slate-900/20 overflow-hidden flex flex-col h-[80vh] my-auto animate-in zoom-in-95 duration-200">
        <!-- Header -->
      <div class="px-8 py-6 border-b border-slate-100 flex items-center justify-between bg-white sticky top-0 z-10">
        <h2 class="text-xl font-black text-slate-900 tracking-tight">发布新动态</h2>
        <button 
          @click="close"
          class="w-8 h-8 rounded-full bg-slate-50 flex items-center justify-center text-slate-400 hover:bg-slate-100 hover:text-slate-900 transition-colors"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Body -->
      <div class="p-8 overflow-y-auto custom-scrollbar space-y-8">
        <!-- Title -->
        <div class="space-y-3">
          <label class="text-xs font-black text-slate-400 uppercase tracking-widest">标题</label>
          <input 
            v-model="title"
            type="text" 
            placeholder="写一个吸引人的标题..."
            class="w-full text-2xl font-bold text-slate-900 placeholder:text-slate-300 border-none outline-none bg-transparent p-0"
          >
        </div>

        <!-- Category -->
        <div class="space-y-3">
          <label class="text-xs font-black text-slate-400 uppercase tracking-widest">选择分区</label>
          <div class="flex flex-wrap gap-3">
            <button 
              v-for="cat in categories" 
              :key="cat.id"
              @click="categoryId = cat.id"
              :class="[
                'px-4 py-2 rounded-xl text-xs font-bold transition-all border',
                categoryId === cat.id 
                  ? 'bg-slate-900 text-white border-slate-900 shadow-lg shadow-slate-200' 
                  : 'bg-white text-slate-500 border-slate-200 hover:border-slate-300'
              ]"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>

        <!-- Content -->
        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <label class="text-xs font-black text-slate-400 uppercase tracking-widest">正文内容</label>
            <button 
              v-if="content.length > 10"
              @click="handleAIAnalysis"
              :disabled="extracting"
              class="flex items-center gap-1.5 text-[10px] font-bold text-indigo-500 hover:text-indigo-600 transition-colors disabled:opacity-50"
            >
              <Sparkles class="w-3 h-3" /> {{ extracting ? 'AI 分析中...' : 'AI 辅助标签' }}
            </button>
          </div>
          <textarea 
            v-model="content"
            placeholder="分享你的校园生活、见闻或疑问..."
            class="w-full h-40 resize-none bg-slate-50 border border-slate-200 rounded-2xl p-5 text-sm font-medium focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 focus:bg-white transition-all outline-none"
          ></textarea>
        </div>

        <!-- Image Upload -->
        <div class="space-y-3">
          <label class="text-xs font-black text-slate-400 uppercase tracking-widest">封面图 (可选)</label>
          <ImageUploader v-model="coverImage" />
        </div>

        <!-- Tags -->
        <div class="space-y-3">
          <label class="text-xs font-black text-slate-400 uppercase tracking-widest">标签</label>
          <input 
            type="text" 
            placeholder="输入标签后按回车添加..."
            class="w-full text-sm font-bold text-slate-900 border-0 border-b-2 border-slate-200 focus:border-indigo-600 focus:ring-0 px-0 py-2 bg-transparent placeholder-slate-300 transition-colors"
            @keydown.enter.prevent="addTag"
          >
          <div class="flex flex-wrap gap-2 min-h-[28px]">
            <span 
              v-for="tag in (tags ? tags.split(',') : [])" 
              :key="tag"
              class="inline-flex items-center gap-1 px-2.5 py-1.5 bg-slate-100 text-slate-600 text-[11px] font-bold rounded-lg group hover:bg-rose-50 hover:text-rose-500 transition-colors cursor-pointer"
              @click="removeTag(tag)"
            >
              #{{ tag }}
              <X class="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity" />
            </span>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="p-6 border-t border-slate-100 bg-slate-50/50 flex items-center justify-between gap-4">
        <div class="flex-1">
          <p v-if="errorMessage" class="text-xs font-bold text-rose-500 animate-pulse">
            {{ errorMessage }}
          </p>
        </div>
        <div class="flex items-center gap-4">
          <button 
            @click="close"
            class="px-6 py-3 text-sm font-bold text-slate-500 hover:text-slate-900 transition-colors"
          >
            取消
          </button>
          <button 
            @click="handleSubmit"
            :disabled="!isValid || loading"
            class="flex items-center gap-2 px-8 py-3 bg-slate-900 text-white text-sm font-bold rounded-xl hover:bg-slate-800 transition-all shadow-lg shadow-slate-200 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none"
          >
            <Loader2 v-if="loading" class="w-4 h-4 animate-spin" />
            <Send v-else class="w-4 h-4" />
            发布动态
          </button>
        </div>
      </div>
    </div>
  </div>
  </Teleport>
</template>
