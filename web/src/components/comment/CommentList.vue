<script setup lang="ts">
import { ChatLineRound, Coordinate } from '@element-plus/icons-vue'
import { timeAgo } from '@/utils/timeAgo'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'

const props = defineProps<{
  comments: any[]
}>()

const emit = defineEmits<{
  (e: 'like', comment: any): void
  (e: 'reply', comment: any): void
}>()

const getAvatar = (c: any) => c.userAvatar || c.avatar || c.author?.avatar || ''
const getName = (c: any) => c.nickname || c.author?.name || '匿名用户'
const getTime = (c: any) => timeAgo(c.createTime || c.createdAt || '')
const getLikes = (c: any) => c.likeCount ?? c.likes ?? 0
const getRoles = (c: any) => c.roles || []

</script>

<template>
  <div class="comment-list">
    <div class="list-header">
      <h3>{{ comments.length }} 条回复</h3>
    </div>

    <div v-for="(comment, index) in comments" :key="comment.id" class="comment-item">
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

        <div class="comment-text" v-html="comment.content"></div>

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
          <div v-for="child in comment.children" :key="child.id" class="reply-item">
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
              <div class="reply-text" v-html="child.content"></div>
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
                  <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', comment)">
                    回复
                  </el-button>
                </div>
              </div>
            </div>
          </div>
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
</style>
