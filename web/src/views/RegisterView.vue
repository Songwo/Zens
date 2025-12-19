<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { LayoutGrid, User, Lock, Mail, ArrowRight, ShieldCheck, School, BookOpen } from 'lucide-vue-next'
import { authApi } from '@/api/auth'
import { toast } from 'vue-sonner'

const router = useRouter()

const formData = ref({
  username: '',
  password: '',
  email: '',
  code: '',
  nickname: '',
  school: '',
  major: '',
  grade: new Date().getFullYear(),
  gender: 1
})

const loading = ref(false)
const codeLoading = ref(false)
const countdown = ref(0)
let timer: any = null

const startCountdown = () => {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (!formData.value.email) {
    toast.warning('请先填写邮箱地址')
    return
  }
  
  codeLoading.value = true
  try {
    await authApi.sendCode(formData.value.email)
    toast.success('验证码已发送')
    startCountdown()
  } catch (error: any) {
    toast.error(error.message || '发送失败')
  } finally {
    codeLoading.value = false
  }
}

const handleRegister = async () => {
  if (!formData.value.username || !formData.value.password || !formData.value.code) {
    toast.warning('请填写必填项')
    return
  }

  loading.value = true
  try {
    await authApi.register({
      ...formData.value,
      avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + formData.value.username // Default random avatar
    })
    toast.success('注册成功，请登录')
    router.push('/auth/login')
  } catch (error: any) {
    toast.error(error.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex items-center justify-center p-6 relative overflow-hidden">
    <!-- Abstract Background Orbs -->
    <div class="absolute top-1/4 -right-20 w-80 h-80 bg-emerald-400/20 rounded-full blur-3xl animate-pulse"></div>
    <div class="absolute bottom-1/4 -left-20 w-80 h-80 bg-blue-400/20 rounded-full blur-3xl animate-pulse" style="animation-delay: 2s"></div>

    <div class="w-full max-w-2xl relative z-10 my-10">
      <div class="glass border border-white/40 rounded-[2.5rem] p-8 md:p-12 shadow-2xl shadow-blue-500/10 backdrop-blur-2xl bg-white/40">
        <!-- Logo -->
        <div class="flex flex-col items-center gap-4 mb-10">
          <div class="w-14 h-14 bg-brand-primary rounded-2xl flex items-center justify-center text-white shadow-xl shadow-blue-500/30">
            <LayoutGrid class="w-8 h-8" />
          </div>
          <div class="text-center">
            <h1 class="text-2xl font-black text-slate-900 tracking-tighter">加入 CampusPulse</h1>
            <p class="text-sm text-slate-500 font-medium">创建您的专属校园账号</p>
          </div>
        </div>

        <form @submit.prevent="handleRegister" class="space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <!-- Username (Student ID) -->
            <div class="relative group">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">学号 <span class="text-red-500">*</span></label>
              <div class="relative">
                <User class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                <input 
                  v-model="formData.username"
                  type="text" 
                  placeholder="请输入学号"
                  class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                  required
                />
              </div>
            </div>

            <!-- Password -->
            <div class="relative group">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">密码 <span class="text-red-500">*</span></label>
              <div class="relative">
                <Lock class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                <input 
                  v-model="formData.password"
                  type="password" 
                  placeholder="设置登录密码"
                  class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                  required
                />
              </div>
            </div>

            <!-- Email -->
            <div class="relative group md:col-span-2">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">邮箱 <span class="text-red-500">*</span></label>
              <div class="flex gap-3">
                <div class="relative flex-1">
                  <Mail class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                  <input 
                    v-model="formData.email"
                    type="email" 
                    placeholder="用于接收验证码"
                    class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                    required
                  />
                </div>
                <button 
                  type="button"
                  @click="handleSendCode"
                  :disabled="codeLoading || countdown > 0"
                  class="min-w-[120px] px-4 py-2 bg-slate-100 text-slate-600 text-xs font-bold rounded-xl hover:bg-slate-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {{ countdown > 0 ? `${countdown}s 后重试` : (codeLoading ? '发送中...' : '发送验证码') }}
                </button>
              </div>
            </div>

            <!-- Code -->
            <div class="relative group">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">验证码 <span class="text-red-500">*</span></label>
              <div class="relative">
                <ShieldCheck class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                <input 
                  v-model="formData.code"
                  type="text" 
                  placeholder="邮箱验证码"
                  class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                  required
                />
              </div>
            </div>

            <!-- Nickname -->
            <div class="relative group">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">昵称</label>
              <div class="relative">
                <User class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                <input 
                  v-model="formData.nickname"
                  type="text" 
                  placeholder="可选"
                  class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                />
              </div>
            </div>

            <!-- School -->
            <div class="relative group">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">学校</label>
              <div class="relative">
                <School class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                <input 
                  v-model="formData.school"
                  type="text" 
                  placeholder="所在学校"
                  class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                />
              </div>
            </div>

            <!-- Major -->
            <div class="relative group">
              <label class="text-xs font-bold text-slate-500 ml-4 mb-1.5 block">专业</label>
              <div class="relative">
                <BookOpen class="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 group-focus-within:text-brand-primary transition-colors" />
                <input 
                  v-model="formData.major"
                  type="text" 
                  placeholder="所学专业"
                  class="w-full bg-slate-50/50 border border-slate-200 rounded-xl py-3 pl-10 pr-4 text-sm focus:ring-4 focus:ring-brand-primary/10 focus:border-brand-primary transition-all outline-none"
                />
              </div>
            </div>
          </div>

          <button 
            type="submit"
            :disabled="loading"
            class="w-full bg-brand-primary text-white font-bold py-4 rounded-2xl shadow-xl shadow-blue-500/20 hover:bg-blue-700 hover:shadow-blue-500/30 transition-all active:scale-[0.98] flex items-center justify-center gap-2 mt-8 disabled:opacity-70 disabled:cursor-not-allowed"
          >
            <span v-if="loading">注册中...</span>
            <span v-else class="flex items-center gap-2">立即注册 <ArrowRight class="w-5 h-5" /></span>
          </button>
        </form>

        <p class="mt-8 text-center text-xs text-slate-500 font-medium">
          已有账号? <router-link to="/auth/login" class="text-brand-primary font-black hover:underline">去登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>
