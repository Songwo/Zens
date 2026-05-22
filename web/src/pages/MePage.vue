<script setup lang="ts">
import { ref, onMounted, watch, computed, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import MainLayout from '@/layouts/MainLayout.vue'
import Avatar from '@/components/common/Avatar.vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { postApi } from '@/api/post'
import { tagApi, type Tag as TagType } from '@/api/tag'
import { userApi } from '@/api/user'
import { followApi } from '@/api/follow'
import { levelApi, type LevelInfo } from '@/api/level'
import { notificationApi } from '@/api/notification'
import type { Notification as NotificationType } from '@/api/notification'
import { viewLogApi, type ViewLog as ViewHistoryItem } from '@/api/viewLog'
import type { Post } from '@/types'
import { ElMessage } from 'element-plus'
import { clearRequestCache } from '@/utils/requestCache'
import { emitNotificationUnreadSync } from '@/utils/notificationSync'
import { getCardThemePalette } from '@/utils/cardTheme'
import { resolveNotificationRoute } from '@/utils/notificationRoute'
import { timeAgo } from '@/utils/timeAgo'
import {
  Document,
  Star,
  Connection,
  Notification,
  Clock,
  EditPen,
  Setting,
  Loading,
  Delete,
  Check,
  Warning,
  CollectionTag,
  User,
  Tickets,
  ChatDotRound,
  PriceTag,
  View,
  CircleClose
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
import { usePostComposerStore } from '@/store/postComposer'
const composerStore = usePostComposerStore()

const activeTab = ref('posts')
const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const userProfile = ref<any>(null)
const profileDrawerVisible = ref(false)
const followingList = ref<any[]>([])
const followerList = ref<any[]>([])
const followerLoading = ref(false)
const followingTags = ref<TagType[]>([])
const followingTagsLoading = ref(false)
const unfollowingTagId = ref<number | null>(null)
const followingLoading = ref(false)
const notifications = ref<NotificationType[]>([])
const notifLoading = ref(false)
const notifUnread = ref(0)
const notifTypeFilter = ref<'all' | 'reply' | 'mention' | 'like' | 'favorite' | 'follow' | 'security' | 'system'>('all')
const notifOnlyUnread = ref(false)
const notifSelectedIds = ref<string[]>([])
const viewHistory = ref<ViewHistoryItem[]>([])
const historyLoading = ref(false)
const historyPagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})
let profileRequestId = 0

const levelInfo = ref<LevelInfo | null>(null)

const fetchLevelInfo = async () => {
  try {
    const res = await levelApi.getInfo()
    levelInfo.value = res.data
  } catch { /* ignore */ }
}

