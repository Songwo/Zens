<script setup lang="ts">
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import ProfileHeader from '@/components/profile/ProfileHeader.vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PostListSkeleton from '@/components/common/PostListSkeleton.vue'
import { userApi, type UserPublicProfile } from '@/api/user'
import { followApi } from '@/api/follow'
import { postApi } from '@/api/post'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { usePostComposerStore } from '@/store/postComposer'
import type { Post } from '@/types'
import { cachedRequest } from '@/utils/requestCache'
import { decodeUserId, encodeUserId } from '@/utils/shortId'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()

const targetUserId = computed(() => {
  const rawId = typeof route.params.id === 'string' ? route.params.id.trim() : ''
  return rawId.startsWith('u') ? decodeUserId(rawId) : rawId
})

watch(
  () => route.params.id,
  (rawId) => {
    if (rawId && /^\d+$/.test(String(rawId))) {
      const shortId = encodeUserId(String(rawId))
      router.replace(`/user/${shortId}`)
    }
  },
  { immediate: true }
)
const profile = ref<UserPublicProfile | null>(null)
const loading = ref(false)
const posts = ref<Post[]>([])
const postsLoading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const isFollowing = ref(false)
const followLoading = ref(false)
const USER_PROFILE_CACHE_TTL = 60 * 1000
let profileRequestId = 0
let postsRequestId = 0

const isSelf = computed(() => userStore.userId === targetUserId.value || String(userStore.userInfo?.id) === targetUserId.value)

const isUserNotFoundError = (error: any) => {
  const message = String(error?.message || '').trim()
  return message.includes('用户不存在')
}

const fetchProfile = async () => {
  const userId = targetUserId.value
  if (!userId) {
    profile.value = null
    isFollowing.value = false
    loading.value = false
    return
  }
  loading.value = true
  const requestId = ++profileRequestId
  try {
    const profileRes = await cachedRequest(
      `user:public:${userId}`,
      USER_PROFILE_CACHE_TTL,
      () => userApi.getPublicProfile(userId)
    )
    if (requestId !== profileRequestId || userId !== targetUserId.value) {
      return
    }
    profile.value = profileRes.data
    if (userStore.isLoggedIn && !isSelf.value) {
      try {
        const followRes = await followApi.isFollowing(userId)
        if (requestId !== profileRequestId || userId !== targetUserId.value) {
          return
        }
        isFollowing.value = followRes?.data ?? false
      } catch {
        isFollowing.value = false
      }
    } else {
      isFollowing.value = false
    }
  } catch (error: any) {
    if (requestId === profileRequestId && userId === targetUserId.value) {
      profile.value = null
      isFollowing.value = false
      posts.value = []
      hasMore.value = false
      postsLoading.value = false
      postsRequestId++
      if (!isUserNotFoundError(error)) {
        ElMessage.error('获取用户信息失败')
      }
    }
  } finally {
    if (requestId === profileRequestId) {
      loading.value = false
    }
  }
}

const fetchPosts = async (reset = false) => {
  const userId = targetUserId.value
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
  }
  if (!userId) {
    posts.value = []
    hasMore.value = false
    postsLoading.value = false
    return
  }
  if (!hasMore.value || postsLoading.value) return
  postsLoading.value = true
  const requestId = ++postsRequestId
  try {
    const requestPayload = {
      page: page.value,
      pageSize: 10,
      needTotal: false,
      userId,
      status: 1
    }
    const res = await cachedRequest(
      `user:posts:${userId}:${page.value}:${requestPayload.pageSize}`,
      USER_PROFILE_CACHE_TTL,
      () => postApi.searchList(requestPayload)
    )
    if (requestId !== postsRequestId || userId !== targetUserId.value) {
      return
    }
    if (res.data.records.length > 0) {
      posts.value.push(...res.data.records)
      page.value++
    } else {
      hasMore.value = false
    }
    if (res.data.records.length < 10) hasMore.value = false
  } catch {
    if (requestId === postsRequestId && userId === targetUserId.value) {
      ElMessage.error('获取帖子失败')
    }
  } finally {
    if (requestId === postsRequestId) {
      postsLoading.value = false
    }
  }
}

