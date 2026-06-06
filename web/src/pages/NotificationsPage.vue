<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import MainLayout from '@/layouts/MainLayout.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { notificationApi } from '@/api/notification'
import type { Notification as NotificationType } from '@/api/notification'
import { ElMessage } from 'element-plus'
import { clearRequestCache } from '@/utils/requestCache'
import { emitNotificationUnreadSync } from '@/utils/notificationSync'
import { resolveNotificationRoute } from '@/utils/notificationRoute'
import { timeAgo } from '@/utils/timeAgo'
import { Check, Delete, Notification, ChatDotRound, Loading } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const notifications = ref<NotificationType[]>([])
const notifLoading = ref(false)
const notifUnread = ref(0)
const notifTypeFilter = ref<'all' | 'reply' | 'mention' | 'like' | 'favorite' | 'follow' | 'security' | 'system'>('all')
const notifOnlyUnread = ref(false)
const notifSelectedIds = ref<string[]>([])

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

const fetchNotifications = async () => {
  notifLoading.value = true
  try {
    const res = await notificationApi.getList(1, 50)
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

watch(
  () => filteredNotificationIdSet.value,
  (idSet) => {
    notifSelectedIds.value = notifSelectedIds.value.filter(id => idSet.has(id))
  },
  { deep: false }
)

onMounted(() => {
  if (!userStore.accessToken) {
    ElMessage.error('请先登录')
    router.push('/auth/login')
    return
  }
  fetchNotifications()
})
</script>

<template>
  <MainLayout>
    <div class="notifications-page">
      <div class="notif-page-header">
        <h1 class="notif-page-title">消息通知</h1>
        <el-badge v-if="notifUnread > 0" :value="notifUnread" :max="99" />
      </div>

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
    </div>
  </MainLayout>
</template>

<style scoped>
.notifications-page {
  width: 100%;
}

.notif-page-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.notif-page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.loading-state {
  text-align: center;
  padding: 24px;
  color: var(--el-text-color-secondary);
}

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
</style>
