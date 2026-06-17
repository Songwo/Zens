<script setup lang="ts">
import { ref, watch, onMounted, type Component } from 'vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PostListSkeleton from '@/components/common/PostListSkeleton.vue'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import type { Post } from '@/types'

const props = defineProps<{
  // 给定页码，返回该页记录；约定每页 10 条
  fetcher: (page: number) => Promise<Post[]>
  emptyTitle: string
  emptyDescription?: string
  emptyIcon?: Component
  // 任一 reloadKey 变化即重置重拉（如切换二级分段）
  reloadKey?: string | number
}>()

const posts = ref<Post[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)

const load = async (reset = false) => {
  if (reset) { page.value = 1; posts.value = []; hasMore.value = true }
  if (!hasMore.value || loading.value) return
  loading.value = true
  try {
    const records = await props.fetcher(page.value)
    if (records.length > 0) { posts.value.push(...records); page.value++ }
    if (records.length < 10) hasMore.value = false
  } finally { loading.value = false }
}

const remove = (id: string) => { posts.value = posts.value.filter(p => p.id !== id) }

const { sentinel } = useInfiniteScroll(() => load(false), {
  canLoadMore: () => hasMore.value && !loading.value && posts.value.length > 0,
})
void sentinel

onMounted(() => load(true))
watch(() => props.reloadKey, () => load(true))
defineExpose({ reload: () => load(true) })
</script>

<template>
  <div class="profile-post-list">
    <PostCard v-for="post in posts" :key="post.id" :post="post" @deleted="remove" @restored="remove" />
    <PostListSkeleton v-if="loading && posts.length === 0" :count="3" />
    <EmptyState v-if="!loading && posts.length === 0"
      :icon="emptyIcon" :title="emptyTitle" :description="emptyDescription" />
    <div ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>
    <div v-if="!hasMore && posts.length > 0" class="no-more">没有更多了</div>
  </div>
</template>

<style scoped>
.profile-post-list { display: flex; flex-direction: column; gap: 16px; }
.infinite-sentinel { height: 1px; width: 100%; }
.no-more { text-align: center; padding: 16px 0; font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
