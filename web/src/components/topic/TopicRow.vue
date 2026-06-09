<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { timeAgo } from '@/utils/timeAgo'
import { encodePostId } from '@/utils/shortId'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import UserBadge from '@/components/common/UserBadge.vue'
import UserQuickCard from '@/components/common/UserQuickCard.vue'

type TopicItem = {
  id: number | string
  title: string
  excerpt: string
  category: { name: string; color: string }
  tags: string[]
  author: { id?: string; username?: string; name: string; avatar: string; roles?: string[]; badgeText?: string; badgeColor?: string; badgeStyle?: string }
  createdAt: string
  lastActive: string
  replies: number
  views: number
  heatScore?: number
  isPinned?: boolean
  isFeatured?: boolean
  isSolved?: boolean
  trendLevel?: string
  sentimentLabel?: string
}

const router = useRouter()

const props = defineProps<{
  topic: TopicItem
}>()

const goTopic = () => {
  router.push(`/t/${encodePostId(props.topic.id)}`)
}

const maxVisibleTags = 3

const visibleTags = computed(() => (props.topic.tags || []).slice(0, maxVisibleTags))
const hiddenTagCount = computed(() => Math.max((props.topic.tags || []).length - maxVisibleTags, 0))

const isNew = computed(() => {
  if (!props.topic.createdAt) return false
  const created = new Date(props.topic.createdAt).getTime()
  if (Number.isNaN(created)) return false
  return Date.now() - created <= 24 * 60 * 60 * 1000
})

const isShortPost = computed(() => {
  const len = (props.topic.excerpt || '').length
  return len > 0 && len < 60 && !props.topic.isPinned && !props.topic.isFeatured
})

const sentimentMap: Record<string, string> = {
  POSITIVE: '情绪正向',
  NEGATIVE: '情绪偏负',
  NEUTRAL: '情绪中性',
}

const aiBadgeText = computed(() => {
  const pieces: string[] = []
  const trend = props.topic.trendLevel?.toLowerCase()
  const sentiment = props.topic.sentimentLabel?.toUpperCase()

  if (trend === 'hot') pieces.push('热度上升')
  else if (trend === 'warm') pieces.push('稳定')
  
  if (sentiment && sentimentMap[sentiment]) {
    pieces.push(sentimentMap[sentiment].replace('情绪', ''))
  } else if (!pieces.length && (props.topic.heatScore ?? 0) >= 60) {
    pieces.push('高价值')
  }

  return pieces.join(' · ')
})

const aiBadgeClass = computed(() => {
  const sentiment = props.topic.sentimentLabel?.toUpperCase()
  if (sentiment === 'POSITIVE') return 'positive'
  if (sentiment === 'NEGATIVE') return 'negative'
  return 'neutral'
})

const aiBadge = computed(() => !!aiBadgeText.value)

const formatMetric = (value: number) => {
  if (!Number.isFinite(value)) return '0'
  if (value >= 10000) return `${(value / 10000).toFixed(1)}w`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return `${value}`
}
</script>

