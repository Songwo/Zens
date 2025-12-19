<script setup lang="ts">
import { onMounted } from 'vue'
import { Toaster } from 'vue-sonner'
import { useUserStore } from '@/store/user'
import { userApi } from '@/api/user'
import { ResultCode } from '@/types'

const userStore = useUserStore()

onMounted(async () => {
  if (userStore.accessToken && !userStore.userInfo) {
    try {
      const res = await userApi.getProfile()
      if (res.code === ResultCode.SUCCESS) {
        userStore.setUserInfo(res.data)
        userStore.setUserId(res.data.id)
      }
    } catch (err) {
      console.error('Failed to auto-login:', err)
      userStore.logout()
    }
  }
})
</script>

<template>
  <div class="app-container min-h-screen bg-slate-50 font-sans text-slate-900">
    <!-- Router View -->
    <router-view v-slot="{ Component }">
      <transition 
        name="fade" 
        mode="out-in"
      >
        <component :is="Component" />
      </transition>
    </router-view>

    <!-- Global Notifications -->
    <Toaster position="top-center" richColors />
  </div>
</template>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
