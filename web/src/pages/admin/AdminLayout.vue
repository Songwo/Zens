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
  Promotion,
  Ticket,
  Reading,
  Cpu,
  Connection,
  Odometer,
  ArrowRight,
  Operation,
  Notebook,
  MagicStick,
  EditPen
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { hasAdminRole, hasModeratorCapability } from '@/utils/sessionProfile'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

type AccessType = 'admin' | 'backoffice' | 'moderator-only'

interface AdminMenuItem {
  id: string
  name: string
  desc: string
  icon: any
  path: string
  access: AccessType
  group: 'overview' | 'content' | 'people' | 'system'
}

const allMenuItems: AdminMenuItem[] = [
  { id: 'dashboard', name: '数据看板', desc: '整体运营态势', icon: PieChart, path: '/admin/dashboard', access: 'admin', group: 'overview' },
  { id: 'my-sections', name: '我的板块', desc: '版主工作入口', icon: Medal, path: '/admin/my-sections', access: 'moderator-only', group: 'overview' },
  { id: 'posts', name: '内容管理', desc: '审核、置顶、精华', icon: Document, path: '/admin/posts', access: 'backoffice', group: 'content' },
  { id: 'reports', name: '举报管理', desc: '优先处理风险内容', icon: Flag, path: '/admin/reports', access: 'backoffice', group: 'content' },
  { id: 'posts-trash', name: '内容回收站', desc: '恢复误删内容', icon: Delete, path: '/admin/posts/trash', access: 'backoffice', group: 'content' },
  { id: 'sections', name: '板块管理', desc: '分类与排序', icon: IconMenu, path: '/admin/sections', access: 'admin', group: 'content' },
  { id: 'users', name: '用户管理', desc: '账号、角色、封禁', icon: User, path: '/admin/users', access: 'admin', group: 'people' },
  { id: 'trust', name: '信任等级', desc: '成长规则与权益', icon: Promotion, path: '/admin/trust', access: 'admin', group: 'people' },
  { id: 'moderator-applications', name: '版主申请', desc: '招募与审批', icon: Medal, path: '/admin/moderator-applications', access: 'admin', group: 'people' },
  { id: 'invite-codes', name: '邀请码', desc: '发放与追踪', icon: Ticket, path: '/admin/invite-codes', access: 'admin', group: 'people' },
  { id: 'changelog', name: '发展历程', desc: '公告和路线图', icon: Timer, path: '/admin/changelog', access: 'admin', group: 'people' },
  { id: 'cache', name: '缓存管理', desc: 'Redis 快捷维护', icon: SetUp, path: '/admin/cache', access: 'admin', group: 'system' },
  { id: 'performance', name: '性能观测', desc: '慢请求与慢 SQL', icon: Odometer, path: '/admin/performance', access: 'admin', group: 'system' },
  { id: 'logs', name: '日志管理', desc: '排障与归档', icon: Reading, path: '/admin/logs', access: 'admin', group: 'system' },
  { id: 'agent', name: 'Agent 状态', desc: '问答链路与可用性', icon: MagicStick, path: '/admin/agent', access: 'admin', group: 'system' },
  { id: 'ops-automation', name: '自动运营', desc: '草稿审批与写入熔断', icon: EditPen, path: '/admin/ops-automation', access: 'admin', group: 'system' },
  { id: 'media', name: '媒体存储', desc: '上传链路状态', icon: Cpu, path: '/admin/media', access: 'admin', group: 'system' },
  { id: 'sso', name: 'SSO 应用', desc: '外部登录配置', icon: Connection, path: '/admin/sso', access: 'admin', group: 'system' },
  { id: 'subsite-events', name: '事件账本', desc: '子站闭环审计', icon: Notebook, path: '/admin/subsite-events', access: 'admin', group: 'system' },
  { id: 'metaverse', name: '星港配置', desc: '子项目入口与权限', icon: Operation, path: '/admin/metaverse', access: 'admin', group: 'system' }
]

