<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, h, defineComponent, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { ChatDotRound, Loading, Promotion, Search, ArrowLeft } from '@element-plus/icons-vue'
import MainLayout from '@/layouts/MainLayout.vue'
import { dmApi, type DirectConversation, type DirectMessage } from '@/api/dm'
import { postApi } from '@/api/post'
import { timeAgo } from '@/utils/timeAgo'
import { decodePostId, encodePostId } from '@/utils/shortId'
import { useUserStore } from '@/store/user'
import { wsClient } from '@/utils/websocket'
import UserBadge from '@/components/common/UserBadge.vue'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'

// 1. 帖子分享嵌入卡片子组件 (TS Render Function, 高兼容)
const PostEmbedCard = defineComponent({
  props: {
    postId: {
      type: String,
      required: true
    }
  },
  setup(props) {
    const router = useRouter()
    const postData = ref<any>(null)
    const loading = ref(false)

    onMounted(async () => {
      if (!props.postId) return
      loading.value = true
      try {
        const res = await postApi.getDetail(props.postId)
        postData.value = res.data || res
      } catch (e) {
        console.error('加载分享帖子失败:', e)
      } finally {
        loading.value = false
      }
    })

    return () => {
      if (loading.value) {
        return h('div', { style: 'padding: 8px; font-size: 11px; color: var(--el-text-color-placeholder); display: flex; align-items: center; gap: 4px;' }, [
          h('span', '正在解析分享的帖子...')
        ])
      }
      if (!postData.value) {
        return h('div', { style: 'padding: 8px; font-size: 11px; color: var(--el-color-danger);' }, '⚠️ 帖子已被删除或无法加载')
      }

      return h('a', {
        href: `/t/${encodePostId(props.postId)}`,
        class: 'dm-post-card',
        onClick: (e: MouseEvent) => {
          e.preventDefault()
          router.push(`/t/${encodePostId(props.postId)}`)
        }
      }, [
        h('div', { class: 'dm-post-card-header' }, [
          h('img', {
            src: resolvePublicAssetUrl(postData.value.authorAvatar) || 'https://api.dicebear.com/7.x/identicon/svg?seed=' + postData.value.authorName,
            style: 'width: 18px; height: 18px; border-radius: 50%; object-fit: cover;'
          }),
          h('span', { class: 'dm-post-card-author', style: 'margin-left: 6px; font-weight: 600; font-size: 11px; color: var(--el-text-color-regular);' }, postData.value.authorNickname || postData.value.authorName || '社区用户'),
          h('span', { class: 'dm-post-card-section' }, postData.value.sectionName || '分享交流')
        ]),
        h('div', { class: 'dm-post-card-title' }, postData.value.title),
        h('div', { class: 'dm-post-card-body' }, postData.value.summary || postData.value.content || '暂无概要')
      ])
    }
  }
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const conversations = ref<DirectConversation[]>([])
const conversationsLoading = ref(false)
const conversationKeyword = ref('')
const activePeerId = ref('')
const activePeerName = ref('')
const activePeerAvatar = ref('')

const messages = ref<DirectMessage[]>([])
const messagePage = ref(1)
const messageTotal = ref(0)
const messageLoading = ref(false)
const hasMoreMessages = ref(true)
const sendLoading = ref(false)
const draft = ref('')

// 状态控制
const showEmojiPicker = ref(false)
const activePeerTyping = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const messageScrollTop = ref(0)
const messageViewportHeight = ref(640)
const VIRTUAL_MESSAGE_THRESHOLD = 120
const ESTIMATED_MESSAGE_HEIGHT = 92
const VIRTUAL_OVERSCAN = 8

// 常用表情包列表
const emojis = ['🎉', '👏', '🔥', '👀', '❤️', '🥺', '✨', '😊', '👍', '🌟', '🤣', '😎', '💡', '🎯', '🙌', '🎂']

let pollTimer: any = null
let unsubscribeMessagesWs: (() => void) | null = null

// 触底检测函数
const isCloseToBottom = () => {
  if (!messageListRef.value) return true
  const threshold = 150
  const { scrollHeight, scrollTop, clientHeight } = messageListRef.value
  return (scrollHeight - scrollTop - clientHeight) <= threshold
}

// 智能滚动机制 (带延迟双保险，防止头像、图片或帖子卡片异步加载时高度变化导致错位)
const scrollToBottom = (smooth = true) => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTo({
        top: messageListRef.value.scrollHeight,
        behavior: smooth ? 'smooth' : 'auto'
      })
    }
    // 120ms 延时兜底二次触底，防异步 DOM 加载
    setTimeout(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTo({
          top: messageListRef.value.scrollHeight,
          behavior: smooth ? 'smooth' : 'auto'
        })
      }
    }, 120)
  })
}

