<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PostCard from '@/components/PostCard.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PostListSkeleton from '@/components/common/PostListSkeleton.vue'
import { postApi } from '@/api/post'
import { publicDataApi } from '@/api/publicData'
import { tagApi } from '@/api/tag'
import { userApi } from '@/api/user'
import { agentApi, type AgentCitation, type AgentRelatedPost, type CommunityQaAskResponse } from '@/api/agent'
import type { Post } from '@/types'
import { ElMessage } from 'element-plus'
import { Search, Filter, Close, Loading, MagicStick } from '@element-plus/icons-vue'
import { cachedRequest } from '@/utils/requestCache'
import { encodePostId, encodeUserId } from '@/utils/shortId'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import {
  DEFAULT_DISCOVERY_SEARCH_TERMS,
  dedupeTextList,
  formatDiscoveryTagName,
  isMeaningfulDiscoveryTagName,
  normalizeDiscoveryTagNames,
} from '@/utils/communityDiscovery'
import { formatSectionName } from '@/utils/communitySections'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'

const route = useRoute()
const router = useRouter()
const publicAssetUrl = (value?: string | null) => resolvePublicAssetUrl(value)

const SEARCH_HISTORY_KEY = 'cp:search:history'
const SEARCH_HISTORY_MAX = 12
const SEARCH_RESULT_CACHE_TTL = 60 * 1000
const SEARCH_SUGGEST_CACHE_TTL = 5 * 60 * 1000
const SEARCH_STOP_WORDS = [
  '怎么', '如何', '一下', '一个', '这个', '那个', '是否', '有没有',
  '还是', '以及', '然后', '什么', '为啥', '为什么', '需要', '想问', '想知道',
  '帖子', '板块', '评论', '社区', '相关', '内容', '问题', '经验', '案例',
]

type SearchTab = 'post' | 'user' | 'tag'

interface SearchUserItem {
  id: string
  username: string
  nickname: string
  avatar: string
  bio?: string
  school?: string
  postCount: number
  followerCount: number
}

interface SearchTagItem {
  id: number
  name: string
  heat?: number
  postCount?: number
}

const searchQuery = ref('')
const searchTab = ref<SearchTab>('post')
const posts = ref<Post[]>([])
const userResults = ref<SearchUserItem[]>([])
const tagResults = ref<SearchTagItem[]>([])
const loading = ref(false)
const userLoading = ref(false)
const tagLoading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const total = ref(0)
let fetchTimer: ReturnType<typeof setTimeout> | null = null
let suggestTimer: ReturnType<typeof setTimeout> | null = null
let suggestRequestId = 0
let searchAbortController: AbortController | null = null
let suggestAbortController: AbortController | null = null
let userAbortController: AbortController | null = null
let tagAbortController: AbortController | null = null
let agentAbortController: AbortController | null = null
let agentTimer: ReturnType<typeof setTimeout> | null = null
let agentRequestId = 0
let lastAgentSignature = ''
let syncingRouteState = false

const filters = ref({
  category: '' as string | number,
  sortBy: 'relevance' as 'relevance' | 'new' | 'hot',
  timeRange: 'all' as 'all' | 'today' | 'week' | 'month'
})

const categories = ref<any[]>([])
const showFilters = ref(false)
const searchHistory = ref<string[]>([])
const hotSuggestions = ref<string[]>([])
const dynamicSuggestions = ref<string[]>([])
const agentLoading = ref(false)
const agentError = ref('')
const agentResult = ref<CommunityQaAskResponse | null>(null)

const normalizedQuery = computed(() => searchQuery.value.trim())
const canOpenAgent = computed(() => searchTab.value === 'post' && normalizedQuery.value.length >= 2)
const agentSignature = computed(() => [
  normalizedQuery.value.toLowerCase(),
  filters.value.category || '_',
  searchTab.value,
].join('|'))
const selectedCategoryLabel = computed(() => {
  const matched = categories.value.find((item) => Number(item.id) === Number(filters.value.category))
  return matched ? formatSectionName(matched.name || '') : ''
})

const filteredHistory = computed(() => {
  const q = normalizedQuery.value.toLowerCase()
  if (!q) return searchHistory.value.slice(0, 8)
  return searchHistory.value.filter((item) => item.toLowerCase().includes(q)).slice(0, 8)
})

const filteredHotSuggestions = computed(() => {
  const q = normalizedQuery.value.toLowerCase()
  if (!q) return hotSuggestions.value.slice(0, 8)
  return hotSuggestions.value.filter((item) => item.toLowerCase().includes(q)).slice(0, 8)
})

const keywordSuggestions = computed(() => {
  const merged = [...dynamicSuggestions.value, ...filteredHistory.value, ...filteredHotSuggestions.value]
  const deduped: string[] = []
  const seen = new Set<string>()
  merged.forEach((item) => {
    const key = item.toLowerCase()
    if (!seen.has(key)) {
      seen.add(key)
      deduped.push(item)
    }
  })
  return deduped.slice(0, 10)
})

const hasActiveFilters = computed(() => {
  return Boolean(filters.value.category || filters.value.sortBy !== 'relevance' || filters.value.timeRange !== 'all')
})

const shouldShowSuggestionPanel = computed(() => {
  return Boolean(normalizedQuery.value && keywordSuggestions.value.length)
})

const shouldShowDiscoveryPanel = computed(() => {
  return !normalizedQuery.value && (searchHistory.value.length > 0 || hotSuggestions.value.length > 0)
})

const showPostEmptyRecovery = computed(() => {
  return searchTab.value === 'post' && !loading.value && posts.value.length === 0 && Boolean(normalizedQuery.value)
})

