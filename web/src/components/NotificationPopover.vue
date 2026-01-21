<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { notificationApi, type Notification } from '@/api/notification'
import { useRouter } from 'vue-router'
import { Bell, MessageSquare, Heart, Star, Info, CheckCheck } from 'lucide-vue-next'
import { toast } from 'vue-sonner'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits(['close', 'read'])

const router = useRouter()
const notifications = ref<Notification[]>([])
const loading = ref(false)
const total = ref(0)

const fetchNotifications = async () => {
  loading.value = true
  try {
    const res = await notificationApi.getList(1, 20)
    notifications.value = res.data.records
    total.value = res.data.total
  } catch (error) {
    // Silent fail
  } finally {
    loading.value = false
  }
}

const handleRead = async (notification: Notification) => {
  if (notification.status === 0) {
    try {
      await notificationApi.markAsRead(notification.id)
      notification.status = 1
      emit('read')
    } catch (e) {}
  }

  if (notification.relatedId && notification.type === 2) {
    router.push(`/post/${notification.relatedId}`)
    emit('close')
  }
}

const handleReadAll = async () => {
  try {
    await notificationApi.markAllAsRead()
    notifications.value.forEach(n => n.status = 1)
    emit('read')
    toast.success('全部标记为已读')
  } catch (e) {
    toast.error('操作失败')
  }
}

const getIcon = (type: number) => {
  switch (type) {
    case 1: return Info
    case 2: return MessageSquare
    case 3: return Heart
    case 4: return Star
    default: return Bell
  }
}

const getIconColor = (type: number) => {
  switch (type) {
    case 1: return 'text-blue-500 bg-blue-50'
    case 2: return 'text-indigo-500 bg-indigo-50'
    case 3: return 'text-rose-500 bg-rose-50'
    case 4: return 'text-amber-500 bg-amber-50'
    default: return 'text-slate-500 bg-slate-50'
  }
}

onMounted(() => {
  fetchNotifications()
})

defineExpose({ refresh: fetchNotifications })
</script>

<template>
  <div 
    v-if="show"
    class="absolute top-full right-0 mt-3 w-80 md:w-96 bg-white rounded-2xl shadow-xl shadow-slate-200/60 border border-slate-100 z-50 flex flex-col max-h-[80vh] animate-in fade-in slide-in-from-top-2 duration-200"
  >
    <!-- Header -->
    <div class="px-5 py-4 border-b border-slate-50 flex items-center justify-between">
      <h3 class="font-bold text-slate-900">消息通知</h3>
      <button 
        @click="handleReadAll"
        class="text-xs font-bold text-slate-400 hover:text-indigo-600 transition-colors flex items-center gap-1"
      >
        <CheckCheck class="w-3.5 h-3.5" /> 全部已读
      </button>
    </div>

    <!-- List -->
    <div class="flex-1 overflow-y-auto custom-scrollbar p-2">
      <div v-if="loading" class="py-10 text-center">
        <div class="w-6 h-6 border-2 border-slate-200 border-t-slate-900 rounded-full animate-spin mx-auto"></div>
      </div>
      
      <div v-else-if="notifications.length === 0" class="py-12 text-center space-y-3">
        <div class="w-12 h-12 bg-slate-50 rounded-full flex items-center justify-center mx-auto text-slate-300">
          <Bell class="w-6 h-6" />
        </div>
        <p class="text-xs font-bold text-slate-400">暂无新消息</p>
      </div>

      <div v-else class="space-y-1">
        <button
          v-for="item in notifications"
          :key="item.id"
          @click="handleRead(item)"
          class="w-full text-left p-3 rounded-xl hover:bg-slate-50 transition-colors flex gap-3 relative group"
        >
          <div v-if="item.status === 0" class="absolute top-4 right-4 w-2 h-2 bg-rose-500 rounded-full"></div>
          
          <!-- Avatar/Icon -->
          <div class="flex-shrink-0">
            <img 
              v-if="item.senderAvatar" 
              :src="item.senderAvatar" 
              class="w-10 h-10 rounded-full object-cover border border-slate-100"
            />
            <div 
              v-else 
              class="w-10 h-10 rounded-full flex items-center justify-center"
              :class="getIconColor(item.type)"
            >
              <component :is="getIcon(item.type)" class="w-5 h-5" />
            </div>
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-0.5">
              <span class="text-sm font-bold text-slate-900 truncate">{{ item.title }}</span>
              <span class="text-[10px] text-slate-400 font-medium">{{ new Date(item.createTime).toLocaleDateString() }}</span>
            </div>
            <p class="text-xs text-slate-500 line-clamp-2 leading-relaxed">{{ item.content }}</p>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>