<template>
  <article 
    class="topic-row" 
    :class="{ 'is-short-post-card': isShortPost }"
    role="button" 
    tabindex="0" 
    @click="goTopic" 
    @keyup.enter="goTopic"
  >
    <!-- 1. SHORT POST (微动态 / 说说) STYLE -->
    <div v-if="isShortPost" class="short-post-container">
      <div class="sp-header">
        <div class="sp-author-info">
          <UserQuickCard
            :user-id="topic.author.id"
            :username="topic.author.username"
            :nickname="topic.author.name"
            :avatar="topic.author.avatar"
            :roles="topic.author.roles"
            @click.stop
          >
            <el-avatar :size="36" :src="topic.author.avatar" class="sp-avatar">
              {{ topic.author.name.charAt(0) }}
            </el-avatar>
          </UserQuickCard>
          <div class="sp-meta-text">
            <div class="sp-name-row">
              <UserQuickCard
                :user-id="topic.author.id"
                :username="topic.author.username"
                :nickname="topic.author.name"
                :avatar="topic.author.avatar"
                :roles="topic.author.roles"
                @click.stop
              >
                <span class="sp-author-name">{{ topic.author.name }}</span>
              </UserQuickCard>
              <UserRoleBadge :roles="topic.author.roles" />
              <UserBadge :text="topic.author.badgeText || ''" :color="topic.author.badgeColor" :effect="topic.author.badgeStyle" />
            </div>
            <div class="sp-time-row">
              <span class="sp-time">{{ timeAgo(topic.lastActive || topic.createdAt) }}</span>
            </div>
          </div>
        </div>
        <div class="sp-header-right">
          <span v-if="topic.isSolved" class="state-tag solved">已解决</span>
          <span class="sp-category-pill" :style="{ '--cat-color': topic.category.color || '#409EFF' }">
            {{ topic.category.name }}
          </span>
        </div>
      </div>

      <div class="sp-content-body">
        <div class="sp-content-card">
          <span class="sp-micro-tag">💬 微动态</span>
          <span class="sp-text">{{ topic.excerpt }}</span>
        </div>
      </div>

      <div class="sp-footer">
        <div class="sp-tags" v-if="visibleTags.length">
          <span v-for="tag in visibleTags" :key="tag" class="sp-tag-chip">#{{ tag }}</span>
        </div>
        
        <div class="sp-actions-metrics">
          <span v-if="aiBadge" class="sp-ai-badge" :class="aiBadgeClass">
            🤖 {{ aiBadgeText }}
          </span>
          <span class="sp-metric">💬 {{ formatMetric(topic.replies) }} 回应</span>
          <span class="sp-metric">👁️ {{ formatMetric(topic.views) }} 浏览</span>
        </div>
      </div>
    </div>

    <!-- 2. REGULAR POST STYLE -->
    <div v-else class="regular-post-container">
      <!-- 作者头部：头像左上 -->
      <div class="author-header">
        <UserQuickCard
          :user-id="topic.author.id"
          :username="topic.author.username"
          :nickname="topic.author.name"
          :avatar="topic.author.avatar"
          :roles="topic.author.roles"
          @click.stop
        >
          <el-avatar :size="38" :src="topic.author.avatar" class="rp-avatar">{{ topic.author.name.charAt(0) }}</el-avatar>
        </UserQuickCard>
        <div class="author-header-meta">
          <div class="author-name-row">
            <UserQuickCard
              :user-id="topic.author.id"
              :username="topic.author.username"
              :nickname="topic.author.name"
              :avatar="topic.author.avatar"
              :roles="topic.author.roles"
              @click.stop
            >
              <span class="author-name">{{ topic.author.name }}</span>
            </UserQuickCard>
            <UserRoleBadge :roles="topic.author.roles" />
            <UserBadge :text="topic.author.badgeText || ''" :color="topic.author.badgeColor" :effect="topic.author.badgeStyle" />
          </div>
          <span class="rp-time">{{ timeAgo(topic.lastActive || topic.createdAt) }}</span>
        </div>
        <div class="state-tags">
          <span v-if="topic.isPinned" class="state-tag pin">置顶</span>
          <span v-if="topic.isFeatured" class="state-tag feature">精华</span>
          <span v-if="topic.isSolved" class="state-tag solved">已解决</span>
          <span v-if="isNew" class="state-tag fresh">NEW</span>
        </div>
      </div>

      <h3 class="topic-title">{{ topic.title }}</h3>
      <p class="topic-excerpt">{{ topic.excerpt }}</p>

      <div class="meta-line">
        <span class="category-pill">
          <span class="cat-dot" :style="{ backgroundColor: topic.category.color || '#409EFF' }"></span>
          {{ topic.category.name }}
        </span>

        <div v-if="visibleTags.length" class="tags-line">
          <span v-for="tag in visibleTags" :key="tag" class="tag-chip">#{{ tag }}</span>
          <span v-if="hiddenTagCount > 0" class="tag-chip muted">+{{ hiddenTagCount }}</span>
        </div>

        <!-- AI Insight Premium Badge -->
        <span v-if="aiBadge" class="ai-badge" :class="aiBadgeClass">
          <span class="ai-badge-icon">🤖</span>
          <span class="ai-badge-text">{{ aiBadgeText }}</span>
        </span>
      </div>

      <div class="foot-line">
        <div class="stats-line">
          <span class="metric">{{ formatMetric(topic.replies) }} 回应</span>
          <span class="metric">{{ formatMetric(topic.views) }} 浏览</span>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.topic-row {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-overlay);
  cursor: pointer;
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.26s ease, background-color 0.22s ease, border-left-color 0.2s ease;
  border-left: 3px solid transparent;
  will-change: transform;
  position: relative;
  overflow: hidden;
}

.topic-row::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, rgba(255, 255, 255, 0) 20%, rgba(255, 255, 255, 0.22) 50%, rgba(255, 255, 255, 0) 80%);
  transform: translateX(-130%);
  transition: transform 0.46s ease;
  pointer-events: none;
}

.topic-row:hover {
  border-left-color: var(--cp-primary);
  background-color: var(--cp-hover);
  transform: translate3d(0, -3px, 0);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.08);
}

.topic-row:hover::after {
  transform: translateX(120%);
}

.topic-row:active {
  transform: translateY(0);
  transition-duration: 0.1s;
}

.topic-row:focus-visible {
  outline: 2px solid var(--cp-primary);
  outline-offset: -2px;
}

.topic-row:last-child {
  border-bottom: none;
}