const menuGroups = [
  { id: 'overview', name: '总览', hint: '先看态势' },
  { id: 'content', name: '内容治理', hint: '帖子、举报、板块' },
  { id: 'people', name: '社区运营', hint: '用户、信任、邀请码' },
  { id: 'system', name: '系统运维', hint: '缓存、日志、性能' }
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

const groupedMenuItems = computed(() => {
  return menuGroups
    .map(group => ({
      ...group,
      items: menuItems.value.filter(item => item.group === group.id)
    }))
    .filter(group => group.items.length > 0)
})

const quickActions = computed(() => {
  const paths = isAdmin.value
    ? ['/admin/posts', '/admin/reports', '/admin/users', '/admin/performance']
    : ['/admin/my-sections', '/admin/posts', '/admin/reports']
  return paths
    .map(path => menuItems.value.find(item => item.path === path))
    .filter(Boolean) as AdminMenuItem[]
})

const activeMenuItem = computed(() => {
  const path = route.path
  return menuItems.value
    .slice()
    .sort((a, b) => b.path.length - a.path.length)
    .find(item => path === item.path || path.startsWith(`${item.path}/`))
})

const activeGroupName = computed(() => {
  const group = menuGroups.find(item => item.id === activeMenuItem.value?.group)
  return group?.name || '后台管理'
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
      
      <div class="header-center">
        <el-button
          v-for="item in quickActions"
          :key="item.id"
          text
          class="quick-action"
          :class="{ active: route.path === item.path }"
          @click="router.push(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.name }}</span>
        </el-button>
      </div>

      <div class="header-right">
        <el-button link @click="router.push('/')" :icon="Back">返回前台</el-button>
        <el-button type="danger" plain size="small" :icon="SwitchButton" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container class="admin-body">
      <el-aside width="240px" class="admin-aside">
        <div class="aside-summary">
          <div class="summary-icon">
            <el-icon><Operation /></el-icon>
          </div>
          <div>
            <div class="summary-title">{{ activeMenuItem?.name || '管理工作台' }}</div>
            <div class="summary-desc">{{ activeMenuItem?.desc || activeGroupName }}</div>
          </div>
        </div>

        <el-scrollbar class="aside-scroll">
          <div class="menu-groups">
            <section v-for="group in groupedMenuItems" :key="group.id" class="menu-group">
              <div class="group-label">
                <span>{{ group.name }}</span>
                <small>{{ group.hint }}</small>
              </div>
              <el-menu
                :default-active="route.path"
                router
                class="admin-menu"
              >
                <el-menu-item
                  v-for="item in group.items"
                  :key="item.id"
                  :index="item.path"
                >
                  <el-icon><component :is="item.icon" /></el-icon>
                  <div class="menu-text">
                    <span>{{ item.name }}</span>
                    <small>{{ item.desc }}</small>
                  </div>
                  <el-icon class="menu-arrow"><ArrowRight /></el-icon>
                </el-menu-item>
              </el-menu>
            </section>
          </div>
        </el-scrollbar>
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
  min-height: 100dvh;
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
  gap: 18px;
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

.header-center {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  overflow: hidden;
}

.quick-action {
  height: 36px;
  border-radius: 8px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}

.quick-action :deep(.el-icon) {
  margin-right: 5px;
}

.quick-action.active,
.quick-action:hover {
  background: var(--cp-hover);
  color: var(--el-color-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.admin-body {
  overflow: hidden;
}

.admin-aside {
  background-color: var(--cp-bg-surface);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.aside-summary {
  margin: 16px 12px 10px;
  padding: 14px;
  border: 1px solid var(--cp-border);
  border-radius: 8px;
  background: var(--cp-bg-card);
  display: flex;
  align-items: center;
  gap: 10px;
}

.summary-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  flex-shrink: 0;
}

.summary-title {
  font-size: 14px;
  line-height: 1.25;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.summary-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.35;
  color: var(--el-text-color-secondary);
}

.aside-scroll {
  flex: 1;
}

.menu-groups {
  padding: 16px 12px;
}

.menu-group + .menu-group {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--cp-divider);
}

.group-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 22px;
  padding: 0 8px 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.group-label small {
  min-width: 0;
  font-size: 11px;
  font-weight: 500;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.admin-menu {
  border-right: none;
  background: transparent;
}

.admin-menu :deep(.el-menu-item) {
  height: 58px;
  line-height: 1.2;
  border-radius: 8px;
  margin-bottom: 4px;
  font-weight: 600;
  padding: 0 10px !important;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 9px;
}

.admin-menu :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9);
}

.menu-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.menu-text span,
.menu-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-text span {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.menu-text small {
  font-size: 11px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.menu-arrow {
  opacity: 0;
  color: var(--el-text-color-placeholder);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.admin-menu :deep(.el-menu-item:hover) .menu-arrow,
.admin-menu :deep(.el-menu-item.is-active) .menu-arrow {
  opacity: 1;
  transform: translateX(2px);
}

.admin-main {
  padding: 24px;
  background-color: var(--el-bg-color-page);
  overflow: auto;
}

.admin-content-card {
  height: 100%;
}

@media (max-width: 1180px) {
  .header-center {
    justify-content: flex-start;
  }

  .quick-action span {
    display: none;
  }
}

@media (max-width: 820px) {
  .admin-header {
    padding: 0 12px;
  }

  .header-text .subtitle,
  .header-center {
    display: none;
  }

  .admin-aside {
    width: 76px !important;
  }

  .aside-summary,
  .group-label,
  .menu-text,
  .menu-arrow {
    display: none;
  }

  .menu-groups {
    padding: 12px 8px;
  }

  .menu-group + .menu-group {
    margin-top: 8px;
    padding-top: 8px;
  }

  .admin-menu :deep(.el-menu-item) {
    grid-template-columns: 1fr;
    justify-items: center;
    height: 44px;
    padding: 0 !important;
  }

  .admin-main {
    padding: 14px;
  }
}
</style>
