<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { useUserStore } from '@/store/user'
import { userApi } from '@/api/user'
import { userProfileApi, type UserProfileStats, type UserProfileDetail } from '@/api/userProfile'
import { viewLogApi, type ViewLog } from '@/api/viewLog'
import { toast } from 'vue-sonner'
import { 
  Camera, User, Mail, School, BookOpen, Save, 
  Settings, Heart, Star, Layout, ShieldCheck, 
  GraduationCap, Calendar, MapPin, History, Clock
} from 'lucide-vue-next'

const userStore = useUserStore()
const loading = ref(false)
const activeTab = ref('overview')
const stats = ref<UserProfileStats | null>(null)
const profileDetail = ref<UserProfileDetail | null>(null)
const viewHistory = ref<ViewLog[]>([])

const form = ref({
  nickname: userStore.userInfo?.nickname || '',
  email: userStore.userInfo?.email || '',
  school: userStore.userInfo?.school || '',
  major: userStore.userInfo?.major || '',
  activeRegion: '广州 · 华南理工大学',
  grade: 2022,
  gender: 0,
  interestTags: ''
})

const fetchData = async () => {
    try {
        const [statsRes, profileRes] = await Promise.all([
            userProfileApi.getMyStats(),
            userProfileApi.getMyProfile()
        ])
        stats.value = statsRes.data
        profileDetail.value = profileRes.data
        if (profileRes.data) {
            form.value.activeRegion = profileRes.data.activeRegion || '广州'
        }
        
        // Fetch history
        const historyRes = await viewLogApi.getUserHistory(userStore.userInfo?.id || '')
        viewHistory.value = historyRes.data || []
    } catch (e) {
        console.error(e)
    }
}

const handleUpdate = async () => {
  loading.value = true
  try {
    // 1. Update basic info
    await userApi.updateUserDetails({
        nickname: form.value.nickname,
        avatar: userStore.userInfo?.avatar || 'http://dummy', // DTO requires avatar
        school: form.value.school,
        major: form.value.major,
        grade: form.value.grade,
        gender: form.value.gender,
        interestTags: form.value.interestTags || 'Java,Vue'
    })
    
    // 2. Update profile (active region)
    await userProfileApi.updateMyProfile({
        activeRegion: form.value.activeRegion
    })

    // Update store
    userStore.setUserInfo({
        ...userStore.userInfo,
        nickname: form.value.nickname,
        school: form.value.school,
        major: form.value.major
    })

    toast.success('个人资料已更新 ✨')
  } catch (error) {
    console.error(error)
    toast.error('更新失败')
  } finally {
    loading.value = false
  }
}

const triggerFileInput = () => {
  document.getElementById('avatar-input')?.click()
}

const handleAvatarChange = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return

  try {
    const res = await userApi.updateAvatar(file)
    if (res.data) {
        userStore.setUserInfo({ ...userStore.userInfo, avatar: res.data })
        toast.success('头像上传成功')
    }
  } catch (error) {
    toast.error('上传失败，请稍后再试')
  }
}

const tabs = [
  { id: 'overview', name: '概览', icon: Layout },
  { id: 'history', name: '足迹', icon: History },
  { id: 'edit', name: '编辑', icon: Settings },
  { id: 'security', name: '安全', icon: ShieldCheck },
]

onMounted(() => {
    fetchData()
})

</script>

