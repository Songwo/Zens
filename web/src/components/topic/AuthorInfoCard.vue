<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Position } from '@element-plus/icons-vue'
import { followApi } from '@/api/follow'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()

const props = defineProps<{
  author: {
    id?: string
    name: string
    avatar: string
    bio: string
    postsCount: number
    followersCount: number
  }
}>()

const isFollowed = ref(false)
const followLoading = ref(false)

onMounted(async () => {
  if (!userStore.accessToken || !props.author.id) return
  if (props.author.id === userStore.userId) return
  try {
    const res = await followApi.isFollowing(props.author.id)
    isFollowed.value = res.data === true
  } catch {}
})

const handleFollow = async () => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  if (!props.author.id) return
  followLoading.value = true
  try {
    if (isFollowed.value) {
      await followApi.unfollow(props.author.id)
      isFollowed.value = false
      ElMessage.success('已取消关注')
    } else {
      await followApi.follow(props.author.id)
      isFollowed.value = true
      ElMessage.success('关注成功')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

const handlePrivateMessage = () => {
  if (!props.author.id || props.author.id === userStore.userId) return
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再私信')
    return
  }
  router.push({
    path: '/messages',
    query: {
      peerId: props.author.id,
      peerName: props.author.name || '',
    },
  })
}
</script>

<template>
  <el-card shadow="never" class="author-card">
    <div class="author-header">
      <el-avatar :size="64" :src="author.avatar" class="avatar">
        {{ author.name.charAt(0) }}
      </el-avatar>
      <div class="author-info">
        <div class="author-name">{{ author.name }}</div>
        <div class="author-title">社区活跃成员</div>
      </div>
    </div>
    
    <p class="author-bio">{{ author.bio || '这个人很懒，什么都没有留下。' }}</p>

    <div class="author-stats">
      <div class="stat-item">
        <div class="stat-value">{{ author.postsCount }}</div>
        <div class="stat-label">发帖</div>
      </div>
      <div class="stat-item">
        <div class="stat-value">{{ author.followersCount }}</div>
        <div class="stat-label">粉丝</div>
      </div>
    </div>

    <div class="author-actions">
      <el-button
        v-if="author.id !== userStore.userId"
        :type="isFollowed ? 'default' : 'primary'"
        class="action-btn"
        :loading="followLoading"
        @click="handleFollow"
      >
        <el-icon><User /></el-icon> {{ isFollowed ? '已关注' : '关注' }}
      </el-button>
      <el-button
        v-if="author.id !== userStore.userId"
        class="action-btn"
        @click="handlePrivateMessage"
      >
        <el-icon><Position /></el-icon> 私信
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.author-card {
  border-radius: var(--el-border-radius-base);
  border-color: var(--el-border-color-lighter);
  margin-bottom: 20px;
}

.author-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.avatar {
  border: 2px solid var(--el-color-primary-light-8);
}

.author-info {
  display: flex;
  flex-direction: column;
}

.author-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.author-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  background-color: var(--el-color-primary-light-9);
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
}

.author-bio {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
  margin: 0 0 20px 0;
}

.author-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  padding: 12px 0;
  border-top: 1px dashed var(--el-border-color-lighter);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.author-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  flex: 1;
}
</style>
