<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, ChatDotRound, Check, EditPen, Search } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
import AppLogo from '@/components/common/AppLogo.vue'
import UserMenu from '@/components/common/UserMenu.vue'
import { usePostComposerStore } from '@/store/postComposer'
import { useUserStore } from '@/store/user'
import { notificationApi, type Notification } from '@/api/notification'
import { dmApi } from '@/api/dm'
import { cachedRequest } from '@/utils/requestCache'
import { timeAgo } from '@/utils/timeAgo'
import { wsClient, type NotificationEvent } from '@/utils/websocket'

const router = useRouter()
const route = useRoute()

const searchQuery = ref('')
const showMobileSearch = ref(false)

const composerStore = usePostComposerStore()
const userStore = useUserStore()

const unreadCount = ref(0)
const dmUnreadCount = ref(0)
const notifications = ref<Notification[]>([])
const notifLoading = ref(false)
let pollTimer: any = null
let unsubscribeWs: (() => void) | null = null

const currentUserId = computed(() => String(userStore.userInfo?.id || userStore.userId || ''))

const currentPageLabel = computed(() => {
  const path = route.path
  if (path === '/hot') return '热门排行'
  if (path === '/featured') return '精华汇总'
  if (path.startsWith('/s/')) return '板块详情'
  if (path.startsWith('/t/')) return '帖子详情'
  if (path === '/me') return '个人中心'
  return '校园脉搏'
})

const goCompose = () => {
  composerStore.open()
}

const handleSearch = () => {
  const q = searchQuery.value.trim()
  if (q) {
    router.push({ path: '/search', query: { q } })
    showMobileSearch.value = false
  }
}

const toggleMobileSearch = () => {
  showMobileSearch.value = !showMobileSearch.value
}

const fetchUnreadCount = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const res = await cachedRequest(
      `notif:unread:${currentUserId.value}`,
      15000,
      () => notificationApi.getUnreadCount(),
      { persist: false }
    )
    unreadCount.value = (res.data ?? res ?? 0) as number
  } catch {
    // Song：说明
  }
}

const fetchDmUnreadCount = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const res = await cachedRequest(
      `dm:unread:${currentUserId.value}`,
      15000,
      () => dmApi.getUnreadCount(),
      { persist: false }
    )
    dmUnreadCount.value = (res.data ?? res ?? 0) as number
  } catch {
    // Song：说明
  }
}

const fetchNotifications = async () => {
  if (!userStore.isLoggedIn) return
  notifLoading.value = true
  try {
    const res = await notificationApi.getList(1, 20)
    const data = res.data || res
    if (Array.isArray(data)) {
      notifications.value = data
    } else if (data?.records) {
      notifications.value = data.records
    }
  } catch {
    // Song：说明
  } finally {
    notifLoading.value = false
  }
}

const handlePopoverShow = async () => {
  await fetchNotifications()
}

const handleNotifClick = async (notif: Notification) => {
  if (notif.isRead === 0) {
    try {
      await notificationApi.markAsRead(notif.id)
      notif.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // Song：说明
    }
  }

  if (notif.relatedId) {
    router.push(`/p/${notif.relatedId}`)
  }
}

const handleMarkAllRead = async () => {
  try {
    await notificationApi.markAllAsRead()
    notifications.value.forEach((item) => {
      item.isRead = 1
    })
    unreadCount.value = 0
  } catch {
    // Song：说明
  }
}

const getNotifTime = (item: Notification) => {
  return timeAgo((item as any).createTime || (item as any).createdAt || '')
}

const handleWebSocketNotification = (event: NotificationEvent) => {
  unreadCount.value++

  if (notifications.value.length > 0) {
    notifications.value.unshift({
      id: String(event.id),
      userId: event.userId,
      type: event.type,
      title: event.title,
      content: event.content,
      relatedId: event.relatedId,
      isRead: 0,
      createdAt: event.createdAt,
    })
  }

  ElNotification({
    title: event.title,
    message: event.content,
    type: 'info',
    duration: 4000,
    onClick: () => {
      if (event.relatedId) {
        router.push(`/p/${event.relatedId}`)
      }
    },
  })
}

const subscribeToNotifications = () => {
  if (!userStore.isLoggedIn || !currentUserId.value) {
    return
  }

  if (unsubscribeWs) {
    unsubscribeWs()
  }

  unsubscribeWs = wsClient.subscribeNotifications(currentUserId.value, handleWebSocketNotification)
}

watch(
  () => userStore.isLoggedIn,
  (isLoggedIn) => {
    if (isLoggedIn) {
      fetchUnreadCount()
      fetchDmUnreadCount()
      subscribeToNotifications()
    } else {
      if (unsubscribeWs) {
        unsubscribeWs()
        unsubscribeWs = null
      }
      unreadCount.value = 0
      dmUnreadCount.value = 0
      notifications.value = []
    }
  },
  { immediate: true }
)

watch(
  () => route.fullPath,
  () => {
    showMobileSearch.value = false
  }
)

onMounted(() => {
  if (userStore.isLoggedIn) {
    fetchUnreadCount()
    fetchDmUnreadCount()
    subscribeToNotifications()
  }
  pollTimer = setInterval(() => {
    if (userStore.isLoggedIn) {
      fetchUnreadCount()
      fetchDmUnreadCount()
    }
  }, 60000)
})

onUnmounted(() => {
  clearInterval(pollTimer)
  if (unsubscribeWs) {
    unsubscribeWs()
  }
})
</script>

