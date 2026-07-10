<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ChatDotRound, Connection, Document, EditPen, Setting, Star, SwitchButton, User } from '@element-plus/icons-vue'
import { hasAdminRole, hasBackofficeAccess } from '@/utils/sessionProfile'
import { triggerSingleLogout } from '@/utils/singleLogout'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'

const router = useRouter()
const userStore = useUserStore()

const isLoggedIn = computed(() => !!userStore.accessToken)
const userInfo = computed(() => userStore.userInfo)

const isAdmin = computed(() => hasAdminRole(userInfo.value))
const canEnterBackoffice = computed(() => hasBackofficeAccess(userInfo.value))
const backofficeLabel = computed(() => (isAdmin.value ? '管理后台' : '版务后台'))
const avatarUrl = computed(() => resolvePublicAssetUrl(userInfo.value?.avatar))

interface UserMenuItem {
  key: string
  label: string
  icon: Component
  command: string
  divided?: boolean
}

// 配置式下拉项：command 即目标路由（或 'logout'）；每项独立，互不复用跳转逻辑。
// tab 取值与 MePage 现有标签一致：posts / favorites / relations / creator。
const menuItems = computed<UserMenuItem[]>(() => {
  const items: UserMenuItem[] = [
    { key: 'profile', label: '个人资料', icon: User, command: '/me', divided: true },
    { key: 'posts', label: '我的动态', icon: Document, command: '/me?tab=posts' },
    { key: 'favorites', label: '我的收藏', icon: Star, command: '/me?tab=favorites' },
    { key: 'relations', label: '关系', icon: Connection, command: '/me?tab=relations' },
    { key: 'creator', label: '创作管理', icon: EditPen, command: '/me?tab=creator' },
    { key: 'messages', label: '私信会话', icon: ChatDotRound, command: '/messages', divided: true },
    { key: 'metaverse', label: '星港', icon: Connection, command: '/metaverse' },
    { key: 'settings', label: '账户设置', icon: Setting, command: '/settings' },
  ]
  if (canEnterBackoffice.value) {
    items.push({ key: 'admin', label: backofficeLabel.value, icon: Setting, command: '/admin', divided: true })
  }
  items.push({ key: 'logout', label: '退出登录（含子站）', icon: SwitchButton, command: 'logout', divided: true })
  return items
})

const handleCommand = (command: string) => {
  if (command === 'logout') {
    // 单点登出:先触发各子站前端通道登出,再清主站会话并跳登录页
    triggerSingleLogout()
    userStore.logout()
    router.push('/auth?type=login')
    return
  }
  router.push(command)
}

const goToLogin = () => {
  router.push('/auth?type=login')
}
</script>

<template>
  <div class="user-menu">
    <template v-if="isLoggedIn">
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="avatar-trigger" type="button" aria-label="打开账户菜单" title="账户菜单">
          <el-avatar :size="36" :src="avatarUrl" class="user-avatar">
            {{ userInfo?.username?.charAt(0)?.toUpperCase() || 'U' }}
          </el-avatar>
        </button>
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown">
            <div class="user-info-dropdown">
              <span class="username">{{ userInfo?.username }}</span>
              <span class="user-id">UID: {{ userInfo?.id }}</span>
            </div>
            <el-dropdown-item
              v-for="item in menuItems"
              :key="item.key"
              :command="item.command"
              :divided="item.divided"
              :class="{ 'logout-item': item.command === 'logout' }"
            >
              <el-icon><component :is="item.icon" /></el-icon> {{ item.label }}
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
  appearance: none;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
}

.avatar-trigger:hover {
  opacity: 0.8;
}

.avatar-trigger:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
  border-radius: 50%;
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