.headline {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.state-tags {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  padding-top: 1px;
}

.state-tag {
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  padding: 4px 7px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.state-tag.pin {
  color: #9a6700;
  background: #fff3d4;
}

.state-tag.feature {
  color: #0f766e;
  background: #dff8f4;
}

.state-tag.solved {
  color: var(--accept-text);
  background: var(--accept-bg);
  border-color: var(--accept-border);
}

.state-tag.fresh {
  color: #0b57d0;
  background: #e7f0ff;
}

.topic-title {
  margin: 0;
  font-size: 16px;
  line-height: 1.45;
  font-weight: 700;
  color: var(--el-text-color-primary);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}

.topic-excerpt {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}

.meta-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.category-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  padding: 3px 9px;
}

.cat-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.tags-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.tag-chip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 999px;
  padding: 2px 8px;
  white-space: nowrap;
}

.tag-chip.muted {
  color: var(--el-text-color-placeholder);
}

.foot-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.author-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.author-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* 常规帖：头像左上的作者头部 */
.regular-post-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.author-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rp-avatar {
  flex: none;
}

.author-header-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.author-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}

.rp-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.author-header .state-tags {
  margin-left: auto;
}

.stats-line {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.metric {
  white-space: nowrap;
}

.time {
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  padding: 3px 9px;
  margin-left: auto;
  transition: all 0.24s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.ai-badge.neutral {
  color: #0b57d0;
  background: #f2f7ff;
  border: 1px solid #d8e6ff;
}

.ai-badge.positive {
  color: #0f766e;
  background: #dff8f4;
  border: 1px solid #b2f5ea;
}

.ai-badge.negative {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.topic-row:hover .ai-badge {
  transform: scale(1.03);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.05);
}

.topic-row.is-short-post-card {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.02) 0%, var(--el-bg-color-overlay) 100%);
  border-left-color: rgba(245, 158, 11, 0.4);
}

.topic-row.is-short-post-card:hover {
  border-left-color: #f59e0b;
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.05) 0%, var(--cp-hover) 100%);
  box-shadow: 0 16px 32px rgba(245, 158, 11, 0.05);
}

.short-post-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sp-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sp-author-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sp-avatar {
  border: 1.5px solid rgba(245, 158, 11, 0.25);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: transform 0.2s ease;
}

.topic-row:hover .sp-avatar {
  transform: scale(1.05);
}

.sp-meta-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.sp-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sp-author-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  transition: color 0.2s;
}

.sp-author-name:hover {
  color: var(--el-color-primary);
}

.sp-time {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.sp-category-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--sp-cat-color, #409EFF) 9%, transparent);
  color: var(--sp-cat-color, #409EFF);
  border: 1px solid color-mix(in srgb, var(--sp-cat-color, #409EFF) 20%, transparent);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.sp-content-body {
  padding: 2px 0;
}

.sp-content-card {
  background: var(--el-fill-color-extra-light);
  border-left: 3.5px solid #f59e0b;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 14.5px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  transition: all 0.24s ease;
}

.topic-row:hover .sp-content-card {
  background: color-mix(in srgb, #f59e0b 4%, var(--el-fill-color-extra-light));
  border-left-color: #d97706;
}

.sp-micro-tag {
  font-size: 10px;
  font-weight: 700;
  color: #B45309;
  background: #FEF3C7;
  padding: 2px 6px;
  border-radius: 4px;
  margin-right: 8px;
  display: inline-block;
  vertical-align: middle;
  line-height: 1.2;
  box-shadow: 0 1px 2px rgba(180, 83, 9, 0.06);
}

.sp-text {
  vertical-align: middle;
  word-break: break-word;
}

.sp-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.sp-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.sp-tag-chip {
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 2px 8px;
  transition: all 0.2s;
}

.sp-tag-chip:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.sp-actions-metrics {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
}

.sp-metric {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.sp-ai-badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
}

.sp-ai-badge.neutral {
  color: #0b57d0;
  background: #f2f7ff;
  border: 1px solid #d8e6ff;
}

.sp-ai-badge.positive {
  color: #0f766e;
  background: #dff8f4;
  border: 1px solid #b2f5ea;
}

.sp-ai-badge.negative {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

@media (max-width: 768px) {
  .topic-row {
    padding: 12px;
    gap: 8px;
  }

  .headline {
    gap: 8px;
  }

  .topic-title {
    font-size: 15px;
  }

  .topic-excerpt {
    -webkit-line-clamp: 1;
    line-clamp: 1;
  }

  .meta-line {
    flex-wrap: wrap;
    gap: 8px;
  }

  .foot-line {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .stats-line {
    justify-content: flex-start;
  }

  .sp-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .sp-actions-metrics {
    margin-left: 0;
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
