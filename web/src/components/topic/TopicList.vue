<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataLine, Finished, Loading, Refresh } from '@element-plus/icons-vue'
import TopicFilters from './TopicFilters.vue'
import TopicRow from './TopicRow.vue'
import { postApi } from '@/api/post'
import type { PostSearchRequest } from '@/types'
import { wsClient, type PostEvent } from '@/utils/websocket'

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

const activeTab = ref('latest')
const activeSort = ref('default')

const hasNewContent = ref(false)
const newContentCount = ref(0)
let newContentTimer: any = null
let wsUnsubscribe: (() => void) | null = null
let realtimeInitialized = false

const loadTrigger = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
const INITIAL_SKELETON_COUNT = 5

const isFirstLoading = computed(() => loading.value && topics.value.length === 0)

const getCurrentPageSize = () => (firstBatchLoaded.value ? pageSize : initialPageSize)

const resolveOrderBy = (): 'new' | 'hot' => {
  let orderBy: 'new' | 'hot' = activeTab.value === 'top' ? 'hot' : 'new'

  if (activeSort.value === 'comments') {
    orderBy = 'hot'
  }

  if (route.query.sort === 'hot' || route.query.sort === 'top') {
    orderBy = 'hot'
  }
  if (route.query.sort === 'latest') {
    orderBy = 'new'
  }

  return orderBy
}

const buildSearchReq = (): PostSearchRequest => {
  const orderBy = resolveOrderBy()
  const req: PostSearchRequest = {
    page: orderBy === 'new' ? 1 : page.value,
    pageSize: getCurrentPageSize(),
    needTotal: false,
    status: 1,
    orderBy,
    ...props.defaultQuery,
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
  },
  createdAt: p.createTime,
  lastActive: p.lastActivityAt || p.createTime,
  replies: p.commentCount ?? 0,
  views: p.viewCount ?? 0,
  heatScore: p.heatScore,
  isPinned: p.isPinned === 1,
  isFeatured: p.isFeatured === 1,
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
  if (hotCount > 0) parts.push(`${hotCount} 条内容热度上升`)
  if (positiveCount > 0) parts.push(`正向讨论占比 ${Math.round((positiveCount / sample.length) * 100)}%`)
  parts.push(`平均浏览 ${avgViews}`)

  return `AI 数据脉冲: ${parts.join(' · ')}`
})

const loadMore = async () => {
  if (loading.value || noMore.value) return
  loading.value = true

  try {
    const currentPageSize = getCurrentPageSize()
    const res = await postApi.searchList(buildSearchReq())
    const records = res.data?.records || []
    if (records.length < currentPageSize) {
      noMore.value = true
    }
    topics.value.push(...records.map(mapPost))
    firstBatchLoaded.value = true

    if (resolveOrderBy() === 'new') {
      const last = records[records.length - 1]
      if (last?.lastActivityAt) {
        cursor.value = last.lastActivityAt
        cursorId.value = last.id
      }
    } else {
      page.value++
    }
  } catch {
    ElMessage.error('加载失败，请重试')
  } finally {
    loading.value = false
  }
}

const refreshLatest = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  hasNewContent.value = false
  newContentCount.value = 0
  topics.value = []
  page.value = 1
  firstBatchLoaded.value = false
  cursor.value = null
  cursorId.value = null
  noMore.value = false
  loadMore()
}

const handleFilterChange = (filter: { tab: string; sort: string }) => {
  activeTab.value = filter.tab
  activeSort.value = filter.sort
  refreshLatest()
}

const handlePostEvent = (event: PostEvent) => {
  if (window.scrollY > 300) {
    if (event.type === 'POST_CREATED' || event.type === 'POST_REPLIED') {
      hasNewContent.value = true
      newContentCount.value++
    }
  }

  if (event.data && event.postId) {
    const index = topics.value.findIndex((item) => item.id === event.postId)
    if (index !== -1) {
      const topic = topics.value[index]

      if (event.data.viewCount !== undefined) topic.views = event.data.viewCount
      if (event.data.commentCount !== undefined) topic.replies = event.data.commentCount
      if (event.data.lastActivityAt) topic.lastActive = event.data.lastActivityAt

      if (event.type === 'POST_REPLIED' && activeTab.value === 'latest') {
        const [movedTopic] = topics.value.splice(index, 1)
        topics.value.unshift(movedTopic)
      }

      if (event.type === 'PIN_UPDATED' && event.data) {
        topic.isPinned = event.data.globalPin === 1 || event.data.categoryPin === 1
      }
    }
  }
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

    try {
      const res = await postApi.searchList({ page: 1, pageSize: 1, needTotal: false, status: 1, orderBy: 'new' })
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
  refreshLatest()
})

onMounted(() => {
  if (topics.value.length === 0) {
    loadMore().finally(() => scheduleRealtimeInit())
  } else {
    scheduleRealtimeInit()
  }

  startNewContentPolling()

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

    <div v-if="pulseInsight" class="pulse-strip">
      <el-icon><DataLine /></el-icon>
      <span>{{ pulseInsight }}</span>
    </div>

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
  padding: 10px 12px;
  color: #0b57d0;
  font-size: 13px;
  font-weight: 600;
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

