<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import Avatar from '@/components/common/Avatar.vue'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import TrustLevelBadge from '@/components/common/TrustLevelBadge.vue'
import UserBadge from '@/components/common/UserBadge.vue'
import ProfileCover from '@/components/profile/ProfileCover.vue'
import { parseCoverConfig, type CoverConfig } from '@/utils/coverConfig'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'
import { EditPen, Connection, ChatDotRound } from '@element-plus/icons-vue'

export interface ProfileHeaderData {
  id: string
  username: string
  nickname?: string
  avatar?: string
  bio?: string
  school?: string
  major?: string
  enrollmentYear?: number
  interestTags?: string
  level?: number
  /** 信任等级 0-4（TL0新人/TL1基础/TL2成员/TL3常客/TL4领袖） */
  trustLevel?: number
  roles?: string[]
  badgeText?: string
  badgeColor?: string
  badgeStyle?: string
  profileCardBgUrl?: string
  coverConfig?: string
  postCount?: number
  followingCount?: number
  followerCount?: number
}

const props = withDefaults(defineProps<{
  profile: ProfileHeaderData
  variant: 'self' | 'other'
  isFollowing?: boolean
  followLoading?: boolean
  levelProgress?: number | null   // 0-100，仅 self
  levelHint?: string               // 如 "距 Lv.6 还差 380 经验"，仅 self
  editable?: boolean
  onSaveCover?: (config: CoverConfig) => Promise<void>
}>(), {
  isFollowing: false,
  followLoading: false,
  levelProgress: null,
  levelHint: '',
  editable: false,
})

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'compose'): void
  (e: 'follow'): void
  (e: 'message'): void
  (e: 'stat-click', type: 'following' | 'followers'): void
}>()

const coverImageUrl = computed(() => {
  return resolvePublicAssetUrl(props.profile.profileCardBgUrl)
})
const parsedCover = computed(() => parseCoverConfig(props.profile.coverConfig))
const onCoverNotice = (msg: string) => ElMessage.info(msg)

const tags = computed(() =>
  String(props.profile.interestTags || '')
    .split(',').map(s => s.trim()).filter(Boolean)
)
const gradeText = computed(() =>
  props.profile.enrollmentYear ? `${props.profile.enrollmentYear}级` : ''
)
const campusMeta = computed(() =>
  [props.profile.school, props.profile.major, gradeText.value].filter(Boolean).join(' · ')
)
const isSelf = computed(() => props.variant === 'self')
const clickableStats = computed(() => isSelf.value)
</script>

<template>
  <header class="profile-header">
    <ProfileCover
      :image-url="coverImageUrl"
      :config="parsedCover"
      :editable="editable"
      :on-save="onSaveCover"
      @notice="onCoverNotice"
    />

    <div class="ph-body">
      <Avatar :src="profile.avatar ?? undefined" :size="72" class="ph-avatar" />

      <div class="ph-namerow">
        <h1 class="ph-name">{{ profile.nickname || profile.username }}</h1>
        <UserRoleBadge :roles="profile.roles || []" />
        <TrustLevelBadge :trust-level="profile.trustLevel ?? 0" />
        <UserBadge :text="profile.badgeText || ''" :color="profile.badgeColor" :effect="profile.badgeStyle" />
        <el-tooltip v-if="profile.level != null" content="资历等级（经验值驱动，仅展示）" placement="top" effect="dark">
          <span class="ph-level-pill">Lv.{{ profile.level }}</span>
        </el-tooltip>
      </div>
      <div class="ph-handle">@{{ profile.username }}</div>

      <p v-if="profile.bio" class="ph-bio">{{ profile.bio }}</p>
      <p v-else-if="isSelf" class="ph-bio ph-bio-empty" @click="emit('edit')">写点介绍吧 ›</p>

      <div class="ph-stats">
        <span class="ph-stat"><b>{{ profile.postCount ?? 0 }}</b> 动态</span>
        <span class="ph-stat" :class="{ clickable: clickableStats }"
              @click="clickableStats && emit('stat-click','following')">
          <b>{{ profile.followingCount ?? 0 }}</b> 关注
        </span>
        <span class="ph-stat" :class="{ clickable: clickableStats }"
              @click="clickableStats && emit('stat-click','followers')">
          <b>{{ profile.followerCount ?? 0 }}</b> 粉丝
        </span>
      </div>

      <div v-if="isSelf && levelProgress != null" class="ph-level">
        <span class="ph-level-bar"><span class="ph-level-fill" :style="{ width: levelProgress + '%' }"></span></span>
        <span class="ph-level-hint">{{ levelHint }}</span>
      </div>

      <div v-if="campusMeta" class="ph-campus">🎓 {{ campusMeta }}</div>

      <div v-if="tags.length" class="ph-tags">
        <span v-for="t in tags" :key="t" class="ph-tag">{{ t }}</span>
      </div>
      <div v-else-if="isSelf" class="ph-tags">
        <span class="ph-tag ph-tag-add" @click="emit('edit')">＋ 添加兴趣</span>
      </div>

      <div class="ph-actions">
        <template v-if="isSelf">
          <el-button type="primary" :icon="EditPen" @click="emit('compose')">发布动态</el-button>
          <el-button @click="emit('edit')">编辑资料</el-button>
        </template>
        <template v-else>
          <el-button :type="isFollowing ? 'default' : 'primary'"
                     :icon="isFollowing ? undefined : Connection"
                     :loading="followLoading" @click="emit('follow')">
            {{ isFollowing ? '已关注' : '关注' }}
          </el-button>
          <el-button :icon="ChatDotRound" @click="emit('message')">私信</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.profile-header { background: var(--el-bg-color); }
.ph-body { padding: 0 8px; }
.ph-avatar { margin-top: -36px; border: 4px solid var(--el-bg-color); border-radius: 50%; }
.ph-namerow { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.ph-name { margin: 0; font-size: 22px; font-weight: 800; color: var(--el-text-color-primary); }
.ph-level-pill { font-size: 11px; font-weight: 600; color: var(--el-text-color-secondary);
  border: 1px solid var(--el-border-color-light); border-radius: 10px; padding: 1px 8px;
  background: var(--el-fill-color-light); cursor: help; }
.ph-handle { font-size: 13px; font-weight: 600; color: var(--el-color-primary); margin-top: 2px; }
.ph-bio { margin: 10px 0; font-size: 13px; line-height: 1.55; color: var(--el-text-color-regular); }
.ph-bio-empty { color: var(--el-text-color-placeholder); cursor: pointer; }
.ph-stats { display: flex; gap: 16px; font-size: 14px; color: var(--el-text-color-secondary); margin-bottom: 10px; }
.ph-stat b { color: var(--el-text-color-primary); }
.ph-stat.clickable { cursor: pointer; }
.ph-stat.clickable:hover b { color: var(--el-color-primary); }
.ph-level { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.ph-level-bar { flex: 1; max-width: 220px; height: 5px; background: var(--el-fill-color); border-radius: 3px; overflow: hidden; }
.ph-level-fill { display: block; height: 100%; background: var(--el-color-primary); }
.ph-level-hint { font-size: 11px; color: var(--el-text-color-placeholder); }
.ph-campus { font-size: 13px; color: var(--el-text-color-secondary); margin-bottom: 10px; }
.ph-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.ph-tag { font-size: 12px; color: var(--el-text-color-secondary); border: 1px solid var(--el-border-color); border-radius: 14px; padding: 3px 10px; }
.ph-tag-add { cursor: pointer; border-style: dashed; }
.ph-actions { display: flex; gap: 10px; margin-bottom: 16px; }
</style>