const profileCardStyle = computed(() => {
  const palette = getCardThemePalette((userStore.userInfo as any)?.profileCardTheme || 'sunset', 'sunset')
  const customBgUrl = String((userStore.userInfo as any)?.profileCardBgUrl || '').trim()
  const hasCustomBg = /^https?:\/\/[^"'\s]+$/.test(customBgUrl) || /^\/uploads\/[^"'\s]+$/.test(customBgUrl)
  return {
    '--cp-profile-card-bg': hasCustomBg
      ? `linear-gradient(135deg, rgba(255,255,255,0.74), rgba(255,255,255,0.74)), url("${customBgUrl}") center/cover no-repeat`
      : palette.background,
    '--cp-profile-card-border': palette.borderColor,
  } as Record<string, string>
})

const notifTypeLabelMap: Record<string, string> = {
  all: '全部类型',
  reply: '评论/回复',
  mention: '@提及',
  like: '点赞',
  favorite: '收藏',
  follow: '关注',
  system: '系统',
  security_alert: '账号安全',
  new_device_login: '新设备登录',
  session_terminated: '会话下线',
  password_changed: '密码修改',
  password_reset: '密码重置',
  two_factor_enabled: '二步验证开启',
  two_factor_disabled: '二步验证关闭',
  login_failed_burst: '登录失败告警',
}

const notificationTypeOptions = [
  { label: '全部类型', value: 'all' },
  { label: '评论/回复', value: 'reply' },
  { label: '@提及', value: 'mention' },
  { label: '点赞', value: 'like' },
  { label: '收藏', value: 'favorite' },
  { label: '关注', value: 'follow' },
  { label: '账号安全', value: 'security' },
  { label: '系统', value: 'system' },
] as const

const filteredNotifications = computed(() => {
  return notifications.value.filter(item => {
    const typeMatched = notifTypeFilter.value === 'all'
      || item.type === notifTypeFilter.value
      || (notifTypeFilter.value === 'security' && item.category === 'SECURITY')
    const unreadMatched = !notifOnlyUnread.value || Number(item.isRead) === 0
    return typeMatched && unreadMatched
  })
})

const groupedNotifications = computed(() => {
  const groups: Array<{ type: string; label: string; items: NotificationType[] }> = []
  const bucket = new Map<string, NotificationType[]>()
  filteredNotifications.value.forEach(item => {
    const key = item.category === 'SECURITY' ? 'security' : (item.type || 'system')
    if (!bucket.has(key)) {
      bucket.set(key, [])
    }
    bucket.get(key)!.push(item)
  })
  ;['reply', 'mention', 'like', 'favorite', 'follow', 'security', 'system'].forEach(type => {
    const items = bucket.get(type) || []
    if (items.length > 0) {
      groups.push({
        type,
        label: type === 'security' ? '账号安全' : (notifTypeLabelMap[type] || '其他'),
        items,
      })
      bucket.delete(type)
    }
  })
  bucket.forEach((items, type) => {
    groups.push({
      type,
      label: notifTypeLabelMap[type] || type,
      items,
    })
  })
  return groups
})

const filteredNotificationIdSet = computed(() => {
  return new Set(filteredNotifications.value.map(item => String(item.id)))
})

const hasNotificationSelection = computed(() => notifSelectedIds.value.length > 0)

const allFilteredSelected = computed(() => {
  if (filteredNotifications.value.length === 0) return false
  const selectedSet = new Set(notifSelectedIds.value)
  return filteredNotifications.value.every(item => selectedSet.has(String(item.id)))
})

const fetchUserProfile = async () => {
  if (!userStore.accessToken) return
  const requestId = ++profileRequestId
  try {
    const res = await userApi.getProfileStats()
    if (requestId !== profileRequestId) {
      return
    }
    const data = res.data
    if (data) {
      userProfile.value = {
        postCount: data.postCount ?? 0,
        followingCount: data.followingCount ?? 0,
        followerCount: data.followerCount ?? 0,
        major: userStore.userInfo?.major,
        enrollmentYear: userStore.userInfo?.enrollmentYear,
        interestTags: (userStore.userInfo as any)?.interestTags,
      }
    }
  } catch (error) {
    if (requestId === profileRequestId) {
      ElMessage.error('获取用户信息失败')
    }
  }
}

const fetchPosts = async (reset = false) => {
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
  }

  if (!hasMore.value || loading.value) return

  loading.value = true
  try {
    let res
    const userId = userStore.userId || userStore.userInfo?.id || ''
    if (activeTab.value === 'posts') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
        needTotal: false,
        userId: userId,
        status: 1
      })
    } else if (activeTab.value === 'drafts') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
        needTotal: false,
        userId: userId,
        status: 0,
        auditStatus: 'DRAFT'
      })
    } else if (activeTab.value === 'rejected') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
        needTotal: false,
        userId: userId,
        status: 0,
        auditStatus: 'REJECTED'
      })
    } else if (activeTab.value === 'trash') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
        needTotal: false,
        userId: userId,
        auditStatus: 'DELETED'
      })
    } else if (activeTab.value === 'favorites') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
        needTotal: false,
        collectedBy: userId,
        status: 1
      })
    } else {
      res = { data: { records: [], total: 0 } }
    }

    if (res.data.records.length > 0) {
      posts.value.push(...res.data.records)
      page.value++
    } else {
      hasMore.value = false
    }

    if (res.data.records.length < 10) {
      hasMore.value = false
    }
  } catch (error) {
    ElMessage.error('获取内容失败')
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tabName: string | number) => {
  const tab = String(tabName)
  router.replace({ query: { ...route.query, tab } })
  if (tab === 'following') {
    fetchFollowing()
  } else if (tab === 'followers') {
    fetchFollowers()
  } else if (tab === 'tags') {
    fetchFollowingTags()
  } else if (tab === 'notifications') {
    fetchNotifications()
  } else if (tab === 'history') {
    fetchViewHistory(1)
  } else {
    fetchPosts(true)
  }
}

const fetchFollowing = async () => {
  followingLoading.value = true
  try {
    const res = await followApi.getMyFollowing()
    followingList.value = res.data ?? []
  } catch (error) {
    followingList.value = []
  }
  finally { followingLoading.value = false }
}

