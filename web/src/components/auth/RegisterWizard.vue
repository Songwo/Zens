<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Message, User, Lock, Ticket, Right, Check } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()

const currentStep = ref(0)
const loading = ref(false)

// Song：说明
const formRef = ref<FormInstance>()
const form = reactive({
  // Song：说明
  accountType: 'email',
  account: '',
  code: '',
  // Song：说明
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  agreement: false,
  // Song：说明
  inviteCode: ''
})

const countdown = ref(0)
let timer: any = null

const usernameStatus = ref<'idle' | 'checking' | 'success' | 'error'>('idle')
const usernameMsg = ref('')
const inviteAuthDetails = ref<any>(null)

// Song：暂存验证码用于注册
const verifiedCode = ref('')

// Song：说明
const pwdStrength = ref(0) // Song：0-3
const checkPwdStrength = (val: string) => {
  if (!val) { pwdStrength.value = 0; return }
  let strength = 0
  if (val.length >= 8) strength += 1
  if (/[A-Za-z]/.test(val) && /[0-9]/.test(val)) strength += 1
  if (/[^A-Za-z0-9]/.test(val)) strength += 1
  pwdStrength.value = strength
}

const rules = reactive<FormRules>({
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (form.accountType === 'email') {
          const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
          emailRegex.test(value) ? callback() : callback(new Error('请输入有效的邮箱地址'))
        } else {
          const phoneRegex = /^1[3-9]\d{9}$/
          phoneRegex.test(value) ? callback() : callback(new Error('请输入有效的手机号'))
        }
      },
      trigger: 'blur'
    }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入唯一的用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '长度在 4 到 20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (usernameStatus.value === 'error') {
          callback(new Error(usernameMsg.value))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码不能少于8位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入密码不一致!'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  agreement: [
    {
      validator: (_rule, value, callback) => {
        if (!value) callback(new Error('请阅读并同意用户协议'))
        else callback()
      },
      trigger: 'change'
    }
  ]
})

