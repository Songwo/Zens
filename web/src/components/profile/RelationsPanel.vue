<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Avatar from '@/components/common/Avatar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { followApi } from '@/api/follow'
import { tagApi, type Tag } from '@/api/tag'
import { encodeUserId } from '@/utils/shortId'
import { ElMessage } from 'element-plus'
import { User, Connection, PriceTag } from '@element-plus/icons-vue'
import { invalidateCommunityContentCaches } from '@/utils/communityCache'

const props = withDefaults(defineProps<{ initialSub?: 'following' | 'followers' | 'tags' }>(), {
  initialSub: 'following',
})
const router = useRouter()
const sub = ref<'following' | 'followers' | 'tags'>(props.initialSub)
const options = [
  { label: '关注', value: 'following' },
  { label: '粉丝', value: 'followers' },
  { label: '话题', value: 'tags' },
]

const following = ref<any[]>([])
const followers = ref<any[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)

const loadFollowing = async () => { loading.value = true; try { following.value = (await followApi.getMyFollowing()).data ?? [] } finally { loading.value = false } }
const loadFollowers = async () => { loading.value = true; try { followers.value = (await followApi.getMyFollowers()).data ?? [] } finally { loading.value = false } }
const loadTags = async () => { loading.value = true; try { tags.value = (await tagApi.getMyFollowing()).data ?? [] } finally { loading.value = false } }

const loadCurrent = () => {
  if (sub.value === 'following') return loadFollowing()
  if (sub.value === 'followers') return loadFollowers()
  return loadTags()
}
watch(sub, loadCurrent)
watch(() => props.initialSub, v => { if (v && v !== sub.value) sub.value = v })
onMounted(loadCurrent)

const goUser = (id: string) => router.push(`/user/${encodeUserId(id)}`)
const unfollowUser = async (id: string) => { await followApi.unfollow(id); following.value = following.value.filter(u => u.id !== id) }
const unfollowTag = async (t: Tag) => { try { await tagApi.unfollow(t.id); tags.value = tags.value.filter(x => x.id !== t.id); invalidateCommunityContentCaches(); ElMessage.success(`已取消关注 #${t.name}`) } catch { ElMessage.error('取消关注失败') } }
</script>

<template>
  <div class="relations-panel">
    <el-segmented v-model="sub" :options="options" />
    <div v-loading="loading" class="rel-list">
      <!-- 关注 -->
      <template v-if="sub === 'following'">
        <div v-for="u in following" :key="u.id" class="rel-row">
          <div class="rel-user" @click="goUser(u.id)">
            <Avatar :src="u.avatar ?? undefined" size="md" />
            <div class="rel-meta"><span class="rel-name">{{ u.nickname || u.username }}</span><span class="rel-sub">@{{ u.username }}</span></div>
          </div>
          <el-button size="small" plain @click="unfollowUser(u.id)">取消关注</el-button>
        </div>
        <EmptyState v-if="!loading && !following.length" :icon="User" title="还没有关注任何人" description="关注有趣的人，第一时间看到动态" />
      </template>
      <!-- 粉丝 -->
      <template v-else-if="sub === 'followers'">
        <div v-for="u in followers" :key="u.id" class="rel-row">
          <div class="rel-user" @click="goUser(u.id)">
            <Avatar :src="u.avatar ?? undefined" size="md" />
            <div class="rel-meta"><span class="rel-name">{{ u.nickname || u.username }}</span><span class="rel-sub">@{{ u.username }}</span></div>
          </div>
          <el-button size="small" type="primary" plain @click="goUser(u.id)">主页</el-button>
        </div>
        <EmptyState v-if="!loading && !followers.length" :icon="Connection" title="还没有粉丝" description="发布优质内容，吸引更多人关注你" />
      </template>
      <!-- 话题 -->
      <template v-else>
        <div v-for="t in tags" :key="t.id" class="rel-row">
          <div class="rel-user" @click="router.push(`/tag/${t.name}`)">
            <span class="rel-tag">#{{ t.name }}</span><span class="rel-sub">{{ t.postCount ?? 0 }} 篇</span>
          </div>
          <el-button size="small" plain @click="unfollowTag(t)">取消关注</el-button>
        </div>
        <EmptyState v-if="!loading && !tags.length" :icon="PriceTag" title="还没有关注话题" description="在话题页点「关注」，有新帖会通知你" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.rel-list { margin-top: 12px; }
.rel-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.rel-user { display: flex; align-items: center; gap: 10px; min-width: 0; cursor: pointer; flex: 1; }
.rel-meta { display: flex; flex-direction: column; min-width: 0; }
.rel-name { font-weight: 600; color: var(--el-text-color-primary); }
.rel-sub { font-size: 12px; color: var(--el-text-color-secondary); }
.rel-tag { font-weight: 700; color: var(--el-color-primary); }
</style>
