<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataLine, Finished, Loading, Refresh } from '@element-plus/icons-vue'
import TopicFilters from './TopicFilters.vue'
import TopicRow from './TopicRow.vue'
import { postApi } from '@/api/post'
import type { PostSearchRequest } from '@/types'
import { wsClient, type PostEvent } from '@/utils/websocket'
import { isTruthyFlag } from '@/utils/flags'

const PulseDashboardDialog = defineAsyncComponent(() => import('./PulseDashboardDialog.vue'))

type PostMetricsUpdate = {
  postId: string
  viewCount?: number | string | null
  commentCount?: number | string | null
  lastActivityAt?: string | null
  hasAdoptedAnswer?: number | string | boolean | null
}

const props = defineProps<{
  defaultQuery?: PostSearchRequest
  hideFilters?: boolean
  hideCategories?: boolean
}>()

const route = useRoute()

const topics = ref<any[]>([])
const page = ref(1)
const initialPageSize = 8
const pageSize = 15
const firstBatchLoaded = ref(false)
const cursor = ref<string | null>(null)
const cursorId = ref<string | null>(null)
const loading = ref(false)
const noMore = ref(false)

type NavType = 'latest' | 'hot' | 'essence'

const activeNav = ref<NavType>('latest')
const activeCategory = ref('all')

const hasNewContent = ref(false)
const newContentCount = ref(0)
let newContentTimer: any = null
let wsUnsubscribe: (() => void) | null = null
let realtimeInitialized = false
let fetchDocumentsToken = 0
const POST_METRICS_UPDATED_EVENT = 'cp:post-metrics-updated'

const loadTrigger = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
const INITIAL_SKELETON_COUNT = 5

const isFirstLoading = computed(() => loading.value && topics.value.length === 0)

const getCurrentPageSize = () => (firstBatchLoaded.value ? pageSize : initialPageSize)

const resolveNavType = (): NavType => {
  const defaultQuery = props.defaultQuery || {}
  const requestedNav = String(defaultQuery.navType || '').toLowerCase()

  if (requestedNav === 'latest' || requestedNav === 'hot' || requestedNav === 'essence') {
    return requestedNav as NavType
  }
  if (Boolean(defaultQuery.isFeatured)) return 'essence'
  if (defaultQuery.orderBy === 'hot') return 'hot'
  if (route.query.sort === 'hot' || route.query.sort === 'top') return 'hot'
  if (route.query.sort === 'essence' || route.query.sort === 'featured') return 'essence'
  if (route.query.sort === 'latest') return 'latest'

  return activeNav.value
}

const resolveOrderBy = (navType = resolveNavType()): 'new' | 'hot' => {
  return navType === 'hot' ? 'hot' : 'new'
}

const resolveCategory = () => {
  const defaultQuery = props.defaultQuery || {}
  const defaultCategory = defaultQuery.category

  if (defaultCategory !== undefined && defaultCategory !== null && `${defaultCategory}`.trim() !== '') {
    return `${defaultCategory}`
  }
  if (defaultQuery.sectionId !== undefined && defaultQuery.sectionId !== null) {
    return `${defaultQuery.sectionId}`
  }

  return props.hideCategories ? 'all' : activeCategory.value
}

const toFiniteMetric = (value: unknown): number | null => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

const applyTopicMetrics = (postId: string, data?: PostMetricsUpdate | PostEvent['data'], options: { incrementReply?: boolean } = {}) => {
  const index = topics.value.findIndex((item) => String(item.id) === String(postId))
  if (index === -1) return

  const topic = topics.value[index]
  const viewCount = toFiniteMetric(data?.viewCount)
  const commentCount = toFiniteMetric(data?.commentCount)

  if (viewCount !== null) topic.views = viewCount
  if (commentCount !== null) {
    topic.replies = commentCount
  } else if (options.incrementReply) {
    topic.replies = Math.max(0, toFiniteMetric(topic.replies) ?? 0) + 1
  }
  if (data?.lastActivityAt) topic.lastActive = data.lastActivityAt
  if ('hasAdoptedAnswer' in (data || {})) {
    topic.isSolved = isTruthyFlag(data?.hasAdoptedAnswer)
  }
}