const fetchFollowers = async () => {
  followerLoading.value = true
  try {
    const res = await followApi.getMyFollowers()
    followerList.value = res.data ?? []
  } catch (error) {
    followerList.value = []
  }
  finally { followerLoading.value = false }
}

const fetchNotifications = async () => {
  notifLoading.value = true
  try {
    const res = await notificationApi.getList(1, 50)
    console.log('通知列表响应:', res)
    notifications.value = res.data?.records ?? []
    notifUnread.value = res.data?.unreadCount ?? 0
    notifSelectedIds.value = []
    emitNotificationUnreadSync({ unreadCount: notifUnread.value })
  } catch (error) {
    console.error('获取通知列表失败:', error)
    notifications.value = []
    notifSelectedIds.value = []
  }
  finally { notifLoading.value = false }
}

const syncTopbarNotificationUnread = () => {
  const currentUserId = String(userStore.userId || userStore.userInfo?.id || '')
  if (currentUserId) {
    clearRequestCache(`notif:unread:${currentUserId}`)
  }
  emitNotificationUnreadSync({ unreadCount: notifUnread.value })
}

const markRead = async (id: string) => {
  const n = notifications.value.find(item => String(item.id) === id)
  if (!n || n.isRead === 1) return
  await notificationApi.markAsRead(id)
  n.isRead = 1
  notifUnread.value = Math.max(0, notifUnread.value - 1)
  syncTopbarNotificationUnread()
}

const markAllRead = async () => {
  await notificationApi.markAllAsRead()
  notifications.value.forEach(item => { item.isRead = 1 })
  notifUnread.value = 0
  syncTopbarNotificationUnread()
}

const openNotification = async (item: NotificationType) => {
  const id = String(item.id)
  if (item.isRead === 0) {
    await markRead(id)
  }
  const targetRoute = resolveNotificationRoute(item.relatedId, { type: item.type, relatedUserId: item.relatedUserId })
  if (targetRoute) {
    router.push(targetRoute)
  }
}

const toggleNotificationSelection = (id: string, selected: boolean) => {
  const next = new Set(notifSelectedIds.value)
  if (selected) {
    next.add(id)
  } else {
    next.delete(id)
  }
  notifSelectedIds.value = Array.from(next)
}

const handleNotificationSelectionChange = (id: string, value: string | number | boolean) => {
  toggleNotificationSelection(id, Boolean(value))
}

const selectAllFilteredNotifications = () => {
  notifSelectedIds.value = filteredNotifications.value.map(item => String(item.id))
}

const clearNotificationSelection = () => {
  notifSelectedIds.value = []
}

const markSelectedNotificationsRead = async () => {
  if (notifSelectedIds.value.length === 0) return
  await notificationApi.markBatchAsRead(notifSelectedIds.value)
  let reducedUnread = 0
  const selectedSet = new Set(notifSelectedIds.value)
  notifications.value.forEach(item => {
    if (selectedSet.has(String(item.id)) && item.isRead === 0) {
      item.isRead = 1
      reducedUnread += 1
    }
  })
  notifUnread.value = Math.max(0, notifUnread.value - reducedUnread)
  syncTopbarNotificationUnread()
  clearNotificationSelection()
}

const deleteSelectedNotifications = async () => {
  if (notifSelectedIds.value.length === 0) return
  const selectedSet = new Set(notifSelectedIds.value)
  const unreadToDelete = notifications.value.filter(item => selectedSet.has(String(item.id)) && item.isRead === 0).length
  await notificationApi.deleteBatch(notifSelectedIds.value)
  notifications.value = notifications.value.filter(item => !selectedSet.has(String(item.id)))
  notifUnread.value = Math.max(0, notifUnread.value - unreadToDelete)
  syncTopbarNotificationUnread()
  clearNotificationSelection()
}

const deleteNotification = async (item: NotificationType) => {
  await notificationApi.delete(String(item.id))
  notifications.value = notifications.value.filter(current => String(current.id) !== String(item.id))
  if (item.isRead === 0) {
    notifUnread.value = Math.max(0, notifUnread.value - 1)
    syncTopbarNotificationUnread()
  }
  notifSelectedIds.value = notifSelectedIds.value.filter(id => id !== String(item.id))
}

const getCurrentUserId = () => {
  return userStore.userId || userStore.userInfo?.id || ''
}

