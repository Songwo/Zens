<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Search, Bell, User, LayoutGrid, Settings, Heart, Star, LogOut, ChevronDown } from 'lucide-vue-next'
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const searchQuery = ref('')
const showDropdown = ref(false)

const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

const closeDropdown = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.user-dropdown-trigger')) {
    showDropdown.value = false
  }
}

const handleLogout = () => {
  userStore.logout()
  router.push('/auth/login')
  showDropdown.value = false
}

const handleSearch = () => {
  if (!searchQuery.value.trim()) return
  router.push({
    path: '/',
    query: { search: searchQuery.value.trim() }
  })
}

onMounted(() => {
  window.addEventListener('click', closeDropdown)
})

onUnmounted(() => {
  window.removeEventListener('click', closeDropdown)
})
</script>

<template>
  <nav class="sticky top-0 z-50 w-full border-b border-white/20 glass px-6 py-3 flex items-center justify-between">
    <div class="flex items-center gap-8">
      <!-- Logo -->
      <router-link to="/" class="flex items-center gap-2 group">
        <div class="w-10 h-10 bg-brand-primary rounded-xl flex items-center justify-center text-white shadow-lg shadow-blue-500/20 group-hover:scale-105 transition-transform">
          <LayoutGrid class="w-6 h-6" />
        </div>
        <span class="text-xl font-bold tracking-tight bg-gradient-to-r from-slate-900 to-slate-600 bg-clip-text text-transparent">
          CampusPulse
        </span>
      </router-link>
      
      <!-- Nav Links -->
      <div class="hidden lg:flex items-center gap-6">
        <router-link 
          to="/" 
          class="text-sm font-bold text-slate-600 hover:text-brand-primary transition-colors flex items-center gap-2"
          active-class="text-brand-primary"
        >
          <LayoutGrid class="w-4 h-4" /> 首页
        </router-link>
      </div>

      <!-- Search Bar -->
      <div class="hidden md:flex items-center relative w-96 group">
        <Search 
          @click="handleSearch"
          class="absolute left-3 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors cursor-pointer" 
        />
        <input 
          v-model="searchQuery"
          type="text" 
          placeholder="搜索热门话题、校园动态..."
          class="w-full bg-slate-100 border-none rounded-2xl py-2 pl-10 pr-4 text-sm focus:ring-2 focus:ring-brand-primary/20 transition-all outline-none"
          @keyup.enter="handleSearch"
        />
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-4">
      <button class="p-2 hover:bg-slate-100 rounded-xl text-slate-500 transition-colors relative">
        <Bell class="w-5 h-5" />
        <span class="absolute top-2 right-2 w-2 h-2 bg-red-500 rounded-full border-2 border-white"></span>
      </button>

      <template v-if="userStore.isLoggedIn">
        <div class="relative user-dropdown-trigger">
          <button 
            @click="toggleDropdown"
            class="flex items-center gap-2 p-1 pl-1 pr-3 hover:bg-slate-100 rounded-full transition-colors border border-slate-200"
          >
            <div class="w-8 h-8 bg-slate-200 rounded-full flex items-center justify-center overflow-hidden border border-slate-200 shadow-sm">
              <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" class="w-full h-full object-cover" />
              <User v-else class="w-5 h-5 text-slate-500" />
            </div>
            <span class="text-sm font-semibold text-slate-700">{{ userStore.userInfo?.nickname || '账户' }}</span>
            <ChevronDown class="w-4 h-4 text-slate-400 transition-transform" :class="{ 'rotate-180': showDropdown }" />
          </button>

          <!-- Dropdown -->
          <div 
            v-if="showDropdown"
            class="absolute right-0 mt-2 w-56 bg-white rounded-2xl shadow-xl shadow-slate-200/50 border border-slate-100 py-2 z-50 animate-in fade-in zoom-in duration-200"
          >
            <router-link 
                to="/profile" 
                @click="showDropdown = false"
                class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 hover:text-brand-primary transition-colors"
            >
                <User class="w-4 h-4" /> 个人主页
            </router-link>
            <router-link 
                to="/my/likes" 
                @click="showDropdown = false"
                class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 hover:text-brand-primary transition-colors"
            >
                <Heart class="w-4 h-4" /> 我的点赞
            </router-link>
            <router-link 
                to="/my/collections" 
                @click="showDropdown = false"
                class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 hover:text-brand-primary transition-colors"
            >
                <Star class="w-4 h-4" /> 我的收藏
            </router-link>
            <router-link 
                to="/settings" 
                @click="showDropdown = false"
                class="flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 hover:text-brand-primary transition-colors"
            >
                <Settings class="w-4 h-4" /> 系统设置
            </router-link>
            <div class="h-px bg-slate-100 my-1 mx-2"></div>
            <button 
                @click="handleLogout"
                class="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-500 hover:bg-red-50 transition-colors"
            >
                <LogOut class="w-4 h-4" /> 退出登录
            </button>
          </div>
        </div>
      </template>
      <template v-else>
        <router-link 
          to="/auth/login"
          class="px-5 py-2 bg-brand-primary text-white text-sm font-semibold rounded-xl hover:bg-blue-700 shadow-lg shadow-blue-500/20 transition-all active:scale-95"
        >
          立即登录
        </router-link>
      </template>
    </div>
  </nav>
</template>