const buildSearchReq = (): PostSearchRequest => {
  const navType = resolveNavType()
  const category = resolveCategory()
  const orderBy = resolveOrderBy(navType)
  const req: PostSearchRequest = {
    ...props.defaultQuery,
    page: orderBy === 'new' ? 1 : page.value,
    pageSize: getCurrentPageSize(),
    needTotal: false,
    status: 1,
    orderBy,
    navType,
    category,
  }

  if (orderBy === 'new' && cursor.value) {
    req.cursor = cursor.value
    req.cursorId = cursorId.value || undefined
  }

  return req
}

const mapPost = (p: any) => ({
  id: p.id,
  title: p.title,
  excerpt: p.summary || p.content?.substring(0, 120) || '',
  category: {
    name: p.sectionName || '未分类',
    color: '#409EFF',
  },
  tags: p.tags ? p.tags.split(',').filter(Boolean) : [],
  author: {
    id: p.userId,
    username: p.authorUsername || '',
    name: p.authorName || '匿名',
    avatar: p.authorAvatar || '',
    roles: p.authorRoles || [],
    badgeText: p.authorBadgeText || '',
    badgeColor: p.authorBadgeColor || '',
    badgeStyle: p.authorBadgeStyle || 'solid',
  },
  createdAt: p.createTime,
  lastActive: p.lastActivityAt || p.createTime,
  replies: toFiniteMetric(p.commentCount) ?? 0,
  views: toFiniteMetric(p.viewCount) ?? 0,
  heatScore: p.heatScore,
  isPinned: p.isPinned === 1,
  isFeatured: p.isFeatured === 1,
  isSolved: isTruthyFlag(p.hasAdoptedAnswer),
  trendLevel: p.trendLevel,
  sentimentLabel: p.sentimentLabel,
})

const pulseInsight = computed(() => {
  if (!topics.value.length) return ''

  const sample = topics.value.slice(0, 20)
  const hotCount = sample.filter((item) => `${item.trendLevel || ''}`.toLowerCase() === 'hot').length
  const positiveCount = sample.filter((item) => `${item.sentimentLabel || ''}`.toUpperCase() === 'POSITIVE').length
  const totalViews = sample.reduce((acc, item) => acc + (item.views || 0), 0)
  const avgViews = Math.round(totalViews / sample.length)

  const parts: string[] = []
  if (hotCount > 0) parts.push(`${hotCount} 条热度上升`)
  if (positiveCount > 0) parts.push(`正向讨论占比 ${Math.round((positiveCount / sample.length) * 100)}%`)
  parts.push(`平均浏览 ${avgViews}`)

  return `AI 数据脉冲: ${parts.join(' · ')}`
})

// Interactive AI Pulse Dashboard State & Computed Charts
const showPulseDashboard = ref(false)

const resetDocumentState = () => {
  hasNewContent.value = false
  newContentCount.value = 0
  topics.value = []
  page.value = 1
  firstBatchLoaded.value = false
  cursor.value = null
  cursorId.value = null
  noMore.value = false
}

const fetchDocuments = async (reset = false) => {
  if (reset) {
    resetDocumentState()
  } else if (loading.value || noMore.value) {
    return
  }

  const requestToken = ++fetchDocumentsToken
  loading.value = true

  try {
    const currentPageSize = getCurrentPageSize()
    const orderBy = resolveOrderBy()
    const res = await postApi.searchList(buildSearchReq())
    if (requestToken !== fetchDocumentsToken) return

    const records = res.data?.records || []
    if (records.length < currentPageSize) {
      noMore.value = true
    }
    topics.value.push(...records.map(mapPost))
    firstBatchLoaded.value = true

    if (orderBy === 'new') {
      const last = records[records.length - 1]
      if (last?.createTime) {
        cursor.value = last.createTime
        cursorId.value = last.id
      }
    } else {
      page.value++
    }
  } catch {
    if (requestToken === fetchDocumentsToken) {
      ElMessage.error('加载失败，请重试')
    }
  } finally {
    if (requestToken === fetchDocumentsToken) {
      loading.value = false
    }
  }
}

