<script setup lang="ts">
import { ChatLineRound, Coordinate } from '@element-plus/icons-vue'
import { ref } from 'vue'
import DOMPurify from 'dompurify'
import { timeAgo } from '@/utils/timeAgo'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'

const props = defineProps<{
  comments: any[]
  activeCommentId?: string | null
}>()

const emit = defineEmits<{
  (e: 'like', comment: any): void
  (e: 'reply', comment: any): void
}>()

const replySortMode = ref<'time' | 'hot'>('time')
const expandedCommentIds = ref<Set<string>>(new Set())
const COMMENT_COLLAPSE_THRESHOLD = 220

const getAvatar = (c: any) => c.userAvatar || c.avatar || c.author?.avatar || ''
const getName = (c: any) => c.nickname || c.author?.name || '匿名用户'
const getTime = (c: any) => timeAgo(c.createTime || c.createdAt || '')
const getLikes = (c: any) => c.likeCount ?? c.likes ?? 0
const getRoles = (c: any) => c.roles || []
const normalizeId = (value: unknown) => String(value ?? '').trim()
const toCommentDomId = (value: unknown) => `comment-${normalizeId(value)}`
const isCommentActive = (value: unknown) => {
  const current = normalizeId(props.activeCommentId)
  if (!current) return false
  return normalizeId(value) === current
}

const highlightMentionHtml = (value: string) => {
  return value.replace(/(^|[\s(])@([\u4e00-\u9fa5A-Za-z0-9_.-]{2,32})/g, '$1<span class="comment-mention">@$2</span>')
}

const sanitizeCommentHtml = (raw: any) => {
  const source = String(raw ?? '')
  const withMention = highlightMentionHtml(source)
  const sanitized = DOMPurify.sanitize(withMention, {
    ALLOWED_TAGS: ['a', 'br', 'p', 'strong', 'em', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'span'],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
    ALLOW_DATA_ATTR: false,
  })
  return sanitized.replace(/\n/g, '<br/>')
}

const getCommentKey = (comment: any) => normalizeId(comment?.id)

const isLongComment = (raw: any) => String(raw ?? '').length > COMMENT_COLLAPSE_THRESHOLD

const isCollapsed = (comment: any) => {
  const id = getCommentKey(comment)
  if (!id || !isLongComment(comment?.content)) return false
  return !expandedCommentIds.value.has(id)
}

const toggleExpand = (comment: any) => {
  const id = getCommentKey(comment)
  if (!id) return
  const next = new Set(expandedCommentIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedCommentIds.value = next
}

const getSortedChildren = (children: any[]) => {
  const list = Array.isArray(children) ? [...children] : []
  if (replySortMode.value === 'hot') {
    return list.sort((a, b) => getLikes(b) - getLikes(a))
  }
  return list.sort((a, b) => {
    const t1 = new Date(a?.createTime || a?.createdAt || 0).getTime()
    const t2 = new Date(b?.createTime || b?.createdAt || 0).getTime()
    return t1 - t2
  })
}

</script>

<template>
  <div class="comment-list">
    <div class="list-header">
      <h3>{{ comments.length }} 条回复</h3>
      <div class="list-tools">
        <span class="sort-label">楼中楼排序</span>
        <el-radio-group v-model="replySortMode" size="small">
          <el-radio-button label="time">按时间</el-radio-button>
          <el-radio-button label="hot">按热度</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div
      v-for="(comment, index) in comments"
      :key="comment.id"
      :id="toCommentDomId(comment.id)"
      class="comment-item"
      :class="{ 'is-active': isCommentActive(comment.id) }"
    >
      <div class="comment-avatar">
        <el-avatar :size="40" :src="getAvatar(comment)">
          {{ getName(comment).charAt(0) }}
        </el-avatar>
      </div>

      <div class="comment-main">
        <div class="comment-meta">
          <div class="meta-left">
            <span class="author-name">{{ getName(comment) }}</span>
            <UserRoleBadge :roles="getRoles(comment)" />
            <span class="time-label">{{ getTime(comment) }}</span>
          </div>
          <div class="meta-right">
            <span class="floor-num">#{{ comment.floor || index + 1 }}</span>
          </div>
        </div>

        <div class="comment-text" :class="{ collapsed: isCollapsed(comment) }" v-html="sanitizeCommentHtml(comment.content)"></div>
        <div v-if="isLongComment(comment.content)" class="expand-wrap">
          <el-button link type="primary" size="small" @click.stop="toggleExpand(comment)">
            {{ isCollapsed(comment) ? '展开全文' : '收起' }}
          </el-button>
        </div>

        <div class="comment-actions">
          <div class="actions-group">
            <el-button
              link
              :type="comment.isLiked ? 'primary' : 'info'"
              size="small"
              :icon="Coordinate"
              @click.stop="emit('like', comment)"
            >
              {{ getLikes(comment) > 0 ? getLikes(comment) : '点赞' }}
            </el-button>
            <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', comment)">
              回复
            </el-button>
          </div>
        </div>

        <!-- Nested Replies (children) -->
        <div v-if="comment.children && comment.children.length > 0" class="replies-block">
          <template v-for="child in getSortedChildren(comment.children)" :key="child.id">
            <div
              :id="toCommentDomId(child.id)"
              class="reply-item"
              :class="{ 'is-active': isCommentActive(child.id) }"
            >
              <div class="reply-avatar">
                <el-avatar :size="30" :src="getAvatar(child)">
                  {{ getName(child).charAt(0) }}
                </el-avatar>
              </div>
              <div class="reply-main">
                <div class="reply-meta">
                  <span class="author-name">{{ getName(child) }}</span>
                  <UserRoleBadge :roles="getRoles(child)" />
                  <span v-if="child.replyUserNickname" class="reply-to">
                    回复 <strong>{{ child.replyUserNickname }}</strong>
                  </span>
                  <span class="time-label">{{ getTime(child) }}</span>
                </div>
                <div class="reply-text" :class="{ collapsed: isCollapsed(child) }" v-html="sanitizeCommentHtml(child.content)"></div>
                <div v-if="isLongComment(child.content)" class="expand-wrap">
                  <el-button link type="primary" size="small" @click.stop="toggleExpand(child)">
                    {{ isCollapsed(child) ? '展开全文' : '收起' }}
                  </el-button>
                </div>
                <div class="comment-actions">
                  <div class="actions-group">
                    <el-button
                      link
                      :type="child.isLiked ? 'primary' : 'info'"
                      size="small"
                      :icon="Coordinate"
                      @click.stop="emit('like', child)"
                    >
                      {{ getLikes(child) > 0 ? getLikes(child) : '点赞' }}
                    </el-button>
                    <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', child)">
                      回复
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
            <!-- 子评论的子评论（楼中楼平铺展示） -->
            <div
              v-for="grandChild in getSortedChildren(child.children || [])"
              :key="grandChild.id"
              :id="toCommentDomId(grandChild.id)"
              class="reply-item reply-item--deep"
              :class="{ 'is-active': isCommentActive(grandChild.id) }"
            >
              <div class="reply-avatar">
                <el-avatar :size="26" :src="getAvatar(grandChild)">
                  {{ getName(grandChild).charAt(0) }}
                </el-avatar>
              </div>
              <div class="reply-main">
                <div class="reply-meta">
                  <span class="author-name">{{ getName(grandChild) }}</span>
                  <UserRoleBadge :roles="getRoles(grandChild)" />
                  <span class="reply-to">
                    回复 <strong>{{ grandChild.replyUserNickname || getName(child) }}</strong>
                  </span>
                  <span class="time-label">{{ getTime(grandChild) }}</span>
                </div>
                <div class="reply-text" :class="{ collapsed: isCollapsed(grandChild) }" v-html="sanitizeCommentHtml(grandChild.content)"></div>
                <div v-if="isLongComment(grandChild.content)" class="expand-wrap">
                  <el-button link type="primary" size="small" @click.stop="toggleExpand(grandChild)">
                    {{ isCollapsed(grandChild) ? '展开全文' : '收起' }}
                  </el-button>
                </div>
                <div class="comment-actions">
                  <div class="actions-group">
                    <el-button
                      link
                      :type="grandChild.isLiked ? 'primary' : 'info'"
                      size="small"
                      :icon="Coordinate"
                      @click.stop="emit('like', grandChild)"
                    >
                      {{ getLikes(grandChild) > 0 ? getLikes(grandChild) : '点赞' }}
                    </el-button>
                    <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', grandChild)">
                      回复
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-list {
  background: var(--cp-bg-card);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-lighter);
  margin-bottom: 24px;
}

.list-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.list-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
  font-weight: 700;
}

