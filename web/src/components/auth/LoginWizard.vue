<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Message, Lock, User } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { authApi } from '@/api/auth'
import { useUiStore } from '@/store/ui'
import { useUserStore } from '@/store/user'
import { ensureCurrentUserProfile } from '@/utils/sessionProfile'

const router = useRouter()
const route = useRoute()
const uiStore = useUiStore()
const userStore = useUserStore()
const { isDark } = storeToRefs(uiStore)

const TURNSTILE_TEST_SITE_KEY = '1x00000000000000000000AA'

const loading = ref(false)
const githubLoading = ref(false)
const handlingGithubCallback = ref(false)
const showTwoFactorDialog = ref(false)
const twoFactorLoading = ref(false)
const twoFactorTicket = ref('')
const pendingRememberMe = ref(false)
const GITHUB_REMEMBER_KEY = 'oauth_github_remember_me'
const configuredTurnstileSiteKey = (import.meta.env.VITE_TURNSTILE_SITE_KEY || '').trim()
const turnstileSiteKey = (configuredTurnstileSiteKey || (import.meta.env.DEV ? TURNSTILE_TEST_SITE_KEY : '')).trim()
const turnstileContainerRef = ref<HTMLElement>()
const turnstileWidgetId = ref<string | null>(null)
const turnstileToken = ref('')
const turnstileScriptReady = ref(false)
const turnstilePollTimer = ref<number | null>(null)
const canSubmitLogin = computed(() => !!turnstileToken.value && !loading.value)
const turnstileTheme = computed<'light' | 'dark'>(() => (isDark.value ? 'dark' : 'light'))
const showTurnstileUnavailable = computed(() => !turnstileSiteKey)
const showTurnstileUsingTestKey = computed(() => !configuredTurnstileSiteKey && import.meta.env.DEV)
const showTurnstileLoading = computed(() => !!turnstileSiteKey && !turnstileScriptReady.value)
const twoFactorForm = reactive({
  code: ''
})

// Song：---- 忘记密码 ----
const showResetDialog = ref(false)
const resetStep = ref(1)
const resetLoading = ref(false)
const resetCountdown = ref(0)
let resetTimer: any = null
const resetForm = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const openResetDialog = () => {
  resetStep.value = 1
  resetForm.email = ''
  resetForm.code = ''
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  showResetDialog.value = true
}

const sendResetCode = async () => {
  if (!resetForm.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetForm.email)) {
    ElMessage.warning('请输入有效的邮箱地址')
    return
  }
  resetLoading.value = true
  try {
    await authApi.sendOtp(resetForm.email)
    ElMessage.success('验证码已发送至您的邮箱')
    resetCountdown.value = 60
    resetTimer = setInterval(() => {
      resetCountdown.value--
      if (resetCountdown.value <= 0) clearInterval(resetTimer)
    }, 1000)
    resetStep.value = 2
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    resetLoading.value = false
  }
}

const verifyResetCode = async () => {
  if (!resetForm.code || resetForm.code.length !== 6) {
    ElMessage.warning('请输入6位验证码')
    return
  }
  resetLoading.value = true
  try {
    const res = await authApi.verifyOtp(resetForm.email, resetForm.code)
    if (res.code === 2000) {
      resetStep.value = 3
    } else {
      ElMessage.error(res.message || '验证码错误')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '校验失败')
  } finally {
    resetLoading.value = false
  }
}

const handleResetPassword = async () => {
  if (!resetForm.newPassword || resetForm.newPassword.length < 6) {
    ElMessage.warning('密码长度至少6位')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  resetLoading.value = true
  try {
    const res = await authApi.resetPassword({
      email: resetForm.email,
      code: resetForm.code,
      newPassword: resetForm.newPassword
    })
    if (res.code === 2000) {
      ElMessage.success('密码重置成功，请使用新密码登录')
      showResetDialog.value = false
    } else {
      ElMessage.error(res.message || '重置失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '重置失败')
  } finally {
    resetLoading.value = false
  }
}

// Song：说明
const loginMode = ref<'password' | 'otp'>('password')

// Song：---- 密码登录表单 ----
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  account: '',
  password: '',
  rememberMe: false
})

const pwdRules = reactive<FormRules>({
  account: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' },
    { min: 2, message: '账号长度至少2个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6个字符', trigger: 'blur' }
  ]
})

