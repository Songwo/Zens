<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Loading, Promotion, Search } from '@element-plus/icons-vue'
import MainLayout from '@/layouts/MainLayout.vue'
import { dmApi, type DirectConversation, type DirectMessage } from '@/api/dm'
import { timeAgo } from '@/utils/timeAgo'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const conversations = ref<DirectConversation[]>([])
const conversationsLoading = ref(false)
const conversationKeyword = ref('')
const activePeerId = ref('')
const activePeerName = ref('')

const messages = ref<DirectMessage[]>([])
const messagePage = ref(1)
const messageTotal = ref(0)
const messageLoading = ref(false)
const hasMoreMessages = ref(true)
const sendLoading = ref(false)
const draft = ref('')
let pollTimer: any = null

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(() => {
    if (!document.hidden) {
      loadConversations().catch(() => {})
    }
  }, 30000)
}

const handleVisibilityChange = () => {
  if (document.hidden) {
    stopPolling()
    return
  }
  loadConversations().catch(() => {})
  startPolling()
}

const applyDraftFromRoute = () => {
  const routeDraft = String(route.query.draft || '').trim()
  if (!routeDraft) return
  if (!draft.value || draft.value === routeDraft) {
    draft.value = routeDraft
  }
}

const filteredConversations = computed(() => {
  const q = conversationKeyword.value.trim().toLowerCase()
  if (!q) return conversations.value
  return conversations.value.filter(item => {
    return (
      item.peerName?.toLowerCase().includes(q) ||
      item.lastMessage?.toLowerCase().includes(q)
    )
  })
})

const activeConversation = computed(() => {
  return conversations.value.find(item => item.peerId === activePeerId.value) || null
})

const orderedMessages = computed(() => {
  return [...messages.value].reverse()
})

const loadConversations = async () => {
  conversationsLoading.value = true
  try {
    const res = await dmApi.getConversations(1, 50)
    conversations.value = res.data?.records || []
    syncActiveConversationByRoute()
  } catch {
    ElMessage.error('会话列表加载失败')
  } finally {
    conversationsLoading.value = false
  }
}

const syncActiveConversationByRoute = () => {
  const peerId = String(route.query.peerId || '')
  const peerName = String(route.query.peerName || '')

  if (peerId) {
    const shouldReload = activePeerId.value !== peerId || messages.value.length === 0
    activePeerId.value = peerId
    if (peerName) {
      activePeerName.value = peerName
    }
    const existed = conversations.value.some(item => item.peerId === peerId)
    if (!existed) {
      conversations.value.unshift({
        conversationId: `temp:${peerId}`,
        peerId,
        peerName: peerName || `用户${peerId.slice(0, 6)}`,
        lastMessage: '',
        lastTime: new Date().toISOString(),
        unreadCount: 0,
      })
    }
    if (shouldReload) {
      loadMessages(true)
    }
    applyDraftFromRoute()
    return
  }

  if (activePeerId.value) {
    return
  }
  const first = conversations.value[0]
  if (first) {
    activePeerId.value = first.peerId
    activePeerName.value = first.peerName
    loadMessages(true)
  }
}

const loadMessages = async (reset = false) => {
  if (!activePeerId.value || messageLoading.value) return

  if (reset) {
    messages.value = []
    messagePage.value = 1
    hasMoreMessages.value = true
  }

  if (!hasMoreMessages.value) return

  messageLoading.value = true
  try {
    const res = await dmApi.getMessages(activePeerId.value, messagePage.value, 30)
    const records = res.data?.records || []
    messageTotal.value = res.data?.total || 0

    if (records.length > 0) {
      messages.value.push(...records)
      messagePage.value += 1
    } else {
      hasMoreMessages.value = false
    }

    if (messages.value.length >= messageTotal.value) {
      hasMoreMessages.value = false
    }

    await dmApi.markRead(activePeerId.value)
    conversations.value = conversations.value.map(item => {
      if (item.peerId === activePeerId.value) {
        return { ...item, unreadCount: 0 }
      }
      return item
    })
  } catch {
    ElMessage.error('消息加载失败')
  } finally {
    messageLoading.value = false
  }
}

const selectConversation = (item: DirectConversation) => {
  if (!item.peerId || item.peerId === activePeerId.value) return
  activePeerId.value = item.peerId
  activePeerName.value = item.peerName
  if (String(route.query.peerId || '') === item.peerId) {
    loadMessages(true)
    return
  }
  router.replace({
    path: '/messages',
    query: { peerId: item.peerId, peerName: item.peerName },
  })
}

const handleSend = async () => {
  const content = draft.value.trim()
  if (!activePeerId.value) {
    ElMessage.warning('请先选择会话对象')
    return
  }
  if (!content) {
    ElMessage.warning('消息内容不能为空')
    return
  }

  sendLoading.value = true
  try {
    await dmApi.send(activePeerId.value, content)
    draft.value = ''
    await Promise.all([loadMessages(true), loadConversations()])
    ElMessage.success('发送成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发送失败')
  } finally {
    sendLoading.value = false
  }
}

