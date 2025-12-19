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
  <DefaultLayout :showLeftSidebar="false" :showRightSidebar="false" wide>
    <div class="min-h-screen bg-slate-50/50 py-12 px-6">
      <div class="max-w-2xl mx-auto">
        <!-- Header -->
        <div class="mb-10">
          <h1 class="text-3xl font-black text-slate-900">系统设置</h1>
          <p class="text-sm text-slate-400 font-bold mt-2">偏好设置、通知管理及账户安全</p>
        </div>

        <div class="space-y-6">
          <!-- Appearance Section -->
          <div class="bg-white rounded-[2.5rem] p-8 border border-slate-100 shadow-xl shadow-slate-200/30">
            <h2 class="text-sm font-black text-slate-400 uppercase tracking-widest mb-6 flex items-center gap-2">
              <Sun class="w-4 h-4 text-brand-primary" /> 外观设置
            </h2>
            
            <div class="grid grid-cols-3 gap-4">
              <button 
                @click="setTheme('light')"
                :class="[
                  'group flex flex-col items-center justify-center gap-3 py-6 rounded-2xl border-2 transition-all duration-300',
                  theme === 'light' 
                  ? 'border-brand-primary bg-blue-50/50 text-brand-primary' 
                  : 'border-slate-50 bg-slate-50 text-slate-400 hover:border-slate-200 hover:text-slate-600'
                ]"
              >
                <div :class="['w-12 h-12 rounded-full flex items-center justify-center transition-colors', theme === 'light' ? 'bg-brand-primary text-white' : 'bg-white text-slate-300 group-hover:text-slate-500 shadow-sm']">
                   <Sun class="w-6 h-6" />
                </div>
                <span class="text-xs font-black">日常浅色</span>
              </button>

              <button 
                @click="setTheme('dark')"
                :class="[
                  'group flex flex-col items-center justify-center gap-3 py-6 rounded-2xl border-2 transition-all duration-300',
                  theme === 'dark' 
                  ? 'border-brand-primary bg-blue-50/50 text-brand-primary' 
                  : 'border-slate-50 bg-slate-50 text-slate-400 hover:border-slate-200 hover:text-slate-600'
                ]"
              >
                <div :class="['w-12 h-12 rounded-full flex items-center justify-center transition-colors', theme === 'dark' ? 'bg-indigo-600 text-white' : 'bg-white text-slate-300 group-hover:text-slate-500 shadow-sm']">
                   <Moon class="w-6 h-6" />
                </div>
                <span class="text-xs font-black">护眼深色</span>
              </button>

              <button 
                @click="setTheme('system')"
                :class="[
                  'group flex flex-col items-center justify-center gap-3 py-6 rounded-2xl border-2 transition-all duration-300',
                  theme === 'system' 
                  ? 'border-brand-primary bg-blue-50/50 text-brand-primary' 
                  : 'border-slate-50 bg-slate-50 text-slate-400 hover:border-slate-200 hover:text-slate-600'
                ]"
              >
                <div :class="['w-12 h-12 rounded-full flex items-center justify-center transition-colors', theme === 'system' ? 'bg-slate-700 text-white' : 'bg-white text-slate-300 group-hover:text-slate-500 shadow-sm']">
                   <Monitor class="w-6 h-6" />
                </div>
                <span class="text-xs font-black">自动切换</span>
              </button>
            </div>
          </div>

          <!-- Notification Section -->
          <div class="bg-white rounded-[2.5rem] p-8 border border-slate-100 shadow-xl shadow-slate-200/30">
            <h2 class="text-sm font-black text-slate-400 uppercase tracking-widest mb-6 flex items-center gap-2">
              <Bell class="w-4 h-4 text-brand-primary" /> 通知提醒
            </h2>
            
            <div class="space-y-4">
              <div class="flex items-center justify-between p-5 bg-slate-50 rounded-[1.5rem] border border-slate-100 group transition-colors hover:bg-white hover:shadow-lg hover:shadow-slate-200/30">
                <div class="flex items-center gap-4">
                  <div class="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-brand-primary shadow-sm">
                    <Mail class="w-5 h-5" />
                  </div>
                  <div>
                    <div class="text-sm font-black text-slate-800">重要消息推送</div>
                    <div class="text-[10px] text-slate-400 font-bold mt-0.5">点赞、评论及系统公告</div>
                  </div>
                </div>
                <label class="relative inline-flex items-center cursor-pointer">
                  <input type="checkbox" v-model="notifications" class="sr-only peer">
                  <div class="w-12 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-primary"></div>
                </label>
              </div>

              <div class="flex items-center justify-between p-5 bg-slate-50 rounded-[1.5rem] border border-slate-100 opacity-50">
                <div class="flex items-center gap-4">
                  <div class="w-10 h-10 bg-white rounded-xl flex items-center justify-center text-indigo-500 shadow-sm">
                    <Smartphone class="w-5 h-5" />
                  </div>
                  <div>
                    <div class="text-sm font-black text-slate-800">短信通知</div>
                    <div class="text-[10px] text-slate-400 font-bold mt-0.5">暂未开启绑定手机号</div>
                  </div>
                </div>
                <div class="text-xs font-bold text-slate-400 bg-slate-100 px-3 py-1 rounded-lg">未开启</div>
              </div>
            </div>
          </div>

          <!-- Account Actions Section -->
          <div class="bg-white rounded-[2.5rem] p-8 border border-slate-100 shadow-xl shadow-slate-200/30">
            <h2 class="text-sm font-black text-slate-400 uppercase tracking-widest mb-6 flex items-center gap-2">
              <Shield class="w-4 h-4 text-brand-primary" /> 账户操作
            </h2>
            
            <div class="space-y-3">
              <button 
                @click="handleLogout"
                class="w-full flex items-center justify-between p-5 bg-red-50 text-red-500 rounded-[1.5rem] hover:bg-red-500 hover:text-white transition-all group duration-300"
              >
                <div class="flex items-center gap-4">
                   <div class="w-10 h-10 bg-white/50 rounded-xl flex items-center justify-center group-hover:bg-white/20">
                      <LogOut class="w-5 h-5" />
                   </div>
                   <div class="text-left">
                     <div class="text-sm font-black">安全的退出登录</div>
                     <div class="text-[10px] opacity-70 font-bold mt-0.5">退出后将清除本地缓存的登录信息</div>
                   </div>
                </div>
                <ChevronRight class="w-5 h-5 opacity-50 group-hover:translate-x-1 transition-transform" />
              </button>

              <button 
                @click="showPasswordModal = true"
                class="w-full flex items-center justify-between p-5 bg-slate-50 text-slate-600 rounded-[1.5rem] hover:bg-slate-900 hover:text-white transition-all group duration-300"
              >
                <div class="flex items-center gap-4">
                   <div class="w-10 h-10 bg-white rounded-xl flex items-center justify-center group-hover:bg-white/10 group-hover:text-white text-slate-500 transition-colors">
                      <Shield class="w-5 h-5" />
                   </div>
                   <div class="text-left">
                     <div class="text-sm font-black">修改账户密码</div>
                     <div class="text-[10px] opacity-70 font-bold mt-0.5">定期更新密码保护账户安全</div>
                   </div>
                </div>
                <ChevronRight class="w-5 h-5 opacity-50 group-hover:translate-x-1 transition-transform" />
              </button>
            </div>
          </div>
        </div>

        <!-- Footer Info -->
        <div class="mt-12 text-center text-[10px] text-slate-300 font-black uppercase tracking-[0.2em]">
          CampusPulse Core Service · Version 2.0.4-stable
        </div>
      </div>
    </div>

    <!-- Password Modal -->
    <div v-if="showPasswordModal" class="fixed inset-0 z-50 flex items-center justify-center p-6">
       <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showPasswordModal = false"></div>
       <div class="bg-white rounded-[2rem] p-8 w-full max-w-md relative z-10 shadow-2xl animate-in zoom-in-95 duration-200">
          <h3 class="text-xl font-black text-slate-900 mb-6">修改密码</h3>
          
          <div class="space-y-4">
             <div class="space-y-2">
               <label class="text-xs font-bold text-slate-400 uppercase tracking-widest ml-1">当前密码</label>
               <input v-model="pwdForm.oldPassword" type="password" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold focus:outline-none focus:border-brand-primary transition-colors" />
             </div>
             <div class="space-y-2">
               <label class="text-xs font-bold text-slate-400 uppercase tracking-widest ml-1">新密码</label>
               <input v-model="pwdForm.newPassword" type="password" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold focus:outline-none focus:border-brand-primary transition-colors" />
             </div>
             <div class="space-y-2">
               <label class="text-xs font-bold text-slate-400 uppercase tracking-widest ml-1">确认新密码</label>
               <input v-model="pwdForm.confirmPassword" type="password" class="w-full bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-sm font-bold focus:outline-none focus:border-brand-primary transition-colors" />
             </div>
          </div>

          <div class="mt-8 flex gap-4">
             <button @click="showPasswordModal = false" class="flex-1 py-3 rounded-xl font-bold text-slate-500 hover:bg-slate-50 transition-colors">取消</button>
             <button @click="handleUpdatePassword" class="flex-1 py-3 bg-brand-primary text-white rounded-xl font-bold shadow-lg shadow-blue-500/30 hover:bg-blue-600 transition-colors">确认修改</button>
          </div>
       </div>
    </div>
  </DefaultLayout>
</template>