// Song：---- 验证码登录表单 ----
const otpFormRef = ref<FormInstance>()
const otpForm = reactive({
  email: '',
  code: '',
  rememberMe: false
})

const otpRules = reactive<FormRules>({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        emailRegex.test(value) ? callback() : callback(new Error('请输入有效的邮箱地址'))
      },
      trigger: 'blur'
    }
  ],
  code: [
    { required: true, message: '请输入6位验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位', trigger: 'blur' }
  ]
})

// Song：---- 验证码倒计时 ----
const countdown = ref(0)
let timer: any = null

const sendOtp = async () => {
  otpFormRef.value?.validateField('email', async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // Song：先检查邮箱是否已注册
        const checkRes = await authApi.checkEmail(otpForm.email)
        if (checkRes.code === 2000 && checkRes.data && !checkRes.data.exists) {
          ElMessage.warning('该邮箱尚未注册，请先注册')
          loading.value = false
          return
        }

        await authApi.sendOtp(otpForm.email)
        ElMessage.success('验证码已发送至您的邮箱')
        countdown.value = 60
        timer = setInterval(() => {
          countdown.value--
          if (countdown.value <= 0) clearInterval(timer)
        }, 1000)
      } catch (e: any) {
        ElMessage.error(e.message || '发送失败，请重试')
      } finally {
        loading.value = false
      }
    }
  })
}

const clearTurnstileTokenState = () => {
  turnstileToken.value = ''
}

const removeTurnstileWidget = () => {
  if (turnstileWidgetId.value && window.turnstile) {
    window.turnstile.remove(turnstileWidgetId.value)
  }
  turnstileWidgetId.value = null
  clearTurnstileTokenState()
}

const renderTurnstileWidget = async () => {
  if (!turnstileSiteKey || !turnstileContainerRef.value || !window.turnstile) {
    return
  }

  removeTurnstileWidget()
  await nextTick()

  turnstileWidgetId.value = window.turnstile.render(turnstileContainerRef.value, {
    sitekey: turnstileSiteKey,
    theme: turnstileTheme.value,
    callback: (token: string) => {
      turnstileToken.value = token
    },
    'expired-callback': () => {
      clearTurnstileTokenState()
      ElMessage.warning('人机验证已过期，请重新完成验证')
    },
    'timeout-callback': () => {
      clearTurnstileTokenState()
      ElMessage.warning('人机验证超时，请重新完成验证')
    },
    'error-callback': () => {
      clearTurnstileTokenState()
      ElMessage.error('人机验证加载失败，请刷新页面重试')
    }
  })
}

const resetTurnstileWidget = (message?: string) => {
  if (!turnstileWidgetId.value || !window.turnstile) {
    clearTurnstileTokenState()
    return
  }
  window.turnstile.reset(turnstileWidgetId.value)
  clearTurnstileTokenState()
  if (message) {
    ElMessage.warning(message)
  }
}

const ensureTurnstileReady = () => {
  if (!turnstileSiteKey) {
    return
  }
  if (window.turnstile?.render) {
    turnstileScriptReady.value = true
    void renderTurnstileWidget()
    return
  }
  if (turnstilePollTimer.value !== null) {
    return
  }
  turnstilePollTimer.value = window.setInterval(() => {
    if (window.turnstile?.render) {
      if (turnstilePollTimer.value !== null) {
        window.clearInterval(turnstilePollTimer.value)
        turnstilePollTimer.value = null
      }
      turnstileScriptReady.value = true
      void renderTurnstileWidget()
    }
  }, 200)

  window.setTimeout(() => {
    if (!turnstileScriptReady.value && turnstilePollTimer.value !== null) {
      ElMessage.warning('Turnstile 脚本加载较慢，请检查网络或稍后刷新页面')
    }
  }, 4000)
}