.comment-item {
  display: flex;
  padding: 24px;
  border-bottom: 1px solid var(--cp-divider);
  transition: background-color 0.2s;
  scroll-margin-top: calc(var(--header-height, 64px) + 20px);
}

.comment-item:hover {
  background-color: var(--cp-hover);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-avatar {
  flex-shrink: 0;
  margin-right: 16px;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.meta-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.time-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.floor-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--cp-text-muted);
  background-color: var(--cp-bg-elevated);
  padding: 2px 6px;
  border-radius: 4px;
}

.comment-text,
.reply-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
  word-break: break-word;
}

.comment-text.collapsed,
.reply-text.collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.expand-wrap {
  margin: -6px 0 8px;
}

.comment-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  opacity: 0.7;
  transition: opacity 0.2s;
}

.comment-item:hover .comment-actions,
.reply-item:hover .comment-actions {
  opacity: 1;
}

.actions-group {
  display: flex;
  gap: 16px;
}

/* Song：说明 */
.replies-block {
  margin-top: 16px;
  padding: 12px 16px;
  background-color: var(--el-fill-color-lighter);
  border-radius: var(--el-border-radius-base);
  border-left: 3px solid var(--el-color-primary-light-5);
}

.reply-item {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  scroll-margin-top: calc(var(--header-height, 64px) + 20px);
}

.comment-item.is-active,
.reply-item.is-active {
  animation: comment-focus 1.8s ease;
  background-color: var(--el-color-primary-light-9);
}

.reply-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.reply-item:first-child {
  padding-top: 0;
}

.reply-avatar {
  flex-shrink: 0;
  margin-right: 12px;
}

.reply-main {
  flex: 1;
  min-width: 0;
}

.reply-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.reply-to {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.reply-to strong {
  color: var(--el-color-primary);
  font-weight: 600;
}

.reply-item--deep {
  padding-left: 8px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}

:deep(.comment-mention) {
  color: var(--el-color-primary);
  font-weight: 600;
}

@keyframes comment-focus {
  0% {
    box-shadow: inset 0 0 0 1px rgba(64, 158, 255, 0.55);
  }
  100% {
    box-shadow: inset 0 0 0 1px transparent;
  }
}
</style>
