<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import MainLayout from '@/layouts/MainLayout.vue'
import ProfileHeader from '@/components/profile/ProfileHeader.vue'
import ProfilePostList from '@/components/profile/ProfilePostList.vue'
import RelationsPanel from '@/components/profile/RelationsPanel.vue'
import CreatorPanel from '@/components/profile/CreatorPanel.vue'
import { postApi } from '@/api/post'
import { userApi } from '@/api/user'
import { levelApi, type LevelInfo } from '@/api/level'
import { usePostComposerStore } from '@/store/postComposer'
import { ElMessage } from 'element-plus'
import type { Post } from '@/types'
import type { CoverConfig } from '@/utils/coverConfig'

const router = useRouter(); const route = useRoute()
const userStore = useUserStore(); const composerStore = usePostComposerStore()

const activeTab = ref<'posts' | 'favorites' | 'relations' | 'creator'>(
  (route.query.tab as any) || 'posts')
const relationsInitialSub = ref<'following' | 'followers' | 'tags'>(
  (route.query.sub as any) || 'following')

const stats = ref<{ postCount: number; followingCount: number; followerCount: number }>(
  { postCount: 0, followingCount: 0, followerCount: 0 })
const levelInfo = ref<LevelInfo | null>(null)

const headerProfile = computed(() => ({
  id: String(userStore.userInfo?.id ?? userStore.userId ?? ''),
  username: userStore.userInfo?.username ?? '',
  nickname: userStore.userInfo?.nickname,
  avatar: userStore.userInfo?.avatar,
  bio: userStore.userInfo?.bio,
  school: userStore.userInfo?.school,
  major: userStore.userInfo?.major,
  enrollmentYear: userStore.userInfo?.enrollmentYear,
  interestTags: userStore.userInfo?.interestTags,
  level: levelInfo.value?.level ?? userStore.userInfo?.level,
  roles: userStore.userInfo?.roles,
  badgeText: userStore.userInfo?.badgeText,
  badgeColor: userStore.userInfo?.badgeColor,
  badgeStyle: userStore.userInfo?.badgeStyle,
  profileCardBgUrl: userStore.userInfo?.profileCardBgUrl,
  coverConfig: userStore.userInfo?.coverConfig,
  postCount: stats.value.postCount,
  followingCount: stats.value.followingCount,
  followerCount: stats.value.followerCount,
}))
const levelHint = computed(() => levelInfo.value
  ? `距 Lv.${levelInfo.value.level + 1} 还差 ${Math.max(0, levelInfo.value.nextLevelExp - levelInfo.value.experience)} 经验`
  : '')

const postsFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: headerProfile.value.id, status: 1 }).then(r => r.data.records as Post[])
const favFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, collectedBy: headerProfile.value.id, status: 1 }).then(r => r.data.records as Post[])

const onTab = (name: string | number) => { router.replace({ query: { ...route.query, tab: String(name) } }) }
const onStatClick = (type: 'following' | 'followers') => {
  relationsInitialSub.value = type
  activeTab.value = 'relations'
  router.replace({ query: { ...route.query, tab: 'relations', sub: type } })
}

const fetchStats = async () => { try { const r = await userApi.getProfileStats(); if (r.data) stats.value = { postCount: r.data.postCount ?? 0, followingCount: r.data.followingCount ?? 0, followerCount: r.data.followerCount ?? 0 } } catch { ElMessage.error('获取统计失败') } }
const fetchLevel = async () => { try { levelInfo.value = (await levelApi.getInfo()).data } catch { /* ignore */ } }

const handleSaveCover = async (cfg: CoverConfig) => {
  try {
    const res = await userApi.updateCover(cfg)
    if (userStore.userInfo) {
      userStore.userInfo.coverConfig = res.data ?? JSON.stringify(cfg)
    }
    ElMessage.success('封面已更新')
  } catch (e) {
    ElMessage.error('封面保存失败')
    throw e
  }
}

onMounted(() => {
  if (!userStore.accessToken) { ElMessage.error('请先登录'); router.push('/auth/login'); return }
  fetchStats(); fetchLevel()
})
watch(() => route.query.tab, t => { if (t && typeof t === 'string') activeTab.value = t as any })
</script>

<template>
  <MainLayout>
    <div class="me-container">
      <ProfileHeader
        :profile="headerProfile"
        variant="self"
        :level-progress="levelInfo?.progress ?? null"
        :level-hint="levelHint"
        :editable="true"
        :on-save-cover="handleSaveCover"
        @edit="router.push('/settings')"
        @compose="composerStore.open()"
        @stat-click="onStatClick"
      />

      <el-tabs v-model="activeTab" class="me-tabs" @tab-change="onTab">
        <el-tab-pane name="posts" label="动态">
          <ProfilePostList :fetcher="postsFetcher" empty-title="还没有发布动态" empty-description="快去分享你的第一篇校园见闻吧！" />
        </el-tab-pane>
        <el-tab-pane name="favorites" label="收藏">
          <ProfilePostList :fetcher="favFetcher" empty-title="暂无收藏" empty-description="去发现感兴趣的内容并收藏吧！" />
        </el-tab-pane>
        <el-tab-pane name="relations" label="关系">
          <RelationsPanel v-if="activeTab === 'relations'" :initial-sub="relationsInitialSub" />
        </el-tab-pane>
        <el-tab-pane name="creator" label="创作管理">
          <CreatorPanel v-if="activeTab === 'creator'" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </MainLayout>
</template>

<style scoped>
.me-container { width: 100%; }
.me-tabs { margin-top: 8px; }
.me-tabs :deep(.el-tabs__active-bar) { background-color: var(--el-color-primary); }
@media (max-width: 640px) {
  .me-tabs :deep(.el-tabs__nav-wrap) { overflow-x: auto; }
  .me-tabs :deep(.el-tabs__nav) { white-space: nowrap; }
}
</style>
