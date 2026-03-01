<script setup lang="ts">
import PostCard from '@/components/PostCard.vue'

defineProps<{
  posts: any[]
  loading: boolean
  hasMore: boolean
}>()

const emit = defineEmits(['loadMore'])

const handleLoadMore = () => {
  emit('loadMore')
}
</script>

<template>
  <div class="post-list-container">
    <!-- Skeleton Loading -->
    <template v-if="loading && posts.length === 0">
      <el-skeleton v-for="i in 5" :key="i" animated class="mb-4">
        <template #template>
          <div class="skeleton-card">
            <div class="flex-row gap-3 mb-3">
              <el-skeleton-item variant="circle" style="width: 32px; height: 32px" />
              <div class="flex-col gap-1">
                <el-skeleton-item variant="text" style="width: 100px" />
                <el-skeleton-item variant="text" style="width: 60px" />
              </div>
            </div>
            <el-skeleton-item variant="p" style="width: 80%" />
            <el-skeleton-item variant="p" style="width: 60%" />
            <div class="flex-row gap-3 mt-4">
              <el-skeleton-item variant="text" style="width: 40px" />
              <el-skeleton-item variant="text" style="width: 40px" />
            </div>
          </div>
        </template>
      </el-skeleton>
    </template>

    <!-- Post Stream -->
    <template v-else-if="posts.length > 0">
      <PostCard
        v-for="post in posts"
        :key="post.id"
        :post="post"
      />
      
      <!-- Load More Action -->
      <div v-if="hasMore" class="load-more-wrapper">
        <el-button 
          :loading="loading" 
          @click="handleLoadMore" 
          class="load-more-btn"
          round
        >
          {{ loading ? '加载中...' : '加载更多' }}
        </el-button>
      </div>

      <div v-else class="end-msg">
        <el-divider border-style="dashed">已经到底了</el-divider>
      </div>
    </template>

    <!-- Empty State -->
    <template v-else>
      <el-empty description="暂无内容，去其他板块逛逛吧" :image-size="120" />
    </template>
  </div>
</template>

<style scoped>
.post-list-container {
  display: flex;
  flex-direction: column;
}

.skeleton-card {
  padding: 20px;
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.load-more-wrapper {
  padding: 24px 0;
  text-align: center;
}

.load-more-btn {
  width: 200px;
}

.end-msg {
  padding: 24px 0;
}

.end-msg :deep(.el-divider__text) {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  background-color: transparent;
}
</style>