const sendOtp = async () => {
  formRef.value?.validateField('account', async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // Song：注册时先检查邮箱是否已注册
        const checkRes = await authApi.checkEmail(form.account)
        if (checkRes.code === 2000 && checkRes.data && checkRes.data.exists) {
          ElMessage.warning('该邮箱已注册，请直接登录')
          loading.value = false
          return
        }

        await authApi.sendOtp(form.account)
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

const handleUsernameBlur = async () => {
  if (!form.username || form.username.length < 4) return
  usernameStatus.value = 'checking'
  try {
    const res = await authApi.checkUsername(form.username)
    if (res.code === 2000 && res.data) {
      if (res.data.available) {
        usernameStatus.value = 'success'
        usernameMsg.value = '用户名可用'
      } else {
        usernameStatus.value = 'error'
        usernameMsg.value = res.data.message || '用户名已被使用'
      }
    } else {
      usernameStatus.value = 'success'
      usernameMsg.value = '用户名可用'
    }
    formRef.value?.validateField('username')
  } catch (e: any) {
    usernameStatus.value = 'error'
    usernameMsg.value = e.message || '用户名已存在'
    formRef.value?.validateField('username')
  }
}

const validateInvite = async () => {
  if (!form.inviteCode) return
  loading.value = true
  try {
    const res = await authApi.validateInviteCode(form.inviteCode)
    if (res.code === 2000) {
      inviteAuthDetails.value = true
      ElMessage.success('邀请码有效')
    } else {
      inviteAuthDetails.value = null
      ElMessage.error(res.message || '邀请码无效')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '无效邀请码')
    inviteAuthDetails.value = null
  } finally {
    loading.value = false
  }
}

const nextStep = async () => {
  if (!formRef.value) return
  
  // Song：说明
  let fieldsToValidate: string[] = []
  if (currentStep.value === 0) fieldsToValidate = ['account', 'code']
  if (currentStep.value === 1) fieldsToValidate = ['username', 'password', 'confirmPassword', 'agreement']
  
  await formRef.value.validateField(fieldsToValidate, async (isValid) => {
    if (isValid) {
      if (currentStep.value === 0) {
        loading.value = true
        try {
          const res = await authApi.verifyOtp(form.account, form.code)
          if (res.code !== 2000) {
            ElMessage.error(res.message || '验证码错误')
            return
          }
          verifiedCode.value = form.code
          currentStep.value++
        } catch (e: any) {
          ElMessage.error('验证码错误')
        } finally {
          loading.value = false
        }
      } else if (currentStep.value === 1) {
        if (usernameStatus.value === 'error') {
          ElMessage.warning('请更换已被使用的用户名')
          return
        }
        currentStep.value++
      } else if (currentStep.value === 2) {
        submitRegistration()
      }
    }
  })
}

const submitRegistration = async () => {
  loading.value = true
  try {
    const registerData = {
      username: form.username,
      password: form.password,
      email: form.account,
      code: verifiedCode.value,
      nickname: form.nickname || undefined,
      inviteCode: form.inviteCode || undefined,
    }

    const res = await authApi.register(registerData)
    if (res.code !== 2000) {
      ElMessage.error(res.message || '注册失败')
      loading.value = false
      return
    }

    currentStep.value = 3 // Song：跳转到完成步骤
    ElMessage.success('注册成功，请登录后继续')
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 从邀请链接自动预填邀请码
  const inviteCode = route.query.invite as string
  if (inviteCode) {
    form.inviteCode = inviteCode
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="wizard-container">
    <div class="wizard-header">
      <h2>{{ currentStep === 3 ? '注册成功！' : '加入社区' }}</h2>
      <p class="subtitle">{{ currentStep === 3 ? '欢迎成为 Zens 的新居民' : '完成几步简单的验证，开始您的探索' }}</p>
    </div>

    <el-steps :active="currentStep" finish-status="success" align-center class="mb-4">
      <el-step title="身份" />
      <el-step title="信息" />
      <el-step title="圈子" />
      <el-step title="完成" />
    </el-steps>

    <el-form 
      ref="formRef" 
      :model="form" 
      :rules="rules" 
      label-position="top"
      class="wizard-form mt-4"
      @submit.prevent
    >
      <transition name="el-fade-in" mode="out-in">
        
        <!-- Step 1: Identity -->
        <div v-if="currentStep === 0" key="step1">
          <el-tabs v-model="form.accountType" class="login-tabs">
            <el-tab-pane label="邮箱注册" name="email"></el-tab-pane>
          </el-tabs>

          <el-form-item prop="account" label="邮箱">
            <el-input 
              v-model="form.account" 
              placeholder="请输入真实的邮箱"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="code" label="验证码">
            <div class="otp-input-group">
              <el-input 
                v-model="form.code" 
                placeholder="6位验证码" 
                :prefix-icon="Message"
                size="large"
                maxlength="6"
              />
              <el-button 
                type="primary" 
                plain 
                size="large"
                class="send-btn"
                :disabled="countdown > 0" 
                :loading="loading && countdown === 0 && !form.code"
                @click="sendOtp"
              >
                {{ countdown > 0 ? `重新发送(${countdown}s)` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
        </div>

        <!-- Step 2: Basic Info -->
        <div v-else-if="currentStep === 1" key="step2">
          <el-form-item prop="username" label="不可重复的唯一用户名">
             <el-input 
              v-model="form.username" 
              placeholder="例如：geek_coder (限数字字母下划线)" 
              size="large"
              @blur="handleUsernameBlur"
            >
              <template #suffix>
                <el-icon v-if="usernameStatus === 'checking'" class="is-loading"><Message /></el-icon>
                <el-icon v-if="usernameStatus === 'success'" color="var(--el-color-success)"><Check /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="nickname" label="对外展示的昵称 (可选)">
             <el-input v-model="form.nickname" placeholder="例如：极客小王" size="large" />
          </el-form-item>

          <el-form-item prop="password" label="账号密码">
            <el-input 
              v-model="form.password" 
              type="password"
              placeholder="设置安全密码，至少8位" 
              show-password
              :prefix-icon="Lock"
              size="large"
              @input="checkPwdStrength"
            />
            <div class="pwd-strength" v-if="form.password">
              <div class="s-bar" :class="{ active: pwdStrength >= 1, weak: pwdStrength === 1 }"></div>
              <div class="s-bar" :class="{ active: pwdStrength >= 2, medium: pwdStrength === 2 }"></div>
              <div class="s-bar" :class="{ active: pwdStrength >= 3, strong: pwdStrength === 3 }"></div>
              <span class="s-text">
                {{ pwdStrength === 1 ? '弱' : pwdStrength === 2 ? '中' : pwdStrength === 3 ? '强' : '' }}
              </span>
            </div>
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input 
              v-model="form.confirmPassword" 
              type="password"
              placeholder="再输入一次确认密码" 
              show-password
              :prefix-icon="Lock"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="agreement">
            <el-checkbox v-model="form.agreement">
              我已阅读并同意
              <el-link type="primary" :underline="false" href="/terms" target="_blank">《服务协议》</el-link>
              与
              <el-link type="primary" :underline="false" href="/privacy" target="_blank">《隐私政策》</el-link>
            </el-checkbox>
          </el-form-item>
        </div>

        <!-- Step 3: Invite Code (Optional) -->
        <div v-else-if="currentStep === 2" key="step3" class="invite-step">
          <div class="invite-banner">
            <el-icon class="i-icon"><Ticket /></el-icon>
            <div class="i-text">
              <h4>输入专属邀请码</h4>
              <p>邀请码用于记录真实好友邀请；注册成功后邀请人获得 30 点经验。没有邀请码也可以直接加入。</p>
            </div>
          </div>

          <el-form-item prop="inviteCode" label="邀请码 (可选)">
             <div class="otp-input-group">
                <el-input v-model="form.inviteCode" placeholder="如：zens2026" size="large" />
                <el-button type="info" plain size="large" :loading="loading" @click="validateInvite">校验</el-button>
             </div>
          </el-form-item>

          <el-alert v-if="inviteAuthDetails" title="校验成功" type="success" show-icon :closable="false" />
        </div>

        <!-- Step 4: Done & Guided Setup -->
        <div v-else-if="currentStep === 3" key="step4" class="done-step">
          <el-result
            icon="success"
            title="注册完成"
            sub-title="请先登录，系统会自动带你回到刚才的页面。"
          >
            <template #extra>
              <div class="guided-actions">
                <el-button
                  type="primary"
                  size="large"
                  @click="router.push({ path: '/auth', query: { type: 'login', account: form.account } })"
                >
                  去登录
                </el-button>
                <el-button size="large" @click="router.push('/')">返回首页</el-button>
              </div>
            </template>
          </el-result>
        </div>
      </transition>

      <!-- Navigation Actions -->
      <div class="wizard-actions" v-if="currentStep < 3">
        <el-button v-if="currentStep > 0" @click="currentStep--" size="large" style="width: 120px;">
          上一步
        </el-button>
        <el-button 
          type="primary" 
          size="large" 
          class="next-btn"
          :loading="loading"
          @click="nextStep"
        >
          {{ currentStep === 0 ? '验证身份' : currentStep === 2 ? '完成注册' : '下一步' }}
          <el-icon v-if="currentStep < 2" class="el-icon--right"><Right /></el-icon>
        </el-button>
      </div>

      <div class="wizard-footer" v-if="currentStep < 3">
        已有账号？ 
        <el-link type="primary" :underline="false" @click="$emit('switch-to-login')">直接登录</el-link>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.wizard-header {
  margin-bottom: 24px;
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

.mb-4 { margin-bottom: 24px; }
.mt-4 { margin-top: 24px; }

.login-tabs { margin-bottom: 20px; }
.login-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 600;
}

.otp-input-group {
  display: flex;
  gap: 12px;
  width: 100%;
}
.otp-input-group .el-input { flex: 1; }
.send-btn { width: 130px; }

.pwd-strength {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  width: 100%;
}
.s-bar {
  height: 4px;
  flex: 1;
  background-color: var(--el-fill-color-darker);
  border-radius: 2px;
  transition: all 0.3s;
}
.s-bar.active.weak { background-color: var(--el-color-danger); }
.s-bar.active.medium { background-color: var(--el-color-warning); }
.s-bar.active.strong { background-color: var(--el-color-success); }
.s-text {
  font-size: 12px;
  font-weight: 600;
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.invite-step {
  padding-top: 10px;
}
.invite-banner {
  display: flex;
  gap: 16px;
  padding: 16px;
  background-color: var(--el-color-primary-light-9);
  border-radius: 8px;
  margin-bottom: 24px;
}
.invite-banner .i-icon {
  font-size: 32px;
  color: var(--el-color-primary);
}
.invite-banner .i-text h4 {
  margin: 0 0 4px 0;
  font-size: 15px;
  color: var(--el-color-primary-dark-2);
}
.invite-banner .i-text p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.done-step {
  padding: 20px 0;
}
.guided-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 280px;
  margin: 0 auto;
}

.wizard-actions {
  display: flex;
  gap: 16px;
  margin-top: 32px;
}
.next-btn {
  flex: 1;
}

.wizard-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