const buildPostAgentSummary = (post: Post) => {
  const source = post.summary || post.content || post.title || '社区相关讨论'
  return String(source)
    .replace(/<[^>]+>/g, ' ')
    .replace(/[#*_>`[\](){}|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 140)
}

const parsePostTags = (tags: string | undefined) => {
  if (!tags) return []
  return tags
    .split(/[,，#\s]+/)
    .map(tag => tag.replace(/[\[\]"]/g, '').trim())
    .filter(Boolean)
    .slice(0, 6)
}

const rawAgentCitations = computed<AgentCitation[]>(() => agentResult.value?.citations?.slice(0, 4) || [])
const rawAgentRelatedPosts = computed<AgentRelatedPost[]>(() => agentResult.value?.related_posts?.slice(0, 4) || [])
const agentAnswer = computed(() => (agentResult.value?.answer || '').trim())
const agentAnswerParagraphs = computed(() =>
  agentAnswer.value
    .split(/\n+/)
    .map(item => item.trim())
    .filter(Boolean)
)
const agentTrace = computed(() => agentResult.value?.trace || null)
const agentBackend = computed(() => agentTrace.value?.backend || agentResult.value?.backend || '')
const agentFallbackReason = computed(() => agentResult.value?.fallback_reason || agentTrace.value?.fallback_reason || '')
const searchResultAgentFallbackPosts = computed<AgentRelatedPost[]>(() =>
  posts.value.slice(0, 4).map((post, index) => ({
    post_id: post.id,
    title: post.title || '未命名帖子',
    section_name: post.sectionName,
    summary: buildPostAgentSummary(post),
    tags: parsePostTags(post.tags),
    score: 1 - index * 0.05,
    url: `/t/${encodePostId(post.id)}`,
  }))
)
const agentUsingSearchFallback = computed(() =>
  canOpenAgent.value &&
  posts.value.length > 0 &&
  rawAgentCitations.value.length === 0 &&
  rawAgentRelatedPosts.value.length === 0 &&
  !agentAnswer.value
)
const agentCitations = computed<AgentCitation[]>(() => rawAgentCitations.value)
const agentRelatedPosts = computed<AgentRelatedPost[]>(() =>
  rawAgentRelatedPosts.value.length > 0
    ? rawAgentRelatedPosts.value
    : agentUsingSearchFallback.value
      ? searchResultAgentFallbackPosts.value
      : []
)
const agentHasHits = computed(() => agentCitations.value.length > 0 || agentRelatedPosts.value.length > 0)
const agentHitCount = computed(() => agentTrace.value?.hit_count || agentCitations.value.length + agentRelatedPosts.value.length)
const agentHasAnswer = computed(() => agentAnswer.value.length > 0)
const agentLatencyText = computed(() => {
  const trace = agentTrace.value
  if (!trace) return ''
  const total = Number(trace.total_ms)
  if (Number.isFinite(total) && total > 0) return `${total} ms`
  const retrieval = Number(trace.retrieval_ms)
  return Number.isFinite(retrieval) && retrieval > 0 ? `${retrieval} ms` : ''
})
const agentConfidenceLabel = computed(() => {
  const confidence = agentResult.value?.confidence
  if (confidence === 'high') return '高置信'
  if (confidence === 'medium') return '中置信'
  if (confidence === 'low') return '低置信'
  return ''
})
const agentFallbackLabel = computed(() => {
  const reason = agentFallbackReason.value
  if (reason === 'agent_disabled') return 'Agent 未启用，已用社区搜索兜底'
  if (reason === 'agent_unavailable') return 'Agent 不可达，已用社区搜索兜底'
  if (reason === 'agent_empty') return 'Agent 无强命中，已补充普通搜索结果'
  return ''
})
const agentPanelTitle = computed(() => {
  if (agentUsingSearchFallback.value) return 'Agent 已沿用当前搜索结果补充推荐'
  if (agentLoading.value && !agentHasAnswer.value) return 'Agent 正在生成社区回答'
  if (agentError.value) return 'Agent 问答暂时没有跑通'
  if (agentHasAnswer.value) return 'Agent 已回答当前搜索'
  if (agentHasHits.value) return 'Agent 已为当前搜索补充证据'
  return 'Agent 会自动帮你整理社区线索'
})
const agentPanelDescription = computed(() => {
  if (agentUsingSearchFallback.value) return '专用 Agent 暂未返回引用时，会先复用已经命中的帖子，保证推荐和搜索结果保持一致。'
  if (agentLoading.value && !agentHasAnswer.value) return '正在沿用当前关键词和板块范围检索帖子、评论，并生成一段可核查的社区回答。'
  if (agentError.value) return agentError.value
  if (agentHasAnswer.value) return agentFallbackLabel.value || '回答基于社区帖子与评论生成，下面保留可跳转的引用和相近帖子。'
  if (agentHasHits.value) return '这些内容来自社区帖子与评论，可以作为普通搜索结果之外的补充入口。'
  return '输入至少 2 个字符后，搜索会自动触发 Agent 回答，不需要再进入单独页面。'
})

const emptyRecoveryDescription = computed(() => {
  if (selectedCategoryLabel.value) {
    return `${selectedCategoryLabel.value} 里暂时没有直接命中，先放宽范围或换一个更聚焦的搜词试试。`
  }
  return '没有找到直接命中的帖子，可以换一个更聚焦的搜词，或者交给 Agent 去帖子和评论里继续归纳。'
})

const parseRouteSectionId = () => {
  const raw = route.query.sectionId
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : ''
}

const extractSearchKeywords = (text: string) => {
  const normalized = String(text || '')
    .toLowerCase()
    .replace(/[\r\n]+/g, ' ')
    .replace(/[，。！？、,.!?:：;；()[\]{}【】<>《》"“”'‘’`~@#$%^&*=+|\\/]+/g, ' ')

  let working = normalized
  SEARCH_STOP_WORDS.forEach((item) => {
    working = working.split(item).join(' ')
  })

  const matches = working.match(/[a-z0-9+#._-]+|[\u4e00-\u9fff]{2,}/g) ?? []
  const deduped: string[] = []
  const seen = new Set<string>()
  matches.forEach((item) => {
    const keyword = formatDiscoveryTagName(item.trim())
    const key = keyword.toLowerCase()
    if (!keyword || keyword.length < 2 || seen.has(key)) {
      return
    }
    seen.add(key)
    deduped.push(keyword)
  })
  return deduped
}

const emptyRecoveryTerms = computed(() => {
  const keywords = extractSearchKeywords(normalizedQuery.value)
  const seedTerms = [
    keywords.slice(0, 2).join(' '),
    keywords[0] || '',
    ...dynamicSuggestions.value,
    ...hotSuggestions.value.slice(0, 4),
  ]

  return dedupeTextList(
    normalizeDiscoveryTagNames(seedTerms.filter(Boolean), {
      limit: 8,
      minScore: 1,
      includeDefaults: true,
    }),
    8
  )
    .filter((item) => item.trim().toLowerCase() !== normalizedQuery.value.trim().toLowerCase())
    .slice(0, 4)
})

const loadSearchHistory = () => {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY)
    if (!raw) {
      searchHistory.value = []
      return
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      searchHistory.value = []
      return
    }
    searchHistory.value = parsed
      .map((item) => String(item || '').trim())
      .filter(Boolean)
      .slice(0, SEARCH_HISTORY_MAX)
  } catch {
    searchHistory.value = []
  }
}

const persistSearchHistory = () => {
  try {
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(searchHistory.value.slice(0, SEARCH_HISTORY_MAX)))
  } catch {
    // ignore
  }
}

const pushSearchHistory = (keyword: string) => {
  const text = keyword.trim()
  if (!text) return
  const merged = [text, ...searchHistory.value.filter((item) => item.toLowerCase() !== text.toLowerCase())]
  searchHistory.value = merged.slice(0, SEARCH_HISTORY_MAX)
  persistSearchHistory()
}

const clearSearchHistory = () => {
  searchHistory.value = []
  try {
    localStorage.removeItem(SEARCH_HISTORY_KEY)
  } catch {
    // ignore
  }
}

const fetchCategories = async () => {
  try {
    const res = await publicDataApi.getActiveSectionsCached()
    if (res.code === 2000 || res.code === 200) {
      categories.value = Array.isArray(res.data)
        ? res.data.map((item: any) => ({
            ...item,
            id: Number(item.id),
          }))
        : []
    }
  } catch (error) {
    console.error('Failed to fetch sections:', error)
  }
}

const fetchHotSuggestions = async () => {
  try {
    const res = await publicDataApi.getHotTagsCached(30)
    const list = Array.isArray(res.data) ? res.data : []
    const curated = normalizeDiscoveryTagNames(
      list.map((item) => item?.name?.trim()).filter((name): name is string => Boolean(name)),
      { limit: 12, minScore: 2 }
    )
    hotSuggestions.value = curated.length ? curated : DEFAULT_DISCOVERY_SEARCH_TERMS.slice(0, 6)
  } catch {
    hotSuggestions.value = DEFAULT_DISCOVERY_SEARCH_TERMS.slice(0, 6)
  }
}

const fetchDynamicSuggestions = async (keyword: string) => {
  const normalizedKeyword = keyword.trim()
  if (normalizedKeyword.length < 2) {
    suggestAbortController?.abort()
    suggestAbortController = null
    dynamicSuggestions.value = []
    return
  }

  const requestId = ++suggestRequestId
  suggestAbortController?.abort()
  const controller = new AbortController()
  suggestAbortController = controller
  try {
    const res = await cachedRequest(
      `search:suggest:${normalizedKeyword.toLowerCase()}`,
      SEARCH_SUGGEST_CACHE_TTL,
      () => tagApi.search(normalizedKeyword, { signal: controller.signal })
    )
    if (requestId !== suggestRequestId) {
      return
    }

    const list = Array.isArray(res.data) ? res.data : []
    dynamicSuggestions.value = normalizeDiscoveryTagNames(
      list
      .map((item) => item?.name?.trim())
      .filter((name): name is string => Boolean(name) && isMeaningfulDiscoveryTagName(name)),
      { limit: 8, minScore: 1, includeDefaults: false }
    )
  } catch (error: any) {
    if (error?.code === 'ERR_CANCELED') {
      return
    }
    if (requestId === suggestRequestId) {
      dynamicSuggestions.value = []
    }
  } finally {
    if (suggestAbortController === controller) {
      suggestAbortController = null
    }
  }
}

const scheduleDynamicSuggestions = () => {
  if (suggestTimer) {
    clearTimeout(suggestTimer)
    suggestTimer = null
  }

  const q = normalizedQuery.value
  if (!q || q.length < 2) {
    dynamicSuggestions.value = []
    return
  }

  suggestTimer = setTimeout(() => {
    fetchDynamicSuggestions(q)
  }, 220)
}

const resetSearchResultState = () => {
  page.value = 1
  posts.value = []
  hasMore.value = true
  total.value = 0
}

const fetchPosts = async (reset = false) => {
  if (reset) {
    searchAbortController?.abort()
    searchAbortController = null
    resetSearchResultState()
  }

  if (!hasMore.value || (loading.value && !reset)) return
  loading.value = true
  const controller = new AbortController()
  searchAbortController = controller
  try {
    const keyword = normalizedQuery.value
    const sectionId = filters.value.category ? Number(filters.value.category) : undefined
    const timeRange = filters.value.timeRange === 'all'
      ? undefined
      : filters.value.timeRange.toUpperCase()
    const requestPayload = {
      page: page.value,
      pageSize: 10,
      needTotal: true,
      keyword,
      sectionId,
      orderBy: filters.value.sortBy,
      timeRange,
      status: 1
    }
    const cacheKey = [
      'search:list',
      requestPayload.page,
      requestPayload.pageSize,
      keyword.trim().toLowerCase() || '_',
      sectionId ?? '_',
      requestPayload.orderBy,
      timeRange ?? 'all',
      requestPayload.status
    ].join(':')

    const res = await cachedRequest(
      cacheKey,
      SEARCH_RESULT_CACHE_TTL,
      () => postApi.searchList(requestPayload, { signal: controller.signal })
    )

    const records = res.data?.records || []
    if (records.length > 0) {
      posts.value.push(...records)
      page.value++
      total.value = res.data.total || 0
    } else {
      hasMore.value = false
    }

    if (records.length < 10 || posts.value.length >= (res.data?.total || 0)) {
      hasMore.value = false
    }
  } catch (error: any) {
    if (error?.code === 'ERR_CANCELED') {
      return
    }
    ElMessage.error('搜索内容获取失败')
  } finally {
    if (searchAbortController === controller) {
      searchAbortController = null
      loading.value = false
    }
  }
}

// Song：帖子结果无限滚动（仅在帖子 tab 生效）
const { sentinel } = useInfiniteScroll(() => fetchPosts(false), {
  canLoadMore: () =>
    searchTab.value === 'post' && hasMore.value && !loading.value && posts.value.length > 0,
})
void sentinel

const fetchUsers = async () => {
  const keyword = normalizedQuery.value
  if (!keyword) {
    userResults.value = []
    return
  }
  userAbortController?.abort()
  const controller = new AbortController()
  userAbortController = controller
  userLoading.value = true
  try {
    const res = await userApi.searchUsers(keyword)
    if (userAbortController !== controller) return
    userResults.value = Array.isArray(res.data) ? res.data : []
  } catch (error: any) {
    if (error?.code === 'ERR_CANCELED') return
    ElMessage.error('用户搜索失败')
  } finally {
    if (userAbortController === controller) {
      userAbortController = null
      userLoading.value = false
    }
  }
}

const fetchTags = async () => {
  const keyword = normalizedQuery.value
  if (!keyword) {
    tagResults.value = []
    return
  }
  tagAbortController?.abort()
  const controller = new AbortController()
  tagAbortController = controller
  tagLoading.value = true
  try {
    const res = await tagApi.search(keyword, { signal: controller.signal })
    if (tagAbortController !== controller) return
    tagResults.value = Array.isArray(res.data) ? res.data : []
  } catch (error: any) {
    if (error?.code === 'ERR_CANCELED') return
    ElMessage.error('标签搜索失败')
  } finally {
    if (tagAbortController === controller) {
      tagAbortController = null
      tagLoading.value = false
    }
  }
}

const scheduleFetch = (reset = true) => {
  if (fetchTimer) {
    clearTimeout(fetchTimer)
  }
  fetchTimer = setTimeout(() => {
    if (searchTab.value === 'post') {
      fetchPosts(reset)
    } else if (searchTab.value === 'user') {
      fetchUsers()
    } else if (searchTab.value === 'tag') {
      fetchTags()
    }
  }, 250)
}

const openAgentTarget = (url: string) => {
  if (!url) return
  router.push(url)
}

const formatCitationSource = (item: AgentCitation) => {
  const section = item.section_name ? formatSectionName(item.section_name) : '社区'
  return `${section} · ${item.source_type === 'comment' ? '评论引用' : '原帖引用'}`
}

const resetAgentState = () => {
  agentAbortController?.abort()
  agentAbortController = null
  agentLoading.value = false
  agentError.value = ''
  agentResult.value = null
  lastAgentSignature = ''
}

const fetchAgentRecommendations = async (force = false) => {
  if (!canOpenAgent.value) {
    resetAgentState()
    return
  }

  const signature = agentSignature.value
  if (!force && signature === lastAgentSignature && (agentLoading.value || agentResult.value || agentError.value)) {
    return
  }

  lastAgentSignature = signature
  const requestId = ++agentRequestId
  agentAbortController?.abort()
  const controller = new AbortController()
  agentAbortController = controller
  agentLoading.value = true
  agentError.value = ''
  if (force) {
    agentResult.value = null
  }

  try {
    const res = await agentApi.ask({
      question: normalizedQuery.value,
      retrievalQuery: normalizedQuery.value,
      sectionId: filters.value.category ? Number(filters.value.category) : undefined,
      limit: 6,
      includeComments: true,
      commentsPerPost: 2,
    }, { signal: controller.signal })
    if (requestId !== agentRequestId) return
    agentResult.value = res.data || null
  } catch (error: any) {
    if (error?.code === 'ERR_CANCELED') return
    if (requestId === agentRequestId) {
      const status = error?.response?.status
      agentError.value = status === 401 || status === 403
        ? 'Agent 问答当前需要登录或服务授权，登录后会自动整理社区回答。'
        : 'Agent 问答服务暂时不可用，可以稍后重试。'
      agentResult.value = null
    }
  } finally {
    if (agentAbortController === controller) {
      agentAbortController = null
      agentLoading.value = false
    }
  }
}

const scheduleAgentRecommendations = (delay = 320) => {
  if (agentTimer) {
    clearTimeout(agentTimer)
    agentTimer = null
  }
  if (!canOpenAgent.value) {
    resetAgentState()
    return
  }
  agentTimer = setTimeout(() => {
    fetchAgentRecommendations(false)
  }, delay)
}

const triggerSearch = (keyword?: string) => {
  const q = (keyword ?? normalizedQuery.value).trim()
  if (!q) {
    ElMessage.warning('请输入关键词再搜搜看')
    return
  }

  searchQuery.value = q
  pushSearchHistory(q)
  const query: Record<string, string> = { q }
  if (filters.value.category) {
    query.sectionId = String(filters.value.category)
  }
  router.push({ path: '/search', query })
  scheduleFetch(true)
}

const handleSearch = () => {
  triggerSearch()
}

const handleSuggestionSelect = (term: string) => {
  triggerSearch(term)
}

const openAgentPanel = () => {
  if (!canOpenAgent.value) {
    ElMessage.warning('先输入至少 2 个字符，再交给 Agent 帮你整理')
    return
  }
  fetchAgentRecommendations(true)
}

const broadenSearchScope = () => {
  if (!normalizedQuery.value) {
    return
  }
  filters.value.category = ''
  router.push({ path: '/search', query: { q: normalizedQuery.value } })
  scheduleFetch(true)
}

const resetSearchFiltersAndRetry = () => {
  filters.value = {
    category: '',
    sortBy: 'relevance',
    timeRange: 'all',
  }
  if (!normalizedQuery.value) {
    return
  }
  router.push({ path: '/search', query: { q: normalizedQuery.value } })
  scheduleFetch(true)
}

const clearFilters = () => {
  filters.value = {
    category: '',
    sortBy: 'relevance',
    timeRange: 'all'
  }
  if (normalizedQuery.value) {
    scheduleFetch(true)
  }
}

watch(normalizedQuery, () => {
  scheduleDynamicSuggestions()
  scheduleAgentRecommendations()
})

watch(() => [route.query.q, route.query.sectionId], ([newQuery]) => {
  const q = typeof newQuery === 'string' ? newQuery.trim() : ''
  syncingRouteState = true
  searchQuery.value = q
  filters.value.category = parseRouteSectionId()
  syncingRouteState = false

  if (q) {
    scheduleFetch(true)
    scheduleAgentRecommendations()
  } else {
    resetSearchResultState()
    resetAgentState()
  }
})

watch(() => [filters.value.category, filters.value.sortBy, filters.value.timeRange], () => {
  if (syncingRouteState) {
    return
  }
  if (normalizedQuery.value && searchTab.value === 'post') {
    scheduleFetch(true)
    scheduleAgentRecommendations()
  }
})

watch(searchTab, () => {
  if (normalizedQuery.value) {
    scheduleFetch(true)
  }
  scheduleAgentRecommendations()
})

onMounted(() => {
  loadSearchHistory()
  fetchCategories()
  fetchHotSuggestions()

  const q = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  const routeSectionId = parseRouteSectionId()
  if (routeSectionId) {
    filters.value.category = routeSectionId
  }
  if (q) {
    searchQuery.value = q
    scheduleFetch(true)
    scheduleAgentRecommendations(0)
  }
})

onUnmounted(() => {
  searchAbortController?.abort()
  suggestAbortController?.abort()
  userAbortController?.abort()
  tagAbortController?.abort()
  agentAbortController?.abort()

  if (fetchTimer) {
    clearTimeout(fetchTimer)
    fetchTimer = null
  }
  if (suggestTimer) {
    clearTimeout(suggestTimer)
    suggestTimer = null
  }
  if (agentTimer) {
    clearTimeout(agentTimer)
    agentTimer = null
  }
})
</script>

<template>
  <MainLayout>
    <div class="search-page-container">
      <div class="search-hero">
        <PageBackButton class="search-back-button" fallback="/" />
        <h1 class="search-title">探索社区</h1>
        <div class="search-bar-wrapper">
          <el-input
            v-model="searchQuery"
            placeholder="搜索话题、用户、项目经验或解决方案..."
            size="large"
            clearable
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
            class="main-search-input"
          >
            <template #append>
              <el-button @click="handleSearch">搜索</el-button>
            </template>
          </el-input>

          <el-button
            v-if="searchTab === 'post'"
            class="filter-toggle"
            :type="showFilters ? 'primary' : 'default'"
            plain
            circle
            @click="showFilters = !showFilters"
          >
            <el-icon><Filter /></el-icon>
          </el-button>
        </div>

        <el-collapse-transition>
          <div v-show="showFilters && searchTab === 'post'" class="filter-panel">
            <el-row :gutter="20">
              <el-col :span="8" :xs="24">
                <div class="filter-item">
                  <span class="label">板块分类</span>
                  <el-select v-model="filters.category" placeholder="全部板块" clearable>
                    <el-option label="全部板块" value="" />
                    <el-option v-for="cat in categories" :key="cat.id" :label="formatSectionName(cat.name || '')" :value="cat.id" />
                  </el-select>
                </div>
              </el-col>
              <el-col :span="8" :xs="24">
                <div class="filter-item">
                  <span class="label">排序规则</span>
                  <el-select v-model="filters.sortBy" placeholder="相关度">
                    <el-option label="相关度" value="relevance" />
                    <el-option label="最新优先" value="new" />
                    <el-option label="热度优先" value="hot" />
                  </el-select>
                </div>
              </el-col>
              <el-col :span="8" :xs="24">
                <div class="filter-item">
                  <span class="label">时间范围</span>
                  <el-select v-model="filters.timeRange" placeholder="全部时间">
                    <el-option label="全部时间" value="all" />
                    <el-option label="24小时内" value="today" />
                    <el-option label="本周内" value="week" />
                    <el-option label="本月内" value="month" />
                  </el-select>
                </div>
              </el-col>
            </el-row>
            <div v-if="hasActiveFilters" class="filter-footer">
              <el-link type="primary" :icon="Close" @click="clearFilters" :underline="false">清除所有筛选</el-link>
            </div>
          </div>
        </el-collapse-transition>

        <el-collapse-transition>
          <div v-if="shouldShowSuggestionPanel" class="suggest-panel">
            <div class="suggest-title">搜索建议</div>
            <div class="suggest-actions">
              <button
                v-for="item in keywordSuggestions"
                :key="`suggest-${item}`"
                type="button"
                class="suggest-action"
                @click="handleSuggestionSelect(item)"
              >
                <el-icon><Search /></el-icon>
                <span>{{ item }}</span>
              </button>
            </div>
          </div>
        </el-collapse-transition>

        <el-collapse-transition>
          <div v-if="shouldShowDiscoveryPanel" class="suggest-panel">
            <div v-if="searchHistory.length" class="suggest-block">
              <div class="suggest-header">
                <span class="suggest-title">最近搜索</span>
                <el-link type="primary" :underline="false" @click="clearSearchHistory">清空</el-link>
              </div>
              <div class="suggest-chips">
                <el-tag
                  v-for="item in searchHistory.slice(0, 8)"
                  :key="`history-${item}`"
                  class="clickable-tag"
                  @click="handleSuggestionSelect(item)"
                >
                  {{ item }}
                </el-tag>
              </div>
            </div>

            <div v-if="hotSuggestions.length" class="suggest-block">
              <div class="suggest-title">推荐搜索</div>
              <div class="suggest-chips">
                <el-tag
                  v-for="item in hotSuggestions.slice(0, 8)"
                  :key="`hot-${item}`"
                  class="clickable-tag hot"
                  @click="handleSuggestionSelect(item)"
                >
                  {{ item }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-collapse-transition>
      </div>

      <div v-if="normalizedQuery" class="search-tabs">
        <button
          v-for="tab in ([
            { key: 'post', label: '帖子' },
            { key: 'user', label: '用户' },
            { key: 'tag', label: '标签' },
          ] as { key: SearchTab; label: string }[])"
          :key="tab.key"
          type="button"
          class="search-tab"
          :class="{ active: searchTab === tab.key }"
          @click="searchTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="normalizedQuery && searchTab === 'post'" class="results-info">
        <p v-if="posts.length > 0">
          为您找到相关结果约 <span class="highlight">{{ total }}</span> 个
        </p>
      </div>

      <section v-if="normalizedQuery && searchTab === 'post'" class="agent-panel">
        <div class="agent-panel-header">
          <div class="agent-panel-copy">
            <p class="agent-panel-kicker">Agent 回答</p>
            <h2 class="agent-panel-title">{{ agentPanelTitle }}</h2>
            <p class="agent-panel-desc">{{ agentPanelDescription }}</p>
            <div class="agent-panel-pill-row">
              <span class="agent-panel-pill">
                当前搜索词
                <strong>{{ normalizedQuery }}</strong>
              </span>
              <span v-if="selectedCategoryLabel" class="agent-panel-pill">
                已限定板块
                <strong>{{ selectedCategoryLabel }}</strong>
              </span>
              <span v-if="agentBackend" class="agent-panel-pill">
                链路
                <strong>{{ agentBackend }}</strong>
              </span>
              <span v-if="agentConfidenceLabel" class="agent-panel-pill">
                {{ agentConfidenceLabel }}
              </span>
            </div>
          </div>
          <el-button
            type="primary"
            :icon="MagicStick"
            class="agent-panel-cta"
            :disabled="!canOpenAgent"
            :loading="agentLoading"
            @click="openAgentPanel"
          >
            {{ agentError ? '重试回答' : '重新回答' }}
          </el-button>
        </div>

        <div v-if="agentLoading && !agentResult && !agentUsingSearchFallback" class="agent-inline-state">
          <div class="agent-skeleton-line wide"></div>
          <div class="agent-skeleton-line"></div>
        </div>

        <div v-else-if="agentError && !agentUsingSearchFallback" class="agent-inline-state error">
          <span>{{ agentError }}</span>
          <el-button link type="primary" @click="fetchAgentRecommendations(true)">再试一次</el-button>
        </div>

        <div v-else-if="agentHasAnswer || agentHasHits" class="agent-answer-stack">
          <div v-if="agentHasAnswer" class="agent-answer-card">
            <div class="agent-answer-heading">
              <span>Agent 结论</span>
              <div class="agent-answer-meta">
                <small v-if="agentLatencyText">{{ agentLatencyText }}</small>
                <small v-if="agentHitCount">{{ agentHitCount }} 条命中</small>
              </div>
            </div>
            <div class="agent-answer-body">
              <p v-for="item in agentAnswerParagraphs" :key="item">{{ item }}</p>
            </div>
            <div v-if="agentFallbackLabel" class="agent-answer-note">
              {{ agentFallbackLabel }}
            </div>
          </div>

          <div v-if="agentHasHits" class="agent-results">
          <div v-if="agentCitations.length" class="agent-result-group">
            <div class="agent-result-heading">
              <span>引用线索</span>
              <small>{{ agentHitCount }} 条相关命中</small>
            </div>
            <button
              v-for="item in agentCitations"
              :key="`${item.source_type}-${item.post_id}-${item.comment_id || item.index}`"
              class="agent-citation-card"
              type="button"
              @click="openAgentTarget(item.url)"
            >
              <span class="agent-citation-meta">{{ formatCitationSource(item) }}</span>
              <strong>{{ item.title }}</strong>
              <em>{{ item.excerpt }}</em>
            </button>
          </div>

          <div v-if="agentRelatedPosts.length" class="agent-result-group">
            <div class="agent-result-heading">
              <span>相近帖子</span>
              <small>可直接跳转</small>
            </div>
            <button
              v-for="item in agentRelatedPosts"
              :key="item.post_id"
              class="agent-related-row"
              type="button"
              @click="openAgentTarget(item.url)"
            >
              <span>
                <strong>{{ item.title }}</strong>
                <em>{{ item.summary || item.tags?.slice(0, 3).join(' / ') || '社区相关讨论' }}</em>
              </span>
              <small>{{ item.section_name ? formatSectionName(item.section_name) : '帖子' }}</small>
            </button>
          </div>
          </div>
        </div>

        <div v-else class="agent-panel-footer">
          <div class="agent-panel-hint">
            <span class="agent-panel-hint-label">本轮会沿用</span>
            <span class="agent-panel-highlight">{{ normalizedQuery }}</span>
            <span v-if="selectedCategoryLabel">· {{ selectedCategoryLabel }}</span>
          </div>
          <span class="agent-panel-feature">自动加载</span>
        </div>
      </section>

      <!-- 帖子结果 -->
      <div v-if="searchTab === 'post'" class="posts-list">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          :highlight-keyword="normalizedQuery"
        />

        <PostListSkeleton v-if="loading && posts.length === 0" :count="4" />

        <div v-else-if="loading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon> 正在为您搜寻...
        </div>

        <EmptyState
          v-if="showPostEmptyRecovery"
          title="先放宽范围，还是交给 Agent 继续？"
          :description="emptyRecoveryDescription"
        >
          <div class="search-empty-recovery">
            <div class="search-empty-summary">
              <span class="search-empty-pill">
                当前搜索词
                <strong>{{ normalizedQuery }}</strong>
              </span>
              <span v-if="selectedCategoryLabel" class="search-empty-pill">
                已限定板块
                <strong>{{ selectedCategoryLabel }}</strong>
              </span>
            </div>

            <p v-if="emptyRecoveryTerms.length" class="search-empty-note">
              可以先换成这些更容易命中的搜词：
            </p>
            <div v-if="emptyRecoveryTerms.length" class="search-examples">
              <el-tag
                v-for="term in emptyRecoveryTerms"
                :key="term"
                class="clickable-tag"
                @click="handleSuggestionSelect(term)"
              >
                {{ term }}
              </el-tag>
            </div>

            <div class="search-empty-actions">
              <el-button v-if="selectedCategoryLabel" plain @click="broadenSearchScope">
                扩大到全站范围
              </el-button>
              <el-button v-else-if="hasActiveFilters" plain @click="resetSearchFiltersAndRetry">
                恢复默认筛选
              </el-button>
              <el-button
                type="primary"
                plain
                :icon="MagicStick"
                :disabled="!canOpenAgent"
                :loading="agentLoading"
                @click="openAgentPanel"
              >
                让 Agent 再找一遍
              </el-button>
            </div>
          </div>
        </EmptyState>

        <EmptyState
          v-if="!loading && !normalizedQuery"
          title="想搜点什么？"
          description="在这里输入你感兴趣的话题、标签或协作者"
        >
          <div class="search-examples">
            <el-tag
              v-for="ex in hotSuggestions.slice(0, 3)"
              :key="ex"
              class="clickable-tag"
              @click="handleSuggestionSelect(ex)"
            >
              {{ ex }}
            </el-tag>
          </div>
        </EmptyState>

        <div v-if="!loading && hasMore && posts.length > 0" class="pagination-footer">
          <el-button plain @click="fetchPosts(false)">加载更多结果</el-button>
        </div>

        <!-- Song：无限滚动哨兵 -->
        <div ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>

        <div v-if="!hasMore && posts.length > 0" class="pagination-footer">
          <span class="end-marker">已显示全部搜索结果</span>
        </div>
      </div>

      <!-- 用户结果 -->
      <div v-if="searchTab === 'user'" class="user-results">
        <div v-if="userLoading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon> 正在搜索用户...
        </div>

        <div v-if="!userLoading && userResults.length > 0" class="user-cards">
          <div
            v-for="user in userResults"
            :key="user.id"
            class="user-card"
            @click="router.push(`/user/${encodeUserId(user.id)}`)"
          >
            <el-avatar :size="48" :src="publicAssetUrl(user.avatar)" class="user-card-avatar">
              {{ user.nickname?.charAt(0) || '?' }}
            </el-avatar>
            <div class="user-card-info">
              <div class="user-card-name">
                <span class="nickname">{{ user.nickname }}</span>
                <span class="username">@{{ user.username }}</span>
              </div>
              <p v-if="user.bio" class="user-card-bio">{{ user.bio }}</p>
              <div class="user-card-stats">
                <span v-if="user.school" class="stat-item school">{{ user.school }}</span>
                <span class="stat-item">帖子 {{ user.postCount }}</span>
                <span class="stat-item">粉丝 {{ user.followerCount }}</span>
              </div>
            </div>
          </div>
        </div>

        <EmptyState
          v-if="!userLoading && userResults.length === 0 && normalizedQuery"
          title="没有找到相关用户"
          description="换个关键词试试？"
        />
      </div>

      <!-- 标签结果 -->
      <div v-if="searchTab === 'tag'" class="tag-results">
        <div v-if="tagLoading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon> 正在搜索标签...
        </div>

        <div v-if="!tagLoading && tagResults.length > 0" class="tag-cards">
          <div
            v-for="tag in tagResults"
            :key="tag.id"
            class="tag-card"
            @click="router.push(`/tag/${encodeURIComponent(tag.name)}`)"
          >
            <div class="tag-card-name"># {{ tag.name }}</div>
            <div class="tag-card-meta">
              <span v-if="tag.heat" class="tag-heat">热度 {{ tag.heat }}</span>
              <span v-if="tag.postCount" class="tag-post-count">{{ tag.postCount }} 篇帖子</span>
            </div>
          </div>
        </div>

        <EmptyState
          v-if="!tagLoading && tagResults.length === 0 && normalizedQuery"
          title="没有找到相关标签"
          description="换个关键词试试？"
        />
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.search-page-container {
  width: 100%;
}

.search-hero {
  padding: 22px;
  margin-bottom: 22px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 12px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 5%, var(--cp-bg-card, var(--el-bg-color-overlay))), var(--cp-bg-card, var(--el-bg-color-overlay)));
  box-shadow: var(--shadow-soft, 0 1px 3px rgba(15, 23, 42, 0.05));
}

.search-back-button {
  margin-bottom: 12px;
}

.search-title {
  font-size: 28px;
  font-weight: 900;
  color: var(--el-text-color-primary);
  margin-bottom: 22px;
}

.search-bar-wrapper {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

.main-search-input {
  flex: 1;
}

.main-search-input :deep(.el-input__wrapper) {
  border-radius: 8px 0 0 8px;
}

.main-search-input :deep(.el-input-group__append) {
  border-radius: 0 8px 8px 0;
  background-color: var(--cp-primary, var(--el-color-primary));
  color: #fff;
  border-color: var(--cp-primary, var(--el-color-primary));
}

.filter-toggle {
  height: 40px;
  width: 40px;
}

.filter-panel {
  background-color: var(--cp-bg-surface, var(--el-fill-color-light));
  border-radius: 8px;
  padding: 18px;
  margin-top: 12px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item .label {
  font-size: 12px;
  font-weight: 800;
  color: var(--cp-text-muted, var(--el-text-color-secondary));
  text-transform: uppercase;
  letter-spacing: 0;
}

.filter-footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.suggest-panel {
  background-color: var(--cp-bg-card, var(--el-bg-color-overlay));
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  padding: 12px 14px;
  margin-top: 10px;
}

.suggest-block + .suggest-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.suggest-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.suggest-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}

.suggest-chips {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.suggest-actions {
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.suggest-action {
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  background: var(--cp-bg-surface, var(--el-fill-color-lighter));
  color: var(--el-text-color-regular);
  border-radius: 8px;
  padding: 8px 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.suggest-action:hover {
  border-color: color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 34%, var(--cp-border, var(--el-border-color-light)));
  color: var(--cp-primary-dark, var(--el-color-primary));
  background: var(--cp-hover-strong, var(--el-color-primary-light-9));
}

.results-info {
  margin-bottom: 20px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.agent-panel {
  margin-bottom: 22px;
  padding: 18px 20px;
  border: 1px solid color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 24%, var(--cp-border, var(--el-border-color-lighter)));
  border-radius: 12px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 6%, var(--cp-bg-card, var(--el-bg-color-overlay))), var(--cp-bg-card, var(--el-bg-color-overlay)));
  box-shadow: var(--shadow-soft, 0 1px 3px rgba(15, 23, 42, 0.05));
}

.agent-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.agent-panel-copy {
  min-width: 0;
}

.agent-panel-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 800;
  color: var(--cp-primary-dark, var(--el-color-primary));
  text-transform: uppercase;
  letter-spacing: 0;
}

.agent-panel-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
}

.agent-panel-desc {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.agent-panel-pill-row {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-panel-pill,
.agent-panel-feature {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
}

.agent-panel-pill {
  border: 1px solid color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 20%, var(--cp-border, var(--el-border-color-light)));
  background: var(--cp-bg-surface, var(--el-bg-color));
  color: var(--el-text-color-secondary);
}

.agent-panel-pill strong {
  color: var(--el-text-color-primary);
}

.agent-panel-cta {
  flex-shrink: 0;
  min-height: 40px;
}

.agent-inline-state {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--cp-bg-surface, var(--el-bg-color));
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 700;
}

.agent-inline-state.error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--el-color-danger);
}

.agent-skeleton-line {
  height: 12px;
  width: 62%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--el-fill-color-light), var(--el-fill-color), var(--el-fill-color-light));
  background-size: 220% 100%;
  animation: agent-shimmer 1.2s ease-in-out infinite;
}

