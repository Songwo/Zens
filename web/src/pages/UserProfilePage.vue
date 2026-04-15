<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import Avatar from '@/components/common/Avatar.vue'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { userApi, type UserPublicProfile } from '@/api/user'
import { followApi } from '@/api/follow'
import { postApi } from '@/api/post'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { getCardThemePalette } from '@/utils/cardTheme'
import { Document, Connection, Star } from '@element-plus/icons-vue'
import type { Post } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const targetUserId = computed(() => route.params.id as string)
const profile = ref<UserPublicProfile | null>(null)
const loading = ref(false)
const posts = ref<Post[]>([])
const postsLoading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const isFollowing = ref(false)
const followLoading = ref(false)

const isSelf = computed(() => userStore.userId === targetUserId.value || String(userStore.userInfo?.id) === targetUserId.value)

const profileCardStyle = computed(() => {
  const palette = getCardThemePalette((profile.value as any)?.profileCardTheme || 'sunset', 'sunset')
  const customBgUrl = String((profile.value as any)?.profileCardBgUrl || '').trim()
  const hasCustomBg = /^https?:\/\/[^"'\s]+$/.test(customBgUrl) || /^\/uploads\/[^"'\s]+$/.test(customBgUrl)
  return {
    '--cp-profile-card-bg': hasCustomBg
      ? `linear-gradient(135deg, rgba(255,255,255,0.74), rgba(255,255,255,0.74)), url("${customBgUrl}") center/cover no-repeat`
      : palette.background,
    '--cp-profile-card-border': palette.borderColor,
  } as Record<string, string>
})

const fetchProfile = async () => {
  loading.value = true
  try {
    const res = await userApi.getPublicProfile(targetUserId.value)
    profile.value = res.data
    if (userStore.accessToken && !isSelf.value) {
      const followRes = await followApi.isFollowing(targetUserId.value)
      isFollowing.value = followRes.data ?? false
    }
  } catch {
    ElMessage.error('获取用户信息失败')
  } finally {
    loading.value = false
  }
}

const fetchPosts = async (reset = false) => {
  if (reset) {
    page.value = 1
    posts.value = []
    hasMore.value = true
  }
  if (!hasMore.value || postsLoading.value) return
  postsLoading.value = true
  try {
    const res = await postApi.searchList({
      page: page.value,
      pageSize: 10,
      needTotal: false,
      userId: targetUserId.value,
      status: 1
    })
    if (res.data.records.length > 0) {
      posts.value.push(...res.data.records)
      page.value++
    } else {
      hasMore.value = false
    }
    if (res.data.records.length < 10) hasMore.value = false
  } catch {
    ElMessage.error('获取帖子失败')
  } finally {
    postsLoading.value = false
  }
}

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

watch(targetUserId, () => {
  fetchProfile()
  fetchPosts(true)
})

onMounted(() => {
  fetchProfile()
  fetchPosts(true)
})
</script>

<template>
  <MainLayout>
    <div class="user-profile-container" v-loading="loading">
      <template v-if="profile">
        <el-card class="profile-card" :style="profileCardStyle" shadow="never">
          <div class="profile-flex">
            <div class="profile-info">
              <Avatar :src="profile.avatar ?? undefined" size="xl" />
              <div class="user-meta">
                <h1 class="nickname">{{ profile.nickname || profile.username }}</h1>
                <p class="username">@{{ profile.username }}</p>
                <p class="bio">{{ profile.bio || '这个人很懒，还没有写简介~' }}</p>
                <div class="meta-tags">
                  <el-tag v-if="profile.school" size="small" type="info" effect="plain">{{ profile.school }}</el-tag>
                  <el-tag v-if="profile.major" size="small" type="info" effect="plain">{{ profile.major }}</el-tag>
                </div>
              </div>
            </div>
            <div class="action-area">
              <el-button
                v-if="isSelf"
                @click="router.push('/me')"
              >我的主页</el-button>
              <el-button
                v-else
                :type="isFollowing ? 'default' : 'primary'"
                :icon="isFollowing ? undefined : Connection"
                :loading="followLoading"
                @click="toggleFollow"
              >{{ isFollowing ? '已关注' : '关注' }}</el-button>
            </div>
          </div>

          <div class="stats-row">
            <div class="stat-item">
              <span class="stat-value">{{ profile.postCount || 0 }}</span>
              <span class="stat-label">动态</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ profile.followingCount || 0 }}</span>
              <span class="stat-label">关注</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ profile.followerCount || 0 }}</span>
              <span class="stat-label">粉丝</span>
            </div>
          </div>
        </el-card>

        <div class="section-title">
          <el-icon><Document /></el-icon> 发布的帖子
        </div>

        <div class="posts-list">
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
          <div v-if="postsLoading" class="loading-state">加载中...</div>
          <EmptyState v-if="!postsLoading && posts.length === 0" title="暂无帖子" description="该用户还没有发布任何内容" />
          <div v-if="!postsLoading && hasMore && posts.length > 0" class="load-more">
            <el-button plain @click="fetchPosts(false)">加载更多</el-button>
          </div>
          <div v-if="!hasMore && posts.length > 0" class="no-more">没有更多了</div>
        </div>
      </template>

      <EmptyState v-else-if="!loading" title="用户不存在" description="该用户可能已注销或不存在" />
    </div>
  </MainLayout>
</template>

<style scoped>
.user-profile-container {
  max-width: 800px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 24px;
  border-radius: var(--el-border-radius-base);
  border-color: var(--cp-profile-card-border, var(--el-border-color));
  background: var(--cp-profile-card-bg, var(--el-bg-color));
}

.profile-card :deep(.el-card__body) {
  background: transparent;
}

.profile-flex {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.profile-info {
  display: flex;
  gap: 20px;
  align-items: center;
}

.user-meta .nickname {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.user-meta .username {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.user-meta .bio {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.meta-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.stats-row {
  display: flex;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 20px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-bottom: 16px;
}

.posts-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.loading-state {
  text-align: center;
  padding: 24px;
  color: var(--el-text-color-secondary);
}

.load-more {
  text-align: center;
  padding: 16px 0;
}

.no-more {
  text-align: center;
  padding: 16px 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 640px) {
  .profile-flex {
    flex-direction: column;
    gap: 16px;
  }
}
</style>
