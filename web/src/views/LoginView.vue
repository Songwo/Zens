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
  <div class="min-h-screen bg-[#F8FAFC] flex items-center justify-center p-6 relative overflow-hidden">
    <!-- Subtle Grid Pattern -->
    <div class="absolute inset-0 opacity-[0.03] pointer-events-none" 
         style="background-image: radial-gradient(#000 1px, transparent 1px); background-size: 24px 24px;"></div>

    <div class="w-full max-w-md relative z-10">
      <div class="bg-white border border-slate-200 rounded-[2rem] p-10 shadow-xl shadow-slate-200/50">
        <!-- Logo -->
        <div class="flex flex-col items-center gap-4 mb-10">
          <div class="w-16 h-16 bg-slate-900 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-slate-200">
            <LayoutGrid class="w-10 h-10" />
          </div>
          <div class="text-center">
            <h1 class="text-2xl font-bold text-slate-900 tracking-tight">欢迎回来</h1>
            <p class="text-sm text-slate-500 font-medium mt-1">登录 CampusPulse 开启校园新体验</p>
          </div>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-6">
          <div class="space-y-4">
            <!-- Username -->
            <div class="space-y-1.5">
              <label class="text-xs font-bold text-slate-700 ml-1">用户名 / 学号</label>
              <div class="relative group">
                <User class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400 group-focus-within:text-slate-900 transition-colors" />
                <input 
                  v-model="username"
                  type="text" 
                  placeholder="请输入您的学号"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl py-3.5 pl-12 pr-4 text-sm focus:ring-4 focus:ring-slate-900/5 focus:border-slate-900 focus:bg-white transition-all outline-none"
                  required
                />
              </div>
            </div>

            <!-- Password -->
            <div class="space-y-1.5">
              <label class="text-xs font-bold text-slate-700 ml-1">登录密码</label>
              <div class="relative group">
                <Lock class="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400 group-focus-within:text-slate-900 transition-colors" />
                <input 
                  v-model="password"
                  type="password" 
                  placeholder="请输入密码"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl py-3.5 pl-12 pr-4 text-sm focus:ring-4 focus:ring-slate-900/5 focus:border-slate-900 focus:bg-white transition-all outline-none"
                  required
                />
              </div>
            </div>

            <!-- Captcha -->
            <div class="space-y-1.5">
              <label class="text-xs font-bold text-slate-700 ml-1">图形验证码</label>
              <div class="flex gap-3">
                <input 
                  v-model="verifyCode"
                  type="text" 
                  placeholder="结果"
                  class="w-24 bg-slate-50 border border-slate-200 rounded-xl py-3.5 px-4 text-center text-sm font-bold focus:ring-4 focus:ring-slate-900/5 focus:border-slate-900 focus:bg-white transition-all outline-none"
                  required
                />
                <div 
                  class="flex-1 h-[50px] bg-slate-50 rounded-xl overflow-hidden cursor-pointer relative group border border-slate-200"
                  @click="refreshCaptcha"
                >
                  <img v-if="captchaUrl" :src="captchaUrl" class="w-full h-full object-contain p-1" />
                  <div v-else class="w-full h-full flex items-center justify-center text-slate-400">
                    <RefreshCw class="w-5 h-5 animate-spin" />
                  </div>
                  <div class="absolute inset-0 bg-black/0 group-hover:bg-black/5 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                    <RefreshCw class="w-4 h-4 text-slate-600" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="flex items-center justify-between px-1">
            <label class="flex items-center gap-2 cursor-pointer group">
              <input type="checkbox" class="w-4 h-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900/20 transition-all" />
              <span class="text-xs text-slate-500 font-medium group-hover:text-slate-700 transition-colors">记住我</span>
            </label>
            <button type="button" class="text-xs font-bold text-slate-900 hover:text-slate-600 transition-colors">忘记密码?</button>
          </div>

          <button 
            type="submit"
            :disabled="loading"
            class="w-full bg-slate-900 text-white font-bold py-4 rounded-xl shadow-lg shadow-slate-200 hover:bg-slate-800 transition-all active:scale-[0.98] flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed"
          >
            <span v-if="loading">正在验证身份...</span>
            <span v-else class="flex items-center gap-2">登录账户 <ArrowRight class="w-5 h-5" /></span>
          </button>
        </form>

        <!-- Divider -->
        <div class="relative my-10 text-center">
          <div class="absolute inset-0 flex items-center"><div class="w-full border-t border-slate-100"></div></div>
          <span class="relative px-4 bg-white text-[10px] uppercase font-black text-slate-400 tracking-widest">其他通行方式</span>
        </div>

        <!-- Social Login -->
        <button class="w-full flex items-center justify-center gap-3 py-3.5 bg-white border border-slate-200 rounded-xl text-xs font-bold text-slate-700 hover:bg-slate-50 hover:border-slate-300 transition-all shadow-sm">
          <Github class="w-4 h-4" /> 通过 GitHub 继续
        </button>

        <p class="mt-8 text-center text-xs text-slate-500 font-medium">
          还没有加入我们? <router-link to="/auth/register" class="text-slate-900 font-black hover:underline">立即注册</router-link>
        </p>
      </div>
    </div>
  </div>
</template>