const loadMore = () => fetchDocuments(false)

const refreshLatest = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  fetchDocuments(true)
}

const handleFilterChange = (filter: { navType: NavType; category: string }) => {
  activeNav.value = filter.navType
  activeCategory.value = filter.category || 'all'
  fetchDocuments(true)
}

const handlePostEvent = (event: PostEvent) => {
  if (window.scrollY > 300) {
    if (event.type === 'POST_CREATED' || event.type === 'POST_REPLIED') {
      hasNewContent.value = true
      newContentCount.value++
    }
  }

  if (event.postId) {
    const index = topics.value.findIndex((item) => item.id === event.postId)
    if (index !== -1) {
      const topic = topics.value[index]

      applyTopicMetrics(event.postId, event.data, { incrementReply: event.type === 'POST_REPLIED' })

      if (event.type === 'POST_REPLIED' && resolveNavType() === 'latest') {
        const [movedTopic] = topics.value.splice(index, 1)
        topics.value.unshift(movedTopic)
      }

      if (event.type === 'PIN_UPDATED' && event.data) {
        topic.isPinned = event.data.globalPin === 1 || event.data.categoryPin === 1
      }
    }
  }
}

const handleLocalMetricsUpdate = (event: Event) => {
  const detail = (event as CustomEvent<PostMetricsUpdate>).detail
  if (!detail?.postId) return
  applyTopicMetrics(detail.postId, detail)
}

const stopNewContentPolling = () => {
  if (newContentTimer) {
    clearInterval(newContentTimer)
    newContentTimer = null
  }
}

const startNewContentPolling = () => {
  if (newContentTimer) return

  newContentTimer = setInterval(async () => {
    if (wsClient.isConnected()) return
    if (window.scrollY <= 300 || hasNewContent.value) return
    if (typeof document !== 'undefined' && document.hidden) return
    if (typeof navigator !== 'undefined' && !navigator.onLine) return
    if (resolveNavType() !== 'latest') return

    try {
      const res = await postApi.searchList({
        ...buildSearchReq(),
        page: 1,
        pageSize: 1,
        cursor: undefined,
        cursorId: undefined,
        navType: 'latest',
        orderBy: 'new',
      })
      const latestId = res.data?.records?.[0]?.id
      if (latestId && topics.value.length > 0 && latestId !== topics.value[0]?.id) {
        hasNewContent.value = true
        newContentCount.value = 1
      }
    } catch {
      // Song：说明
    }
  }, 30000)
}

const initRealtimeUpdates = () => {
  if (realtimeInitialized) return
  realtimeInitialized = true

  try {
    if (props.defaultQuery?.sectionId) {
      wsUnsubscribe = wsClient.subscribeSection(props.defaultQuery.sectionId, handlePostEvent)
    } else {
      wsUnsubscribe = wsClient.subscribeGlobal(handlePostEvent)
    }
  } catch (error) {
    console.warn('[TopicList] WebSocket 订阅失败，降级到轮询模式', error)
    startNewContentPolling()
  }
}

const scheduleRealtimeInit = () => {
  const win = window as Window & {
    requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number
  }
  if (typeof win.requestIdleCallback === 'function') {
    win.requestIdleCallback(() => initRealtimeUpdates(), { timeout: 1200 })
    return
  }
  setTimeout(() => initRealtimeUpdates(), 600)
}

watch(() => route.query.sort, () => {
  activeNav.value = resolveNavType()
  refreshLatest()
})

onMounted(() => {
  if (topics.value.length === 0) {
    loadMore().finally(() => scheduleRealtimeInit())
  } else {
    scheduleRealtimeInit()
  }

  startNewContentPolling()
  window.addEventListener(POST_METRICS_UPDATED_EVENT, handleLocalMetricsUpdate)

  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) {
        loadMore()
      }
    },
    { threshold: 0.1 }
  )

  if (loadTrigger.value) {
    observer.observe(loadTrigger.value)
  }
})