// Song：---- 密码登录 ----
const handlePasswordLogin = async () => {
  if (!turnstileSiteKey) {
    ElMessage.error('未配置 Turnstile Site Key，当前无法登录')
    return
  }
  if (!turnstileToken.value) {
    ElMessage.warning('请先完成人机验证')
    return
  }
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await authApi.login({
        loginType: 'password',
        account: pwdForm.account,
        password: pwdForm.password,
        rememberMe: pwdForm.rememberMe,
        'cf-turnstile-response': turnstileToken.value
      })
      if (res.code !== 2000 || !res.data) {
        resetTurnstileWidget('人机验证已刷新，请重新完成验证后再试')
        ElMessage.error(res.message || '登录失败')
        return
      }
      await processLoginResponse(res.data, pwdForm.rememberMe)
    } catch (e: any) {
      resetTurnstileWidget('人机验证已刷新，请重新完成验证后再试')
      ElMessage.error(e.message || '登录失败，请检查账号密码')
    } finally {
      loading.value = false
    }
  })
}

// Song：---- 验证码登录 ----
const handleOtpLogin = async () => {
  if (!turnstileSiteKey) {
    ElMessage.error('未配置 Turnstile Site Key，当前无法登录')
    return
  }
  if (!turnstileToken.value) {
    ElMessage.warning('请先完成人机验证')
    return
  }
  if (!otpFormRef.value) return
  await otpFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await authApi.login({
        loginType: 'otp',
        email: otpForm.email,
        code: otpForm.code,
        rememberMe: otpForm.rememberMe,
        'cf-turnstile-response': turnstileToken.value
      })
      if (res.code !== 2000 || !res.data) {
        resetTurnstileWidget('人机验证已刷新，请重新完成验证后再试')
        ElMessage.error(res.message || '登录失败')
        return
      }
      await processLoginResponse(res.data, otpForm.rememberMe)
    } catch (e: any) {
      resetTurnstileWidget('人机验证已刷新，请重新完成验证后再试')
      ElMessage.error(e.message || '验证码错误或已失效')
    } finally {
      loading.value = false
    }
  })
}

// Song：---- 登录成功后的统一处理 ----
const handleLoginSuccess = async (data: { accessToken: string; refreshToken: string }, rememberMe: boolean) => {
  userStore.setAuth(data.accessToken, data.refreshToken, rememberMe)

  try {
    await ensureCurrentUserProfile({ force: true })
  } catch {
    // Song：即使获取资料失败也已登录
  }

  ElMessage.success('登录成功，欢迎回来！')
  const redirect = route.query.redirect as string || '/'
  router.push(redirect)
}

const processLoginResponse = async (
  data: {
    accessToken?: string
    refreshToken?: string
    twoFactorRequired?: boolean
    twoFactorTicket?: string
  },
  rememberMe: boolean
) => {
  if (data.twoFactorRequired && data.twoFactorTicket) {
    twoFactorTicket.value = data.twoFactorTicket
    pendingRememberMe.value = rememberMe
    twoFactorForm.code = ''
    showTwoFactorDialog.value = true
    ElMessage.info('请完成谷歌验证器二步验证')
    return
  }
  if (data.accessToken && data.refreshToken) {
    await handleLoginSuccess(
      { accessToken: data.accessToken, refreshToken: data.refreshToken },
      rememberMe
    )
    return
  }
  ElMessage.error('登录响应异常，请重试')
}

const handleTwoFactorVerify = async () => {
  if (!twoFactorTicket.value) {
    ElMessage.error('二步验证票据缺失，请重新登录')
    return
  }
  if (!/^[0-9]{6}$/.test(twoFactorForm.code)) {
    ElMessage.warning('请输入6位二步验证码')
    return
  }
  twoFactorLoading.value = true
  try {
    const res = await authApi.verifyTwoFactorLogin({
      ticket: twoFactorTicket.value,
      code: twoFactorForm.code
    })
    if (res.code !== 2000 || !res.data?.accessToken || !res.data?.refreshToken) {
      ElMessage.error(res.message || '二步验证失败')
      return
    }
    showTwoFactorDialog.value = false
    await handleLoginSuccess(
      { accessToken: res.data.accessToken, refreshToken: res.data.refreshToken },
      pendingRememberMe.value
    )
  } catch (e: any) {
    ElMessage.error(e.message || '二步验证失败')
  } finally {
    twoFactorLoading.value = false
  }
}

