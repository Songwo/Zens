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
  <DefaultLayout wide isFluid>
    <div class="py-6 font-sans">
      <div class="max-w-6xl">
        <!-- Minimalist Header -->
        <div class="flex items-center justify-between mb-10 pb-6 border-b border-slate-200">
          <div>
             <h1 class="text-2xl font-black text-slate-900 tracking-tight">个人中心</h1>
             <p class="text-[11px] text-slate-400 font-black uppercase tracking-widest mt-1">Personal Management Center</p>
          </div>
          <div class="hidden md:flex gap-2">
             <div class="px-3 py-1 bg-slate-900 text-white rounded-lg text-[10px] font-black tracking-widest uppercase">
               Stable V2.0
             </div>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-12 gap-12">
          
          <!-- Left Sidebar (Internal) -->
          <div class="lg:col-span-3 space-y-8">
            <!-- Profile Info -->
            <div class="flex flex-col items-center text-center">
               <div class="relative group mb-6">
                  <div class="w-32 h-32 rounded-3xl bg-slate-50 p-1 border border-slate-100 overflow-hidden cursor-pointer relative shadow-xl shadow-slate-200/50">
                    <img v-if="userStore.userInfo?.avatar" :src="userStore.userInfo.avatar" class="w-full h-full object-cover rounded-[1.25rem] group-hover:opacity-90 transition-opacity" />
                    <div v-else class="w-full h-full flex items-center justify-center text-slate-300">
                      <User class="w-12 h-12" />
                    </div>
                    <div 
                      @click="triggerFileInput"
                      class="absolute inset-0 flex items-center justify-center bg-black/40 rounded-[1.25rem] opacity-0 group-hover:opacity-100 transition-opacity"
                    >
                       <Camera class="w-6 h-6 text-white" />
                    </div>
                    <input id="avatar-input" type="file" class="hidden" accept="image/*" @change="handleAvatarChange" />
                  </div>
               </div>
               
               <h2 class="text-xl font-black text-slate-900 tracking-tight">{{ userStore.userInfo?.nickname || '校园脉搏用户' }}</h2>
               <p class="text-[10px] font-black text-slate-400 uppercase tracking-widest mt-1">@{{ userStore.userInfo?.username }}</p>
               
               <div class="mt-6 flex flex-wrap justify-center gap-2">
                 <span v-if="userStore.userInfo?.school" class="px-2.5 py-1 bg-slate-50 text-slate-500 border border-slate-100 rounded-lg text-[10px] font-black uppercase tracking-widest">{{ userStore.userInfo.school }}</span>
                 <span class="px-2.5 py-1 bg-blue-50 text-brand-primary border border-blue-100 rounded-lg text-[10px] font-black uppercase tracking-widest">Lv.5 Contributor</span>
               </div>
            </div>

            <!-- Vertical Navigation -->
            <div class="flex flex-col gap-1.5">
               <button 
                  v-for="tab in tabs" 
                  :key="tab.id"
                  @click="activeTab = tab.id"
                  :class="[
                    'flex items-center gap-3 px-5 py-3.5 rounded-2xl text-xs font-black uppercase tracking-widest transition-all text-left',
                    activeTab === tab.id 
                    ? 'bg-slate-900 text-white shadow-xl shadow-slate-900/20' 
                    : 'text-slate-500 hover:bg-slate-50 hover:text-slate-900'
                  ]"
                >
                  <component :is="tab.icon" class="w-4 h-4" />
                  {{ tab.name }}
                </button>
            </div>
          </div>

          <!-- Main Content Area -->
          <div class="lg:col-span-9">
            <!-- Overview Tab -->
            <div v-if="activeTab === 'overview'" class="space-y-10 animate-in fade-in slide-in-from-bottom-4 duration-500">
               <!-- Stats Row -->
               <div class="grid grid-cols-1 sm:grid-cols-3 gap-6">
                  <div class="p-8 rounded-[2rem] border border-slate-100 bg-white shadow-xl shadow-slate-200/30 hover:shadow-slate-200/50 transition-all">
                     <div class="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-4">My Content</div>
                     <div class="text-4xl font-black text-slate-900 flex items-end gap-2 italic">
                        {{ stats?.posts || 0 }} <span class="text-[10px] text-emerald-500 font-black uppercase tracking-widest bg-emerald-50 px-2 py-1 rounded mb-1 not-italic">Items</span>
                     </div>
                  </div>
                  <div class="p-8 rounded-[2rem] border border-slate-100 bg-white shadow-xl shadow-slate-200/30 hover:shadow-slate-200/50 transition-all">
                     <div class="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-4">Social Impact</div>
                     <div class="text-4xl font-black text-slate-900 flex items-end gap-2 italic">
                        {{ stats?.likes || 0 }} <span class="text-[10px] text-pink-500 font-black uppercase tracking-widest bg-pink-50 px-2 py-1 rounded mb-1 not-italic">Likes</span>
                     </div>
                  </div>
                  <div class="p-8 rounded-[2rem] border border-slate-100 bg-white shadow-xl shadow-slate-200/30 hover:shadow-slate-200/50 transition-all">
                     <div class="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-4">Trust Score</div>
                     <div class="text-4xl font-black text-slate-900 flex items-end gap-2 italic">
                        {{ stats?.reputation || 0 }} <span class="text-[10px] text-brand-primary font-black uppercase tracking-widest bg-blue-50 px-2 py-1 rounded mb-1 not-italic">Rep</span>
                     </div>
                  </div>
               </div>

               <!-- Active Region -->
               <div>
                  <div class="flex items-center gap-3 mb-6">
                    <h3 class="text-lg font-black text-slate-900 tracking-tight">地理位置画像</h3>
                    <div class="h-px flex-1 bg-slate-100"></div>
                  </div>
                  <div class="p-8 rounded-[2rem] border border-slate-100 bg-slate-50/50 flex items-center justify-between group hover:bg-white hover:shadow-xl hover:shadow-slate-200/30 transition-all duration-300">
                     <div class="flex items-center gap-5">
                       <div class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center text-brand-primary shadow-sm border border-slate-100 group-hover:scale-110 transition-transform">
                         <MapPin class="w-6 h-6" />
                       </div>
                       <div>
                         <span class="block text-sm font-black text-slate-800 tracking-tight">{{ profileDetail?.activeRegion || '未探测到活跃地点' }}</span>
                         <span class="text-[10px] text-slate-400 font-black uppercase tracking-widest mt-1">Primary Activity Zone</span>
                       </div>
                     </div>
                     <button class="text-[10px] font-black text-slate-400 uppercase tracking-widest hover:text-brand-primary transition-colors">修改位置</button>
                  </div>
               </div>
            </div>

            <!-- History Tab -->
            <div v-if="activeTab === 'history'" class="animate-in fade-in slide-in-from-bottom-4 duration-500">
               <div class="flex items-center justify-between mb-8">
                 <h3 class="text-xl font-black text-slate-900 tracking-tight">足迹浏览</h3>
                 <span class="text-[10px] font-black text-slate-400 uppercase tracking-widest bg-slate-50 px-3 py-1 rounded-lg">{{ viewHistory.length }} 条记录</span>
               </div>
               
               <div class="grid grid-cols-1 gap-4">
                  <div 
                    v-for="log in viewHistory" 
                    :key="log.id" 
                    class="p-5 rounded-2xl border border-slate-100 bg-white hover:bg-slate-50 hover:shadow-lg hover:shadow-slate-200/20 transition-all flex items-center justify-between group cursor-pointer" 
                    @click="$router.push(`/post/${log.postId}`)"
                  >
                     <div class="flex items-center gap-5">
                        <div class="w-10 h-10 bg-slate-50 rounded-xl flex items-center justify-center text-slate-400 group-hover:bg-white group-hover:text-brand-primary transition-all shadow-sm">
                           <Clock class="w-5 h-5" />
                        </div>
                        <div>
                           <div class="text-xs font-black text-slate-900 line-clamp-1 mb-1">浏览了帖子 {{ log.postId }}</div>
                           <div class="text-[10px] text-slate-400 font-black uppercase tracking-widest">Visited at {{ new Date(log.viewTime).toLocaleTimeString() }}</div>
                        </div>
                     </div>
                     <ArrowRight class="w-4 h-4 text-slate-200 group-hover:text-slate-400 group-hover:translate-x-1 transition-all" />
                  </div>
                  <div v-if="viewHistory.length === 0" class="text-center py-24 bg-slate-50/50 rounded-[2rem] border border-dashed border-slate-200">
                     <History class="w-12 h-12 text-slate-200 mx-auto mb-4" />
                     <p class="text-xs font-black text-slate-400 uppercase tracking-widest">暂无历史记录</p>
                  </div>
               </div>
            </div>

            <!-- Edit Tab -->
            <div v-if="activeTab === 'edit'" class="animate-in fade-in slide-in-from-bottom-4 duration-500 max-w-2xl">
               <h3 class="text-xl font-black text-slate-900 tracking-tight mb-10">编辑账户资料</h3>
               
               <div class="space-y-8">
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
                     <div class="space-y-2.5">
                        <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">昵称 / Nickname</label>
                        <input v-model="form.nickname" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black text-slate-900 focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" />
                     </div>
                     <div class="space-y-2.5">
                        <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">电子邮箱 / Email</label>
                        <input v-model="form.email" type="text" disabled class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black text-slate-300 cursor-not-allowed shadow-sm" />
                     </div>
                     <div class="space-y-2.5">
                        <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">所属学校 / Institution</label>
                        <input v-model="form.school" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black text-slate-900 focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" />
                     </div>
                     <div class="space-y-2.5">
                        <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">专业领域 / Major</label>
                        <input v-model="form.major" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black text-slate-900 focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" />
                     </div>
                  </div>
                  
                  <div class="space-y-2.5">
                        <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">活跃地区 / Active Region</label>
                        <input v-model="form.activeRegion" type="text" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black text-slate-900 focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" placeholder="e.g. 广州 · 华南理工大学" />
                  </div>

                  <div class="pt-6">
                     <button 
                       @click="handleUpdate"
                       :disabled="loading"
                       class="px-10 py-4 bg-slate-900 text-white text-xs font-black uppercase tracking-widest rounded-2xl hover:bg-slate-800 transition-all shadow-xl shadow-slate-900/20 active:scale-95 disabled:opacity-50 flex items-center gap-3"
                     >
                       <Save class="w-4 h-4" />
                       保存更新内容
                     </button>
                  </div>
               </div>
            </div>

            <!-- Security Tab -->
            <div v-if="activeTab === 'security'" class="animate-in fade-in slide-in-from-bottom-4 duration-500 max-w-2xl">
               <h3 class="text-xl font-black text-slate-900 tracking-tight mb-10">账户安全中心</h3>
               
               <div class="space-y-4">
                  <div class="p-6 rounded-2xl border border-slate-100 bg-white flex items-center justify-between hover:bg-slate-50 transition-all shadow-sm group">
                     <div class="flex items-center gap-5">
                        <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400 group-hover:bg-white group-hover:text-brand-primary transition-all shadow-sm">
                           <ShieldCheck class="w-6 h-6" />
                        </div>
                        <div>
                           <div class="text-sm font-black text-slate-900 tracking-tight">账户密码</div>
                           <div class="text-[10px] text-slate-400 font-black uppercase tracking-widest mt-1">Protect your account with a strong password</div>
                        </div>
                     </div>
                     <button class="text-[10px] font-black text-slate-900 border-2 border-slate-100 px-4 py-2 rounded-xl hover:bg-slate-900 hover:text-white hover:border-slate-900 transition-all">更新</button>
                  </div>
                  
                  <div class="p-6 rounded-2xl border border-slate-100 bg-white flex items-center justify-between hover:bg-slate-50 transition-all shadow-sm group">
                     <div class="flex items-center gap-5">
                        <div class="w-12 h-12 bg-slate-50 rounded-2xl flex items-center justify-center text-slate-400 group-hover:bg-white group-hover:text-emerald-500 transition-all shadow-sm">
                           <Mail class="w-6 h-6" />
                        </div>
                        <div>
                           <div class="text-sm font-black text-slate-900 tracking-tight">邮箱验证</div>
                           <div class="text-[10px] text-emerald-500 font-black uppercase tracking-widest mt-1">Status: Verified & Protected</div>
                        </div>
                     </div>
                     <button class="text-[10px] font-black text-slate-400 border-2 border-slate-50 px-4 py-2 rounded-xl cursor-not-allowed uppercase tracking-widest">已验证</button>
                  </div>
               </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </DefaultLayout>
</template>
