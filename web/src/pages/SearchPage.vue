<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { postApi } from '@/api/post'
import { publicDataApi } from '@/api/publicData'
import { tagApi } from '@/api/tag'
import type { Post } from '@/types'
import { ElMessage } from 'element-plus'
import { Search, Filter, Close, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const SEARCH_HISTORY_KEY = 'cp:search:history'
const SEARCH_HISTORY_MAX = 12

const searchQuery = ref('')
const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const total = ref(0)
let fetchTimer: ReturnType<typeof setTimeout> | null = null
let suggestTimer: ReturnType<typeof setTimeout> | null = null
let suggestRequestId = 0
let searchAbortController: AbortController | null = null
let suggestAbortController: AbortController | null = null

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

const normalizedQuery = computed(() => searchQuery.value.trim())

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
      categories.value = res.data || []
    }
  } catch (error) {
    console.error('Failed to fetch sections:', error)
  }
}

const fetchHotSuggestions = async () => {
  try {
    const res = await tagApi.getHotTags(12)
    const list = Array.isArray(res.data) ? res.data : []
    hotSuggestions.value = list
      .map((item) => item?.name?.trim())
      .filter((name): name is string => Boolean(name))
      .slice(0, 12)
  } catch {
    hotSuggestions.value = ['学习打卡', '校园生活', '求职实习', '开源项目', '社团活动', '二手交易']
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
    const res = await tagApi.search(normalizedKeyword, { signal: controller.signal })
    if (requestId !== suggestRequestId) {
      return
    }

    const list = Array.isArray(res.data) ? res.data : []
    dynamicSuggestions.value = list
      .map((item) => item?.name?.trim())
      .filter((name): name is string => Boolean(name))
      .slice(0, 8)
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

    const res = await postApi.searchList({
      page: page.value,
      pageSize: 10,
      needTotal: true,
      keyword,
      sectionId,
      orderBy: filters.value.sortBy,
      timeRange,
      status: 1
    }, { signal: controller.signal })

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

const scheduleFetch = (reset = true) => {
  if (fetchTimer) {
    clearTimeout(fetchTimer)
  }
  fetchTimer = setTimeout(() => {
    fetchPosts(reset)
  }, 250)
}

const triggerSearch = (keyword?: string) => {
  const q = (keyword ?? normalizedQuery.value).trim()
  if (!q) {
    ElMessage.warning('请输入关键词再搜搜看')
    return
  }

  searchQuery.value = q
  pushSearchHistory(q)
  router.push({ path: '/search', query: { q } })
  scheduleFetch(true)
}

const handleSearch = () => {
  triggerSearch()
}

const handleSuggestionSelect = (term: string) => {
  triggerSearch(term)
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
})

watch(() => route.query.q, (newQuery) => {
  const q = typeof newQuery === 'string' ? newQuery.trim() : ''
  searchQuery.value = q
  if (q) {
    scheduleFetch(true)
  } else {
    resetSearchResultState()
  }
})

watch(() => [filters.value.category, filters.value.sortBy, filters.value.timeRange], () => {
  if (normalizedQuery.value) {
    scheduleFetch(true)
  }
})

onMounted(() => {
  loadSearchHistory()
  fetchCategories()
  fetchHotSuggestions()

  const q = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (q) {
    searchQuery.value = q
    scheduleFetch(true)
  }
})

onUnmounted(() => {
  searchAbortController?.abort()
  suggestAbortController?.abort()

  if (fetchTimer) {
    clearTimeout(fetchTimer)
    fetchTimer = null
  }
  if (suggestTimer) {
    clearTimeout(suggestTimer)
    suggestTimer = null
  }
})
</script>

<template>
  <MainLayout>
    <div class="search-page-container">
      <div class="search-hero">
        <h1 class="search-title">探索校园</h1>
        <div class="search-bar-wrapper">
          <el-input
            v-model="searchQuery"
            placeholder="搜索话题、用户、甚至是奇思妙想..."
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
          <div v-show="showFilters" class="filter-panel">
            <el-row :gutter="20">
              <el-col :span="8" :xs="24">
                <div class="filter-item">
                  <span class="label">板块分类</span>
                  <el-select v-model="filters.category" placeholder="全部板块" clearable>
                    <el-option label="全部板块" value="" />
                    <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
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
              <div class="suggest-title">热门搜索</div>
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

      <div v-if="normalizedQuery" class="results-info">
        <p v-if="posts.length > 0">
          为您找到相关结果约 <span class="highlight">{{ total }}</span> 个
        </p>
      </div>

      <div class="posts-list">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          :highlight-keyword="normalizedQuery"
        />

        <div v-if="loading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon> 正在为您搜寻...
        </div>

        <EmptyState
          v-if="!loading && posts.length === 0 && normalizedQuery"
          title="换个姿势再搜一次？"
          description="没有找到相关内容，试着精简关键词或调整筛选条件"
        />

        <EmptyState
          v-if="!loading && !normalizedQuery"
          title="想搜点什么？"
          description="在这里输入你感兴趣的话题、标签或同学"
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

        <div v-if="!hasMore && posts.length > 0" class="pagination-footer">
          <span class="end-marker">已显示全部搜索结果</span>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.search-page-container {
  max-width: 800px;
  margin: 0 auto;
}

.search-hero {
  margin-bottom: 26px;
}

.search-title {
  font-size: 32px;
  font-weight: 900;
  color: var(--el-text-color-primary);
  margin-bottom: 24px;
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
  border-radius: 12px 0 0 12px;
}

.main-search-input :deep(.el-input-group__append) {
  border-radius: 0 12px 12px 0;
  background-color: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}

.filter-toggle {
  height: 40px;
  width: 40px;
}

.filter-panel {
  background-color: var(--el-fill-color-light);
  border-radius: 16px;
  padding: 24px;
  margin-top: 12px;
  border: 1px solid var(--el-border-color-lighter);
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item .label {
  font-size: 12px;
  font-weight: 800;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.filter-footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.suggest-panel {
  background-color: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
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
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  border-radius: 10px;
  padding: 8px 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.suggest-action:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.results-info {
  margin-bottom: 20px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
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

:deep(.el-select) {
  width: 100%;
}

@media (max-width: 768px) {
  .suggest-actions {
    grid-template-columns: 1fr;
  }
}
</style>