.agent-skeleton-line + .agent-skeleton-line {
  margin-top: 10px;
}

.agent-skeleton-line.wide {
  width: 88%;
}

.agent-answer-stack {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.agent-answer-card {
  border: 1px solid color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 22%, var(--cp-border, var(--el-border-color-lighter)));
  border-radius: 10px;
  background: var(--cp-bg-surface, var(--el-bg-color));
  padding: 14px 16px;
}

.agent-answer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 900;
}

.agent-answer-meta {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.agent-answer-meta small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.agent-answer-body {
  margin-top: 10px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.8;
}

.agent-answer-body p {
  margin: 0;
}

.agent-answer-body p + p {
  margin-top: 8px;
}

.agent-answer-note {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--cp-border, var(--el-border-color-lighter));
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.agent-results {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(260px, 0.9fr);
  gap: 12px;
}

.agent-result-group {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.agent-result-heading {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 800;
}

.agent-result-heading small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.agent-citation-card,
.agent-related-row {
  width: 100%;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--cp-bg-surface, var(--el-bg-color));
  color: var(--el-text-color-primary);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background-color 0.18s ease, transform 0.18s ease;
}

.agent-citation-card:hover,
.agent-related-row:hover {
  border-color: color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 38%, var(--cp-border, var(--el-border-color-light)));
  background: color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 5%, var(--cp-bg-surface, var(--el-bg-color)));
  transform: translateY(-1px);
}