const renderPeerName = (item: DirectConversation | null) => {
  if (!item) {
    return activePeerName.value || (activePeerId.value ? `用户${activePeerId.value.slice(0, 6)}` : '未选择会话')
  }
  return item.peerName || `用户${item.peerId.slice(0, 6)}`
}

const formatConversationTime = (value?: string) => {
  if (!value) return ''
  return timeAgo(value)
}

const formatMessageTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
}

watch(
  () => route.query.peerId,
  () => {
    syncActiveConversationByRoute()
  }
)

watch(
  () => route.query.draft,
  () => {
    applyDraftFromRoute()
  }
)

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.replace('/auth?type=login')
    return
  }

  await loadConversations()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <MainLayout>
    <div class="messages-page">
      <el-card class="messages-shell" shadow="never">
        <div class="conversation-panel">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon><ChatDotRound /></el-icon>
              <span>私信</span>
            </div>
            <el-input
              v-model="conversationKeyword"
              class="search-input"
              size="small"
              placeholder="搜索会话"
              :prefix-icon="Search"
              clearable
            />
          </div>

          <div v-loading="conversationsLoading" class="conversation-list">
            <div
              v-for="item in filteredConversations"
              :key="item.conversationId"
              class="conversation-item"
              :class="{ active: item.peerId === activePeerId }"
              @click="selectConversation(item)"
            >
              <el-avatar :size="40" :src="item.peerAvatar">
                {{ item.peerName?.charAt(0) || 'U' }}
              </el-avatar>
              <div class="conversation-main">
                <div class="conversation-top">
                  <span class="peer-name">{{ item.peerName }}</span>
                  <span class="time">{{ formatConversationTime(item.lastTime) }}</span>
                </div>
                <div class="conversation-bottom">
                  <span class="preview">{{ item.lastMessage || '点击开始聊天' }}</span>
                  <el-badge
                    v-if="item.unreadCount > 0"
                    :value="item.unreadCount > 99 ? '99+' : item.unreadCount"
                    class="unread-badge"
                  />
                </div>
              </div>
            </div>

            <el-empty v-if="!conversationsLoading && filteredConversations.length === 0" description="暂无会话" :image-size="70" />
          </div>
        </div>

        <div class="message-panel">
          <template v-if="activePeerId">
            <div class="message-header">
              <div class="target-name">{{ renderPeerName(activeConversation) }}</div>
              <div class="target-desc">文明交流，友好沟通</div>
            </div>

            <div class="message-body">
              <div class="message-toolbar">
                <el-button v-if="hasMoreMessages && !messageLoading" text @click="loadMessages(false)">
                  加载更早消息
                </el-button>
                <span v-else-if="messageLoading" class="loading-text">
                  <el-icon class="is-loading"><Loading /></el-icon> 正在加载
                </span>
                <span v-else class="loading-text">没有更多消息了</span>
              </div>

              <div class="message-list">
                <div v-for="msg in orderedMessages" :key="msg.id" class="message-row" :class="{ self: msg.self }">
                  <div class="bubble">
                    <div class="content">{{ msg.content }}</div>
                    <div class="meta">{{ formatMessageTime(msg.createdAt) }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="message-editor">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="输入消息，按 Enter 发送，Shift + Enter 换行"
                @keydown.enter.exact.prevent="handleSend"
              />
              <div class="editor-actions">
                <el-button type="primary" :icon="Promotion" :loading="sendLoading" @click="handleSend">发送</el-button>
              </div>
            </div>
          </template>

          <div v-else class="empty-panel">
            <el-empty description="请选择一个会话开始聊天" />
          </div>
        </div>
      </el-card>
    </div>
  </MainLayout>
</template>

<style scoped>
.messages-page {
  max-width: 1100px;
  margin: 0 auto;
}

.messages-shell {
  min-height: 680px;
  padding: 0;
}

.messages-shell :deep(.el-card__body) {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  min-height: 680px;
  padding: 0;
}

.conversation-panel {
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  min-height: 680px;
}

.panel-header {
  padding: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.conversation-item {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  transition: background-color 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.conversation-item:hover {
  background: var(--el-fill-color-light);
}

.conversation-item.active {
  background: var(--el-color-primary-light-9);
}

.conversation-main {
  flex: 1;
  min-width: 0;
}

.conversation-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 4px;
}

.peer-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}

.conversation-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.preview {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.message-panel {
  display: flex;
  flex-direction: column;
  min-height: 680px;
}

.message-header {
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.target-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.target-desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.message-toolbar {
  padding: 8px 18px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.loading-text {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-row {
  display: flex;
  justify-content: flex-start;
}

.message-row.self {
  justify-content: flex-end;
}

.bubble {
  max-width: min(72%, 560px);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.message-row.self .bubble {
  background: var(--el-color-primary-light-8);
  border-color: var(--el-color-primary-light-7);
}

.content {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.meta {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  text-align: right;
}

.message-editor {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 12px 16px;
}

.editor-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.empty-panel {
  flex: 1;
  display: grid;
  place-items: center;
}

@media (max-width: 900px) {
  .messages-shell :deep(.el-card__body) {
    grid-template-columns: 1fr;
  }

  .conversation-panel {
    min-height: 320px;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .message-panel {
    min-height: 460px;
  }
}
</style>
