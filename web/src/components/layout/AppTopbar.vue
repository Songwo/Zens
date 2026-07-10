<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, ChatDotRound, Check, Delete, EditPen, Search } from '@element-plus/icons-vue'
import { ElNotification } from 'element-plus'
import AppLogo from '@/components/common/AppLogo.vue'
import UserMenu from '@/components/common/UserMenu.vue'
import AppLauncher from '@/components/layout/AppLauncher.vue'
import { usePostComposerStore } from '@/store/postComposer'
import { useUserStore } from '@/store/user'
import { notificationApi, type Notification } from '@/api/notification'
import { dmApi } from '@/api/dm'
import { tagApi } from '@/api/tag'
import { postApi } from '@/api/post'
import { userApi } from '@/api/user'
import { cachedRequest, clearRequestCache } from '@/utils/requestCache'
import { timeAgo } from '@/utils/timeAgo'
import { wsClient, type NotificationEvent } from '@/utils/websocket'
import { emitNotificationUnreadSync, onNotificationUnreadSync } from '@/utils/notificationSync'
import { resolveNotificationRoute } from '@/utils/notificationRoute'
import { DEFAULT_DISCOVERY_SEARCH_TERMS } from '@/utils/communityDiscovery'
import { encodePostId, encodeUserId } from '@/utils/shortId'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'

const router = useRouter()
const route = useRoute()

const searchQuery = ref('')
const showMobileSearch = ref(false)
const searchFocused = ref(false)
const commandTagSuggestions = ref<string[]>([])
const commandPostResults = ref<Array<{ id: string; title: string; sectionName?: string; commentCount?: number }>>([])
const commandUserResults = ref<Array<{ id: string; nickname?: string; username?: string; avatar?: string; postCount?: number }>>([])
const commandSuggestLoading = ref(false)

const composerStore = usePostComposerStore()
const userStore = useUserStore()

const unreadCount = ref(0)
const dmUnreadCount = ref(0)
const notifications = ref<Notification[]>([])
const notifLoading = ref(false)
const notifPopoverVisible = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null
let commandSuggestTimer: ReturnType<typeof setTimeout> | null = null
let commandCloseTimer: ReturnType<typeof setTimeout> | null = null
let commandAbortController: AbortController | null = null
let commandRequestId = 0
let unsubscribeWs: (() => void) | null = null
let unsubscribeUnreadSync: (() => void) | null = null

const currentUserId = computed(() => String(userStore.userInfo?.id || userStore.userId || ''))
const normalizedCommandQuery = computed(() => searchQuery.value.trim())
const publicAssetUrl = (value?: string | null) => resolvePublicAssetUrl(value)

const currentPageLabel = computed(() => {
  const path = route.path
  if (path === '/hot') return '热榜'
  if (path === '/featured') return '精华'
  if (path === '/agent') return 'Agent'
  if (path === '/benefits') return '福利'
  if (path === '/metaverse') return '星港'
  if (path.startsWith('/s/')) return '板块'
  if (path.startsWith('/t/')) return '帖子'
  if (path === '/me') return '我的'
  return '首页'
})

const mobileSearchShortcuts = [
  { label: '热门排行', path: '/hot' },
  { label: '精华文档', path: '/featured' },
  { label: '福利中心', path: '/benefits' },
  { label: '星港', path: '/metaverse' },
]

const mobileSearchTags = DEFAULT_DISCOVERY_SEARCH_TERMS.slice(0, 5)

const commandQuickActions = computed(() => {
  const q = normalizedCommandQuery.value
  const actions = [
    { label: '看热榜', desc: '进入社区热门讨论', path: '/hot' },
    { label: '精华汇总', desc: '浏览已沉淀的高价值内容', path: '/featured' },
    { label: '福利中心', desc: '查看可领取的社区福利', path: '/benefits' },
    { label: '星港', desc: '打开活动、积分与治理入口', path: '/metaverse' },
  ]

  if (!q) return actions
  return [
    { label: `搜索「${q}」`, desc: '帖子、标签、用户与 Agent 推荐', path: `/search?q=${encodeURIComponent(q)}` },
    { label: '让 Agent 补充推荐', desc: '从帖子和评论里继续找相关线索', path: `/search?q=${encodeURIComponent(q)}&agent=1` },
    ...actions.slice(0, 3),
  ]
})

