<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { postApi } from '@/api/post'
import { publicDataApi } from '@/api/publicData'
import type { Post } from '@/types'
import { ElMessage } from 'element-plus'
import { Search, Filter, Close, Loading } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const searchQuery = ref('')
const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const total = ref(0)
let fetchTimer: any = null

const filters = ref({
  category: '' as string | number,
  sortBy: 'relevance' as 'relevance' | 'new' | 'hot',
  timeRange: 'all' as 'all' | 'today' | 'week' | 'month'
})

const categories = ref<any[]>([])
const showFilters = ref(false)

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

const fetchPosts = async (reset = false) => {
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
  }

  if (!hasMore.value || loading.value) return

  loading.value = true
  try {
    const keyword = searchQuery.value.trim()
    const sectionId = filters.value.category ? Number(filters.value.category) : undefined
    const timeRange = filters.value.timeRange === 'all'
      ? undefined
      : filters.value.timeRange.toUpperCase()

    const res = await postApi.searchList({
      page: page.value,
      pageSize: 10,
      keyword,
      sectionId,
      orderBy: filters.value.sortBy,
      timeRange,
      status: 1
    })

    if (res.data.records.length > 0) {
      posts.value.push(...res.data.records)
      page.value++
      total.value = res.data.total
    } else {
      hasMore.value = false
    }

    if (posts.value.length >= res.data.total) {
      hasMore.value = false
    }
  } catch (error) {
    ElMessage.error('搜索内容获取失败')
  } finally {
    loading.value = false
  }
}

const scheduleFetch = (reset = true) => {
  clearTimeout(fetchTimer)
  fetchTimer = setTimeout(() => {
    fetchPosts(reset)
  }, 250)
}

const handleSearch = () => {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入关键词再搜搜看')
    return
  }
  const q = searchQuery.value.trim()
  router.push({ path: '/search', query: { q } })
  scheduleFetch(true)
}

const clearFilters = () => {
  filters.value = {
    category: '',
    sortBy: 'relevance',
    timeRange: 'all'
  }
  scheduleFetch(true)
}

const hasActiveFilters = () => {
  return filters.value.category || filters.value.sortBy !== 'relevance' || filters.value.timeRange !== 'all'
}

watch(() => route.query.q, (newQuery) => {
  if (newQuery) {
    searchQuery.value = newQuery as string
    scheduleFetch(true)
  }
})

watch(() => [filters.value.category, filters.value.sortBy, filters.value.timeRange], () => {
  scheduleFetch(true)
})

onMounted(() => {
  fetchCategories()
  if (route.query.q) {
    searchQuery.value = route.query.q as string
    scheduleFetch(true)
  }
})

onUnmounted(() => {
  clearTimeout(fetchTimer)
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

        <!-- Filters Expansion -->
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
            <div v-if="hasActiveFilters()" class="filter-footer">
              <el-link type="primary" :icon="Close" @click="clearFilters" :underline="false">清除所有筛选</el-link>
            </div>
          </div>
        </el-collapse-transition>
      </div>

      <!-- Results Info -->
      <div v-if="searchQuery" class="results-info">
        <p v-if="posts.length > 0">
          为您找到相关结果约 <span class="highlight">{{ total }}</span> 个
        </p>
      </div>

      <!-- Posts List -->
      <div class="posts-list">
        <PostCard v-for="post in posts" :key="post.id" :post="post" />

        <div v-if="loading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon> 正在为您搜寻...
        </div>

        <EmptyState
          v-if="!loading && posts.length === 0 && searchQuery"
          title="换个姿势再搜一次？"
          description="没有找到相关内容，试着精简关键词或调整筛选条件"
        />

        <EmptyState
          v-if="!loading && !searchQuery"
          title="想搜点什么？"
          description="在这里输入你感兴趣的话题、标签或同学"
        >
          <div class="search-examples">
            <el-tag 
              v-for="ex in ['学习打卡', '校园生活', '寻物启事']" 
              :key="ex" 
              class="clickable-tag"
              @click="searchQuery = ex; handleSearch()"
            >
              {{ ex }}
            </el-tag>
          </div>
        </EmptyState>

        <!-- Load More -->
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
  margin-bottom: 40px;
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
  margin-bottom: 20px;
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
}

.clickable-tag {
  cursor: pointer;
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
</style>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
