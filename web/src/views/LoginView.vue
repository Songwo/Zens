<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { LayoutGrid, User, Lock, ArrowRight, Github, RefreshCw } from 'lucide-vue-next'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import { toast } from 'vue-sonner'
import { ResultCode } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const username = ref('')
const password = ref('')
const verifyCode = ref('')
const captchaUrl = ref('')
const uuid = ref('')
const loading = ref(false)

const refreshCaptcha = async () => {
  try {
    uuid.value = crypto.randomUUID()
    const response = await authApi.getCaptcha(uuid.value)
    captchaUrl.value = URL.createObjectURL(response as any)
  } catch (error) {
    toast.error('验证码获取失败')
  }
}

const handleLogin = async () => {
  if (!username.value || !password.value || !verifyCode.value) {
    toast.warning('请填写完整登录信息')
    return
  }

  loading.value = true
  try {
    const res = await authApi.login({
      username: username.value,
      password: password.value,
      code: verifyCode.value,
      uuid: uuid.value
    })

    if (res.code === ResultCode.SUCCESS) {
      const { accessToken, refreshToken } = res.data
      
      // 1. Save tokens first
      userStore.setAuth(accessToken, refreshToken)
      
      // 2. Fetch user profile
      try {
        const profileRes = await userApi.getProfile()
        if (profileRes.code === ResultCode.SUCCESS) {
          userStore.setUserInfo(profileRes.data)
          userStore.setUserId(profileRes.data.id)
        }
      } catch (err) {
        console.error('Failed to fetch profile:', err)
      }

      toast.success('登录成功')
      router.push('/')
    } else {
      toast.error(res.message || '登录失败')
      refreshCaptcha()
    }
  } catch (error: any) {
    console.error('Login error:', error)
    toast.error(error.message || '登录异常')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center p-6 relative overflow-hidden">
    <!-- Abstract Background Orbs -->
    <div class="absolute top-1/4 -left-20 w-80 h-80 bg-blue-400/20 rounded-full blur-3xl animate-pulse"></div>
    <div class="absolute bottom-1/4 -right-20 w-80 h-80 bg-indigo-400/20 rounded-full blur-3xl animate-pulse" style="animation-delay: 2s"></div>

    <div class="w-full max-w-md relative z-10">
      <div class="glass border border-white/40 rounded-[2.5rem] p-10 shadow-2xl shadow-blue-500/10 backdrop-blur-2xl bg-white/40">
        <!-- Logo -->
        <div class="flex flex-col items-center gap-4 mb-10">
          <div class="w-16 h-16 bg-brand-primary rounded-2xl flex items-center justify-center text-white shadow-xl shadow-blue-500/30 transform -rotate-6">
            <LayoutGrid class="w-10 h-10" />
          </div>
          <div class="text-center">
            <h1 class="text-2xl font-black text-slate-900 tracking-tighter">欢迎回来</h1>
            <p class="text-sm text-slate-500 font-medium">登录 CampusPulse 开启智慧校园生活</p>
          </div>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-6">
          <div class="space-y-4">
            <!-- Username -->
            <div class="relative group">
              <User class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
              <input 
                v-model="username"
                type="text" 
                placeholder="用户名 / 学号"
                class="w-full bg-slate-50/50 border border-slate-200 rounded-2xl py-4 pl-12 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                required
              />
            </div>

            <!-- Password -->
            <div class="relative group">
              <Lock class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
              <input 
                v-model="password"
                type="password" 
                placeholder="登录密码"
                class="w-full bg-slate-50/50 border border-slate-200 rounded-2xl py-4 pl-12 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                required
              />
            </div>

            <!-- Captcha -->
            <div class="flex gap-3">
              <input 
                v-model="verifyCode"
                type="text" 
                placeholder="验证码"
                class="flex-1 bg-slate-50/50 border border-slate-200 rounded-2xl py-4 px-4 text-center text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                required
              />
              <div 
                class="w-32 h-[54px] bg-slate-100 rounded-2xl overflow-hidden cursor-pointer relative group border border-slate-200"
                @click="refreshCaptcha"
              >
                <img v-if="captchaUrl" :src="captchaUrl" class="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-opacity" />
                <div v-else class="w-full h-full flex items-center justify-center text-slate-400">
                  <RefreshCw class="w-5 h-5 animate-spin" />
                </div>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-between px-2">
            <label class="flex items-center gap-2 cursor-pointer group">
              <input type="checkbox" class="w-4 h-4 rounded border-slate-300 text-brand-primary focus:ring-brand-primary/20 transition-all" />
              <span class="text-xs text-slate-500 font-medium group-hover:text-slate-700">记住我</span>
            </label>
            <button class="text-xs font-bold text-brand-primary hover:underline">忘记密码?</button>
          </div>

          <button 
            type="submit"
            :disabled="loading"
            class="w-full bg-brand-primary text-white font-bold py-4 rounded-2xl shadow-xl shadow-blue-500/20 hover:bg-blue-700 hover:shadow-blue-500/30 transition-all active:scale-[0.98] flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed"
          >
            <span v-if="loading">登录中...</span>
            <span v-else class="flex items-center gap-2">立即登录 <ArrowRight class="w-5 h-5" /></span>
          </button>
        </form>

        <!-- Divider -->
        <div class="relative my-10 text-center">
          <div class="absolute inset-0 flex items-center"><div class="w-full border-t border-slate-200"></div></div>
          <span class="relative px-4 bg-white/0 text-[10px] uppercase font-black text-slate-400 tracking-widest">其他登录方式</span>
        </div>

        <!-- Social Login -->
        <div class="grid grid-cols-1 gap-3">
          <button class="w-full flex items-center justify-center gap-3 py-3 bg-white border border-slate-200 rounded-2xl text-xs font-bold text-slate-700 hover:bg-slate-50 transition-all">
            <Github class="w-4 h-4" /> 使用 GitHub 账号登录
          </button>
        </div>

        <p class="mt-8 text-center text-xs text-slate-500 font-medium">
          还没有账号? <router-link to="/auth/register" class="text-brand-primary font-black hover:underline">立即注册</router-link>
        </p>
      </div>
    </div>
  </div>
</template>