const resolveRememberPreference = () => {
  return loginMode.value === 'otp' ? !!otpForm.rememberMe : !!pwdForm.rememberMe
}

const startGithubLogin = async () => {
  githubLoading.value = true
  try {
    localStorage.setItem(GITHUB_REMEMBER_KEY, resolveRememberPreference() ? '1' : '0')
    const res = await authApi.getGithubAuthorizeUrl()
    if (res.code !== 2000 || !res.data?.url) {
      ElMessage.error(res.message || '获取GitHub登录地址失败')
      return
    }
    window.location.href = res.data.url
  } catch (e: any) {
    ElMessage.error(e.message || 'GitHub登录暂不可用')
  } finally {
    githubLoading.value = false
  }
}

const handleGithubCallbackIfNeeded = async () => {
  const provider = String(route.query.provider || '')
  const code = String(route.query.code || '')
  const state = String(route.query.state || '')
  if (provider !== 'github' || !code || !state || handlingGithubCallback.value) return
  const rememberMe = localStorage.getItem(GITHUB_REMEMBER_KEY) === '1'
  handlingGithubCallback.value = true
  loading.value = true
  // 先清理 URL 参数，防止与后续 router.push 冲突
  localStorage.removeItem(GITHUB_REMEMBER_KEY)
  const query = { ...route.query }
  delete query.code
  delete query.state
  delete query.provider
  await router.replace({ path: route.path, query })
  try {
    const res = await authApi.githubLogin({ code, state, rememberMe })
    if (res.code !== 2000 || !res.data) {
      ElMessage.error(res.message || 'GitHub登录失败')
      return
    }
    await processLoginResponse(res.data, rememberMe)
  } catch (e: any) {
    ElMessage.error(e.message || 'GitHub登录失败')
  } finally {
    loading.value = false
    handlingGithubCallback.value = false
  }
}

const applyRoutePrefill = () => {
  const account = String(route.query.account || '').trim()
  if (!account) return
  if (account.includes('@')) {
    otpForm.email = account
    pwdForm.account = account
    loginMode.value = 'otp'
  } else {
    pwdForm.account = account
  }
}

onMounted(() => {
  applyRoutePrefill()
  handleGithubCallbackIfNeeded()
  ensureTurnstileReady()
})

watch(
  () => [route.query.account, route.query.provider, route.query.code, route.query.state],
  () => {
    applyRoutePrefill()
    handleGithubCallbackIfNeeded()
  }
)

watch(loginMode, async () => {
  await nextTick()
  if (turnstileScriptReady.value) {
    void renderTurnstileWidget()
  }
})