// 增量式消息加载：防闪烁
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
  }, 35000)
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
const getPeerAvatar = (value?: string | null) => resolvePublicAssetUrl(value)
const activePeerAvatarUrl = computed(() =>
  getPeerAvatar(activeConversation.value?.peerAvatar || activePeerAvatar.value)
)

const orderedMessages = computed(() => {
  return [...messages.value].reverse()
})

const useVirtualMessages = computed(() => orderedMessages.value.length > VIRTUAL_MESSAGE_THRESHOLD)

const virtualRange = computed(() => {
  const total = orderedMessages.value.length
  if (!useVirtualMessages.value) {
    return { start: 0, end: total, top: 0, bottom: 0 }
  }
  const start = Math.max(0, Math.floor(messageScrollTop.value / ESTIMATED_MESSAGE_HEIGHT) - VIRTUAL_OVERSCAN)
  const visibleCount = Math.ceil(messageViewportHeight.value / ESTIMATED_MESSAGE_HEIGHT) + VIRTUAL_OVERSCAN * 2
  const end = Math.min(total, start + visibleCount)
  return {
    start,
    end,
    top: start * ESTIMATED_MESSAGE_HEIGHT,
    bottom: Math.max(0, total - end) * ESTIMATED_MESSAGE_HEIGHT,
  }
})

const renderedMessages = computed(() => {
  const range = virtualRange.value
  return orderedMessages.value.slice(range.start, range.end)
})

const handleMessageScroll = () => {
  const el = messageListRef.value
  if (!el) return
  messageScrollTop.value = el.scrollTop
  messageViewportHeight.value = el.clientHeight || messageViewportHeight.value
}

