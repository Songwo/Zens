<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { useUiStore } from '@/store/ui'
import { userApi } from '@/api/user'
import { ResultCode } from '@/types'
import AppearanceDock from '@/components/ui/AppearanceDock.vue'
import PostComposerModal from '@/components/compose/PostComposerModal.vue'

const userStore = useUserStore()
const uiStore = useUiStore()

onMounted(async () => {
  // Song：说明
  uiStore.applyUiSettings()

  if (userStore.accessToken && !userStore.userInfo) {
    try {
      const res = await userApi.getProfile()
      if (res.code === ResultCode.SUCCESS) {
        userStore.setUserInfo(res.data)
        userStore.setUserId(res.data.id)
      }
    } catch (err: any) {
      console.error('Failed to auto-login:', err)
      const status = err?.response?.status
      if (status === 401 || status === 403) {
        userStore.logout()
      }
    }
  }
})
</script>

<template>
  <div class="app-container">
    <router-view v-slot="{ Component, route }">
      <transition name="fade" mode="out-in">
        <keep-alive>
          <component :is="Component" v-if="route.meta.keepAlive" :key="route.path" />
        </keep-alive>
      </transition>
      <transition name="fade" mode="out-in">
        <component :is="Component" v-if="!route.meta.keepAlive" :key="route.path" />
      </transition>
    </router-view>

    <!-- Global Appearance Dock -->
    <AppearanceDock />

    <!-- Global Post Composer Modal -->
    <PostComposerModal />
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
