<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import {
  Sunny, 
  Moon, 
  Monitor, 
  Bell, 
  Operation, 
  Lock,
  Message,
  Iphone,
  ArrowRight,
  User,
  Upload,
  Edit
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { useUiStore } from '@/store/ui'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/user'
import { authApi } from '@/api/auth'
import { uploadApi } from '@/api/upload'
import { UPLOAD_IMAGE_MAX_SIZE_BYTES, UPLOAD_IMAGE_MAX_SIZE_MB } from '@/constants/upload'
import { CARD_THEME_OPTIONS, getCardThemePalette } from '@/utils/cardTheme'
import { ensureCurrentUserProfile, patchCurrentUserProfile } from '@/utils/sessionProfile'

const userStore = useUserStore()
const uiStore = useUiStore()
const router = useRouter()

// 与 uiStore 同步
const theme = computed(() => uiStore.colorMode || (uiStore.isDark ? 'dark' : 'light'))
const notifications = ref(true)
const twoFactorEnabled = ref(false)

// Song：说明
const profileLoading = ref(false)
const profileForm = reactive({
  nickname: '',
  bio: '',
  school: '',
  major: '',
  gender: 0,
  enrollmentYear: 2024,
  interestTags: '',
  avatar: '',
  profileCardTheme: 'sunset',
  quickCardTheme: 'ocean',
  profileCardBgUrl: '',
  quickCardBgUrl: ''
})

const cardThemeOptions = CARD_THEME_OPTIONS
const profileCardBgUploading = ref(false)
const quickCardBgUploading = ref(false)

const applyProfileForm = (data: any) => {
  if (!data) return
  profileForm.nickname = data.nickname || ''
  profileForm.bio = data.bio || ''
  profileForm.school = data.school || ''
  profileForm.major = data.major || ''
  profileForm.gender = Number(data.gender) || 0
  profileForm.enrollmentYear = data.enrollmentYear || 2024
  profileForm.interestTags = data.interestTags || ''
  profileForm.avatar = data.avatar || ''
  profileForm.profileCardTheme = data.profileCardTheme || 'sunset'
  profileForm.quickCardTheme = data.quickCardTheme || 'ocean'
  profileForm.profileCardBgUrl = data.profileCardBgUrl || ''
  profileForm.quickCardBgUrl = data.quickCardBgUrl || ''
  twoFactorEnabled.value = Number(data.twoFactorEnabled || 0) === 1
  notifications.value = Number(data.emailNotifyEnabled ?? 1) === 1
}

const getThemePreviewStyle = (themeKey: string, customBgUrl?: string) => {
  const palette = getCardThemePalette(themeKey, 'sunset')
  const normalizedUrl = String(customBgUrl || '').trim()
  const hasCustomBg = /^https?:\/\/[^"'\s]+$/.test(normalizedUrl) || /^\/uploads\/[^"'\s]+$/.test(normalizedUrl)
  return {
    background: hasCustomBg
      ? `linear-gradient(135deg, rgba(255,255,255,0.78), rgba(255,255,255,0.78)), url("${normalizedUrl}") center/cover no-repeat`
      : palette.background,
    borderColor: palette.borderColor
  }
}

const loadProfile = async () => {
  try {
    const data = await ensureCurrentUserProfile()
    if (data) {
      applyProfileForm(data)
    }
  } catch {
    // 网络抖一下别把设置页整崩，优先兜底到当前内存里的用户信息。
    if (userStore.userInfo) {
      applyProfileForm(userStore.userInfo)
    }
  }
}

const loadNotificationSettings = async () => {
  try {
    const res = await userApi.getNotificationSettings()
    if (res.code === 2000 && res.data) {
      notifications.value = !!res.data.emailNotifyEnabled
    }
  } catch {
    // Song：保持当前值，不打断页面使用
  }
}

const handleSaveProfile = async () => {
  profileLoading.value = true
  try {
    await userApi.updateUserDetails({
      nickname: profileForm.nickname,
      bio: profileForm.bio,
      school: profileForm.school,
      major: profileForm.major,
      gender: profileForm.gender,
      enrollmentYear: profileForm.enrollmentYear,
      interestTags: profileForm.interestTags || 'Java,Spring Boot,Vue3,Docker',
      avatar: profileForm.avatar || userStore.userInfo?.avatar || '',
      profileCardTheme: profileForm.profileCardTheme,
      quickCardTheme: profileForm.quickCardTheme,
      profileCardBgUrl: profileForm.profileCardBgUrl || '',
      quickCardBgUrl: profileForm.quickCardBgUrl || ''
    })
    ElMessage.success('个人信息已更新')
    // 这里不再立刻回捞 /user/profile，更新接口刚走完，再补一枪只是在挤接口。
    patchCurrentUserProfile({
      nickname: profileForm.nickname,
      bio: profileForm.bio,
      school: profileForm.school,
      major: profileForm.major,
      gender: profileForm.gender,
      enrollmentYear: profileForm.enrollmentYear,
      interestTags: profileForm.interestTags || 'Java,Spring Boot,Vue3,Docker',
      avatar: profileForm.avatar || userStore.userInfo?.avatar || '',
      profileCardTheme: profileForm.profileCardTheme,
      quickCardTheme: profileForm.quickCardTheme,
      profileCardBgUrl: profileForm.profileCardBgUrl || '',
      quickCardBgUrl: profileForm.quickCardBgUrl || '',
      emailNotifyEnabled: notifications.value ? 1 : 0,
      twoFactorEnabled: twoFactorEnabled.value ? 1 : 0,
    } as any)
  } catch {
    ElMessage.error('更新失败，请重试')
  } finally {
    profileLoading.value = false
  }
}

const handleAvatarUpload = async (file: any) => {
  const rawFile = file.raw || file
  if (!rawFile?.type?.startsWith?.('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (rawFile.size > UPLOAD_IMAGE_MAX_SIZE_BYTES) {
    ElMessage.warning(`头像不能超过 ${UPLOAD_IMAGE_MAX_SIZE_MB}MB`)
    return
  }
  try {
    const res = await userApi.updateAvatar(rawFile)
    const url = typeof res?.data === 'string' ? res.data : ''
    if (!url) {
      throw new Error('头像地址为空')
    }
    ElMessage.success('头像更新成功')
    profileForm.avatar = url
    patchCurrentUserProfile({ avatar: url } as any)
  } catch {
    ElMessage.error('头像上传失败')
  }
}

const setTheme = (t: string) => {
  uiStore.setColorMode(t as 'light' | 'dark' | 'system')
  ElMessage.success(`外观模式已切换为: ${t === 'light' ? '浅色' : t === 'dark' ? '深色' : '跟随系统'}`)
}

const uploadCardBg = async (file: File, target: 'profile' | 'quick') => {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  if (file.size > UPLOAD_IMAGE_MAX_SIZE_BYTES) {
    ElMessage.warning(`背景图不能超过 ${UPLOAD_IMAGE_MAX_SIZE_MB}MB`)
    return
  }

  if (target === 'profile') profileCardBgUploading.value = true
  if (target === 'quick') quickCardBgUploading.value = true
  try {
    const url = await uploadApi.uploadImage(file, 'profile-card')
    if (!url) {
      throw new Error('上传失败')
    }
    if (target === 'profile') {
      profileForm.profileCardBgUrl = url
    } else {
      profileForm.quickCardBgUrl = url
    }
    ElMessage.success('背景图上传成功，记得点击保存修改')
  } catch (e: any) {
    if (!e?.response) {
      ElMessage.error(e?.message || '背景图上传失败')
    }
  } finally {
    if (target === 'profile') profileCardBgUploading.value = false
    if (target === 'quick') quickCardBgUploading.value = false
  }
}

const handleProfileCardBgUpload = async (file: any) => {
  const rawFile = file?.raw || file
  if (!rawFile) return
  await uploadCardBg(rawFile, 'profile')
}

const handleQuickCardBgUpload = async (file: any) => {
  const rawFile = file?.raw || file
  if (!rawFile) return
  await uploadCardBg(rawFile, 'quick')
}

const clearCustomBg = (target: 'profile' | 'quick') => {
  if (target === 'profile') {
    profileForm.profileCardBgUrl = ''
  } else {
    profileForm.quickCardBgUrl = ''
  }
}

const handleNotificationSwitch = async (value: boolean) => {
  try {
    await userApi.updateNotificationSettings({ emailNotifyEnabled: value })
    ElMessage.success(value ? '已开启邮件同步通知' : '已关闭邮件同步通知')
  } catch {
    notifications.value = !value
    ElMessage.error('通知设置保存失败')
  }
}

const showTwoFactorSetupDialog = ref(false)
const twoFactorSetupLoading = ref(false)
const twoFactorSetupForm = reactive({
  secret: '',
  qrCodeUrl: '',
  code: ''
})

const openTwoFactorSetup = async () => {
  twoFactorSetupLoading.value = true
  try {
    const res = await authApi.getTwoFactorSetup()
    if (res.code !== 2000 || !res.data) {
      ElMessage.error(res.message || '获取二步验证配置失败')
      return
    }
    twoFactorSetupForm.secret = res.data.secret
    twoFactorSetupForm.qrCodeUrl = res.data.qrCodeUrl
    twoFactorSetupForm.code = ''
    showTwoFactorSetupDialog.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '获取二步验证配置失败')
  } finally {
    twoFactorSetupLoading.value = false
  }
}

const confirmEnableTwoFactor = async () => {
  if (!/^[0-9]{6}$/.test(twoFactorSetupForm.code)) {
    ElMessage.warning('请输入6位动态验证码')
    return
  }
  twoFactorSetupLoading.value = true
  try {
    const res = await authApi.enableTwoFactor({ code: twoFactorSetupForm.code })
    if (res.code !== 2000) {
      ElMessage.error(res.message || '开启失败')
      return
    }
    twoFactorEnabled.value = true
    showTwoFactorSetupDialog.value = false
    ElMessage.success('谷歌验证器二步验证已开启')
  } catch (e: any) {
    ElMessage.error(e.message || '开启失败')
  } finally {
    twoFactorSetupLoading.value = false
  }
}

const closeTwoFactor = async () => {
  try {
    const promptResult: any = await ElMessageBox.prompt('请输入谷歌验证器6位动态码', '关闭二步验证', {
      confirmButtonText: '关闭',
      cancelButtonText: '取消',
      inputPattern: /^[0-9]{6}$/,
      inputErrorMessage: '请输入6位数字验证码'
    })
    const value = String(promptResult?.value || '')
    if (!value) return
    const res = await authApi.disableTwoFactor({ code: value })
    if (res.code !== 2000) {
      ElMessage.error(res.message || '关闭失败')
      return
    }
    twoFactorEnabled.value = false
    ElMessage.success('已关闭二步验证')
  } catch {
    // Song：用户取消或请求失败均不额外处理
  }
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/auth/login')
    ElMessage.info('期待再次见到你 👋')
  }).catch(() => {})
}