// Song：帖子列表无限滚动
const { sentinel } = useInfiniteScroll(() => fetchPosts(false), {
  canLoadMore: () => hasMore.value && !postsLoading.value && posts.value.length > 0,
})
void sentinel

const toggleFollow = async () => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  followLoading.value = true
  try {
    if (isFollowing.value) {
      await followApi.unfollow(targetUserId.value)
      isFollowing.value = false
      if (profile.value) profile.value.followerCount = Math.max(0, profile.value.followerCount - 1)
      ElMessage.success('已取消关注')
    } else {
      await followApi.follow(targetUserId.value)
      isFollowing.value = true
      if (profile.value) profile.value.followerCount++
      ElMessage.success('关注成功')
    }
  } catch {
    ElMessage.error('操作失败')
  } finally {
    followLoading.value = false
  }
}

const goMessage = () => {
  if (!profile.value) return
  router.push({ path: '/messages', query: {
    peerId: profile.value.id,
    peerName: profile.value.nickname || profile.value.username,
    peerAvatar: profile.value.avatar || '',
  }})
}

watch(targetUserId, () => {
  if (!targetUserId.value) {
    profile.value = null
    posts.value = []
    hasMore.value = false
    loading.value = false
    postsLoading.value = false
    isFollowing.value = false
    return
  }
  fetchProfile()
  fetchPosts(true)
})

onMounted(() => {
  if (!targetUserId.value) {
    hasMore.value = false
    return
  }
  fetchProfile()
  fetchPosts(true)
})

onBeforeUnmount(() => {
  profileRequestId++
  postsRequestId++
})
</script>

<template>
  <MainLayout>
    <div class="user-profile-container">
      <!-- Song：首屏骨架屏（加载中且尚无资料） -->
      <template v-if="loading && !profile">
        <el-skeleton animated class="profile-card-skeleton">
          <template #template>
            <div class="profile-skeleton-head">
              <el-skeleton-item variant="circle" style="width: 64px; height: 64px" />
              <div class="profile-skeleton-meta">
                <el-skeleton-item variant="h3" style="width: 160px" />
                <el-skeleton-item variant="text" style="width: 100px" />
                <el-skeleton-item variant="text" style="width: 220px" />
              </div>
            </div>
          </template>
        </el-skeleton>
        <PostListSkeleton :count="3" class="profile-posts-skeleton" />
      </template>

      <template v-else-if="profile">
        <PageBackButton class="profile-back-button" fallback="/" />
        <ProfileHeader
          v-if="!isSelf"
          :profile="profile"
          variant="other"
          :is-following="isFollowing"
          :follow-loading="followLoading"
          @follow="toggleFollow"
          @message="goMessage"
        />
        <ProfileHeader
          v-else
          :profile="profile"
          variant="self"
          @edit="router.push('/settings')"
          @compose="composerStore.open()"
          @stat-click="() => router.push('/me')"
        />

        <div class="posts-list">
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
          <PostListSkeleton v-if="postsLoading && posts.length === 0" :count="3" />
          <EmptyState v-if="!postsLoading && posts.length === 0" title="暂无帖子" description="该用户还没有发布任何内容" />
          <!-- Song：无限滚动哨兵 -->
          <div ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>
          <div v-if="!hasMore && posts.length > 0" class="no-more">没有更多了</div>
        </div>
      </template>

      <EmptyState v-else-if="!loading" title="用户不存在" description="该用户可能已注销或不存在" />
    </div>
  </MainLayout>
</template>

<style scoped>
.user-profile-container {
  width: 100%;
}

.profile-card-skeleton {
  margin-bottom: 24px;
  padding: 24px;
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.profile-skeleton-head {
  display: flex;
  gap: 20px;
  align-items: center;
}

.profile-skeleton-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.profile-posts-skeleton {
  margin-top: 8px;
}

.profile-back-button {
  margin-bottom: 12px;
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.no-more {
  text-align: center;
  padding: 16px 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.infinite-sentinel {
  height: 1px;
  width: 100%;
}
</style>