const receiptLabel = (msg: DirectMessage) => {
  if (!msg.self) return ''
  return msg.readReceipt === 'READ' || msg.isRead === 1 ? '已读' : '已送达'
}

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
  const peerAvatar = String(route.query.peerAvatar || '')

  if (peerId) {
    const shouldReload = activePeerId.value !== peerId || messages.value.length === 0
    activePeerId.value = peerId
    if (peerName) activePeerName.value = peerName
    if (peerAvatar) activePeerAvatar.value = peerAvatar

    const existed = conversations.value.some(item => item.peerId === peerId)
    if (!existed) {
      conversations.value.unshift({
        conversationId: `temp:${peerId}`,
        peerId,
        peerName: peerName || `用户${peerId.slice(0, 6)}`,
        peerAvatar: peerAvatar || '',
        lastMessage: '',
        lastTime: new Date().toISOString(),
        unreadCount: 0,
      })
    }
    if (shouldReload) {
      loadMessages(true)
      showTypingIndicator()
    }
    applyDraftFromRoute()
    return
  }

  if (activePeerId.value) return

  const first = conversations.value[0]
  if (first) {
    activePeerId.value = first.peerId
    activePeerName.value = first.peerName
    activePeerAvatar.value = first.peerAvatar || ''
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
  const prevScrollHeight = messageListRef.value?.scrollHeight || 0

  try {
    const res = await dmApi.getMessages(activePeerId.value, messagePage.value, 30)
    const records = res.data?.records || []
    messageTotal.value = res.data?.total || 0

    if (records.length > 0) {
      messages.value.push(...records)
      messagePage.value += 1
      
      // 保持滚动高度，防跳动
      nextTick(() => {
        if (messageListRef.value && !reset) {
          const newScrollHeight = messageListRef.value.scrollHeight
          messageListRef.value.scrollTop = newScrollHeight - prevScrollHeight
          handleMessageScroll()
        }
      })
    } else {
      hasMoreMessages.value = false
    }

    if (messages.value.length >= messageTotal.value) {
      hasMoreMessages.value = false
    }

    if (reset) {
      scrollToBottom(false)
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

const goBackToList = () => {
  activePeerId.value = ''
  router.replace({ path: '/messages', query: {} })
}

const selectConversation = (item: DirectConversation) => {
  if (!item.peerId || item.peerId === activePeerId.value) return
  activePeerId.value = item.peerId
  activePeerName.value = item.peerName
  activePeerAvatar.value = item.peerAvatar || ''
  
  if (String(route.query.peerId || '') === item.peerId) {
    loadMessages(true)
    showTypingIndicator()
    return
  }
  router.replace({
    path: '/messages',
    query: { peerId: item.peerId, peerName: item.peerName, peerAvatar: item.peerAvatar },
  })
}

// 极其丝滑的增量式消息发送，彻底规避页面闪烁
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
  
  // 1. 本地立即增量式追加（秒弹效果，流畅度飙升）
  const tempMsg: DirectMessage = {
    id: 'temp-' + Date.now(),
    senderId: userStore.userId || '',
    receiverId: activePeerId.value,
    content: content,
    isRead: 1,
    readReceipt: 'DELIVERED',
    createdAt: new Date().toISOString(),
    self: true
  }
  messages.value.unshift(tempMsg)
  scrollToBottom(true)
  draft.value = ''
  showEmojiPicker.value = false

  try {
    await dmApi.send(activePeerId.value, content)
    
    // 2. 本地同步会话卡片最后一句话
    conversations.value = conversations.value.map(c => {
      if (c.peerId === activePeerId.value) {
        return { ...c, lastMessage: content, lastTime: new Date().toISOString() }
      }
      return c
    })
    
    // 3. 全局高质感飞信送达 Toast 提醒
    ElNotification({
      customClass: 'zens-premium-notif notif-success',
      duration: 3000,
      position: 'bottom-right',
      message: h('div', { style: 'display: flex; align-items: center; gap: 12px;' }, [
        h('div', {
          style: 'font-size: 22px; padding: 6px; background: var(--el-color-primary-light-9); border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--el-color-primary);'
        }, '✉️'),
        h('div', { style: 'flex: 1;' }, [
          h('div', { style: 'font-size: 13px; font-weight: 700; color: var(--el-text-color-primary);' }, '私信投递成功'),
          h('div', { style: 'font-size: 11px; color: var(--el-text-color-secondary); margin-top: 1px;' }, '消息已通过实时通道送达')
        ])
      ])
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '发送失败')
    // 失败回退逻辑：从数组移出
    messages.value = messages.value.filter(m => m.id !== tempMsg.id)
  } finally {
    sendLoading.value = false
  }
}

// 智能社交解析与工具函数
const isEmojiOnly = (content: string) => {
  const emojiRegex = /^[\s\p{Emoji_Presentation}\p{Emoji}\uFE0F]{1,9}$/u
  const stripped = content.trim().replace(/\s+/g, '')
  if (!stripped) return false
  const match = emojiRegex.test(stripped)
  const count = [...stripped].length
  return match && count <= 3
}

const selectEmoji = (emoji: string) => {
  draft.value += emoji
}

// 模拟正在输入反馈
const showTypingIndicator = () => {
  activePeerTyping.value = true
  setTimeout(() => {
    activePeerTyping.value = false
  }, 1600)
}

// 智能时间聚合合并（5分钟）
const shouldShowTime = (msg: DirectMessage, index: number) => {
  if (index === 0) return true
  const prev = orderedMessages.value[index - 1]
  if (!prev) return true
  const diff = new Date(msg.createdAt).getTime() - new Date(prev.createdAt).getTime()
  return diff > 5 * 60 * 1000 // 5分钟
}

// 帖子卡片探测
const hasPostEmbed = (content: string) => {
  return content.includes('[post:') || content.includes('/t/')
}

const getPostEmbedId = (content: string) => {
  const match1 = content.match(/\[post:([a-zA-Z0-9_-]+)\]/)
  if (match1) {
    const id = match1[1] || ''
    return (id.startsWith('p') ? decodePostId(id) : id) || ''
  }
  const match2 = content.match(/\/t\/([a-zA-Z0-9_-]+)/)
  if (match2) {
    const id = match2[1] || ''
    return (id.startsWith('p') ? decodePostId(id) : id) || ''
  }
  return ''
}

const getNonEmbedContent = (content: string) => {
  return content.replace(/\[post:[a-zA-Z0-9_-]+\]/g, '').trim()
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

// 侦听器
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

// STOMP WebSocket 实时订阅，告别低效轮询
const subscribeToMessagesWs = () => {
  if (!userStore.isLoggedIn || !userStore.userId) return

  if (unsubscribeMessagesWs) {
    unsubscribeMessagesWs()
  }

  // 监听后端发送的私信主题
  unsubscribeMessagesWs = wsClient.subscribe(`/user/${userStore.userId}/queue/messages`, (event: any) => {
    if (event?.type === 'message-read') {
      if (event.readerId === activePeerId.value) {
        messages.value = messages.value.map(msg =>
          msg.self ? { ...msg, isRead: 1, readReceipt: 'READ' } : msg
        )
      }
      return
    }

    const newMsg: DirectMessage = event
    
    // 如果是当前正在聊天的人发送的，直接追加并滚动
    if (newMsg.senderId === activePeerId.value) {
      messages.value.unshift({
        ...newMsg,
        self: false
      })
      if (isCloseToBottom()) {
        scrollToBottom(true)
      }
      showTypingIndicator()
      dmApi.markRead(activePeerId.value).catch(() => {})
    } else {
      // 否则，为其他会话卡片未读数加一，并置顶
      const existed = conversations.value.find(c => c.peerId === newMsg.senderId)
      if (existed) {
        existed.unreadCount++
        existed.lastMessage = newMsg.content
        existed.lastTime = newMsg.createdAt
        conversations.value = [existed, ...conversations.value.filter(c => c.peerId !== newMsg.senderId)]
      } else {
        loadConversations().catch(() => {})
      }
    }
  })
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.replace('/auth?type=login')
    return
  }

  // 极致体验：强制复位窗口滚动位置，防止从其他长页面跳转过来时残留滚动高度导致输入框被截断
  window.scrollTo(0, 0)
  if (document.body) document.body.scrollTop = 0
  if (document.documentElement) document.documentElement.scrollTop = 0

  // 锁死 body 与 html 滚动条，形成媲美 Slack/Discord 的固定视口桌面级体验
  document.body.style.overflow = 'hidden'
  document.documentElement.style.overflow = 'hidden'

  await loadConversations()
  subscribeToMessagesWs()
  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  handleMessageScroll()
  scrollToBottom(false)
})

onUnmounted(() => {
  // 恢复全局滚动条
  document.body.style.overflow = ''
  document.documentElement.style.overflow = ''
  
  stopPolling()
  if (unsubscribeMessagesWs) {
    unsubscribeMessagesWs()
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <MainLayout>
    <div class="messages-page">
      <el-card class="messages-shell" shadow="never">
        <div class="conversation-panel" :class="{ 'hidden-mobile': activePeerId }">
          <div class="panel-header">
            <div class="panel-title">
              <el-icon><ChatDotRound /></el-icon>
              <span>站内私信</span>
            </div>
            <el-input
              v-model="conversationKeyword"
              class="search-input"
              size="small"
              placeholder="搜索联系人..."
              :prefix-icon="Search"
              clearable
            />
          </div>

          <div class="conversation-list">
            <!-- Song：会话列表骨架屏 -->
            <template v-if="conversationsLoading && filteredConversations.length === 0">
              <el-skeleton
                v-for="i in 6"
                :key="'conv-skel-' + i"
                animated
                class="conv-skel"
              >
                <template #template>
                  <div class="conv-skel-row">
                    <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
                    <div class="conv-skel-text">
                      <el-skeleton-item variant="text" style="width: 50%" />
                      <el-skeleton-item variant="text" style="width: 80%" />
                    </div>
                  </div>
                </template>
              </el-skeleton>
            </template>

            <div
              v-for="item in filteredConversations"
              :key="item.conversationId"
              class="conversation-item"
              :class="{ active: item.peerId === activePeerId }"
              @click="selectConversation(item)"
            >
              <div class="avatar-container-relative">
                <el-avatar :size="40" :src="getPeerAvatar(item.peerAvatar)">
                  {{ item.peerName?.charAt(0) || 'U' }}
                </el-avatar>
                <!-- 在线状态呼吸灯 -->
                <span class="status-breath-dot online"></span>
              </div>
              <div class="conversation-main">
                <div class="conversation-top">
                  <span class="peer-name">{{ item.peerName }}</span>
                  <UserBadge :text="item.peerBadgeText || ''" :color="item.peerBadgeColor" :effect="item.peerBadgeStyle" />
                  <span class="time">{{ formatConversationTime(item.lastTime) }}</span>
                </div>
                <div class="conversation-bottom">
                  <span class="preview">{{ item.lastMessage || '点击开始聊点什么吧...' }}</span>
                  <el-badge
                    v-if="item.unreadCount > 0"
                    :value="item.unreadCount > 99 ? '99+' : item.unreadCount"
                    class="unread-badge"
                  />
                </div>
              </div>
            </div>

            <el-empty v-if="!conversationsLoading && filteredConversations.length === 0" description="暂无活动会话" :image-size="70" />
          </div>
        </div>

        <div class="message-panel chat-container flex flex-col h-full min-h-0" :class="{ 'hidden-mobile': !activePeerId }">
          <template v-if="activePeerId">
            <div class="chat-header message-header shrink-0">
              <div style="display: flex; align-items: center; gap: 10px;">
                <el-button
                  class="mobile-back-btn"
                  circle
                  text
                  :icon="ArrowLeft"
                  @click="goBackToList"
                  style="margin-right: 4px; font-size: 18px;"
                />
                <div class="avatar-container-relative">
                  <el-avatar :size="38" :src="activePeerAvatarUrl">
                    {{ renderPeerName(activeConversation).charAt(0) }}
                  </el-avatar>
                  <span class="status-breath-dot" :class="activePeerTyping ? 'typing' : 'online'"></span>
                </div>
                <div>
                  <div class="target-name">
                    {{ renderPeerName(activeConversation) }}
                    <UserBadge :text="activeConversation?.peerBadgeText || ''" :color="activeConversation?.peerBadgeColor" :effect="activeConversation?.peerBadgeStyle" />
                    <span v-if="activePeerTyping" class="typing-label" style="font-size: 11px; font-weight: normal; color: #8b5cf6; margin-left: 6px;">对方正在输入...</span>
                  </div>
                  <div class="target-desc">文明交流，友好沟通 • 绿色实时安全信道已启动</div>
                </div>
              </div>
            </div>

            <!-- 中间独立滚动的消息列表 -->
            <div class="message-list flex-1 overflow-y-auto min-h-0" ref="messageListRef" @scroll="handleMessageScroll">
              <div class="message-toolbar">
                <el-button v-if="hasMoreMessages && !messageLoading" text @click="loadMessages(false)" class="load-more-btn">
                  加载更早消息
                </el-button>
                <span v-else-if="messageLoading" class="loading-text">
                  <el-icon class="is-loading"><Loading /></el-icon> 正在追溯历史消息
                </span>
                <span v-else class="loading-text">已加载全部历史记录</span>
              </div>

              <!-- 智能群组消息列表 -->
              <div v-if="virtualRange.top > 0" class="virtual-spacer" :style="{ height: `${virtualRange.top}px` }"></div>

              <template v-for="(msg, idx) in renderedMessages" :key="msg.id">
                  <!-- 智能时间合并 (5分钟内自动合并) -->
                  <div v-if="shouldShowTime(msg, virtualRange.start + idx)" class="timeline-divider">
                    <span>{{ formatMessageTime(msg.createdAt) }}</span>
                  </div>

                  <div class="message-row" :class="{ self: msg.self }">
                    <el-avatar v-if="!msg.self" :size="36" :src="activePeerAvatarUrl" class="chat-peer-avatar">
                      {{ renderPeerName(activeConversation).charAt(0) }}
                    </el-avatar>
                    
                    <div class="bubble">
                      <!-- 1-3个纯表情大字体渲染 -->
                      <div v-if="isEmojiOnly(msg.content)" class="emoji-only-content">
                        {{ msg.content }}
                      </div>
                      <div v-else>
                        <div class="content">{{ getNonEmbedContent(msg.content) }}</div>
                        <!-- 如果检测到引用社区帖子链接，渲染嵌入分享卡片 -->
                        <div v-if="hasPostEmbed(msg.content)" class="dm-post-card-wrap">
                          <PostEmbedCard :post-id="getPostEmbedId(msg.content)" />
                        </div>
                      </div>
                      <div class="meta">
                        <span>{{ formatMessageTime(msg.createdAt) }}</span>
                        <span v-if="msg.self" class="receipt">{{ receiptLabel(msg) }}</span>
                      </div>
                    </div>
                  </div>
                </template>

                <div v-if="virtualRange.bottom > 0" class="virtual-spacer" :style="{ height: `${virtualRange.bottom}px` }"></div>

                <!-- 正在输入状态动画 -->
                <div v-if="activePeerTyping" class="message-row">
                  <el-avatar :size="36" :src="activePeerAvatarUrl" class="chat-peer-avatar">
                    {{ renderPeerName(activeConversation).charAt(0) }}
                  </el-avatar>
                  <div class="bubble" style="padding: 12px 18px; display: flex; align-items: center; gap: 4px; background: rgba(255,255,255,0.5); backdrop-filter: blur(10px); border-radius: 20px 20px 20px 4px;">
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                  </div>
                </div>
              </div>

            <!-- 私信编辑器区域 -->
            <div class="chat-input message-editor shrink-0">
              <div class="editor-toolbar" style="padding-bottom: 8px; display: flex; align-items: center; gap: 8px;">
                <!-- 快捷表情抽屉 -->
                <el-popover
                  v-model:visible="showEmojiPicker"
                  placement="top-start"
                  :width="260"
                  trigger="click"
                  popper-class="emoji-popover"
                >
                  <template #reference>
                    <el-button circle text class="emoji-btn-trigger" style="font-size: 18px; padding: 4px; background: transparent; border: none; cursor: pointer;">😊</el-button>
                  </template>
                  <div class="emoji-picker-content" style="display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; padding: 8px;">
                    <span
                      v-for="emoji in emojis"
                      :key="emoji"
                      class="emoji-item"
                      style="font-size: 20px; text-align: center; cursor: pointer; padding: 4px; border-radius: 6px; transition: background 0.15s ease;"
                      @click="selectEmoji(emoji)"
                    >
                      {{ emoji }}
                    </span>
                  </div>
                </el-popover>
              </div>

              <el-input
                v-model="draft"
                type="textarea"
                :rows="3"
                maxlength="1000"
                show-word-limit
                placeholder="键入消息... 按 Enter 发送，Shift + Enter 换行"
                @keydown.enter.exact.prevent="handleSend"
              />
              <div class="editor-actions">
                <el-button type="primary" :icon="Promotion" :loading="sendLoading" @click="handleSend" class="send-btn">发送</el-button>
              </div>
            </div>
          </template>

          <div v-else class="empty-panel">
            <el-empty description="选择左侧的联系人，即刻开始安全加密私信" />
          </div>
        </div>
      </el-card>
    </div>
  </MainLayout>
</template>

<style scoped>
/* 精准模拟 Tailwind CSS Flex 布局属性 */
.flex { display: flex; }
.flex-col { flex-direction: column; }
.h-full { height: 100%; }
.min-h-0 { min-height: 0; }
.flex-1 { flex: 1 1 0%; }
.shrink-0 { flex-shrink: 0; }
.overflow-y-auto { overflow-y: auto; }
.items-center { align-items: center; }
.justify-between { justify-content: space-between; }

.messages-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 12px;
  height: calc(100vh - var(--header-height, 56px) - 24px);
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  position: relative;
  min-height: 0;
}

.messages-shell {
  height: 100%;
  min-height: 0;
  padding: 0;
  background: rgba(255, 255, 255, 0.5) !important;
  backdrop-filter: blur(20px) saturate(140%) !important;
  border: 1px solid rgba(255, 255, 255, 0.5) !important;
  border-radius: 20px !important;
  box-shadow: 0 20px 45px rgba(0, 0, 0, 0.04) !important;
  overflow: hidden;
}

html[data-mode="dark"] .messages-shell,
html.dark .messages-shell {
  background: rgba(20, 22, 34, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.05) !important;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.35) !important;
}

.messages-shell :deep(.el-card__body) {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  padding: 0;
}

.conversation-panel {
  border-right: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: rgba(255, 255, 255, 0.15);
}

html[data-mode="dark"] .conversation-panel,
html.dark .conversation-panel {
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.1);
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

html[data-mode="dark"] .panel-header,
html.dark .panel-header {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.5px;
  color: var(--el-text-color-primary);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.conv-skel {
  padding: 6px 14px;
}

.conv-skel-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conv-skel-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.conversation-item {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  border-bottom: 1px solid rgba(0, 0, 0, 0.02);
  align-items: center;
}

html[data-mode="dark"] .conversation-item,
html.dark .conversation-item {
  border-bottom: 1px solid rgba(255, 255, 255, 0.02);
}

.conversation-item:hover {
  background: rgba(255, 255, 255, 0.4);
  transform: translate3d(2px, 0, 0);
}

html[data-mode="dark"] .conversation-item:hover,
html.dark .conversation-item:hover {
  background: rgba(255, 255, 255, 0.03);
}

.conversation-item.active {
  background: var(--el-color-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
}

html[data-mode="dark"] .conversation-item.active,
html.dark .conversation-item.active {
  background: rgba(99, 102, 241, 0.15) !important;
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
  font-size: 13.5px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.time {
  font-size: 11px;
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
  height: 100%;
  min-height: 0;
}

.message-header {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  background: rgba(255, 255, 255, 0.15);
}

html[data-mode="dark"] .message-header,
html.dark .message-header {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.1);
}

.target-name {
  font-size: 15px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
}

.target-desc {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
}

/* removed message-body */

.message-toolbar {
  padding: 6px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.02);
  display: flex;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
}

.load-more-btn {
  font-size: 11px;
  color: var(--el-color-primary);
  font-weight: 600;
}

.loading-text {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  scrollbar-width: thin;
  background: rgba(255, 255, 255, 0.05);
}

.chat-peer-avatar {
  margin-right: 8px;
  align-self: flex-end;
  margin-bottom: 4px;
}

.timeline-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 10px 0;
  user-select: none;
}

.timeline-divider span {
  font-size: 10.5px;
  font-weight: 700;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--el-text-color-secondary); /* 更暗/清晰的颜色 */
  border-radius: 99px;
  letter-spacing: 0.5px;
  backdrop-filter: blur(10px);
}

html[data-mode="dark"] .timeline-divider span,
html.dark .timeline-divider span {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.85) !important; /* 暗色模式下更高对比度 */
}

.content {
  font-size: 13.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.virtual-spacer {
  flex: 0 0 auto;
  pointer-events: none;
}

.emoji-only-content {
  font-size: clamp(28px, 4.5vw, 40px);
  line-height: 1.12;
  letter-spacing: 0;
  word-break: normal;
  overflow-wrap: anywhere;
  max-width: 180px;
}

.meta {
  margin-top: 4px;
  font-size: 10px;
  opacity: 0.85;
  text-align: right;
  user-select: none;
  color: var(--el-text-color-secondary); /* 显著提升可读性 */
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.receipt {
  color: var(--el-color-primary);
}

.message-row.self .meta {
  color: rgba(255, 255, 255, 0.82);
}

.message-row.self .receipt {
  color: rgba(255, 255, 255, 0.96);
  font-weight: 700;
}

.message-editor {
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.15);
}

html[data-mode="dark"] .message-editor,
html.dark .message-editor {
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(0, 0, 0, 0.1);
}

.message-editor :deep(.el-textarea__inner) {
  border-radius: 12px !important;
  background: rgba(255, 255, 255, 0.6) !important;
  border: 1px solid rgba(0, 0, 0, 0.06) !important;
  padding: 10px 14px;
  font-size: 13.5px;
  transition: all 0.25s ease;
  resize: none;
}

html[data-mode="dark"] .message-editor :deep(.el-textarea__inner),
html.dark .message-editor :deep(.el-textarea__inner) {
  background: rgba(20, 22, 34, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.05) !important;
}

.editor-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.send-btn {
  border-radius: 99px;
  padding: 10px 22px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
}

.empty-panel {
  flex: 1;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.05);
}

.emoji-btn-trigger:hover {
  background: rgba(255, 255, 255, 0.1) !important;
}

.emoji-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

/* 帖子卡片包裹区 */
.dm-post-card-wrap {
  margin-top: 6px;
  max-width: 320px;
}

.mobile-back-btn {
  display: none;
}

@media (max-width: 900px) {
  .hidden-mobile {
    display: none !important;
  }

  .messages-page {
    position: relative !important;
    height: calc(100vh - var(--header-height, 56px) - 56px - 16px) !important;
    padding: 8px;
  }
  .messages-shell {
    height: 100% !important;
    max-height: none !important;
  }
  .messages-shell :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    height: 100% !important;
    min-height: 0;
  }

  .conversation-panel {
    height: 100% !important;
    min-height: 0;
    border-right: none;
    width: 100%;
  }

  .message-panel {
    height: 100% !important;
    min-height: 0;
    width: 100%;
  }

  .emoji-only-content {
    font-size: clamp(24px, 8vw, 34px);
    max-width: 140px;
  }
  
  .mobile-back-btn {
    display: inline-flex !important;
  }
}
</style>
