<script setup lang="ts">
import { Pointer } from '@element-plus/icons-vue'
import type { Comment } from '@/types'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import UserBadge from '@/components/common/UserBadge.vue'
import UserQuickCard from '@/components/common/UserQuickCard.vue'

const props = defineProps<{
  comment: Comment
  isChild?: boolean
}>()

const emit = defineEmits(['like', 'reply'])

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="comment-item" :class="{ 'is-child': isChild }">
    <div class="comment-avatar">
      <UserQuickCard
        :user-id="comment.userId"
        :nickname="comment.nickname"
        :avatar="comment.userAvatar"
        :roles="comment.roles || []"
      >
        <el-avatar :size="isChild ? 28 : 36" :src="comment.userAvatar">
          {{ comment.nickname?.charAt(0) || 'U' }}
        </el-avatar>
      </UserQuickCard>
    </div>
    
    <div class="comment-main">
      <div class="comment-bubble">
        <div class="comment-header">
          <span style="display: flex; align-items: center;">
            <UserQuickCard
              :user-id="comment.userId"
              :nickname="comment.nickname"
              :avatar="comment.userAvatar"
              :roles="comment.roles || []"
            >
              <span class="user-name">{{ comment.nickname }}</span>
            </UserQuickCard>
            <UserRoleBadge :roles="comment.roles || []" />
            <UserBadge :text="comment.userBadgeText || ''" :color="comment.userBadgeColor" :effect="comment.userBadgeStyle" />
          </span>
          <span class="comment-time">{{ formatDate(comment.createTime) }}</span>
        </div>
        <div class="comment-content">
          {{ comment.content }}
        </div>
      </div>
      
      <div class="comment-actions">
        <span 
          class="action-btn" 
          :class="{ 'is-liked': comment.isLiked }"
          @click="$emit('like', comment)"
        >
          <el-icon><Pointer /></el-icon> {{ comment.likeCount || 0 }}
        </span>
        <span class="action-btn" @click="$emit('reply', comment)">
          回复
        </span>
      </div>

      <!-- Nested Children -->
      <div v-if="comment.children && comment.children.length > 0" class="nested-comments">
        <CommentItem 
          v-for="child in comment.children" 
          :key="child.id" 
          :comment="child" 
          :is-child="true"
          @like="$emit('like', $event)"
          @reply="$emit('reply', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.comment-item.is-child {
  margin-bottom: 12px;
  gap: 8px;
}

.comment-avatar {
  flex-shrink: 0;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-bubble {
  background-color: var(--el-fill-color-light);
  border-radius: var(--el-border-radius-base);
  padding: 12px 16px;
  border: 1px solid transparent;
  transition: background-color 0.2s;
}

.is-child .comment-bubble {
  background-color: var(--el-bg-color);
  border-color: var(--el-border-color-lighter);
  padding: 10px 14px;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.comment-content {
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  word-break: break-word;
}

.is-child .comment-content {
  font-size: 13px;
}

.comment-actions {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  padding: 0 4px;
}

.action-btn {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color 0.2s;
}

.action-btn:hover {
  color: var(--el-color-primary);
}

.action-btn.is-liked {
  color: var(--el-color-primary);
}

.nested-comments {
  margin-top: 16px;
  padding-left: 12px;
  border-left: 2px solid var(--el-border-color-lighter);
}
</style>
