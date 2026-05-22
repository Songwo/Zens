<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  PieChart,
  Document,
  Delete,
  User,
  Flag,
  SwitchButton,
  Back,
  Menu as IconMenu,
  Timer,
  Medal,
  SetUp,
  Ticket,
  Reading,
  Cpu,
  Connection
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { hasAdminRole, hasModeratorCapability } from '@/utils/sessionProfile'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const allMenuItems = [
  { id: 'dashboard', name: '数据看板', icon: PieChart, path: '/admin/dashboard', access: 'admin' },
  { id: 'my-sections', name: '我的板块', icon: Medal, path: '/admin/my-sections', access: 'moderator-only' },
  { id: 'posts', name: '内容管理', icon: Document, path: '/admin/posts', access: 'backoffice' },
  { id: 'posts-trash', name: '内容回收站', icon: Delete, path: '/admin/posts/trash', access: 'backoffice' },
  { id: 'sections', name: '板块管理', icon: IconMenu, path: '/admin/sections', access: 'admin' },
  { id: 'users', name: '用户管理', icon: User, path: '/admin/users', access: 'admin' },
  { id: 'reports', name: '举报管理', icon: Flag, path: '/admin/reports', access: 'backoffice' },
  { id: 'cache', name: '缓存管理', icon: SetUp, path: '/admin/cache', access: 'admin' },
  { id: 'logs', name: '日志管理', icon: Reading, path: '/admin/logs', access: 'admin' },
  { id: 'media', name: '媒体存储', icon: Cpu, path: '/admin/media', access: 'admin' },
  { id: 'changelog', name: '发展历程管理', icon: Timer, path: '/admin/changelog', access: 'admin' },
  { id: 'moderator-applications', name: '版主申请管理', icon: Medal, path: '/admin/moderator-applications', access: 'admin' },
  { id: 'invite-codes', name: '邀请码管理', icon: Ticket, path: '/admin/invite-codes', access: 'admin' },
  { id: 'sso', name: 'SSO 应用管理', icon: Connection, path: '/admin/sso', access: 'admin' }
]

const isAdmin = computed(() => hasAdminRole(userStore.userInfo))
const hasBackoffice = computed(() => isAdmin.value || hasModeratorCapability(userStore.userInfo))

const isModerator = computed(() => !isAdmin.value && hasBackoffice.value)

const menuItems = computed(() => {
  return allMenuItems.filter(item => {
    if (item.access === 'admin') return isAdmin.value
    if (item.access === 'moderator-only') return isModerator.value
    return hasBackoffice.value
  })
})

const handleLogout = () => {
  userStore.logout()
  router.push('/auth/login')
}

onMounted(() => {
  if (!userStore.accessToken) {
    router.push('/auth/login')
    return
  }
})
</script>

<template>
  <el-container class="admin-layout">
    <el-header class="admin-header">
      <div class="header-left">
        <img src="/logo.png" alt="Zens" style="width:40px;height:40px;border-radius:8px;object-fit:contain;" />
        <div class="header-text">
          <span class="title">智能分析决策后台</span>
          <span class="subtitle">Community Insight Console</span>
        </div>
      </div>
      
      <div class="header-right">
        <el-button link @click="router.push('/')" :icon="Back">返回前台</el-button>
        <el-button type="danger" plain size="small" :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container class="admin-body">
      <el-aside width="240px" class="admin-aside">
        <el-menu
          :default-active="route.path"
          router
          class="admin-menu"
        >
          <el-menu-item 
            v-for="item in menuItems" 
            :key="item.id" 
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.name }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="admin-main">
        <div class="admin-content-card">
          <slot />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
  background-color: var(--el-bg-color-page);
}

.admin-header {
  height: 64px;
  background-color: var(--cp-bg-surface);
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-dark-2));
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  font-size: 18px;
}

.header-text {
  display: flex;
  flex-direction: column;
}

.header-text .title {
  font-size: 16px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.header-text .subtitle {
  font-size: 10px;
  color: var(--el-text-color-placeholder);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-body {
  overflow: hidden;
}

.admin-aside {
  background-color: var(--cp-bg-surface);
  border-right: 1px solid var(--el-border-color-lighter);
}

.admin-menu {
  border-right: none;
  padding: 16px 12px;
}

.admin-menu :deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  border-radius: 8px;
  margin-bottom: 4px;
  font-weight: 600;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9);
}

.admin-main {
  padding: 24px;
  background-color: var(--el-bg-color-page);
}

.admin-content-card {
  height: 100%;
}
</style>