const commandSearchTerms = computed(() => {
  const source = commandTagSuggestions.value.length
    ? commandTagSuggestions.value
    : DEFAULT_DISCOVERY_SEARCH_TERMS
  return source.slice(0, 6)
})

const commandHasDirectResults = computed(() => (
  commandPostResults.value.length > 0 ||
  commandUserResults.value.length > 0 ||
  commandTagSuggestions.value.length > 0
))

const commandShowNoResults = computed(() => (
  normalizedCommandQuery.value.length >= 2 &&
  !commandSuggestLoading.value &&
  !commandHasDirectResults.value
))

const goCompose = () => {
  composerStore.open()
}

const handleSearch = () => {
  const q = searchQuery.value.trim()
  if (q) {
    router.push({ path: '/search', query: { q } })
    showMobileSearch.value = false
    searchFocused.value = false
  }
}

const toggleMobileSearch = () => {
  showMobileSearch.value = !showMobileSearch.value
}

const closeMobileSearch = () => {
  showMobileSearch.value = false
}

const openCommandPanel = () => {
  if (commandCloseTimer) {
    clearTimeout(commandCloseTimer)
    commandCloseTimer = null
  }
  searchFocused.value = true
}

const closeCommandPanelSoon = () => {
  if (commandCloseTimer) {
    clearTimeout(commandCloseTimer)
  }
  commandCloseTimer = setTimeout(() => {
    searchFocused.value = false
  }, 140)
}

const goMobileSearchShortcut = (path: string) => {
  showMobileSearch.value = false
  router.push(path)
}

const goCommandAction = (path: string) => {
  searchFocused.value = false
  showMobileSearch.value = false
  router.push(path)
}

const goCommandPost = (postId: string) => {
  searchFocused.value = false
  showMobileSearch.value = false
  router.push(`/t/${encodePostId(postId)}`)
}

const goCommandUser = (userId: string) => {
  searchFocused.value = false
  showMobileSearch.value = false
  router.push(`/user/${encodeUserId(userId)}`)
}

const searchByTag = (tag: string) => {
  searchQuery.value = tag
  handleSearch()
}

const fetchCommandTagSuggestions = async (keyword: string) => {
  const q = keyword.trim()
  const requestId = ++commandRequestId
  commandAbortController?.abort()
  if (q.length < 2) {
    commandTagSuggestions.value = []
    commandPostResults.value = []
    commandUserResults.value = []
    commandSuggestLoading.value = false
    return
  }

  const controller = new AbortController()
  commandAbortController = controller
  commandSuggestLoading.value = true
  try {
    const [tagRes, postRes, userRes] = await Promise.allSettled([
      tagApi.search(q, { signal: controller.signal }),
      postApi.searchList({
        keyword: q,
        page: 1,
        pageSize: 3,
        needTotal: false,
        status: 1,
        orderBy: 'relevance',
      }, { signal: controller.signal }),
      userApi.searchUsers(q),
    ])
    if (commandAbortController !== controller || requestId !== commandRequestId) return
    const tagList = tagRes.status === 'fulfilled' && Array.isArray(tagRes.value.data) ? tagRes.value.data : []
    commandTagSuggestions.value = Array.from(
      new Set(
        tagList
          .map((item: any) => String(item?.name || '').trim())
          .filter(Boolean)
      )
    ).slice(0, 6)
    const postList = postRes.status === 'fulfilled' && Array.isArray(postRes.value.data?.records)
      ? postRes.value.data.records
      : []
    commandPostResults.value = postList
      .map((item: any) => ({
        id: String(item?.id || ''),
        title: String(item?.title || '').trim(),
        sectionName: item?.sectionName,
        commentCount: Number(item?.commentCount || 0),
      }))
      .filter((item) => item.id && item.title)
      .slice(0, 3)
    const userList = userRes.status === 'fulfilled' && Array.isArray(userRes.value.data)
      ? userRes.value.data
      : []
    commandUserResults.value = userList
      .map((item: any) => ({
        id: String(item?.id || ''),
        nickname: item?.nickname,
        username: item?.username,
        avatar: item?.avatar,
        postCount: Number(item?.postCount || 0),
      }))
      .filter((item) => item.id)
      .slice(0, 3)
  } catch (error: any) {
    if (error?.code !== 'ERR_CANCELED') {
      commandTagSuggestions.value = []
      commandPostResults.value = []
      commandUserResults.value = []
    }
  } finally {
    if (commandAbortController === controller) {
      commandAbortController = null
      commandSuggestLoading.value = false
    }
  }
}

