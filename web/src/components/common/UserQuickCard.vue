<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Position, UserFilled } from '@element-plus/icons-vue'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import TrustLevelBadge from '@/components/common/TrustLevelBadge.vue'
import UserBadge from '@/components/common/UserBadge.vue'
import { followApi } from '@/api/follow'
import { userApi, type UserPublicProfile } from '@/api/user'
import { useUserStore } from '@/store/user'
import { getCardThemePalette } from '@/utils/cardTheme'
import { encodeUserId } from '@/utils/shortId'

const props = withDefaults(
  defineProps<{
    userId?: string | number
    nickname?: string
    username?: string
    avatar?: string
    bio?: string
    roles?: string[]
    quickCardTheme?: string
    quickCardBgUrl?: string
    placement?: 'top' | 'top-start' | 'top-end' | 'bottom' | 'bottom-start' | 'bottom-end' | 'left' | 'right'
  }>(),
  {
    placement: 'bottom'
  }
)

const router = useRouter()
const userStore = useUserStore()

const publicProfileCache = new Map<string, UserPublicProfile>()

const loading = ref(false)
const followLoading = ref(false)
const followedLoaded = ref(false)
const isFollowed = ref(false)
const profile = ref<UserPublicProfile | null>(null)

const normalizedUserId = computed(() => {
  const raw = props.userId
  if (raw === null || raw === undefined) return ''
  return String(raw).trim()
})

const displayName = computed(() => {
  return profile.value?.nickname || props.nickname || props.username || '未知用户'
})

const displayAvatar = computed(() => {
  return profile.value?.avatar || props.avatar || ''
})

const displayBio = computed(() => {
  const text = profile.value?.bio || props.bio || ''
  return text || '这个人很懒，还没有留下简介。'
})

const displayUsername = computed(() => {
  return profile.value?.username || props.username || ''
})

const displayRoles = computed(() => {
  if (profile.value?.roles && profile.value.roles.length > 0) return profile.value.roles
  return props.roles || []
})

const displayBadge = computed(() => profile.value?.badgeText || '')
const displayBadgeColor = computed(() => profile.value?.badgeColor || '')
const displayBadgeEffect = computed(() => profile.value?.badgeStyle || 'solid')

const stats = computed(() => ({
  postCount: profile.value?.postCount ?? 0,
  followerCount: profile.value?.followerCount ?? 0,
  followingCount: profile.value?.followingCount ?? 0
}))

const quickCardPalette = computed(() =>
  getCardThemePalette(profile.value?.quickCardTheme || props.quickCardTheme || 'ocean', 'ocean')
)

const quickCardBgUrl = computed(() =>
  String(profile.value?.quickCardBgUrl || props.quickCardBgUrl || '').trim()
)

