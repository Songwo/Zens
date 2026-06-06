<script setup lang="ts">
import { computed } from 'vue'
import Avatar from '@/components/common/Avatar.vue'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
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
  roles?: string[]
  profileCardBgUrl?: string
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
}>(), {
  isFollowing: false,
  followLoading: false,
  levelProgress: null,
  levelHint: '',
})

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'compose'): void
  (e: 'follow'): void
  (e: 'message'): void
  (e: 'stat-click', type: 'following' | 'followers'): void
}>()

const SUNSET = 'linear-gradient(135deg, #fff2db 0%, #ffe3b2 48%, #ffd39a 100%)'
const coverUrl = computed(() => {
  const url = String(props.profile.profileCardBgUrl || '').trim()
  const ok = /^https?:\/\/[^"'\s]+$/.test(url) || /^\/uploads\/[^"'\s]+$/.test(url)
  return ok ? url : ''
})
const coverBaseStyle = computed(() =>
  coverUrl.value ? { background: 'var(--el-fill-color-light)' } : { background: SUNSET }
)
const coverImageStyle = computed(() => ({ backgroundImage: `url("${coverUrl.value}")` }))

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
    <div class="ph-cover" :style="coverBaseStyle">
      <template v-if="coverUrl">
        <div class="ph-cover-blur" :style="coverImageStyle"></div>
        <div class="ph-cover-photo" :style="coverImageStyle"></div>
      </template>
    </div>

    <div class="ph-body">
      <Avatar :src="profile.avatar ?? undefined" :size="72" class="ph-avatar" />

      <div class="ph-namerow">
        <h1 class="ph-name">{{ profile.nickname || profile.username }}</h1>
        <UserRoleBadge :roles="profile.roles || []" />
        <span v-if="profile.level != null" class="ph-level-pill">Lv.{{ profile.level }}</span>
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
.ph-cover { position: relative; height: 320px; border-radius: 10px 10px 0 0; overflow: hidden; }
.ph-cover-blur, .ph-cover-photo { position: absolute; inset: 0; background-position: center; background-repeat: no-repeat; }
.ph-cover-blur { background-size: cover; filter: blur(22px) brightness(0.92); transform: scale(1.12); }
.ph-cover-photo { background-size: contain; }
.ph-body { padding: 0 8px; }
.ph-avatar { margin-top: -36px; border: 4px solid var(--el-bg-color); border-radius: 50%; }
.ph-namerow { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.ph-name { margin: 0; font-size: 22px; font-weight: 800; color: var(--el-text-color-primary); }
.ph-level-pill { font-size: 11px; font-weight: 700; color: var(--el-color-primary);
  border: 1px solid var(--el-border-color); border-radius: 10px; padding: 1px 8px; }
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
@media (max-width: 640px) { .ph-cover { height: 220px; border-radius: 0; } }
</style>