const fetchViewHistory = async (pageNo = 1) => {
  const userId = getCurrentUserId()
  if (!userId) {
    viewHistory.value = []
    return
  }

  historyLoading.value = true
  try {
    const res = await viewLogApi.getUserHistoryPaged(userId, pageNo, historyPagination.value.size)
    viewHistory.value = res.data?.records || []
    historyPagination.value.current = Number(res.data?.current || pageNo)
    historyPagination.value.size = Number(res.data?.size || historyPagination.value.size)
    historyPagination.value.total = Number(res.data?.total || 0)
    historyPagination.value.pages = Number(res.data?.pages || 0)
  } catch (error) {
    console.error('获取浏览历史失败:', error)
    ElMessage.error('获取浏览历史失败')
    viewHistory.value = []
    historyPagination.value.total = 0
    historyPagination.value.pages = 0
  } finally {
    historyLoading.value = false
  }
}

const handleHistoryPageChange = (pageNo: number) => {
  fetchViewHistory(pageNo)
}

const goToHistoryPost = (postId: string) => {
  if (!postId) return
  router.push(`/t/${postId}`)
}

const formatHistoryTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

watch(
  () => route.query.tab,
  (newTab) => {
    if (newTab && typeof newTab === 'string' && newTab !== activeTab.value) {
      activeTab.value = newTab
      if (newTab === 'following') {
        fetchFollowing()
      } else if (newTab === 'followers') {
        fetchFollowers()
      } else if (newTab === 'tags') {
        fetchFollowingTags()
      } else if (newTab === 'notifications') {
        fetchNotifications()
      } else if (newTab === 'history') {
        fetchViewHistory(1)
      } else {
        fetchPosts(true)
      }
    }
  }
)

watch(
  () => filteredNotificationIdSet.value,
  (idSet) => {
    notifSelectedIds.value = notifSelectedIds.value.filter(id => idSet.has(id))
  },
  { deep: false }
)

const goToSettings = () => {
  router.push('/settings')
}

const handleUnfollow = async (userId: string) => {
  await followApi.unfollow(userId)
  followingList.value = followingList.value.filter(u => u.id !== userId)
  if (userProfile.value) userProfile.value.followingCount = Math.max(0, (userProfile.value.followingCount ?? 1) - 1)
}

const fetchFollowingTags = async () => {
  followingTagsLoading.value = true
  try {
    const res = await tagApi.getMyFollowing()
    followingTags.value = res.data ?? []
  } catch {
    ElMessage.error('获取关注话题失败')
  } finally {
    followingTagsLoading.value = false
  }
}

const handleUnfollowTag = async (tag: TagType) => {
  unfollowingTagId.value = tag.id
  try {
    await tagApi.unfollow(tag.id)
    followingTags.value = followingTags.value.filter(t => t.id !== tag.id)
    ElMessage.success(`已取消关注 #${tag.name}`)
  } catch {
    ElMessage.error('取消关注失败')
  } finally {
    unfollowingTagId.value = null
  }
}

const goToCompose = () => {
  composerStore.open()
}

const removePostFromList = (postId: string) => {
  posts.value = posts.value.filter(post => post.id !== postId)
}

onMounted(() => {
  if (!userStore.accessToken) {
    ElMessage.error('请先登录')
    router.push('/auth/login')
    return
  }

  if (route.query.tab && typeof route.query.tab === 'string') {
    activeTab.value = route.query.tab
  }

  fetchUserProfile()
  fetchLevelInfo()

  // Song：说明
  if (activeTab.value === 'following') {
    fetchFollowing()
  } else if (activeTab.value === 'followers') {
    fetchFollowers()
  } else if (activeTab.value === 'tags') {
    fetchFollowingTags()
  } else if (activeTab.value === 'notifications') {
    fetchNotifications()
  } else if (activeTab.value === 'history') {
    fetchViewHistory(1)
  } else {
    fetchPosts(true)
  }
})

onBeforeUnmount(() => {
  profileRequestId++
})
</script>

