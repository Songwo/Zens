<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import MainLayout from '@/layouts/MainLayout.vue'
import Avatar from '@/components/common/Avatar.vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { postApi } from '@/api/post'
import { userApi } from '@/api/user'
import { followApi } from '@/api/follow'
import { notificationApi } from '@/api/notification'
import type { Notification as NotificationType } from '@/api/notification'
import { viewLogApi, type ViewLog as ViewHistoryItem } from '@/api/viewLog'
import type { Post } from '@/types'
import { ElMessage } from 'element-plus'
import {
  Document,
  Star,
  Connection,
  Notification,
  Clock,
  EditPen,
  Setting,
  Loading
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
const followingLoading = ref(false)
const notifications = ref<NotificationType[]>([])
const notifLoading = ref(false)
const notifUnread = ref(0)
const viewHistory = ref<ViewHistoryItem[]>([])
const historyLoading = ref(false)
const historyPagination = ref({
  current: 1,
  size: 10,
  total: 0,
  pages: 0
})

const fetchUserProfile = async () => {
  try {
    const res = await userApi.getProfileStats()
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
    ElMessage.error('获取用户信息失败')
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
    const userId = userStore.userId ?? ''
    if (activeTab.value === 'posts') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
        userId: userId,
        status: 1
      })
    } else if (activeTab.value === 'favorites') {
      res = await postApi.searchList({
        page: page.value,
        pageSize: 10,
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

    if (posts.value.length >= res.data.total) {
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
    console.log('关注列表响应:', res)
    followingList.value = res.data ?? []
  } catch (error) {
    console.error('获取关注列表失败:', error)
    followingList.value = []
  }
  finally { followingLoading.value = false }
}

const fetchNotifications = async () => {
  notifLoading.value = true
  try {
    const res = await notificationApi.getList(1, 50)
    console.log('通知列表响应:', res)
    notifications.value = res.data?.records ?? []
    notifUnread.value = res.data?.unreadCount ?? 0
  } catch (error) {
    console.error('获取通知列表失败:', error)
    notifications.value = []
  }
  finally { notifLoading.value = false }
}

const markRead = async (id: string) => {
  await notificationApi.markAsRead(id)
  const n = notifications.value.find(n => String(n.id) === id)
  if (n) { n.isRead = 1; notifUnread.value = Math.max(0, notifUnread.value - 1) }
}

const markAllRead = async () => {
  await notificationApi.markAllAsRead()
  notifications.value.forEach(n => n.isRead = 1)
  notifUnread.value = 0
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

const goToSettings = () => {
  router.push('/settings')
}

const handleUnfollow = async (userId: string) => {
  await followApi.unfollow(userId)
  followingList.value = followingList.value.filter(u => u.id !== userId)
  if (userProfile.value) userProfile.value.followingCount = Math.max(0, (userProfile.value.followingCount ?? 1) - 1)
}

const goToCompose = () => {
  composerStore.open()
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

  // Song：说明
  if (activeTab.value === 'following') {
    fetchFollowing()
  } else if (activeTab.value === 'notifications') {
    fetchNotifications()
  } else if (activeTab.value === 'history') {
    fetchViewHistory(1)
  } else {
    fetchPosts(true)
  }
})
</script>

<template>
  <MainLayout>
    <div class="me-container">
      <!-- Profile Area -->
      <el-card class="profile-card" shadow="never">
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
        </div>
      </el-card>

      <!-- Content Tabs -->
      <el-tabs v-model="activeTab" class="me-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="posts">
          <template #label>
            <span class="tab-label"><el-icon><Document /></el-icon> 我的动态</span>
          </template>
          
          <div class="posts-list">
            <PostCard v-for="post in posts" :key="post.id" :post="post" />
            
            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading"><Loading /></el-icon> 正在加载...
            </div>

            <EmptyState
              v-if="!loading && posts.length === 0"
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

        <el-tab-pane name="favorites">
          <template #label>
            <span class="tab-label"><el-icon><Star /></el-icon> 我的收藏</span>
          </template>
          
          <div class="posts-list">
            <PostCard v-for="post in posts" :key="post.id" :post="post" />
            <EmptyState
              v-if="!loading && posts.length === 0"
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
          <EmptyState v-else title="还没有关注任何人" description="关注有趣的人，第一时间看到他们的动态" />
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
              <el-button size="small" text type="primary" @click="markAllRead">全部已读</el-button>
            </div>
            <el-card v-for="n in notifications" :key="n.id" shadow="never"
              class="notif-item" :class="{ unread: n.isRead === 0 }"
              @click="n.isRead === 0 && markRead(String(n.id))">
              <div class="notif-row">
                <div class="notif-body">
                  <span class="notif-title">{{ n.title }}</span>
                  <span class="notif-content">{{ n.content }}</span>
                  <span class="notif-time">{{ n.createdAt || n.createTime }}</span>
                </div>
                <el-tag v-if="n.isRead === 0" size="small" type="danger" effect="dark">未读</el-tag>
              </div>
            </el-card>
          </template>
          <EmptyState v-else title="暂无通知" description="当有人给你评论或点赞时，会在这里显示" />
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
  max-width: 800px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 24px;
  border-radius: var(--el-border-radius-base);
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

.me-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
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
  justify-content: flex-end;
  margin-top: 12px;
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
  justify-content: space-between;
  align-items: center;
}
.notif-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.notif-title { font-weight: 600; font-size: 14px; }
.notif-content { font-size: 13px; color: var(--el-text-color-regular); }
.notif-time { font-size: 12px; color: var(--el-text-color-placeholder); }
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
</style>
