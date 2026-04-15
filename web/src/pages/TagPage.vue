<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PostCard from '@/components/PostCard.vue'
import { postApi } from '@/api/post'
import { tagApi } from '@/api/tag'
import type { Post } from '@/types'
import { ElMessage } from 'element-plus'
import { CollectionTag, Star, StarFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()
const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const sortMode = ref<'new' | 'hot'>('hot')
const tagId = ref<number | null>(null)
const isFollowing = ref(false)
const followLoading = ref(false)

const tagName = computed(() => route.params.name as string)

const fetchPosts = async (reset = false) => {
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
  }

  if (!hasMore.value || loading.value) return

  loading.value = true
  try {
    const res = await postApi.searchList({
      page: page.value,
      pageSize: 10,
      needTotal: false,
      orderBy: sortMode.value,
      tag: tagName.value,
      status: 1
    })

    const records = res?.data?.records || []

    if (records.length > 0) {
      posts.value.push(...records)
      page.value++
    } else {
      hasMore.value = false
    }

    if (records.length < 10) {
      hasMore.value = false
    }
  } catch (error) {
    ElMessage.error('无法同步标签内容')
  } finally {
    loading.value = false
  }
}

const changeSort = (mode: any) => {
  sortMode.value = mode
  fetchPosts(true)
}

const loadTagInfo = async () => {
  if (!userStore.isLoggedIn) return
  try {
    // 通过搜索找到tagId
    const res = await tagApi.search(tagName.value)
    const found = res.data?.find((t: any) => t.name === tagName.value)
    if (found) {
      tagId.value = found.id
      const statusRes = await tagApi.getStatus(found.id)
      isFollowing.value = statusRes.data?.isFollowing ?? false
    }
  } catch {
    // ignore
  }
}

const toggleFollow = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再关注话题')
    return
  }
  if (!tagId.value) {
    ElMessage.warning('暂未找到该话题，无法关注')
    return
  }
  followLoading.value = true
  try {
    const res = await tagApi.toggleFollow(tagId.value)
    isFollowing.value = res.data?.isFollowing ?? !isFollowing.value
    ElMessage.success(isFollowing.value ? '已关注该话题，有新帖子时将收到通知' : '已取消关注')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    followLoading.value = false
  }
}

// Song：说明
watch(() => route.params.name, () => {
  tagId.value = null
  isFollowing.value = false
  fetchPosts(true)
  loadTagInfo()
})

onMounted(() => {
  fetchPosts(true)
  loadTagInfo()
})
</script>

<template>
  <MainLayout>
    <div class="tag-page-container">
      <!-- Tag Header -->
      <div class="tag-hero">
        <div class="tag-banner">
          <div class="tag-badge">
            <el-icon><CollectionTag /></el-icon>
          </div>
          <div class="tag-info">
            <h1 class="tag-name">#{{ tagName }}</h1>
            <p class="tag-meta">聚合属于这个关键词的校园瞬间</p>
            <div class="tag-stats">
               <el-tag size="small" effect="dark" round>{{ posts.length }} 份内容</el-tag>
            </div>
          </div>
          <el-button
            :type="isFollowing ? 'default' : 'primary'"
            :icon="isFollowing ? StarFilled : Star"
            round
            class="action-btn"
            :loading="followLoading"
            @click="toggleFollow"
          >{{ isFollowing ? '已关注' : '关注话题' }}</el-button>
        </div>
      </div>

      <!-- Feed Controls -->
      <div class="feed-controls">
         <el-radio-group v-model="sortMode" @change="changeSort" size="default">
           <el-radio-button label="hot" value="hot">热门话题</el-radio-button>
           <el-radio-button label="new" value="new">时间顺序</el-radio-button>
         </el-radio-group>
      </div>

      <!-- Posts -->
      <div class="posts-feed" v-loading="loading">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
        />

        <el-empty 
          v-if="!loading && posts.length === 0" 
          description="暂时没有发现与此话题相关的内容" 
        />

        <!-- Pagination -->
        <div v-if="!loading && hasMore && posts.length > 0" class="pagination-area">
          <el-button @click="fetchPosts(false)" class="load-more-btn" text bg>展开更多内容</el-button>
        </div>

        <div v-if="!hasMore && posts.length > 0" class="pagination-area">
          <span class="end-text">话题在这里靠岸了</span>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.tag-page-container {
  max-width: 800px;
  margin: 0 auto;
}

.tag-hero {
  margin-bottom: 24px;
}

.tag-banner {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 20px;
  padding: 32px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.tag-badge {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, var(--el-color-primary-light-3), var(--el-color-primary));
  color: white;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  box-shadow: 0 8px 16px var(--el-color-primary-light-8);
}

.tag-info {
  flex: 1;
}

.tag-name {
  font-size: 28px;
  font-weight: 900;
  color: var(--el-text-color-primary);
  margin: 0 0 4px 0;
  letter-spacing: -0.02em;
}

.tag-meta {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}

.action-btn {
  font-weight: 800;
  padding: 10px 24px;
}

.feed-controls {
  margin-bottom: 24px;
}

.posts-feed {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.pagination-area {
  padding: 40px 0;
  text-align: center;
}

.load-more-btn {
  width: 180px;
  font-weight: 700;
}

.end-text {
  font-size: 12px;
  font-weight: 800;
  color: var(--el-text-color-placeholder);
  text-transform: uppercase;
  letter-spacing: 0.15em;
}

@media (max-width: 640px) {
  .tag-banner {
    flex-direction: column;
    text-align: center;
    padding: 24px;
  }
  .tag-badge {
    margin: 0 auto;
  }
  .action-btn {
    width: 100%;
  }
}
</style>

<style scoped>
.tag-page-container {
  max-width: 896px;
  margin: 0 auto;
  padding: 24px 16px;
}

.tag-header {
  margin-bottom: 32px;
  padding: 24px;
  background: linear-gradient(135deg, #ecf5ff, #f0f9ff);
  border-radius: 16px;
  border: 1px solid #d9ecff;
}

.header-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.header-info {
  flex: 1;
}

.tag-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.tag-icon-wrap {
  width: 48px;
  height: 48px;
  background-color: var(--el-color-primary);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
}
.text-white {
  color: white;
}

.tag-title {
  font-size: 24px;
  font-weight: 900;
  color: #303133;
  margin: 0;
  letter-spacing: -0.5px;
}

.tag-stats {
  font-size: 12px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 16px;
}

.follow-btn {
  font-weight: bold;
  border-radius: 12px;
}

.sort-tabs {
  margin-bottom: 24px;
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}

.load-more-wrapper {
  padding: 32px 0;
  text-align: center;
}
.load-more-btn {
  width: 200px;
  font-weight: bold;
}

.end-msg {
  padding: 32px 0;
  color: #c0c4cc;
}
.end-msg :deep(.el-divider__text) {
  color: #c0c4cc;
  font-size: 12px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 1px;
}
</style>