<template>
  <MainLayout>
    <div class="me-container">
      <!-- Profile Area -->
      <el-card class="profile-card" :style="profileCardStyle" shadow="never">
        <div class="profile-flex">
          <div class="profile-info">
            <Avatar
              :src="userStore.userInfo?.avatar ?? undefined"
              size="xl"
            />
            <div class="user-meta">
              <h1 class="nickname">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</h1>
              <p class="username">@{{ userStore.userInfo?.username }}</p>
              <p class="bio">{{ userStore.userInfo?.bio || '这个人很懒，还没有写简介~' }}</p>
              <div class="badge-row">
                <template v-if="userProfile?.interestTags">
                  <el-tag
                    v-for="(tag, index) in (userProfile.interestTags as string).split(',')"
                    :key="index"
                    size="small"
                    :type="['danger', 'success', 'primary', 'info', 'warning'][Number(index) % 5] as any"
                    effect="dark"
                  >
                    {{ tag.trim() }}
                  </el-tag>
                </template>
                <template v-else>
                  <el-tag size="small" type="danger" effect="dark">Java</el-tag>
                  <el-tag size="small" type="success" effect="dark">Spring Boot</el-tag>
                  <el-tag size="small" type="primary" effect="dark">Vue3</el-tag>
                  <el-tag size="small" type="info" effect="dark">Docker</el-tag>
                </template>
              </div>
            </div>
          </div>

          <div class="action-buttons">
            <el-button type="info" plain @click="profileDrawerVisible = true">资料</el-button>
            <el-button :icon="EditPen" type="primary" @click="goToCompose">发布动态</el-button>
            <el-button :icon="Setting" @click="goToSettings">个人设置</el-button>
          </div>
        </div>

        <div class="stats-row">
          <div class="stat-item">
            <span class="stat-value">{{ userProfile?.postCount || 0 }}</span>
            <span class="stat-label">动态</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ userProfile?.followingCount || 0 }}</span>
            <span class="stat-label">关注</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ userProfile?.followerCount || 0 }}</span>
            <span class="stat-label">粉丝</span>
          </div>
          <div class="stat-item" style="cursor:pointer" @click="router.push('/connect')">
            <span class="stat-value level-value">Lv.{{ levelInfo?.level ?? '-' }}</span>
            <span class="stat-label">等级</span>
            <el-progress
              v-if="levelInfo"
              :percentage="levelInfo.nextLevelExp > 0 ? Math.round(levelInfo.experience / levelInfo.nextLevelExp * 100) : 100"
              :stroke-width="3"
              :show-text="false"
              style="width:60px;margin-top:4px"
            />
          </div>
        </div>
      </el-card>

      <!-- Content Tabs -->
      <el-tabs v-model="activeTab" class="me-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="posts">
          <template #label>
            <span class="tab-label"><el-icon><Document /></el-icon> 我的动态</span>
          </template>
          
          <div class="posts-list">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @deleted="removePostFromList"
              @restored="removePostFromList"
            />
            
            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
            </div>

            <EmptyState
              v-if="!loading && posts.length === 0"
              :icon="Document"
              title="还没有发布动态"
              description="快去分享你的第一篇校园见闻吧！"
            >
              <el-button type="primary" :icon="EditPen" @click="goToCompose">发布动态</el-button>
            </EmptyState>

            <div v-if="!loading && hasMore && posts.length > 0" class="load-more">
              <el-button plain @click="fetchPosts(false)">加载更多</el-button>
            </div>

            <div v-if="!hasMore && posts.length > 0" class="no-more">
              <span>没有更多动态了</span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="drafts">
          <template #label>
            <span class="tab-label"><el-icon><EditPen /></el-icon> 我的草稿</span>
          </template>

          <div class="posts-list">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @deleted="removePostFromList"
              @restored="removePostFromList"
            />

            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
            </div>

            <EmptyState
              v-if="!loading && posts.length === 0"
              :icon="Tickets"
              title="暂无草稿"
              description="保存但未提交的草稿会显示在这里，被打回的帖子请查看「打回修改」标签"
            >
              <el-button type="primary" :icon="EditPen" @click="goToCompose">新建草稿</el-button>
            </EmptyState>

            <div v-if="!loading && hasMore && posts.length > 0" class="load-more">
              <el-button plain @click="fetchPosts(false)">加载更多</el-button>
            </div>

            <div v-if="!hasMore && posts.length > 0" class="no-more">
              <span>草稿已经全部展示</span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="rejected">
          <template #label>
            <span class="tab-label"><el-icon><Warning /></el-icon> 打回修改</span>
          </template>

          <div class="posts-list">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @deleted="removePostFromList"
              @restored="removePostFromList"
            />

            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
            </div>

            <EmptyState
              v-if="!loading && posts.length === 0"
              :icon="CircleClose"
              title="暂无打回帖子"
              description="被管理员打回修改的帖子会显示在这里，编辑后重新提交审核"
            />

            <div v-if="!loading && hasMore && posts.length > 0" class="load-more">
              <el-button plain @click="fetchPosts(false)">加载更多</el-button>
            </div>

            <div v-if="!hasMore && posts.length > 0" class="no-more">
              <span>已全部展示</span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="trash">
          <template #label>
            <span class="tab-label"><el-icon><Delete /></el-icon> 我的回收站</span>
          </template>

          <div class="posts-list">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @deleted="removePostFromList"
              @restored="removePostFromList"
            />

            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
            </div>

            <EmptyState
              v-if="!loading && posts.length === 0"
              :icon="Delete"
              title="回收站为空"
              description="你删除的帖子会在这里保留 7 天，可在期限内恢复"
            />

            <div v-if="!loading && hasMore && posts.length > 0" class="load-more">
              <el-button plain @click="fetchPosts(false)">加载更多</el-button>
            </div>

            <div v-if="!hasMore && posts.length > 0" class="no-more">
              <span>回收站内容已全部展示</span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="favorites">
          <template #label>
            <span class="tab-label"><el-icon><Star /></el-icon> 我的收藏</span>
          </template>
          
          <div class="posts-list">
            <PostCard
              v-for="post in posts"
              :key="post.id"
              :post="post"
              @deleted="removePostFromList"
              @restored="removePostFromList"
            />
            <EmptyState
              v-if="!loading && posts.length === 0"
              :icon="Star"
              title="暂无收藏"
              description="去发现感兴趣的内容并收藏吧！"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane name="following">
          <template #label>
            <span class="tab-label"><el-icon><Connection /></el-icon> 我的关注</span>
          </template>
          <div v-if="followingLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
          </div>
          <div v-else-if="followingList.length > 0" class="follow-list">
            <el-card v-for="user in followingList" :key="user.id" shadow="never" class="follow-item">
              <div class="follow-user">
                <Avatar :src="user.avatar ?? undefined" size="md" />
                <div class="follow-meta">
                  <span class="follow-name">{{ user.nickname || user.username }}</span>
                  <span class="follow-sub">@{{ user.username }}</span>
                </div>
                <el-button size="small" type="info" plain @click="handleUnfollow(user.id)">取消关注</el-button>
              </div>
            </el-card>
          </div>
          <EmptyState v-else :icon="User" title="还没有关注任何人" description="关注有趣的人，第一时间看到他们的动态" />
        </el-tab-pane>

        <el-tab-pane name="followers">
          <template #label>
            <span class="tab-label"><el-icon><User /></el-icon> 我的粉丝</span>
          </template>
          <div v-if="followerLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
          </div>
          <div v-else-if="followerList.length > 0" class="follow-list">
            <el-card v-for="user in followerList" :key="user.id" shadow="never" class="follow-item">
              <div class="follow-user">
                <Avatar :src="user.avatar ?? undefined" size="md" />
                <div class="follow-meta">
                  <span class="follow-name">{{ user.nickname || user.username }}</span>
                  <span class="follow-sub">@{{ user.username }}</span>
                </div>
                <el-button size="small" type="primary" plain @click="$router.push(`/user/${user.id}`)">查看主页</el-button>
              </div>
            </el-card>
          </div>
          <EmptyState v-else :icon="Connection" title="还没有粉丝" description="发布优质内容，吸引更多人关注你" />
        </el-tab-pane>

        <el-tab-pane name="tags">
          <template #label>
            <span class="tab-label"><el-icon><CollectionTag /></el-icon> 关注话题</span>
          </template>
          <div v-if="followingTagsLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
          </div>
          <div v-else-if="followingTags.length > 0" class="tag-follow-list">
            <el-card
              v-for="tag in followingTags"
              :key="tag.id"
              shadow="never"
              class="tag-follow-item"
            >
              <div class="tag-follow-row">
                <div class="tag-follow-info">
                  <el-tag type="primary" size="large" class="tag-name-badge" @click="$router.push(`/tag/${tag.name}`)">#{{ tag.name }}</el-tag>
                  <span class="tag-post-count">{{ tag.postCount ?? 0 }} 篇帖子</span>
                </div>
                <el-button
                  size="small"
                  type="info"
                  plain
                  :loading="unfollowingTagId === tag.id"
                  @click="handleUnfollowTag(tag)"
                >取消关注</el-button>
              </div>
            </el-card>
          </div>
          <EmptyState v-else :icon="PriceTag" title="还没有关注任何话题" description="在话题页点击「关注话题」，有新帖子时会收到通知" />
        </el-tab-pane>

        <el-tab-pane name="history">
          <template #label>
            <span class="tab-label"><el-icon><Clock /></el-icon> 浏览记录</span>
          </template>
          <div v-if="historyLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
          </div>
          <template v-else-if="viewHistory.length > 0">
            <el-card
              v-for="item in viewHistory"
              :key="item.postId"
              shadow="never"
              class="history-item"
              @click="goToHistoryPost(item.postId)"
            >
              <div class="history-row">
                <div class="history-body">
                  <span class="history-title">{{ item.title || '该帖子已删除' }}</span>
                  <span class="history-time">浏览时间：{{ formatHistoryTime(item.viewTime) }}</span>
                </div>
                <el-button text type="primary" @click.stop="goToHistoryPost(item.postId)">查看</el-button>
              </div>
            </el-card>
            <div class="history-pagination" v-if="historyPagination.total > historyPagination.size">
              <el-pagination
                layout="prev, pager, next, total"
                :current-page="historyPagination.current"
                :page-size="historyPagination.size"
                :total="historyPagination.total"
                @current-change="handleHistoryPageChange"
              />
            </div>
          </template>
          <EmptyState
            v-else
            :icon="View"
            title="暂无浏览记录"
            description="浏览过的帖子会展示在这里，便于快速回看"
          />
        </el-tab-pane>

        <el-tab-pane name="notifications">
          <template #label>
            <span class="tab-label">
              <el-icon><Notification /></el-icon> 消息通知
              <el-badge v-if="notifUnread > 0" :value="notifUnread" :max="99" class="notif-badge" />
            </span>
          </template>
          <div v-if="notifLoading" class="loading-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
          </div>
          <template v-else-if="notifications.length > 0">
            <div class="notif-header">
              <div class="notif-header-left">
                <el-select v-model="notifTypeFilter" size="small" class="notif-filter-select">
                  <el-option
                    v-for="item in notificationTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-checkbox v-model="notifOnlyUnread">只看未读</el-checkbox>
              </div>
              <div class="notif-header-right">
                <el-button size="small" text @click="allFilteredSelected ? clearNotificationSelection() : selectAllFilteredNotifications()">
                  {{ allFilteredSelected ? '取消全选' : '全选筛选结果' }}
                </el-button>
                <el-button size="small" text type="primary" @click="markAllRead">全部已读</el-button>
                <el-button
                  size="small"
                  text
                  type="primary"
                  :icon="Check"
                  :disabled="!hasNotificationSelection"
                  @click="markSelectedNotificationsRead"
                >
                  批量已读
                </el-button>
                <el-button
                  size="small"
                  text
                  type="danger"
                  :icon="Delete"
                  :disabled="!hasNotificationSelection"
                  @click="deleteSelectedNotifications"
                >
                  批量删除
                </el-button>
              </div>
            </div>

            <template v-if="groupedNotifications.length > 0">
              <div v-for="group in groupedNotifications" :key="group.type" class="notif-group">
                <div class="notif-group-title">{{ group.label }}（{{ group.items.length }}）</div>
                <el-card
                  v-for="n in group.items"
                  :key="n.id"
                  shadow="never"
                  class="notif-item"
                  :class="{ unread: n.isRead === 0 }"
                  @click="openNotification(n)"
                >
                  <div class="notif-row">
                    <div class="notif-check" @click.stop>
                      <el-checkbox
                        :model-value="notifSelectedIds.includes(String(n.id))"
                        @change="handleNotificationSelectionChange(String(n.id), $event)"
                      />
                    </div>
                    <div class="notif-body">
                      <span class="notif-title">{{ n.title }}</span>
                      <span class="notif-content">{{ n.content }}</span>
                      <span class="notif-time">{{ timeAgo(n.createdAt || n.createTime) }}</span>
                    </div>
                    <div class="notif-extra">
                      <el-tag v-if="n.isRead === 0" size="small" type="danger" effect="dark">未读</el-tag>
                      <el-button text type="danger" size="small" @click.stop="deleteNotification(n)">删除</el-button>
                    </div>
                  </div>
                </el-card>
              </div>
            </template>
            <EmptyState
              v-else
              :icon="Notification"
              title="没有符合筛选条件的通知"
              description="可以切换类型或取消“只看未读”"
            />
          </template>
          <EmptyState v-else :icon="ChatDotRound" title="暂无通知" description="当有人给你评论或点赞时，会在这里显示" />
        </el-tab-pane>
      </el-tabs>

      <!-- Profile Details Drawer -->
      <el-drawer
        v-model="profileDrawerVisible"
        title="详细资料"
        direction="rtl"
        size="350px"
      >
        <div class="drawer-content" v-if="userProfile">
          <div class="info-item">
            <span class="info-label">学校</span>
            <span class="info-value">{{ userProfile.school || '未填写' }}</span>
          </div>
          <el-divider />
          <div class="info-item">
            <span class="info-label">专业</span>
            <span class="info-value">{{ userProfile.major || '未填写' }}</span>
          </div>
          <el-divider />
          <div class="info-item">
            <span class="info-label">入学年份/年级</span>
            <span class="info-value">{{ userProfile.enrollmentYear ? userProfile.enrollmentYear + '级' : '未填写' }}</span>
          </div>
        </div>
      </el-drawer>
    </div>
  </MainLayout>