.agent-citation-card {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
}

.agent-citation-meta {
  color: var(--cp-primary-dark, var(--el-color-primary));
  font-size: 11px;
  font-weight: 800;
}

.agent-citation-card strong,
.agent-related-row strong {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-citation-card em,
.agent-related-row em {
  display: -webkit-box;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-style: normal;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.agent-related-row {
  min-height: 58px;
  padding: 9px 11px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.agent-related-row span {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.agent-related-row small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 800;
}

.agent-panel-footer {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--cp-bg-surface, var(--el-bg-color));
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.agent-panel-hint {
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.agent-panel-hint-label {
  color: var(--cp-primary-dark, var(--el-color-primary));
  font-weight: 700;
}

.agent-panel-features {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.agent-panel-feature {
  background: color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 10%, var(--cp-bg-card, var(--el-bg-color-overlay)));
  color: var(--cp-primary-dark, var(--el-color-primary));
}

.agent-panel-highlight {
  color: var(--el-text-color-primary);
  font-weight: 700;
}

.highlight {
  color: var(--el-color-primary);
  font-weight: 800;
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.infinite-sentinel {
  height: 1px;
  width: 100%;
}

.loading-state {
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-placeholder);
  font-weight: 700;
}

.search-examples {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 16px;
  flex-wrap: wrap;
}

.search-empty-recovery {
  width: min(100%, 640px);
  margin-top: 12px;
  display: grid;
  gap: 14px;
}

.search-empty-summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.search-empty-pill {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 20%, var(--cp-border, var(--el-border-color-light)));
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  color: var(--el-text-color-secondary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
}

.search-empty-pill strong {
  color: var(--el-text-color-primary);
}

.search-empty-note {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}

.search-empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.clickable-tag {
  cursor: pointer;
}

.clickable-tag.hot {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger-light-5);
}

.pagination-footer {
  text-align: center;
  padding: 40px 0;
}

.end-marker {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

@keyframes agent-shimmer {
  0% {
    background-position: 120% 0;
  }
  100% {
    background-position: -120% 0;
  }
}

:deep(.el-select) {
  width: 100%;
}

.search-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  padding: 4px;
  background: var(--cp-bg-surface, var(--el-fill-color-light));
  border-radius: 10px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
}

.search-tab {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.search-tab:hover {
  color: var(--el-text-color-primary);
}

.search-tab.active {
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  color: var(--cp-primary-dark, var(--el-color-primary));
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.user-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card {
  display: flex;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.user-card:hover {
  border-color: color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 34%, var(--cp-border, var(--el-border-color-light)));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.user-card-avatar {
  flex-shrink: 0;
}

.user-card-info {
  flex: 1;
  min-width: 0;
}

.user-card-name {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}

.user-card-name .nickname {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.user-card-name .username {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.user-card-bio {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 4px 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-weight: 600;
}

.user-card-stats .school {
  color: var(--el-color-primary);
}

.tag-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.tag-card {
  padding: 16px 18px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.tag-card:hover {
  border-color: color-mix(in srgb, var(--cp-primary, var(--el-color-primary)) 34%, var(--cp-border, var(--el-border-color-light)));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.tag-card-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}

.tag-card-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-weight: 600;
}

.tag-heat {
  color: var(--el-color-danger);
}

@media (max-width: 768px) {
  .search-hero {
    padding: 18px;
  }

  .agent-panel {
    padding: 18px;
  }

  .agent-panel-header {
    flex-direction: column;
    align-items: stretch;
  }

  .agent-results {
    grid-template-columns: 1fr;
  }

  .agent-answer-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .agent-answer-meta {
    justify-content: flex-start;
  }

  .agent-inline-state.error {
    flex-direction: column;
    align-items: flex-start;
  }

  .agent-panel-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .agent-panel-features {
    justify-content: flex-start;
  }

  .search-empty-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .suggest-actions {
    grid-template-columns: 1fr;
  }

  .tag-cards {
    grid-template-columns: 1fr;
  }

  .user-card-name {
    flex-direction: column;
    gap: 2px;
  }
}
</style>
