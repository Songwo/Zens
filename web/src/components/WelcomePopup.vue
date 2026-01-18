<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { announcementApi, type Announcement } from '@/api/announcement'
import { PartyPopper, X, Check } from 'lucide-vue-next'

const announcement = ref<Announcement | null>(null)
const show = ref(false)

const checkPopup = async () => {
  try {
    const res = await announcementApi.getPendingPopup()
    if (res.data) {
      announcement.value = res.data
      show.value = true
    }
  } catch (error) {
    // Silent fail
  }
}

const handleClose = async () => {
  if (announcement.value) {
    try {
      await announcementApi.markSeen(announcement.value.id)
    } catch (error) {}
  }
  show.value = false
}

onMounted(checkPopup)
</script>

<template>
  <div v-if="show && announcement" class="fixed inset-0 z-[200] flex items-center justify-center p-6">
    <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-md animate-in fade-in duration-500"></div>
    <div class="bg-white w-full max-w-lg rounded-[3rem] p-12 relative z-10 shadow-2xl animate-in zoom-in-95 slide-in-from-bottom-8 duration-500">
      <div class="absolute -top-12 left-1/2 -translate-x-1/2 w-24 h-24 bg-slate-900 rounded-[2rem] flex items-center justify-center text-white shadow-2xl shadow-slate-200">
        <PartyPopper class="w-12 h-12" />
      </div>
      
      <div class="text-center mt-8">
        <h2 class="text-3xl font-black text-slate-900 tracking-tighter mb-4">{{ announcement.title }}</h2>
        <div class="prose prose-slate max-w-none text-slate-500 font-medium leading-relaxed mb-10" v-html="announcement.content"></div>
        
        <button 
          @click="handleClose"
          class="w-full py-5 bg-slate-900 text-white rounded-2xl text-sm font-black uppercase tracking-widest hover:bg-slate-800 transition-all shadow-xl shadow-slate-200 active:scale-[0.98] flex items-center justify-center gap-3"
        >
          <Check class="w-5 h-5" /> 开启校园之旅
        </button>
      </div>
    </div>
  </div>
</template>
