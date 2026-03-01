<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { timeAgo } from '@/utils/timeAgo'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'

type TopicItem = {
  id: number | string
  title: string
  excerpt: string
  category: { name: string; color: string }
  tags: string[]
  author: { name: string; avatar: string; roles?: string[] }
  createdAt: string
  lastActive: string
  replies: number
  views: number
  heatScore?: number
  isPinned?: boolean
  isFeatured?: boolean
  trendLevel?: string
  sentimentLabel?: string
}

const router = useRouter()

const props = defineProps<{
  topic: TopicItem
}>()

const goTopic = () => {
  router.push(`/t/${props.topic.id}`)
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

const sentimentMap: Record<string, string> = {
  POSITIVE: '情绪正向',
  NEGATIVE: '情绪偏负',
  NEUTRAL: '情绪中性',
}

const aiHint = computed(() => {
  const pieces: string[] = []
  const trend = props.topic.trendLevel?.toLowerCase()
  const sentiment = props.topic.sentimentLabel?.toUpperCase()

  if (trend === 'hot') pieces.push('热度持续上升')
  if (trend === 'warm') pieces.push('热度稳定')
  if (sentiment && sentimentMap[sentiment]) pieces.push(sentimentMap[sentiment])
  if (!pieces.length && (props.topic.heatScore ?? 0) >= 60) pieces.push('互动价值高')

  return pieces.length ? `AI洞察: ${pieces.join(' · ')}` : ''
})

const formatMetric = (value: number) => {
  if (!Number.isFinite(value)) return '0'
  if (value >= 10000) return `${(value / 10000).toFixed(1)}w`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return `${value}`
}
</script>

<template>
  <article class="topic-row" role="button" tabindex="0" @click="goTopic" @keyup.enter="goTopic">
    <div class="headline">
      <div class="state-tags">
        <span v-if="topic.isPinned" class="state-tag pin">置顶</span>
        <span v-if="topic.isFeatured" class="state-tag feature">精华</span>
        <span v-if="isNew" class="state-tag fresh">NEW</span>
      </div>

      <h3 class="topic-title">{{ topic.title }}</h3>
    </div>

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
    </div>

    <div class="foot-line">
      <div class="author-line">
        <el-avatar :size="26" :src="topic.author.avatar">{{ topic.author.name.charAt(0) }}</el-avatar>
        <span class="author-name">{{ topic.author.name }}</span>
        <UserRoleBadge :roles="topic.author.roles" />
      </div>

      <div class="stats-line">
        <span class="metric">{{ formatMetric(topic.replies) }} 回应</span>
        <span class="metric">{{ formatMetric(topic.views) }} 浏览</span>
        <span class="time">{{ timeAgo(topic.lastActive || topic.createdAt) }}</span>
      </div>
    </div>

    <div v-if="aiHint" class="ai-hint">{{ aiHint }}</div>
  </article>
</template>

<style scoped>
.topic-row {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-overlay);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
  border-left: 3px solid transparent;
}

.topic-row:hover {
  border-left-color: var(--cp-primary);
  background-color: var(--cp-hover);
  transform: translateY(-1px);
  box-shadow: var(--el-box-shadow-lighter);
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

.ai-hint {
  font-size: 12px;
  color: #0b57d0;
  background: #f2f7ff;
  border: 1px solid #d8e6ff;
  border-radius: 8px;
  padding: 6px 10px;
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
}
</style>
