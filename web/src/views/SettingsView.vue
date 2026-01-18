<script setup lang="ts">
import { ref } from 'vue'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import { 
  Moon, Sun, Monitor, Bell, Shield, LogOut, 
  Globe, Smartphone, Mail, ChevronRight, UserMinus
} from 'lucide-vue-next'
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

const userStore = useUserStore()
const router = useRouter()

const theme = ref('light')
const notifications = ref(true)
const setTheme = (t: string) => {
    theme.value = t
    toast.success(`外观模式已切换为: ${t === 'light' ? '浅色' : t === 'dark' ? '深色' : '跟随系统'}`)
}

const handleLogout = () => {
    userStore.logout()
    router.push('/auth/login')
    toast.info('期待再次见到你 👋')
}

// Password Change Logic
import { userApi } from '@/api/user'
const showPasswordModal = ref(false)
const pwdForm = ref({
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
})

const handleUpdatePassword = async () => {
    if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) {
        toast.error('请填写完整')
        return
    }
    if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
        toast.error('两次输入的密码不一致')
        return
    }
    try {
        await userApi.updatePwd({
            oldPassword: pwdForm.value.oldPassword,
            newPassword: pwdForm.value.newPassword,
            confirmPassword: pwdForm.value.confirmPassword
        })
        toast.success('密码修改成功，请重新登录')
        showPasswordModal.value = false
        setTimeout(() => {
            handleLogout()
        }, 1000)
    } catch (error) {
        toast.error('密码修改失败，请检查原密码')
    }
}
</script>