onUnmounted(() => {
  if (observer) observer.disconnect()
  window.removeEventListener(POST_METRICS_UPDATED_EVENT, handleLocalMetricsUpdate)
  stopNewContentPolling()
  if (wsUnsubscribe) wsUnsubscribe()
})

defineExpose({
  refreshLatest,
})
</script>

<template>
  <div class="topic-list-container">
    <TopicFilters v-if="!hideFilters" :hide-categories="hideCategories" @filter-change="handleFilterChange" />

    <transition name="el-zoom-in-top">
      <div v-if="hasNewContent" class="new-content-alert" @click="refreshLatest">
        <el-icon class="alert-icon"><Refresh /></el-icon>
        <span>有 {{ newContentCount }} 条新内容，点击刷新</span>
      </div>
    </transition>

    <div v-if="pulseInsight" class="pulse-strip clickable" @click="showPulseDashboard = true">
      <el-icon class="pulse-icon"><DataLine /></el-icon>
      <span class="pulse-text">{{ pulseInsight }}</span>
      <span class="pulse-action-hint">查看趋势大屏 ➜</span>
    </div>

    <PulseDashboardDialog
      v-if="showPulseDashboard"
      v-model="showPulseDashboard"
      :topics="topics"
    />

    <div class="topic-list">
      <template v-if="isFirstLoading">
        <div v-for="item in INITIAL_SKELETON_COUNT" :key="`skeleton-${item}`" class="topic-skeleton">
          <div class="skeleton-title"></div>
          <div class="skeleton-line"></div>
          <div class="skeleton-line short"></div>
          <div class="skeleton-meta"></div>
        </div>
      </template>
      <transition-group v-else name="list-item" tag="div" style="display:contents">
        <TopicRow v-for="topic in topics" :key="topic.id" :topic="topic" />
      </transition-group>

      <div v-if="topics.length === 0 && !loading" class="empty-state">
        <el-empty description="这里还没有内容，发一篇试试吧" :image-size="120">
          <template #image>
            <el-icon :size="48" color="#dcdfe6"><Finished /></el-icon>
          </template>
        </el-empty>
      </div>

      <div ref="loadTrigger" class="list-footer">
        <div v-if="loading && !isFirstLoading" class="footer-state loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-else-if="noMore && topics.length > 0" class="footer-state no-more">
          <span>已经到底了，期待你的下一篇内容</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.topic-list-container {
  display: flex;
  flex-direction: column;
}