<template>
  <DefaultLayout :showLeftSidebar="false" :showRightSidebar="false" wide>
    <div class="min-h-screen bg-white">
      <!-- Minimalist Header -->
      <div class="max-w-6xl mx-auto px-6 pt-12 pb-6 flex items-center justify-between border-b border-slate-100">
        <div>
           <h1 class="text-3xl font-black text-slate-900 tracking-tight">个人中心</h1>
           <p class="text-sm text-slate-400 font-bold mt-2">Personal Dashboard</p>
        </div>
        <div class="hidden md:flex gap-2">
           <div class="px-4 py-2 bg-slate-50 text-slate-500 rounded-xl text-xs font-bold border border-slate-100">
             V2.0.4
           </div>
        </div>
      </div>

      <div class="max-w-6xl mx-auto px-6 py-10">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-12">
          
          <!-- Left Sidebar -->
          <div class="lg:col-span-3 lg:sticky lg:top-24 h-fit space-y-8">
            <!-- Profile Info -->
            <div class="text-center">
               <div class="relative inline-block group mb-6">
                  <div class="w-32 h-32 rounded-full bg-slate-50 p-1 border-2 border-slate-100 overflow-hidden cursor-pointer relative">
                    <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" class="w-full h-full object-cover rounded-full group-hover:opacity-90 transition-opacity" />
                    <div v-else class="w-full h-full flex items-center justify-center text-slate-400">
                      <User class="w-12 h-12" />
                    </div>
                    <div 
                      @click="triggerFileInput"
                      class="absolute inset-0 flex items-center justify-center bg-black/5 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                       <Camera class="w-6 h-6 text-white drop-shadow-md" />
                    </div>
                    <input id="avatar-input" type="file" class="hidden" accept="image/*" @change="handleAvatarChange" />
                  </div>
               </div>
               
               <h2 class="text-xl font-black text-slate-900">{{ userStore.userInfo?.nickname || 'Hello World' }}</h2>
               <p class="text-xs font-bold text-slate-400 mt-1">@{{ userStore.userInfo?.username }}</p>
               
               <div class="mt-6 flex flex-wrap justify-center gap-2">
                 <span v-if="userStore.userInfo?.school" class="px-3 py-1 bg-slate-50 text-slate-600 border border-slate-100 rounded-full text-[10px] font-bold">{{ userStore.userInfo.school }}</span>
                 <span class="px-3 py-1 bg-blue-50 text-brand-primary border border-blue-100 rounded-full text-[10px] font-bold">LV.5 贡献者</span>
               </div>
            </div>

            <!-- Vertical Navigation -->
            <div class="flex flex-col gap-1">
               <button 
                  v-for="tab in tabs" 
                  :key="tab.id"
                  @click="activeTab = tab.id"
                  :class="[
                    'flex items-center gap-3 px-5 py-3 rounded-xl text-sm font-bold transition-all text-left',
                    activeTab === tab.id 
                    ? 'bg-slate-900 text-white shadow-lg shadow-slate-200' 
                    : 'text-slate-500 hover:bg-slate-50 hover:text-slate-900'
                  ]"
                >
                  <component :is="tab.icon" class="w-4 h-4" />
                  {{ tab.name }}
                </button>
            </div>
            
            <div class="pt-8 border-t border-slate-100">
               <div class="space-y-3 text-xs text-slate-400 font-bold">
                  <div class="flex items-center gap-3">
                     <MapPin class="w-3 h-3" /> 广州 · 华南理工大学
                  </div>
                  <div class="flex items-center gap-3">
                     <Calendar class="w-3 h-3" /> 加入于 2024.03.12
                  </div>
               </div>
            </div>
          </div>

          <!-- Main Content -->
          <div class="lg:col-span-9 pl-0 lg:pl-10 border-l border-slate-100/0 lg:border-slate-100">
            <!-- Overview Tab -->
            <div v-if="activeTab === 'overview'" class="space-y-10 animate-in fade-in slide-in-from-bottom-5 duration-500">
               <!-- Stats Row -->
               <div class="grid grid-cols-1 sm:grid-cols-3 gap-6">
                  <div class="p-6 rounded-2xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 transition-colors">
                     <div class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">My Posts</div>
                     <div class="text-3xl font-black text-slate-900 flex items-end gap-2">
                        {{ stats?.posts || 0 }} <span class="text-xs text-green-500 font-bold bg-green-50 px-1.5 py-0.5 rounded mb-1">published</span>
                     </div>
                  </div>
                  <div class="p-6 rounded-2xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 transition-colors">
                     <div class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">Total Likes</div>
                     <div class="text-3xl font-black text-slate-900 flex items-end gap-2">
                        {{ stats?.likes || 0 }} <span class="text-xs text-slate-400 font-bold mb-1">hearts</span>
                     </div>
                  </div>
                  <div class="p-6 rounded-2xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 transition-colors">
                     <div class="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-2">Reputation</div>
                     <div class="text-3xl font-black text-slate-900 flex items-end gap-2">
                        {{ stats?.reputation || 0 }} <span class="text-xs text-slate-400 font-bold mb-1">score</span>
                     </div>
                  </div>
               </div>

               <!-- Active Region / Bio Replacement -->
               <div>
                  <h3 class="text-lg font-black text-slate-900 mb-4">Active Region</h3>
                  <div class="p-6 rounded-2xl border border-slate-100 bg-slate-50/50 flex items-center gap-4">
                     <MapPin class="w-5 h-5 text-brand-primary" />
                     <span class="text-sm font-bold text-slate-700">{{ profileDetail?.activeRegion || '未设置活跃地点' }}</span>
                  </div>
               </div>
            </div>

            <!-- History Tab -->
            <div v-if="activeTab === 'history'" class="animate-in fade-in duration-500">
               <h3 class="text-lg font-black text-slate-900 mb-6">Browsing History</h3>
               <div class="space-y-4">
                  <div v-for="log in viewHistory" :key="log.id" class="p-4 rounded-xl border border-slate-100 hover:bg-slate-50 transition-colors flex items-center justify-between group cursor-pointer" @click="$router.push(`/post/${log.postId}`)">
                     <div class="flex items-center gap-4">
                        <div class="w-10 h-10 bg-slate-100 rounded-full flex items-center justify-center text-slate-400 group-hover:bg-white group-hover:text-brand-primary transition-colors">
                           <Clock class="w-5 h-5" />
                        </div>
                        <div>
                           <div class="text-xs font-bold text-slate-400 mb-0.5">Visited on {{ new Date(log.viewTime).toLocaleDateString() }}</div>
                           <div class="text-sm font-bold text-slate-900">Post ID: {{ log.postId }}</div>
                        </div>
                     </div>
                     <Settings class="w-4 h-4 text-slate-300 group-hover:text-slate-500 opacity-0 group-hover:opacity-100 transition-all" />
                  </div>
                  <div v-if="viewHistory.length === 0" class="text-center py-10 text-slate-400 text-sm font-bold">
                     暂无浏览记录
                  </div>
               </div>
            </div>

            <!-- Edit Tab -->
            <div v-if="activeTab === 'edit'" class="animate-in fade-in duration-500 max-w-2xl">
               <h3 class="text-lg font-black text-slate-900 mb-8">Edit Profile</h3>
               
               <div class="space-y-6">
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                     <div class="space-y-2">
                        <label class="text-xs font-bold text-slate-500 uppercase tracking-wide">Nickname</label>
                        <input v-model="form.nickname" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-400 transition-all" />
                     </div>
                     <div class="space-y-2">
                        <label class="text-xs font-bold text-slate-500 uppercase tracking-wide">Email</label>
                        <input v-model="form.email" type="text" disabled class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold text-slate-400 cursor-not-allowed" />
                     </div>
                     <div class="space-y-2">
                        <label class="text-xs font-bold text-slate-500 uppercase tracking-wide">School</label>
                        <input v-model="form.school" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-400 transition-all" />
                     </div>
                     <div class="space-y-2">
                        <label class="text-xs font-bold text-slate-500 uppercase tracking-wide">Major</label>
                        <input v-model="form.major" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-400 transition-all" />
                     </div>
                  </div>
                  
                  <div class="space-y-2">
                        <label class="text-xs font-bold text-slate-500 uppercase tracking-wide">Active Region</label>
                        <input v-model="form.activeRegion" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-slate-900/10 focus:border-slate-400 transition-all" placeholder="e.g. 广州 · 天河" />
                  </div>

                  <div class="pt-4">
                     <button 
                       @click="handleUpdate"
                       :disabled="loading"
                       class="px-8 py-3 bg-slate-900 text-white font-bold rounded-xl hover:bg-slate-800 transition-colors disabled:opacity-50 flex items-center gap-2"
                     >
                       <Save class="w-4 h-4" />
                       Save Changes
                     </button>
                  </div>
               </div>
            </div>

            <!-- Security Tab -->
            <div v-if="activeTab === 'security'" class="animate-in fade-in duration-500 max-w-2xl">
               <h3 class="text-lg font-black text-slate-900 mb-8">Security</h3>
               
               <div class="space-y-4">
                  <div class="p-5 rounded-xl border border-slate-100 flex items-center justify-between hover:bg-slate-50 transition-colors">
                     <div class="flex items-center gap-4">
                        <div class="w-10 h-10 bg-slate-100 rounded-full flex items-center justify-center text-slate-500">
                           <ShieldCheck class="w-5 h-5" />
                        </div>
                        <div>
                           <div class="text-sm font-bold text-slate-900">Password</div>
                           <div class="text-xs text-slate-400">Last changed 3 months ago</div>
                        </div>
                     </div>
                     <button class="text-xs font-bold text-slate-900 border border-slate-200 px-3 py-1.5 rounded-lg hover:bg-white transition-colors">Update</button>
                  </div>
                  
                  <div class="p-5 rounded-xl border border-slate-100 flex items-center justify-between hover:bg-slate-50 transition-colors">
                     <div class="flex items-center gap-4">
                        <div class="w-10 h-10 bg-slate-100 rounded-full flex items-center justify-center text-slate-500">
                           <Mail class="w-5 h-5" />
                        </div>
                        <div>
                           <div class="text-sm font-bold text-slate-900">Email Verification</div>
                           <div class="text-xs text-green-500">Verified</div>
                        </div>
                     </div>
                     <button class="text-xs font-bold text-slate-900 border border-slate-200 px-3 py-1.5 rounded-lg hover:bg-white transition-colors">Change</button>
                  </div>
               </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
