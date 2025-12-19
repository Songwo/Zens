<script setup lang="ts">
import { ref } from 'vue'
import { User, MessageCircle, UserPlus, Heart, Hash } from 'lucide-vue-next'

interface Props {
  user: {
    id: string
    nickname: string
    avatar?: string
    school?: string
    major?: string
    grade?: number
    role?: number
  }
}

const props = defineProps<Props>()
const isHovered = ref(false)

</script>

<template>
  <div class="relative inline-block group" @mouseenter="isHovered = true" @mouseleave="isHovered = false">
    <!-- Trigger -->
    <slot />
    
    <!-- Card -->
    <div 
      class="absolute left-0 top-full mt-2 z-50 w-72 bg-white rounded-2xl shadow-xl shadow-slate-200/50 border border-slate-100 p-5 transform transition-all duration-300 origin-top-left opacity-0 translate-y-2 pointer-events-none group-hover:opacity-100 group-hover:translate-y-0 group-hover:pointer-events-auto"
    >
      <div class="flex items-start gap-4 mb-4">
        <div class="w-14 h-14 rounded-full bg-slate-100 border-2 border-white shadow-sm overflow-hidden flex-shrink-0">
          <img v-if="props.user.avatar" :src="props.user.avatar" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full flex items-center justify-center text-slate-400 bg-slate-50">
            <User class="w-6 h-6" />
          </div>
        </div>
        <div class="flex-1 min-w-0">
          <h4 class="text-base font-bold text-slate-800 truncate">{{ props.user.nickname }}</h4>
          <div class="flex flex-wrap gap-2 mt-1.5">
            <span v-if="props.user.school" class="px-2 py-0.5 bg-blue-50 text-blue-600 text-[10px] font-bold rounded-md">{{ props.user.school }}</span>
            <span v-if="props.user.major" class="px-2 py-0.5 bg-slate-100 text-slate-500 text-[10px] font-bold rounded-md">{{ props.user.major }}</span>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-3 gap-2 mb-4">
        <div class="text-center p-2 bg-slate-50 rounded-xl">
          <div class="text-xs font-bold text-slate-900">128</div>
          <div class="text-[10px] text-slate-400 font-medium">获赞</div>
        </div>
        <div class="text-center p-2 bg-slate-50 rounded-xl">
          <div class="text-xs font-bold text-slate-900">42</div>
          <div class="text-[10px] text-slate-400 font-medium">关注</div>
        </div>
        <div class="text-center p-2 bg-slate-50 rounded-xl">
          <div class="text-xs font-bold text-slate-900">356</div>
          <div class="text-[10px] text-slate-400 font-medium">粉丝</div>
        </div>
      </div>

      <div class="flex gap-2">
        <button class="flex-1 py-1.5 bg-brand-primary text-white text-xs font-bold rounded-lg hover:bg-blue-700 transition-colors flex items-center justify-center gap-1.5">
          <UserPlus class="w-3.5 h-3.5" /> 关注
        </button>
        <button class="flex-1 py-1.5 bg-slate-100 text-slate-600 text-xs font-bold rounded-lg hover:bg-slate-200 transition-colors flex items-center justify-center gap-1.5">
          <MessageCircle class="w-3.5 h-3.5" /> 私信
        </button>
      </div>
    </div>
  </div>
</template>