<template>
  <DefaultLayout wide isFluid>
    <div class="py-6 font-sans">
      <div class="max-w-4xl">
        <!-- Header -->
        <div class="flex items-center justify-between mb-10 pb-6 border-b border-slate-200">
          <div>
             <h1 class="text-2xl font-black text-slate-900 tracking-tight">系统设置</h1>
             <p class="text-[11px] text-slate-400 font-black uppercase tracking-widest mt-1">Preferences & Account Security</p>
          </div>
          <div class="w-12 h-12 bg-slate-900 text-white rounded-2xl flex items-center justify-center shadow-xl shadow-slate-900/20">
             <Settings class="w-6 h-6" />
          </div>
        </div>

        <div class="space-y-8">
          <!-- Appearance Section -->
          <div class="bg-white rounded-[2rem] p-8 border border-slate-100 shadow-xl shadow-slate-200/20">
            <h2 class="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-8 flex items-center gap-3">
              <Sun class="w-4 h-4 text-brand-primary" /> Visual Appearance
            </h2>
            
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-6">
              <button 
                @click="setTheme('light')"
                :class="[
                  'group flex flex-col items-center justify-center gap-4 py-8 rounded-3xl border-2 transition-all duration-500',
                  theme === 'light' 
                  ? 'border-slate-900 bg-slate-900 text-white shadow-2xl shadow-slate-900/30' 
                  : 'border-slate-50 bg-slate-50 text-slate-400 hover:border-slate-200 hover:text-slate-600'
                ]"
              >
                <div :class="['w-14 h-14 rounded-2xl flex items-center justify-center transition-all duration-500', theme === 'light' ? 'bg-white/10 text-white rotate-12' : 'bg-white text-slate-300 shadow-sm group-hover:scale-110']">
                   <Sun class="w-7 h-7" />
                </div>
                <span class="text-[10px] font-black uppercase tracking-widest">日常浅色</span>
              </button>

              <button 
                @click="setTheme('dark')"
                :class="[
                  'group flex flex-col items-center justify-center gap-4 py-8 rounded-3xl border-2 transition-all duration-500',
                  theme === 'dark' 
                  ? 'border-slate-900 bg-slate-900 text-white shadow-2xl shadow-slate-900/30' 
                  : 'border-slate-50 bg-slate-50 text-slate-400 hover:border-slate-200 hover:text-slate-600'
                ]"
              >
                <div :class="['w-14 h-14 rounded-2xl flex items-center justify-center transition-all duration-500', theme === 'dark' ? 'bg-white/10 text-white rotate-12' : 'bg-white text-slate-300 shadow-sm group-hover:scale-110']">
                   <Moon class="w-7 h-7" />
                </div>
                <span class="text-[10px] font-black uppercase tracking-widest">护眼深色</span>
              </button>

              <button 
                @click="setTheme('system')"
                :class="[
                  'group flex flex-col items-center justify-center gap-4 py-8 rounded-3xl border-2 transition-all duration-500',
                  theme === 'system' 
                  ? 'border-slate-900 bg-slate-900 text-white shadow-2xl shadow-slate-900/30' 
                  : 'border-slate-50 bg-slate-50 text-slate-400 hover:border-slate-200 hover:text-slate-600'
                ]"
              >
                <div :class="['w-14 h-14 rounded-2xl flex items-center justify-center transition-all duration-500', theme === 'system' ? 'bg-white/10 text-white rotate-12' : 'bg-white text-slate-300 shadow-sm group-hover:scale-110']">
                   <Monitor class="w-7 h-7" />
                </div>
                <span class="text-[10px] font-black uppercase tracking-widest">自动切换</span>
              </button>
            </div>
          </div>

          <!-- Notification Section -->
          <div class="bg-white rounded-[2rem] p-8 border border-slate-100 shadow-xl shadow-slate-200/20">
            <h2 class="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-8 flex items-center gap-3">
              <Bell class="w-4 h-4 text-brand-primary" /> Notifications
            </h2>
            
            <div class="space-y-4">
              <div class="flex items-center justify-between p-6 bg-slate-50/50 rounded-2xl border border-slate-100 group transition-all hover:bg-white hover:shadow-xl hover:shadow-slate-200/30">
                <div class="flex items-center gap-5">
                  <div class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center text-brand-primary shadow-sm border border-slate-100 group-hover:scale-110 transition-transform">
                    <Mail class="w-6 h-6" />
                  </div>
                  <div>
                    <div class="text-sm font-black text-slate-800 tracking-tight">重要消息推送</div>
                    <div class="text-[10px] text-slate-400 font-black uppercase tracking-widest mt-1">Likes, Comments & System Alerts</div>
                  </div>
                </div>
                <label class="relative inline-flex items-center cursor-pointer">
                  <input type="checkbox" v-model="notifications" class="sr-only peer">
                  <div class="w-14 h-7 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[4px] after:left-[4px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-6 after:w-6 after:transition-all peer-checked:bg-slate-900"></div>
                </label>
              </div>

              <div class="flex items-center justify-between p-6 bg-slate-50/50 rounded-2xl border border-slate-100 opacity-50 grayscale">
                <div class="flex items-center gap-5">
                  <div class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center text-indigo-500 shadow-sm border border-slate-100">
                    <Smartphone class="w-6 h-6" />
                  </div>
                  <div>
                    <div class="text-sm font-black text-slate-800 tracking-tight">短信通知服务</div>
                    <div class="text-[10px] text-slate-400 font-black uppercase tracking-widest mt-1">Mobile SMS Authentication Needed</div>
                  </div>
                </div>
                <div class="text-[10px] font-black text-slate-400 bg-slate-100 px-4 py-2 rounded-xl uppercase tracking-widest">Unavailable</div>
              </div>
            </div>
          </div>

          <!-- Account Actions Section -->
          <div class="bg-white rounded-[2rem] p-8 border border-slate-100 shadow-xl shadow-slate-200/20">
            <h2 class="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] mb-8 flex items-center gap-3">
              <Shield class="w-4 h-4 text-brand-primary" /> Security & Session
            </h2>
            
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <button 
                @click="handleLogout"
                class="flex items-center justify-between p-6 bg-rose-50/50 text-rose-600 rounded-2xl hover:bg-rose-600 hover:text-white transition-all group duration-500 border border-rose-100/50"
              >
                <div class="flex items-center gap-5">
                   <div class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center group-hover:bg-white/20 group-hover:rotate-12 transition-all shadow-sm border border-rose-100 group-hover:border-transparent">
                      <LogOut class="w-6 h-6" />
                   </div>
                   <div class="text-left">
                     <div class="text-sm font-black tracking-tight">安全退出登录</div>
                     <div class="text-[10px] opacity-60 font-black uppercase tracking-widest mt-1">Destroy current session</div>
                   </div>
                </div>
                <ArrowRight class="w-5 h-5 opacity-30 group-hover:translate-x-1 transition-all" />
              </button>

              <button 
                @click="showPasswordModal = true"
                class="flex items-center justify-between p-6 bg-slate-50/50 text-slate-600 rounded-2xl hover:bg-slate-900 hover:text-white transition-all group duration-500 border border-slate-100"
              >
                <div class="flex items-center gap-5">
                   <div class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center group-hover:bg-white/10 group-hover:rotate-12 transition-all shadow-sm border border-slate-100 group-hover:border-transparent">
                      <Shield class="w-6 h-6" />
                   </div>
                   <div class="text-left">
                     <div class="text-sm font-black tracking-tight">修改账户密码</div>
                     <div class="text-[10px] opacity-60 font-black uppercase tracking-widest mt-1">Update security credentials</div>
                   </div>
                </div>
                <ArrowRight class="w-5 h-5 opacity-30 group-hover:translate-x-1 transition-all" />
              </button>
            </div>
          </div>
        </div>

        <!-- Footer Info -->
        <div class="mt-16 text-center">
          <div class="inline-flex items-center gap-3 px-6 py-2 bg-slate-50 rounded-full border border-slate-100">
            <span class="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></span>
            <span class="text-[10px] text-slate-400 font-black uppercase tracking-[0.2em]">CampusPulse Core · v2.0.4-stable</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Password Modal (Enhanced) -->
    <div v-if="showPasswordModal" class="fixed inset-0 z-[100] flex items-center justify-center p-6">
       <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-md transition-all duration-500" @click="showPasswordModal = false"></div>
       <div class="bg-white rounded-[2.5rem] p-10 w-full max-w-md relative z-10 shadow-2xl animate-in zoom-in-95 duration-300 border border-slate-100">
          <h3 class="text-2xl font-black text-slate-900 mb-8 tracking-tight">修改账户密码</h3>
          
          <div class="space-y-6">
             <div class="space-y-2">
               <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">当前密码 / Current</label>
               <input v-model="pwdForm.oldPassword" type="password" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" />
             </div>
             <div class="space-y-2">
               <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">新设密码 / New</label>
               <input v-model="pwdForm.newPassword" type="password" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" />
             </div>
             <div class="space-y-2">
               <label class="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-1">确认密码 / Confirm</label>
               <input v-model="pwdForm.confirmPassword" type="password" class="w-full bg-slate-50 border border-slate-200 rounded-2xl px-5 py-4 text-sm font-black focus:outline-none focus:ring-4 focus:ring-slate-900/5 focus:border-slate-400 transition-all shadow-sm" />
             </div>
          </div>

          <div class="mt-10 flex gap-4">
             <button @click="showPasswordModal = false" class="flex-1 py-4 rounded-2xl font-black text-slate-400 hover:bg-slate-50 transition-all uppercase tracking-widest text-xs">取消</button>
             <button @click="handleUpdatePassword" class="flex-1 py-4 bg-slate-900 text-white rounded-2xl font-black shadow-xl shadow-slate-900/20 hover:bg-slate-800 transition-all active:scale-95 uppercase tracking-widest text-xs">确认更新</button>
          </div>
       </div>
    </div>
  </DefaultLayout>
</template>
