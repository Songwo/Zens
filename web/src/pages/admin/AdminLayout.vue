<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { 
  PieChart, 
  Document, 
  User, 
  Flag, 
  SwitchButton,
  Back,
  Menu as IconMenu,
  Timer,
  Medal,
  SetUp
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const menuItems = [
  { id: 'dashboard', name: '数据看板', icon: PieChart, path: '/admin/dashboard' },
  { id: 'posts', name: '内容管理', icon: Document, path: '/admin/posts' },
  { id: 'sections', name: '板块管理', icon: IconMenu, path: '/admin/sections' },
  { id: 'users', name: '用户管理', icon: User, path: '/admin/users' },
  { id: 'reports', name: '举报管理', icon: Flag, path: '/admin/reports' },
  { id: 'cache', name: '缓存管理', icon: SetUp, path: '/admin/cache' },
  { id: 'changelog', name: '发展历程管理', icon: Timer, path: '/admin/changelog' },
  { id: 'moderator-applications', name: '版主申请管理', icon: Medal, path: '/admin/moderator-applications' }
]

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
          <span class="title">管理后台</span>
          <span class="subtitle">Zens Admin</span>
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