const scheduleCommandSuggestions = () => {
  if (commandSuggestTimer) {
    clearTimeout(commandSuggestTimer)
    commandSuggestTimer = null
  }
  const q = normalizedCommandQuery.value
  if (q.length < 2) {
    commandAbortController?.abort()
    commandTagSuggestions.value = []
    commandPostResults.value = []
    commandUserResults.value = []
    commandSuggestLoading.value = false
    return
  }
  commandSuggestTimer = setTimeout(() => {
    fetchCommandTagSuggestions(q)
  }, 180)
}

const updateUnreadCount = (value: number) => {
  unreadCount.value = Math.max(0, Number(value) || 0)
}

const updateUnreadCountByDelta = (delta: number) => {
  updateUnreadCount(unreadCount.value + delta)
}

const invalidateNotificationUnreadCache = () => {
  if (!currentUserId.value) return
  clearRequestCache(`notif:unread:${currentUserId.value}`)
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
    updateUnreadCount((res.data ?? res ?? 0) as number)
    emitNotificationUnreadSync({ unreadCount: unreadCount.value })
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

const refreshNotificationBadges = () => {
  if (!userStore.isLoggedIn) return
  void fetchUnreadCount()
  void fetchDmUnreadCount()
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPolling = () => {
  stopPolling()

  if (typeof document !== 'undefined' && document.hidden) {
    return
  }

  pollTimer = setInterval(() => {
    if (!userStore.isLoggedIn || document.hidden) {
      return
    }
    refreshNotificationBadges()
  }, 60000)
}

const handlePopoverShow = async () => {
  invalidateNotificationUnreadCache()
  await fetchUnreadCount()
  await fetchNotifications()
}

const handleNotifClick = async (notif: Notification) => {
  if (notif.isRead === 0) {
    try {
      await notificationApi.markAsRead(String(notif.id))
      notif.isRead = 1
      updateUnreadCountByDelta(-1)
      invalidateNotificationUnreadCache()
      emitNotificationUnreadSync({ unreadCount: unreadCount.value })
    } catch {
      // Song：说明
    }
  }

  const targetRoute = resolveNotificationRoute(notif.relatedId, { type: notif.type, relatedUserId: notif.relatedUserId })
  if (targetRoute) {
    notifPopoverVisible.value = false
    await router.push(targetRoute)
  }
}

const handleMarkAllRead = async () => {
  try {
    await notificationApi.markAllAsRead()
    notifications.value.forEach((item) => {
      item.isRead = 1
    })
    updateUnreadCount(0)
    invalidateNotificationUnreadCache()
    emitNotificationUnreadSync({ unreadCount: 0 })
  } catch {
    // Song：说明
  }
}

const handleNotifDelete = async (event: MouseEvent, notif: Notification) => {
  event.stopPropagation()
  try {
    await notificationApi.delete(String(notif.id))
    notifications.value = notifications.value.filter((item) => String(item.id) !== String(notif.id))
    if (notif.isRead === 0) {
      updateUnreadCountByDelta(-1)
      invalidateNotificationUnreadCache()
      emitNotificationUnreadSync({ unreadCount: unreadCount.value })
    }
  } catch {
    // Song：说明
  }
}

const goNotificationCenter = async () => {
  notifPopoverVisible.value = false
  await router.push('/notifications')
}

const getNotifTime = (item: Notification) => {
  return timeAgo((item as any).createTime || (item as any).createdAt || '')
}

const handleWebSocketNotification = (event: NotificationEvent) => {
  updateUnreadCountByDelta(1)
  invalidateNotificationUnreadCache()
  emitNotificationUnreadSync({ unreadCount: unreadCount.value })

  notifications.value.unshift({
    id: String(event.id),
    userId: event.userId,
    type: event.type,
    title: event.title,
    content: event.content,
    relatedId: event.relatedId,
    relatedUserId: event.relatedUserId,
    isRead: 0,
    createdAt: event.createdAt,
  })
  notifications.value = notifications.value.slice(0, 20)

  // 1. 根据不同类型定制图标与发光色彩样式
  const getNotifDetails = (type: string) => {
    switch (type) {
      case 'like':
        return { icon: '❤️', class: 'notif-like' }
      case 'reply':
        return { icon: '💬', class: 'notif-reply' }
      case 'favorite':
        return { icon: '⭐', class: 'notif-favorite' }
      case 'follow':
        return { icon: '👤', class: 'notif-follow' }
      case 'mention':
        return { icon: '🏷️', class: 'notif-mention' }
      case 'security_alert':
      case 'new_device_login':
      case 'login_failed_burst':
        return { icon: '🛡️', class: 'notif-security' }
      default:
        return { icon: '📢', class: 'notif-system' }
    }
  }

  const details = getNotifDetails(event.type)
  const isSecurity = ['security_alert', 'new_device_login', 'login_failed_burst'].includes(event.type)
    || Boolean(event.title?.includes('登录') || event.title?.includes('安全'))

  // 2. 触发极致奢华的定制发光弹窗
  ElNotification({
    customClass: `zens-premium-notif ${details.class}`,
    duration: isSecurity ? 8000 : 4500,
    position: 'bottom-right',
    message: h('div', { style: 'display: flex; align-items: center; gap: 12px; cursor: pointer;' }, [
      h('div', {
        style: 'font-size: 22px; padding: 8px; background: rgba(255, 255, 255, 0.08); border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 10px rgba(0,0,0,0.08); flex-shrink: 0;'
      }, details.icon),
      h('div', { style: 'flex: 1; min-width: 0;' }, [
        h('div', { style: 'font-size: 13px; font-weight: 700; color: var(--el-text-color-primary); margin-bottom: 2px;' }, event.title),
        h('div', { style: 'font-size: 11px; color: var(--el-text-color-regular); line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;' }, event.content)
      ])
    ]),
    onClick: () => {
      const targetRoute = resolveNotificationRoute(event.relatedId, { type: event.type, relatedUserId: event.relatedUserId })
      if (targetRoute) {
        router.push(targetRoute)
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

const handleVisibilityChange = () => {
  if (document.hidden) {
    stopPolling()
    return
  }

  refreshNotificationBadges()
  subscribeToNotifications()
  startPolling()
}

watch(
  () => userStore.isLoggedIn,
  (isLoggedIn) => {
    if (isLoggedIn) {
      refreshNotificationBadges()
      subscribeToNotifications()
      startPolling()
    } else {
      if (unsubscribeWs) {
        unsubscribeWs()
        unsubscribeWs = null
      }
      stopPolling()
      updateUnreadCount(0)
      dmUnreadCount.value = 0
      notifications.value = []
      emitNotificationUnreadSync({ unreadCount: 0 })
    }
  },
  { immediate: true }
)

watch(
  () => route.fullPath,
  () => {
    showMobileSearch.value = false
    searchFocused.value = false
  }
)

watch(normalizedCommandQuery, () => {
  scheduleCommandSuggestions()
})

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)

  unsubscribeUnreadSync = onNotificationUnreadSync((detail) => {
    if (typeof detail.unreadCount === 'number') {
      updateUnreadCount(detail.unreadCount)
      return
    }
    if (typeof detail.delta === 'number') {
      updateUnreadCountByDelta(detail.delta)
      return
    }
    if (detail.forceRefresh) {
      invalidateNotificationUnreadCache()
      refreshNotificationBadges()
    }
  })

  if (userStore.isLoggedIn) {
    refreshNotificationBadges()
    subscribeToNotifications()
    startPolling()
  }
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  stopPolling()
  commandAbortController?.abort()
  if (commandSuggestTimer) {
    clearTimeout(commandSuggestTimer)
    commandSuggestTimer = null
  }
  if (commandCloseTimer) {
    clearTimeout(commandCloseTimer)
    commandCloseTimer = null
  }
  if (unsubscribeWs) {
    unsubscribeWs()
  }
  if (unsubscribeUnreadSync) {
    unsubscribeUnreadSync()
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
        <div
          class="desktop-search-shell"
          @focusin="openCommandPanel"
          @focusout="closeCommandPanelSoon"
        >
          <el-input
            v-model="searchQuery"
            placeholder="搜索帖子、标签、用户"
            :prefix-icon="Search"
            class="nav-search-input desktop-search"
            @keyup.enter="handleSearch"
            clearable
          />

          <section
            class="command-search-panel"
            aria-label="命令式搜索建议"
            @mousedown.prevent
          >
            <div v-if="normalizedCommandQuery && commandPostResults.length" class="command-section">
              <span class="command-heading">帖子结果</span>
              <button
                v-for="post in commandPostResults"
                :key="post.id"
                class="command-item command-result"
                type="button"
                @click="goCommandPost(post.id)"
              >
                <span class="command-item-title">{{ post.title }}</span>
                <span class="command-item-desc">
                  {{ post.sectionName || '社区帖子' }} · {{ post.commentCount || 0 }} 回复
                </span>
              </button>
            </div>

            <div v-if="normalizedCommandQuery && commandUserResults.length" class="command-section">
              <span class="command-heading">用户</span>
              <button
                v-for="user in commandUserResults"
                :key="user.id"
                class="command-user-item"
                type="button"
                @click="goCommandUser(user.id)"
              >
                <el-avatar :size="26" :src="publicAssetUrl(user.avatar)">
                  {{ (user.nickname || user.username || 'U').charAt(0) }}
                </el-avatar>
                <span class="command-user-copy">
                  <strong>{{ user.nickname || user.username }}</strong>
                  <small>@{{ user.username || 'user' }} · {{ user.postCount || 0 }} 帖</small>
                </span>
              </button>
            </div>

            <div class="command-section">
              <span class="command-heading">快捷入口</span>
              <button
                v-for="item in commandQuickActions"
                :key="item.path"
                class="command-item"
                type="button"
                @click="goCommandAction(item.path)"
              >
                <span class="command-item-title">{{ item.label }}</span>
                <span class="command-item-desc">{{ item.desc }}</span>
              </button>
            </div>

            <div class="command-section">
              <span class="command-heading">
                {{ normalizedCommandQuery ? '相关标签' : '热门搜索' }}
              </span>
              <div v-if="commandSuggestLoading" class="command-loading">正在匹配...</div>
              <div v-else-if="commandShowNoResults" class="command-empty">
                没有直接命中，试试完整搜索或让 Agent 补充线索。
              </div>
              <div v-else class="command-tags">
                <button
                  v-for="tag in commandSearchTerms"
                  :key="tag"
                  class="command-tag"
                  type="button"
                  @click="searchByTag(tag)"
                >
                  # {{ tag }}
                </button>
              </div>
            </div>
          </section>
        </div>
        <span class="mobile-title">{{ currentPageLabel }}</span>
      </div>

      <div class="topbar-right">
        <el-button circle text class="icon-btn mobile-only" aria-label="打开搜索" title="搜索" @click="toggleMobileSearch">
          <el-icon><Search /></el-icon>
        </el-button>

        <span class="ecosystem-launcher mobile-hidden-action">
          <AppLauncher />
        </span>

        <el-button type="primary" @click="goCompose" class="compose-btn mobile-hidden-action">
          <el-icon><EditPen /></el-icon>
          <span class="compose-label">发帖</span>
        </el-button>

        <el-badge
          v-if="userStore.isLoggedIn"
          :value="dmUnreadCount > 0 ? dmUnreadCount : ''"
          :max="99"
          class="notification-badge"
        >
          <el-button circle text class="icon-btn" aria-label="打开私信" title="私信" @click="router.push('/messages')">
            <el-icon><ChatDotRound /></el-icon>
          </el-button>
        </el-badge>

        <el-popover v-model:visible="notifPopoverVisible" placement="bottom-end" :width="360" trigger="click" popper-class="notif-popover" @show="handlePopoverShow">
          <template #reference>
            <el-badge :value="unreadCount > 0 ? unreadCount : ''" :max="99" class="notification-badge">
              <el-button circle text class="icon-btn" aria-label="打开消息通知" title="消息通知">
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
                <el-button
                  class="notif-delete"
                  text
                  type="danger"
                  size="small"
                  :icon="Delete"
                  :aria-label="`删除通知：${notif.title}`"
                  title="删除通知"
                  @click="handleNotifDelete($event, notif)"
                />
              </div>

              <div v-if="!notifLoading && notifications.length === 0" class="notif-empty">
                <el-empty description="暂无通知" :image-size="60" />
              </div>
            </div>

            <div class="notif-footer">
              <el-button link type="primary" size="small" @click="goNotificationCenter">查看全部通知</el-button>
            </div>
          </div>
        </el-popover>

        <UserMenu />
      </div>
    </div>

    <transition name="slide-fade">
      <div v-if="showMobileSearch" class="mobile-search-layer">
        <button class="mobile-search-backdrop" type="button" aria-label="关闭搜索" @click="closeMobileSearch"></button>
        <section class="mobile-search-panel" aria-label="移动端搜索">
          <el-input
            v-model="searchQuery"
            placeholder="搜索帖子、标签、用户"
            :prefix-icon="Search"
            class="nav-search-input mobile-search-input"
            autofocus
            @keyup.enter="handleSearch"
            clearable
          />

          <div class="mobile-search-section">
            <span class="mobile-search-heading">快捷入口</span>
            <div class="mobile-search-grid">
              <button
                v-for="item in mobileSearchShortcuts"
                :key="item.path"
                class="mobile-search-shortcut"
                type="button"
                @click="goMobileSearchShortcut(item.path)"
              >
                {{ item.label }}
              </button>
            </div>
          </div>

          <div class="mobile-search-section">
            <span class="mobile-search-heading">{{ normalizedCommandQuery ? '相关标签' : '推荐搜索' }}</span>
            <div v-if="commandSuggestLoading" class="mobile-search-hint">正在匹配...</div>
            <div v-else-if="commandShowNoResults" class="mobile-search-hint">没有直接命中，可以按回车进入完整搜索。</div>
            <div class="mobile-search-tags">
              <button
                v-for="tag in (normalizedCommandQuery ? commandSearchTerms : mobileSearchTags)"
                :key="tag"
                class="mobile-search-tag"
                type="button"
                @click="searchByTag(tag)"
              >
                {{ tag }}
              </button>
            </div>
          </div>
        </section>
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

.desktop-search-shell {
  position: relative;
  width: min(100%, 520px);
}

.desktop-search-shell .desktop-search {
  max-width: none;
}

.command-search-panel {
  position: absolute;
  z-index: 140;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--el-border-color-light) 78%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--el-bg-color-overlay) 96%, transparent);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(16px) saturate(128%);
  opacity: 0;
  pointer-events: none;
  transform: translateY(-4px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.desktop-search-shell:focus-within .command-search-panel {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.command-section + .command-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.command-heading {
  display: block;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.command-item {
  width: 100%;
  min-height: 42px;
  padding: 7px 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;
  display: grid;
  gap: 2px;
  text-align: left;
  transition: background-color 0.18s ease, transform 0.18s ease;
}

.command-item:hover {
  background: var(--el-fill-color-light);
  transform: translateY(-1px);
}

.command-result {
  border-left: 3px solid transparent;
}

.command-result:hover {
  border-left-color: var(--cp-primary);
}

.command-user-item {
  width: 100%;
  min-height: 42px;
  padding: 7px 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  text-align: left;
  transition: background-color 0.18s ease, transform 0.18s ease;
}

.command-user-item:hover {
  background: var(--el-fill-color-light);
  transform: translateY(-1px);
}

.command-user-copy {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.command-user-copy strong,
.command-user-copy small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-user-copy strong {
  font-size: 13px;
}

.command-user-copy small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.command-item-title,
.command-item-desc {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-item-title {
  font-size: 13px;
  font-weight: 800;
}

.command-item-desc {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.command-loading {
  min-height: 32px;
  padding: 7px 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.command-empty {
  min-height: 34px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.command-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.command-tag {
  max-width: 100%;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.command-tag:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  transform: translateY(-1px);
}

.command-pop-enter-active,
.command-pop-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.command-pop-enter-from,
.command-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.mobile-title {
  display: none;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  width: 36px;
  height: 36px;
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

.notif-delete {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.notif-item:hover .notif-delete {
  opacity: 1;
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

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.2s ease;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.mobile-search-layer {
  display: none;
}

@media (max-width: 980px) {
  .app-topbar {
    background: color-mix(in srgb, var(--el-bg-color) 96%, transparent);
    backdrop-filter: blur(14px) saturate(126%);
  }

  .topbar-container {
    height: 54px;
    padding: 0 12px;
    gap: 8px;
  }

  .topbar-left {
    width: auto;
    min-width: 48px;
  }

  .desktop-search-shell {
    display: none;
  }

  .mobile-title {
    display: block;
    max-width: clamp(96px, 42vw, 180px);
  }

  .mobile-only {
    display: inline-flex;
  }

  .mobile-hidden-action {
    display: none;
  }

  .topbar-right {
    gap: 6px;
  }

  .icon-btn {
    width: 34px;
    height: 34px;
    font-size: 17px;
  }

  .notification-badge :deep(.el-badge__content) {
    transform: translateY(-2px) translateX(4px);
  }

  .mobile-search-layer {
    position: fixed;
    inset: 54px 0 0;
    z-index: 130;
    display: block;
  }

  .mobile-search-backdrop {
    position: absolute;
    inset: 0;
    border: none;
    background: rgba(15, 23, 42, 0.18);
    backdrop-filter: blur(4px);
  }

  .mobile-search-panel {
    position: relative;
    margin: 10px;
    padding: 12px;
    border: 1px solid color-mix(in srgb, var(--el-border-color-light) 72%, transparent);
    border-radius: 18px;
    background: color-mix(in srgb, var(--el-bg-color) 96%, transparent);
    box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18);
    backdrop-filter: blur(18px) saturate(128%);
  }

  .mobile-search-input :deep(.el-input__wrapper) {
    min-height: 42px;
  }

  .mobile-search-section {
    margin-top: 14px;
  }

  .mobile-search-heading {
    display: block;
    margin-bottom: 8px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 800;
  }

  .mobile-search-hint {
    margin-bottom: 8px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.45;
  }

  .mobile-search-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .mobile-search-shortcut,
  .mobile-search-tag {
    border: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-lighter);
    color: var(--el-text-color-regular);
    font-weight: 700;
    cursor: pointer;
  }

  .mobile-search-shortcut {
    min-height: 38px;
    border-radius: 12px;
    font-size: 13px;
  }

  .mobile-search-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .mobile-search-tag {
    min-height: 32px;
    border-radius: 999px;
    padding: 0 11px;
    font-size: 12px;
  }

  .mobile-search-shortcut:active,
  .mobile-search-tag:active {
    transform: scale(0.98);
  }
}

@media (max-width: 768px) {
  .topbar-container {
    padding: 0 10px;
  }

  .topbar-left :deep(.logo-img) {
    width: 34px;
    height: 34px;
    border-radius: 7px;
  }

  .topbar-left :deep(.logo-text) {
    display: none;
  }

  .topbar-center {
    justify-content: flex-start;
  }
}

@media (max-width: 430px) {
  .topbar-container {
    gap: 6px;
  }

  .topbar-left {
    min-width: 36px;
  }

  .mobile-title {
    max-width: clamp(84px, 38vw, 150px);
    font-size: 13px;
  }

  .topbar-right {
    gap: 4px;
  }

  .icon-btn {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }

  .mobile-search-panel {
    margin: 8px;
    padding: 10px;
  }
}
</style>