<template>
  <header class="app-topbar">
    <div class="topbar-container">
      <div class="topbar-left">
        <AppLogo />
      </div>

      <div class="topbar-center">
        <el-input
          v-model="searchQuery"
          placeholder="搜索帖子、标签、用户"
          :prefix-icon="Search"
          class="nav-search-input desktop-search"
          @keyup.enter="handleSearch"
          clearable
        />
        <span class="mobile-title">{{ currentPageLabel }}</span>
      </div>

      <div class="topbar-right">
        <el-button circle text class="icon-btn mobile-only" @click="toggleMobileSearch">
          <el-icon><Search /></el-icon>
        </el-button>

        <el-button type="primary" @click="goCompose" class="compose-btn">
          <el-icon><EditPen /></el-icon>
          <span class="compose-label">发帖</span>
        </el-button>

        <el-badge
          v-if="userStore.isLoggedIn"
          :value="dmUnreadCount > 0 ? dmUnreadCount : ''"
          :max="99"
          class="notification-badge"
        >
          <el-button circle text class="icon-btn" @click="router.push('/messages')">
            <el-icon><ChatDotRound /></el-icon>
          </el-button>
        </el-badge>

        <el-popover placement="bottom-end" :width="360" trigger="click" popper-class="notif-popover" @show="handlePopoverShow">
          <template #reference>
            <el-badge :value="unreadCount > 0 ? unreadCount : ''" :max="99" class="notification-badge">
              <el-button circle text class="icon-btn">
                <el-icon><Bell /></el-icon>
              </el-button>
            </el-badge>
          </template>

          <div class="notif-panel">
            <div class="notif-header">
              <span class="notif-title">消息通知</span>
              <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="handleMarkAllRead">
                <el-icon><Check /></el-icon>
                全部已读
              </el-button>
            </div>

            <div v-loading="notifLoading" class="notif-list">
              <div
                v-for="notif in notifications"
                :key="notif.id"
                class="notif-item"
                :class="{ unread: notif.isRead === 0 }"
                @click="handleNotifClick(notif)"
              >
                <div class="notif-dot" v-if="notif.isRead === 0"></div>
                <div class="notif-body">
                  <div class="notif-item-title">{{ notif.title }}</div>
                  <div class="notif-content">{{ notif.content }}</div>
                  <div class="notif-time">{{ getNotifTime(notif) }}</div>
                </div>
              </div>

              <div v-if="!notifLoading && notifications.length === 0" class="notif-empty">
                <el-empty description="暂无通知" :image-size="60" />
              </div>
            </div>

            <div class="notif-footer">
              <el-button link type="primary" size="small" @click="router.push('/me')">查看全部通知</el-button>
            </div>
          </div>
        </el-popover>

        <UserMenu />
      </div>
    </div>

    <transition name="slide-fade">
      <div v-if="showMobileSearch" class="mobile-search-wrap">
        <el-input
          v-model="searchQuery"
          placeholder="搜索帖子、标签、用户"
          :prefix-icon="Search"
          class="nav-search-input"
          @keyup.enter="handleSearch"
          clearable
        />
      </div>
    </transition>
  </header>
</template>

<style scoped>
.app-topbar {
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  position: sticky;
  top: 0;
  z-index: 110;
  box-shadow: var(--el-box-shadow-lighter);
}

.topbar-container {
  max-width: var(--max-content-width);
  margin: 0 auto;
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  gap: 14px;
}

.topbar-left {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.topbar-center {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.desktop-search {
  max-width: 520px;
  width: 100%;
}

.mobile-title {
  display: none;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.nav-search-input :deep(.el-input__wrapper) {
  border-radius: 999px;
  background-color: var(--el-fill-color-light);
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset;
  transition: box-shadow 0.2s ease, background-color 0.2s ease;
  padding: 0 16px;
}

.nav-search-input :deep(.el-input__wrapper.is-focus) {
  background-color: var(--el-bg-color);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.topbar-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.icon-btn {
  color: var(--el-text-color-regular);
  font-size: 18px;
  border: 1px solid var(--el-border-color-light);
  background-color: transparent;
  transition: color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.icon-btn:hover {
  color: var(--el-text-color-primary);
  background-color: var(--el-fill-color);
  transform: translateY(-1px);
}

.compose-btn {
  font-weight: 700;
  border-radius: 999px;
  padding: 8px 15px;
}

.mobile-only {
  display: none;
}

.notification-badge :deep(.el-badge__content) {
  border: none;
}

.notif-panel {
  margin: -12px;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.notif-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.notif-list {
  max-height: 380px;
  overflow-y: auto;
  min-height: 100px;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.15s ease;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.notif-item:hover {
  background-color: var(--el-fill-color-lighter);
}

.notif-item.unread {
  background-color: var(--el-color-primary-light-9);
}

.notif-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
  flex-shrink: 0;
  margin-top: 6px;
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.notif-content {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.notif-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.notif-empty {
  padding: 20px;
}

.notif-footer {
  padding: 8px 16px;
  text-align: center;
  border-top: 1px solid var(--el-border-color-lighter);
}

.mobile-search-wrap {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 10px 14px 12px;
  background: var(--el-bg-color);
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.2s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 980px) {
  .topbar-left {
    width: auto;
  }

  .desktop-search {
    display: none;
  }

  .mobile-title {
    display: inline-flex;
  }

  .mobile-only {
    display: inline-flex;
  }

  .compose-label {
    display: none;
  }

  .compose-btn {
    padding: 8px 10px;
  }
}

@media (max-width: 768px) {
  .topbar-container {
    padding: 0 10px;
    gap: 8px;
  }
}
</style>