watch(isDark, () => {
  if (turnstileScriptReady.value) {
    void renderTurnstileWidget()
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (resetTimer) clearInterval(resetTimer)
  if (turnstilePollTimer.value !== null) {
    window.clearInterval(turnstilePollTimer.value)
    turnstilePollTimer.value = null
  }
  removeTurnstileWidget()
})
</script>

<template>
  <div class="wizard-container">
    <div class="wizard-header">
      <h2>欢迎回来</h2>
      <p class="subtitle">登录您的账号，继续探索技术社区</p>
    </div>

    <!-- 登录方式切换 Tab -->
    <el-tabs v-model="loginMode" class="login-tabs">
      <el-tab-pane label="密码登录" name="password"></el-tab-pane>
      <el-tab-pane label="验证码登录" name="otp"></el-tab-pane>
    </el-tabs>

    <el-button
      class="github-btn"
      size="large"
      :loading="githubLoading"
      @click="startGithubLogin"
    >
      GitHub 快捷登录
    </el-button>

    <transition name="el-fade-in" mode="out-in">
      <!-- ===== 密码登录 Tab ===== -->
      <div v-if="loginMode === 'password'" key="password">
        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          label-position="top"
          class="wizard-form"
          @submit.prevent
        >
          <el-form-item prop="account" label="用户名 / 邮箱">
            <el-input
              v-model="pwdForm.account"
              placeholder="请输入用户名或邮箱"
              :prefix-icon="User"
              size="large"
              @keyup.enter="handlePasswordLogin"
            />
          </el-form-item>

          <el-form-item prop="password" label="密码">
            <el-input
              v-model="pwdForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              :prefix-icon="Lock"
              size="large"
              @keyup.enter="handlePasswordLogin"
            />
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="pwdForm.rememberMe">记住我</el-checkbox>
            <el-link type="primary" :underline="false" class="forgot-link" @click="openResetDialog">忘记密码？</el-link>
          </div>

          <div class="turnstile-block">
            <p class="turnstile-label">安全验证</p>
            <p v-if="showTurnstileUsingTestKey" class="turnstile-dev-tip">
              当前为开发环境，已自动启用 Cloudflare Turnstile 测试 Site Key。
            </p>
            <div v-if="showTurnstileUnavailable" class="turnstile-placeholder">
              未配置 Turnstile Site Key，当前无法显示人机验证。
            </div>
            <div v-else ref="turnstileContainerRef" class="turnstile-widget"></div>
            <p v-if="showTurnstileLoading" class="turnstile-loading">
              正在加载 Cloudflare Turnstile...
            </p>
            <p class="turnstile-hint">
              Turnstile token 约 5 分钟内有效，提交失败后需要重新完成验证。
            </p>
          </div>

          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            :disabled="!canSubmitLogin"
            @click="handlePasswordLogin"
          >
            登 录
          </el-button>
        </el-form>
      </div>

      <!-- ===== 验证码登录 Tab ===== -->
      <div v-else key="otp">
        <el-form
          ref="otpFormRef"
          :model="otpForm"
          :rules="otpRules"
          label-position="top"
          class="wizard-form"
          @submit.prevent
        >
          <el-form-item prop="email" label="邮箱">
            <el-input
              v-model="otpForm.email"
              placeholder="请输入注册邮箱"
              :prefix-icon="Message"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="code" label="验证码">
            <div class="otp-input-group">
              <el-input
                v-model="otpForm.code"
                placeholder="6位验证码"
                :prefix-icon="Lock"
                size="large"
                maxlength="6"
                @keyup.enter="handleOtpLogin"
              />
              <el-button
                type="primary"
                plain
                size="large"
                class="send-btn"
                :disabled="countdown > 0"
                :loading="loading && countdown === 0 && !otpForm.code"
                @click="sendOtp"
              >
                {{ countdown > 0 ? `重新发送(${countdown}s)` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="otpForm.rememberMe">记住我</el-checkbox>
          </div>

          <div class="turnstile-block">
            <p class="turnstile-label">安全验证</p>
            <p v-if="showTurnstileUsingTestKey" class="turnstile-dev-tip">
              当前为开发环境，已自动启用 Cloudflare Turnstile 测试 Site Key。
            </p>
            <div v-if="showTurnstileUnavailable" class="turnstile-placeholder">
              未配置 Turnstile Site Key，当前无法显示人机验证。
            </div>
            <div v-else ref="turnstileContainerRef" class="turnstile-widget"></div>
            <p v-if="showTurnstileLoading" class="turnstile-loading">
              正在加载 Cloudflare Turnstile...
            </p>
            <p class="turnstile-hint">
              Turnstile token 约 5 分钟内有效，提交失败后需要重新完成验证。
            </p>
          </div>

          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            :disabled="!canSubmitLogin"
            @click="handleOtpLogin"
          >
            登 录
          </el-button>
        </el-form>
      </div>
    </transition>

    <div class="wizard-footer">
      还没有账号？
      <el-link type="primary" :underline="false" @click="$emit('switch-to-register')">立即注册</el-link>
    </div>

    <!-- 忘记密码弹窗 -->
    <el-dialog v-model="showResetDialog" title="重置密码" width="460px" :close-on-click-modal="false" class="reset-dialog">
      <div class="reset-steps">
        <el-steps :active="resetStep - 1" finish-status="success" simple>
          <el-step title="验证邮箱" />
          <el-step title="输入验证码" />
          <el-step title="设置新密码" />
        </el-steps>
      </div>

      <div class="reset-body">
        <!-- Step 1: 输入邮箱 -->
        <div v-if="resetStep === 1">
          <p class="reset-tip">请输入您注册时使用的邮箱地址，我们将向其发送验证码。</p>
          <el-input v-model="resetForm.email" placeholder="请输入注册邮箱" :prefix-icon="Message" size="large" />
          <el-button type="primary" size="large" class="reset-btn" :loading="resetLoading" @click="sendResetCode">发送验证码</el-button>
        </div>

        <!-- Step 2: 输入验证码 -->
        <div v-if="resetStep === 2">
          <p class="reset-tip">验证码已发送至 <strong>{{ resetForm.email }}</strong></p>
          <el-input v-model="resetForm.code" placeholder="6位验证码" :prefix-icon="Lock" size="large" maxlength="6" />
          <div class="reset-actions">
            <el-button :disabled="resetCountdown > 0" @click="sendResetCode" size="large" plain>{{ resetCountdown > 0 ? `重新发送(${resetCountdown}s)` : '重新发送' }}</el-button>
            <el-button type="primary" size="large" :loading="resetLoading" @click="verifyResetCode">验证</el-button>
          </div>
        </div>

        <!-- Step 3: 设置新密码 -->
        <div v-if="resetStep === 3">
          <p class="reset-tip">请设置您的新密码（至少6位）。</p>
          <el-input v-model="resetForm.newPassword" type="password" placeholder="新密码" show-password :prefix-icon="Lock" size="large" style="margin-bottom: 16px" />
          <el-input v-model="resetForm.confirmPassword" type="password" placeholder="确认新密码" show-password :prefix-icon="Lock" size="large" />
          <el-button type="primary" size="large" class="reset-btn" :loading="resetLoading" @click="handleResetPassword">重置密码</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="showTwoFactorDialog" title="二步验证" width="420px" :close-on-click-modal="false">
      <p class="reset-tip">账号已开启谷歌验证器，请输入 6 位动态验证码完成登录。</p>
      <el-input
        v-model="twoFactorForm.code"
        placeholder="请输入6位验证码"
        maxlength="6"
        size="large"
        @keyup.enter="handleTwoFactorVerify"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showTwoFactorDialog = false">取消</el-button>
          <el-button type="primary" :loading="twoFactorLoading" @click="handleTwoFactorVerify">验证登录</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wizard-header {
  margin-bottom: 32px;
}
.wizard-header h2 {
  font-size: 28px;
  font-weight: 800;
  margin: 0 0 8px 0;
  color: var(--el-text-color-primary);
}
.subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.login-tabs {
  margin-bottom: 8px;
}
.login-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 600;
}

.github-btn {
  width: 100%;
  margin-bottom: 16px;
}

.otp-input-group {
  display: flex;
  gap: 12px;
  width: 100%;
}
.otp-input-group .el-input {
  flex: 1;
}
.send-btn {
  width: 130px;
}

.login-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.login-btn {
  width: 100%;
  margin-top: 16px;
}

.forgot-link {
  font-size: 13px;
}

.turnstile-block {
  margin-top: 18px;
}

.turnstile-label {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.turnstile-widget {
  min-height: 66px;
}

.turnstile-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 66px;
  padding: 12px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 12px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  text-align: center;
}

.turnstile-loading,
.turnstile-dev-tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
}

.turnstile-loading {
  color: var(--el-text-color-secondary);
}

.turnstile-dev-tip {
  color: var(--el-color-warning-dark-2);
}

.turnstile-hint {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.wizard-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

/* Song：说明 */
.reset-steps {
  margin-bottom: 32px;
}

.reset-body {
  min-height: 160px;
}

.reset-tip {
  margin: 0 0 20px 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.reset-btn {
  width: 100%;
  margin-top: 20px;
}

.reset-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.reset-actions .el-button {
  flex: 1;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