// Song：说明
const showPasswordDialog = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const handleUpdatePassword = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.error('请填写完整')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  
  try {
    await userApi.updatePwd({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
      confirmPassword: pwdForm.confirmPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    showPasswordDialog.value = false
    setTimeout(() => {
      userStore.logout()
      router.push('/auth/login')
    }, 1500)
  } catch (error) {
    ElMessage.error('密码修改失败，请检查原密码')
  }
}

onMounted(() => {
  loadProfile()
  loadNotificationSettings()
})
</script>

<template>
  <MainLayout>
    <div class="settings-container">
      <div class="settings-header">
        <h1 class="page-title">系统设置</h1>
        <p class="page-subtitle">PROFILE · PREFERENCES · SECURITY</p>
      </div>

      <div class="settings-sections">
        <!-- Profile Editing -->
        <el-card class="settings-card" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </div>
          </template>

          <div class="profile-edit-section">
            <div class="avatar-edit">
              <el-avatar :size="80" :src="userStore.userInfo?.avatar">
                {{ (userStore.userInfo?.nickname || userStore.userInfo?.username || '?').charAt(0) }}
              </el-avatar>
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                @change="handleAvatarUpload"
              >
                <el-button size="small" :icon="Upload">更换头像</el-button>
              </el-upload>
            </div>

            <el-form label-position="top" class="profile-form">
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="昵称">
                    <el-input v-model="profileForm.nickname" placeholder="输入昵称" maxlength="20" show-word-limit />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="性别">
                    <el-select v-model="profileForm.gender" style="width: 100%">
                      <el-option label="未设置" :value="0" />
                      <el-option label="男" :value="1" />
                      <el-option label="女" :value="2" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-form-item label="个人简介">
                <el-input v-model="profileForm.bio" type="textarea" :rows="3" placeholder="介绍一下自己吧..." maxlength="200" show-word-limit />
              </el-form-item>

              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="学校">
                    <el-input v-model="profileForm.school" placeholder="输入你的学校" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="专业">
                    <el-input v-model="profileForm.major" placeholder="输入你的专业" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="入学年份">
                    <el-input-number v-model="profileForm.enrollmentYear" :min="2000" :max="2030" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="极客标签 (用逗号分隔)">
                    <el-input v-model="profileForm.interestTags" placeholder="例如: Java,Spring Boot,Vue3,Docker" />
                  </el-form-item>
                </el-col>
              </el-row>

              <div class="form-actions">
                <el-button type="primary" :loading="profileLoading" @click="handleSaveProfile">
                  <el-icon><Edit /></el-icon> 保存修改
                </el-button>
              </div>
            </el-form>
          </div>
        </el-card>

        <!-- Appearance -->
        <el-card class="settings-card" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Sunny /></el-icon>
              <span>外观视觉</span>
            </div>
          </template>
          
          <div class="theme-grid">
            <div 
              class="theme-item" 
              :class="{ active: theme === 'light' }"
              @click="setTheme('light')"
            >
              <div class="theme-icon"><el-icon><Sunny /></el-icon></div>
              <span class="theme-label">日常浅色</span>
            </div>

            <div 
              class="theme-item" 
              :class="{ active: theme === 'dark' }"
              @click="setTheme('dark')"
            >
              <div class="theme-icon"><el-icon><Moon /></el-icon></div>
              <span class="theme-label">护眼深色</span>
            </div>

            <div 
              class="theme-item" 
              :class="{ active: theme === 'system' }"
              @click="setTheme('system')"
            >
              <div class="theme-icon"><el-icon><Monitor /></el-icon></div>
              <span class="theme-label">自动切换</span>
            </div>
          </div>

          <div class="card-bg-config">
            <div class="card-bg-title">资料卡背景</div>
            <el-row :gutter="16">
              <el-col :span="12" :xs="24">
                <div class="bg-config-item">
                  <span class="bg-label">个人资料卡片</span>
                  <el-select v-model="profileForm.profileCardTheme" style="width: 100%">
                    <el-option
                      v-for="opt in cardThemeOptions"
                      :key="`profile-${opt.key}`"
                      :label="opt.label"
                      :value="opt.key"
                    />
                  </el-select>
                  <div class="bg-upload-row">
                    <el-upload
                      :auto-upload="false"
                      :show-file-list="false"
                      accept="image/*"
                      @change="handleProfileCardBgUpload"
                    >
                      <el-button
                        size="small"
                        :icon="Upload"
                        :loading="profileCardBgUploading"
                      >
                        {{ profileForm.profileCardBgUrl ? '更换背景图' : '上传背景图' }}
                      </el-button>
                    </el-upload>
                    <el-button
                      size="small"
                      text
                      :disabled="!profileForm.profileCardBgUrl"
                      @click="clearCustomBg('profile')"
                    >
                      清空自定义图
                    </el-button>
                  </div>
                  <div class="bg-preview" :style="getThemePreviewStyle(profileForm.profileCardTheme, profileForm.profileCardBgUrl)">
                    个人资料卡片预览
                  </div>
                </div>
              </el-col>
              <el-col :span="12" :xs="24">
                <div class="bg-config-item">
                  <span class="bg-label">头像预览卡片</span>
                  <el-select v-model="profileForm.quickCardTheme" style="width: 100%">
                    <el-option
                      v-for="opt in cardThemeOptions"
                      :key="`quick-${opt.key}`"
                      :label="opt.label"
                      :value="opt.key"
                    />
                  </el-select>
                  <div class="bg-upload-row">
                    <el-upload
                      :auto-upload="false"
                      :show-file-list="false"
                      accept="image/*"
                      @change="handleQuickCardBgUpload"
                    >
                      <el-button
                        size="small"
                        :icon="Upload"
                        :loading="quickCardBgUploading"
                      >
                        {{ profileForm.quickCardBgUrl ? '更换背景图' : '上传背景图' }}
                      </el-button>
                    </el-upload>
                    <el-button
                      size="small"
                      text
                      :disabled="!profileForm.quickCardBgUrl"
                      @click="clearCustomBg('quick')"
                    >
                      清空自定义图
                    </el-button>
                  </div>
                  <div class="bg-preview" :style="getThemePreviewStyle(profileForm.quickCardTheme, profileForm.quickCardBgUrl)">
                    头像预览卡片预览
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-card>

        <!-- Notifications -->
        <el-card class="settings-card" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Bell /></el-icon>
              <span>个人通知</span>
            </div>
          </template>
          
          <div class="setting-item">
            <div class="item-info">
              <div class="item-icon bg-blue"><el-icon><Message /></el-icon></div>
              <div class="item-text">
                <div class="item-title">重要消息推送</div>
                <div class="item-desc">获赞、评论及系统提醒</div>
              </div>
            </div>
            <el-switch v-model="notifications" @change="(value: string | number | boolean) => handleNotificationSwitch(Boolean(value))" />
          </div>

          <div class="setting-item disabled">
            <div class="item-info">
              <div class="item-icon bg-indigo"><el-icon><Iphone /></el-icon></div>
              <div class="item-text">
                <div class="item-title">短信通知服务</div>
                <div class="item-desc">敏感操作短信验证</div>
              </div>
            </div>
            <el-tag size="small" type="info" effect="plain">暂不可用</el-tag>
          </div>
        </el-card>

        <!-- Security -->
        <el-card class="settings-card" shadow="never">
          <template #header>
            <div class="card-header">
              <el-icon><Operation /></el-icon>
              <span>账户与安全</span>
            </div>
          </template>
          
          <div class="action-grid">
            <div class="action-item danger" @click="handleLogout">
              <div class="item-info">
                <div class="item-icon"><el-icon><Lock /></el-icon></div>
                <div class="item-text">
                  <div class="item-title">安全退出登录</div>
                  <div class="item-desc">结束当前会话</div>
                </div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </div>

            <div class="action-item" @click="showPasswordDialog = true">
              <div class="item-info">
                <div class="item-icon"><el-icon><Operation /></el-icon></div>
                <div class="item-text">
                  <div class="item-title">修改账户密码</div>
                  <div class="item-desc">定期更新更安全</div>
                </div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </div>

            <div
              class="action-item"
              @click="twoFactorEnabled ? closeTwoFactor() : openTwoFactorSetup()"
            >
              <div class="item-info">
                <div class="item-icon"><el-icon><Lock /></el-icon></div>
                <div class="item-text">
                  <div class="item-title">谷歌验证器二步验证</div>
                  <div class="item-desc">{{ twoFactorEnabled ? '已开启，点击可关闭' : '未开启，点击进行配置' }}</div>
                </div>
              </div>
              <el-tag size="small" :type="twoFactorEnabled ? 'success' : 'info'" effect="plain">
                {{ twoFactorEnabled ? '已开启' : '未开启' }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </div>

      <div class="version-info">
        <el-tag round effect="plain" class="version-tag">
          <span class="dot"></span> Zens · v2.0.4-stable
        </el-tag>
      </div>
    </div>

    <!-- Password Dialog -->
    <el-dialog
      v-model="showPasswordDialog"
      title="修改账户密码"
      width="400px"
      append-to-body
      class="pwd-dialog"
    >
      <el-form label-position="top">
        <el-form-item label="当前密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入原密码" />
        </el-form-item>
        <el-form-item label="新设密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showPasswordDialog = false">取消</el-button>
          <el-button type="primary" @click="handleUpdatePassword">确认更新</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showTwoFactorSetupDialog"
      title="配置谷歌验证器"
      width="460px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="twofactor-box">
        <p>1. 用谷歌验证器扫描二维码，或手动输入密钥。</p>
        <div class="twofactor-qr-wrap">
          <img :src="twoFactorSetupForm.qrCodeUrl" alt="2FA QR" class="twofactor-qr" />
        </div>
        <p class="twofactor-secret">密钥：{{ twoFactorSetupForm.secret }}</p>
        <p>2. 输入谷歌验证器里显示的 6 位动态码完成绑定。</p>
        <el-input v-model="twoFactorSetupForm.code" maxlength="6" placeholder="请输入6位动态码" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showTwoFactorSetupDialog = false">取消</el-button>
          <el-button type="primary" :loading="twoFactorSetupLoading" @click="confirmEnableTwoFactor">确认开启</el-button>
        </div>
      </template>
    </el-dialog>
  </MainLayout>
</template>

<style scoped>
.settings-container {
  max-width: min(100%, var(--cp-profile-page-width, 1080px));
  margin: 0 auto;
  padding-bottom: 40px;
}

.settings-header {
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 4px 0 0 0;
  font-size: 10px;
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  letter-spacing: 0.1em;
}

.settings-sections {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-card {
  border-radius: var(--el-border-radius-base);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* Song：说明 */
.profile-edit-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.avatar-edit {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-form {
  max-width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}

/* Song：说明 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

.theme-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  border-radius: var(--el-border-radius-base);
  border: 2px solid var(--el-border-color-lighter);
  background-color: var(--el-fill-color-blank);
  cursor: pointer;
  transition: all 0.3s;
}

.theme-item:hover {
  border-color: var(--el-color-primary-light-5);
  background-color: var(--el-color-primary-light-9);
}

.theme-item.active {
  background-color: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}

.theme-icon {
  font-size: 24px;
}

.theme-label {
  font-size: 12px;
  font-weight: 700;
}

.card-bg-config {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 16px;
}

.card-bg-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.bg-config-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bg-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.bg-preview {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--el-text-color-primary);
}

.bg-upload-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: var(--el-fill-color-lighter);
  border-radius: var(--el-border-radius-base);
  margin-bottom: 12px;
}

.setting-item.disabled {
  opacity: 0.6;
}

.item-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.item-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}

.bg-blue { color: var(--el-color-primary); }
.bg-indigo { color: #6366f1; }

.item-text {
  display: flex;
  flex-direction: column;
}

.item-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.item-desc {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 2px;
}

.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.action-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: var(--el-fill-color-lighter);
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.action-item:hover {
  background-color: var(--el-bg-color);
  border-color: var(--el-border-color);
  box-shadow: var(--el-box-shadow-light);
}

.action-item.danger {
  color: var(--el-color-danger);
}
.action-item.danger:hover {
  background-color: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-7);
}

.version-info {
  margin-top: 48px;
  text-align: center;
}

.version-tag {
  color: var(--el-text-color-placeholder);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background-color: #10b981;
  border-radius: 50%;
  margin-right: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.twofactor-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.twofactor-qr-wrap {
  display: flex;
  justify-content: center;
}

.twofactor-qr {
  width: 200px;
  height: 200px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.twofactor-secret {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

@media (max-width: 768px) {
  .theme-grid, .action-grid {
    grid-template-columns: 1fr;
  }
}
</style>
