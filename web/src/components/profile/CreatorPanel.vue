<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import ProfilePostList from '@/components/profile/ProfilePostList.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { postApi } from '@/api/post'
import { viewLogApi, type ViewLog } from '@/api/viewLog'
import { useUserStore } from '@/store/user'
import { encodePostId } from '@/utils/shortId'
import { EditPen, Warning, Delete, View } from '@element-plus/icons-vue'
import type { Post } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const uid = () => userStore.userId || userStore.userInfo?.id || ''
const sub = ref<'drafts' | 'rejected' | 'trash' | 'history'>('drafts')
const options = [
  { label: '草稿', value: 'drafts' },
  { label: '打回修改', value: 'rejected' },
  { label: '回收站', value: 'trash' },
  { label: '浏览记录', value: 'history' },
]

const draftsFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: uid(), status: 0, auditStatus: 'DRAFT' }).then(r => r.data.records as Post[])
const rejectedFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: uid(), status: 0, auditStatus: 'REJECTED' }).then(r => r.data.records as Post[])
const trashFetcher = (page: number) => postApi.searchList({ page, pageSize: 10, needTotal: false, userId: uid(), auditStatus: 'DELETED' }).then(r => r.data.records as Post[])

// 浏览记录
const history = ref<ViewLog[]>([])
const histLoading = ref(false)
const loadHistory = async () => {
  histLoading.value = true
  try { history.value = (await viewLogApi.getUserHistoryPaged(uid(), 1, 20)).data?.records || [] }
  finally { histLoading.value = false }
}
const goPost = (id: string) => id && router.push(`/t/${encodePostId(id)}`)
</script>

<template>
  <div class="creator-panel">
    <el-segmented v-model="sub" :options="options" @change="sub === 'history' && loadHistory()" />
    <div class="creator-body">
      <ProfilePostList v-if="sub === 'drafts'" :fetcher="draftsFetcher" :empty-icon="EditPen" empty-title="暂无草稿" empty-description="保存未提交的草稿在这里" />
      <ProfilePostList v-else-if="sub === 'rejected'" :fetcher="rejectedFetcher" :empty-icon="Warning" empty-title="暂无打回帖子" empty-description="被打回的帖子在这里，可编辑后重新提交" />
      <ProfilePostList v-else-if="sub === 'trash'" :fetcher="trashFetcher" :empty-icon="Delete" empty-title="回收站为空" empty-description="删除的帖子保留 7 天，可在此恢复" />
      <div v-else v-loading="histLoading">
        <div v-for="h in history" :key="h.postId" class="hist-row" @click="goPost(h.postId)">
          <span class="hist-title">{{ h.title || '该帖子已删除' }}</span>
          <span class="hist-time">{{ h.viewTime }}</span>
        </div>
        <EmptyState v-if="!histLoading && !history.length" :icon="View" title="暂无浏览记录" description="浏览过的帖子会展示在这里" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.creator-body { margin-top: 12px; }
.hist-row { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); cursor: pointer; }
.hist-title { font-weight: 600; color: var(--el-text-color-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hist-time { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
</style>