</template>

<style scoped>
.me-container {
  max-width: min(100%, var(--cp-profile-page-width, 1080px));
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 24px;
  border-radius: var(--el-border-radius-base);
  border-color: var(--cp-profile-card-border, var(--el-border-color));
  background: var(--cp-profile-card-bg, var(--el-bg-color));
}

.profile-card :deep(.el-card__body) {
  background: transparent;
}

.profile-flex {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.profile-info {
  display: flex;
  gap: 20px;
  align-items: center;
}

.user-meta .nickname {
  margin: 0 0 4px 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.user-meta .username {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.badge-row {
  display: flex;
  gap: 8px;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.stats-row {
  display: flex;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 24px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-top: 4px;
}

.level-value {
  color: var(--el-color-warning);
}

.me-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

@media (max-width: 640px) {
  .me-tabs :deep(.el-tabs__nav-wrap) {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .me-tabs :deep(.el-tabs__nav) {
    white-space: nowrap;
    display: flex;
    flex-wrap: nowrap;
  }
  .me-tabs :deep(.el-tabs__nav-scroll) {
    overflow: visible;
  }
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.loading-state {
  text-align: center;
  padding: 24px;
  color: var(--el-text-color-secondary);
}

.load-more {
  text-align: center;
  padding: 24px 0;
}

.no-more {
  text-align: center;
  padding: 24px 0;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

@media (max-width: 768px) {
  .profile-flex {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .profile-info {
    flex-direction: column;
    margin-bottom: 20px;
  }
  
  .action-buttons {
    width: 100%;
    justify-content: center;
  }
}

.drawer-content {
  padding: 0 10px;
}
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 0;
}
.info-label {
  font-weight: bold;
  color: var(--el-text-color-secondary);
}
.info-value {
  color: var(--el-text-color-primary);
}

.bio {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.follow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}
.follow-item { border-radius: 8px; }
.follow-user {
  display: flex;
  align-items: center;
  gap: 12px;
}
.follow-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.follow-name { font-weight: 600; }
.follow-sub { font-size: 12px; color: var(--el-text-color-secondary); }

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.notif-header-left,
.notif-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notif-filter-select {
  width: 140px;
}

.notif-group {
  margin-top: 10px;
}

.notif-group-title {
  margin: 8px 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}

.notif-item {
  margin-top: 8px;
  border-radius: 8px;
  cursor: pointer;
}

.notif-item.unread {
  border-left: 3px solid var(--el-color-primary);
}

.notif-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.notif-check {
  flex-shrink: 0;
}

.notif-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.notif-title { font-weight: 600; font-size: 14px; }
.notif-content { font-size: 13px; color: var(--el-text-color-regular); }
.notif-time { font-size: 12px; color: var(--el-text-color-placeholder); }
.notif-extra {
  display: flex;
  align-items: center;
  gap: 6px;
}
.notif-badge { margin-left: 6px; }

.history-item {
  margin-top: 8px;
  border-radius: 8px;
  cursor: pointer;
}

.history-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.history-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.history-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.history-pagination {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.tag-follow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}

.tag-follow-item {
  border-radius: 8px;
}

.tag-follow-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tag-follow-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tag-name-badge {
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
}

.tag-post-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
