<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ChatDotRound, Clock, Document, Setting, Star, SwitchButton, User } from '@element-plus/icons-vue'
import { hasAdminRole, hasBackofficeAccess } from '@/utils/sessionProfile'

const router = useRouter()
const userStore = useUserStore()

const isLoggedIn = computed(() => !!userStore.accessToken)
const userInfo = computed(() => userStore.userInfo)

const isAdmin = computed(() => hasAdminRole(userInfo.value))
const canEnterBackoffice = computed(() => hasBackofficeAccess(userInfo.value))
const backofficeLabel = computed(() => (isAdmin.value ? '管理后台' : '版务后台'))

const handleCommand = (command: string) => {
  if (command === 'logout') {
    userStore.logout()
    router.push('/auth?type=login')
  } else {
    router.push(command)
  }
}

const goToLogin = () => {
  router.push('/auth?type=login')
}
</script>

<template>
  <div class="user-menu">
    <template v-if="isLoggedIn">
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="avatar-trigger">
          <el-avatar :size="36" :src="userInfo?.avatar" class="user-avatar">
            {{ userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown">
            <div class="user-info-dropdown">
              <span class="username">{{ userInfo?.username }}</span>
              <span class="user-id">UID: {{ userInfo?.id }}</span>
            </div>
            <el-dropdown-item divided command="/me">
              <el-icon><User /></el-icon> 个人资料
            </el-dropdown-item>
            <el-dropdown-item command="/me?tab=posts">
              <el-icon><Document /></el-icon> 我的帖子
            </el-dropdown-item>
            <el-dropdown-item command="/me?tab=favorites">
              <el-icon><Star /></el-icon> 我的收藏
            </el-dropdown-item>
            <el-dropdown-item command="/me?tab=history">
              <el-icon><Clock /></el-icon> 浏览记录
            </el-dropdown-item>
            <el-dropdown-item command="/messages">
              <el-icon><ChatDotRound /></el-icon> 私信会话
            </el-dropdown-item>
            <el-dropdown-item command="/settings" divided>
              <el-icon><Setting /></el-icon> 账户设置
            </el-dropdown-item>
            <el-dropdown-item v-if="canEnterBackoffice" command="/admin" divided>
              <el-icon><Setting /></el-icon> {{ backofficeLabel }}
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided class="logout-item">
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>
    <template v-else>
      <el-button type="primary" plain @click="goToLogin">登录 / 注册</el-button>
    </template>
  </div>
</template>

<style scoped>
.user-menu {
  display: flex;
  align-items: center;
}

.avatar-trigger {
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
}

.avatar-trigger:hover {
  opacity: 0.8;
}

.user-avatar {
  background-color: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-weight: 600;
  border: 1px solid var(--el-border-color-lighter);
}

.user-info-dropdown {
  padding: 12px 20px;
  display: flex;
  flex-direction: column;
  outline: none;
}

.user-info-dropdown .username {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.user-info-dropdown .user-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.user-dropdown {
  min-width: 180px;
}

.logout-item {
  color: var(--el-color-danger) !important;
}
</style>