.topic-list {
  border-radius: 10px;
  background-color: var(--el-bg-color-overlay);
  box-shadow: var(--el-box-shadow-light);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.empty-state {
  padding: 40px 0;
}

.new-content-alert {
  background-color: var(--el-color-primary);
  color: #fff;
  text-align: center;
  padding: 10px;
  border-radius: 10px;
  margin-bottom: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  box-shadow: var(--el-box-shadow-light);
  transition: transform 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
  position: sticky;
  top: calc(var(--header-height) + 14px);
  z-index: 90;
}

.new-content-alert:hover {
  background-color: var(--el-color-primary-light-3);
  transform: translateY(-1px);
  box-shadow: var(--el-box-shadow);
}

.alert-icon {
  font-size: 16px;
}

.pulse-strip {
  margin-bottom: 12px;
  border: 1px solid #d8e6ff;
  background: linear-gradient(90deg, #f3f8ff 0%, #f7fbff 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  color: #0b57d0;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.26s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.pulse-strip.clickable {
  cursor: pointer;
  border-color: #bcd2ff;
  box-shadow: 0 2px 4px rgba(11, 87, 208, 0.02);
}

.pulse-strip.clickable:hover {
  background: linear-gradient(90deg, #e8f1ff 0%, #f0f6ff 100%);
  border-color: #8ab4f8;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(11, 87, 208, 0.08);
}

.pulse-icon {
  font-size: 15px;
  animation: pulseBeat 1.8s infinite alternate;
}

@keyframes pulseBeat {
  0% { transform: scale(0.92); opacity: 0.8; }
  100% { transform: scale(1.08); opacity: 1; }
}

.pulse-text {
  flex: 1;
}

.pulse-action-hint {
  font-size: 11px;
  color: #1a73e8;
  opacity: 0.8;
  transition: opacity 0.2s, transform 0.2s;
}

.pulse-strip.clickable:hover .pulse-action-hint {
  opacity: 1;
  transform: translateX(2px);
}



.topic-skeleton {
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: grid;
  gap: 10px;
}

.topic-skeleton:last-child {
  border-bottom: none;
}

.skeleton-title,
.skeleton-line,
.skeleton-meta {
  border-radius: 8px;
  background: linear-gradient(90deg, #eef1f6 25%, #e3e8f0 37%, #eef1f6 63%);
  background-size: 400% 100%;
  animation: skeleton-loading 1.2s ease infinite;
}

.skeleton-title {
  height: 20px;
  width: 70%;
}

.skeleton-line {
  height: 12px;
}

.skeleton-line.short {
  width: 58%;
}

.skeleton-meta {
  height: 12px;
  width: 40%;
}

@keyframes skeleton-loading {
  0% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0 50%;
  }
}

.list-footer {
  padding: 20px 0;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 48px;
}

.footer-state {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.footer-state.no-more {
  color: var(--el-text-color-placeholder);
}
</style>

<style>
/* Global Glassmorphism Styles for Teleported AI Pulse Dashboard Dialog */
.pulse-dashboard-dialog {
  border-radius: 16px !important;
  backdrop-filter: blur(20px) !important;
  background: rgba(255, 255, 255, 0.9) !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.15) !important;
  overflow: hidden;
}

.pulse-dashboard-dialog .el-dialog__header {
  border-bottom: 1px solid rgba(241, 245, 249, 0.8) !important;
  padding: 18px 24px !important;
  margin: 0 !important;
}

.pulse-dashboard-dialog .el-dialog__title {
  font-weight: 800 !important;
  font-size: 16px !important;
  color: var(--el-text-color-primary) !important;
}

.pulse-dashboard-dialog .el-dialog__body {
  padding: 20px 24px 24px 24px !important;
}

.pulse-dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-intro {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.25) 0%, rgba(255, 255, 255, 0.4) 100%) !important;
  border: 1px solid rgba(252, 211, 77, 0.3) !important;
  border-radius: 10px;
  padding: 10px 14px;
}

.dashboard-intro p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #92400E;
}

.sparkle-icon {
  font-size: 15px;
  animation: bounceSpark 2s infinite alternate;
}

@keyframes bounceSpark {
  0% { transform: translateY(0); }
  100% { transform: translateY(-2px); }
}

.pulse-chart-card {
  background: #ffffff !important;
  border: 1px solid rgba(226, 232, 240, 0.8) !important;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02) !important;
  transition: all 0.24s ease;
}

.pulse-chart-card:hover {
  border-color: rgba(203, 213, 225, 1) !important;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04) !important;
}

.chart-title {
  margin: 0 0 10px 0 !important;
  font-size: 13px !important;
  font-weight: 700 !important;
  color: var(--el-text-color-primary) !important;
  display: flex;
  align-items: center;
}

.chart-wrapper {
  height: 180px;
  width: 100%;
}

.pulse-insight-box {
  background: #f8fafc !important;
  border: 1px dashed #cbd5e1 !important;
  border-radius: 10px;
  padding: 12px 16px;
}

.insight-box-title {
  margin: 0 0 6px 0 !important;
  font-size: 12.5px !important;
  font-weight: 700 !important;
  color: #1e293b !important;
}

.insight-list {
  margin: 0 !important;
  padding-left: 16px !important;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.insight-list li {
  font-size: 11.5px !important;
  line-height: 1.5 !important;
  color: #475569 !important;
}
</style>