const quickCardStyle = computed(() => ({
  background: /^https?:\/\/[^"'\s]+$/.test(quickCardBgUrl.value) || /^\/uploads\/[^"'\s]+$/.test(quickCardBgUrl.value)
    ? `linear-gradient(135deg, rgba(255,255,255,0.80), rgba(255,255,255,0.80)), url("${quickCardBgUrl.value}") center/cover no-repeat`
    : quickCardPalette.value.background,
  border: `1px solid ${quickCardPalette.value.borderColor}`,
}))

const canInteract = computed(() => {
  return !!normalizedUserId.value && normalizedUserId.value !== String(userStore.userId || '')
})

const canFollow = computed(() => userStore.isLoggedIn && canInteract.value)

const loadPublicProfile = async () => {
  const userId = normalizedUserId.value
  if (!userId || loading.value) return

  if (publicProfileCache.has(userId)) {
    profile.value = publicProfileCache.get(userId) || null
    return
  }

  loading.value = true
  try {
    const res = await userApi.getPublicProfile(userId)
    const data = res.data || null
    profile.value = data
    if (data) {
      publicProfileCache.set(userId, data)
    }
  } catch {
    // Song：说明
  } finally {
    loading.value = false
  }
}

const loadFollowStatus = async () => {
  const userId = normalizedUserId.value
  if (!canFollow.value || !userId || followedLoaded.value) return
  try {
    const res = await followApi.isFollowing(userId)
    isFollowed.value = res.data === true
    followedLoaded.value = true
  } catch {
    // Song：说明
  }
}

const handleShow = () => {
  loadPublicProfile()
  loadFollowStatus()
}

const handleFollow = async () => {
  const userId = normalizedUserId.value
  if (!canFollow.value || !userId || followLoading.value) return

  followLoading.value = true
  try {
    if (isFollowed.value) {
      await followApi.unfollow(userId)
      isFollowed.value = false
      if (profile.value) {
        profile.value.followerCount = Math.max(0, (profile.value.followerCount || 0) - 1)
      }
      ElMessage.success('已取消关注')
    } else {
      await followApi.follow(userId)
      isFollowed.value = true
      if (profile.value) {
        profile.value.followerCount = (profile.value.followerCount || 0) + 1
      }
      ElMessage.success('关注成功')
    }
    if (profile.value && userId) {
      publicProfileCache.set(userId, profile.value)
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

const handlePrivateMessage = () => {
  const userId = normalizedUserId.value
  if (!userId || !canInteract.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再私信')
    return
  }
  router.push({
    path: '/messages',
    query: {
      peerId: userId,
      peerName: displayName.value,
    },
  })
}

const goToProfile = () => {
  const userId = normalizedUserId.value
  if (userId) {
    router.push(`/user/${encodeUserId(userId)}`)
  }
}
</script>

<template>
  <el-popover
    :width="280"
    trigger="click"
    :placement="placement"
    popper-class="user-quick-popper"
    @show="handleShow"
  >
    <template #reference>
      <span class="trigger-wrap" @click.stop>
        <slot />
      </span>
    </template>

    <div class="quick-card" :style="quickCardStyle" @click.stop>
      <template v-if="loading">
        <div class="loading-box">
          <el-skeleton animated :rows="4" />
        </div>
      </template>
      <template v-else>
        <div class="head clickable-head" @click.stop="goToProfile">
          <el-avatar :size="46" :src="displayAvatar">
            {{ displayName.charAt(0) || 'U' }}
          </el-avatar>
          <div class="meta">
            <div class="name-row">
              <span class="name hover-underline">{{ displayName }}</span>
              <UserRoleBadge :roles="displayRoles" />
              <TrustLevelBadge :trust-level="profile?.trustLevel ?? 0" />
              <UserBadge :text="displayBadge" :color="displayBadgeColor" :effect="displayBadgeEffect" />
            </div>
            <div class="username" v-if="displayUsername">@{{ displayUsername }}</div>
            <el-tooltip v-if="profile?.level" content="资历等级（经验值驱动，仅展示）" placement="top" effect="dark">
              <div class="level level-secondary">Lv.{{ profile.level }}</div>
            </el-tooltip>
          </div>
        </div>

        <p class="bio">{{ displayBio }}</p>

        <div class="stats">
          <div class="stat-item">
            <div class="value">{{ stats.postCount }}</div>
            <div class="label">帖子</div>
          </div>
          <div class="stat-item">
            <div class="value">{{ stats.followerCount }}</div>
            <div class="label">粉丝</div>
          </div>
          <div class="stat-item">
            <div class="value">{{ stats.followingCount }}</div>
            <div class="label">关注</div>
          </div>
        </div>

        <div class="actions" v-if="canInteract">
          <el-button
            v-if="canFollow"
            size="small"
            :type="isFollowed ? 'default' : 'primary'"
            :loading="followLoading"
            @click.stop="handleFollow"
          >
            <el-icon><UserFilled /></el-icon>
            {{ isFollowed ? '已关注' : '关注' }}
          </el-button>
          <el-button
            size="small"
            plain
            @click.stop="handlePrivateMessage"
          >
            <el-icon><Position /></el-icon>
            私信
          </el-button>
        </div>
      </template>
    </div>
  </el-popover>
</template>

<style scoped>
.trigger-wrap {
  display: inline-flex;
  align-items: center;
}

.quick-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
}

.loading-box {
  min-height: 120px;
}

.head {
  display: flex;
  gap: 10px;
  align-items: center;
}

.clickable-head {
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.clickable-head:hover {
  opacity: 0.85;
}

.hover-underline:hover {
  text-decoration: underline;
}

.meta {
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.username {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.level {
  font-size: 12px;
  color: var(--el-color-primary);
  margin-top: 2px;
}
/* Song：资历副徽章 —— 降为灰色次要展示，让 TL 彩色 chip 视觉更突出 */
.level-secondary {
  color: var(--el-text-color-secondary);
  display: inline-block;
  width: fit-content;
  cursor: help;
}

.bio {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 8px 0;
  border-top: 1px dashed var(--el-border-color-lighter);
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.stat-item {
  text-align: center;
}

.value {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions {
  display: flex;
  gap: 8px;
}

.actions .el-button {
  flex: 1;
}
</style>
