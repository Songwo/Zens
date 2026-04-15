<script setup lang="ts">
import { ref, nextTick, watch, computed } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'

const props = defineProps<{
  loading?: boolean
  replyingTo?: string | null
  replyName?: string | null
}>()

const emit = defineEmits<{
  (e: 'submit', content: string): void
  (e: 'cancel'): void
}>()

const content = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const editorRef = ref<HTMLElement | null>(null)

// @mention 相关状态
const mentionVisible = ref(false)
const mentionKeyword = ref('')
const mentionUsers = ref<Array<{ id: string; username: string; nickname: string; avatar: string }>>([])
const mentionLoading = ref(false)
const mentionAtStart = ref(-1) // @ 符号在 content 中的位置
let mentionSearchTimer: ReturnType<typeof setTimeout> | null = null

const handleSubmit = () => {
  if (!content.value.trim()) return
  emit('submit', content.value)
  content.value = ''
  closeMention()
}

const closeMention = () => {
  mentionVisible.value = false
  mentionKeyword.value = ''
  mentionUsers.value = []
  mentionAtStart.value = -1
}

const onInput = (e: Event) => {
  const el = e.target as HTMLTextAreaElement
  const val = el.value
  const cursor = el.selectionStart ?? val.length

  // 从光标往左找最近的 @
  let atIdx = -1
  for (let i = cursor - 1; i >= 0; i--) {
    if (val[i] === '@') {
      // @ 前面必须是行首、空格或换行
      if (i === 0 || /[\s\n]/.test(val[i - 1])) {
        atIdx = i
      }
      break
    }
    // 遇到空格或换行说明 @ 已不在当前词
    if (/[\s\n]/.test(val[i])) break
  }

  if (atIdx >= 0) {
    const keyword = val.slice(atIdx + 1, cursor)
    mentionAtStart.value = atIdx
    mentionKeyword.value = keyword
    triggerMentionSearch(keyword)
  } else {
    closeMention()
  }
}

const triggerMentionSearch = (keyword: string) => {
  if (mentionSearchTimer) clearTimeout(mentionSearchTimer)
  if (keyword.length > 20) {
    closeMention()
    return
  }
  mentionSearchTimer = setTimeout(async () => {
    mentionLoading.value = true
    mentionVisible.value = true
    try {
      const res = await userApi.searchUsers(keyword)
      mentionUsers.value = res.data || []
    } catch {
      mentionUsers.value = []
    } finally {
      mentionLoading.value = false
    }
  }, 200)
}

const selectMention = (user: { id: string; nickname: string; username: string }) => {
  const el = textareaRef.value
  if (!el) return
  const val = content.value
  const cursor = el.selectionStart ?? val.length
  // 替换 @ 到光标之间的内容
  const before = val.slice(0, mentionAtStart.value)
  const after = val.slice(cursor)
  const insertName = user.nickname || user.username
  content.value = `${before}@${insertName} ${after}`
  closeMention()
  nextTick(() => {
    const newCursor = before.length + insertName.length + 2 // @name<space>
    el.setSelectionRange(newCursor, newCursor)
    el.focus()
  })
}

const onKeydown = (e: KeyboardEvent) => {
  if (!mentionVisible.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    closeMention()
  }
}

watch(() => props.replyingTo, async (val) => {
  if (val) {
    await nextTick()
    textareaRef.value?.focus()
  }
})
</script>
<template>
  <div class="comment-editor" ref="editorRef">
    <!-- Reply indicator -->
    <div v-if="replyingTo && replyName" class="reply-indicator">
      <span>回复 <strong>{{ replyName }}</strong></span>
      <el-button link type="info" size="small" :icon="Close" @click="emit('cancel')">取消</el-button>
    </div>

    <div class="textarea-wrap">
      <textarea
        ref="textareaRef"
        v-model="content"
        class="editor-native-textarea"
        :placeholder="replyingTo ? `回复 ${replyName}...` : '写下你的回复...'" 
        rows="4"
        @input="onInput"
        @keydown="onKeydown"
      />

      <!-- @mention 下拉 -->
      <div v-if="mentionVisible" class="mention-dropdown">
        <div v-if="mentionLoading" class="mention-loading">搜索中...</div>
        <div v-else-if="mentionUsers.length === 0" class="mention-empty">没有找到用户</div>
        <div
          v-for="user in mentionUsers"
          :key="user.id"
          class="mention-item"
          @mousedown.prevent="selectMention(user)"
        >
          <el-avatar :size="24" :src="user.avatar" class="mention-avatar">
            {{ (user.nickname || user.username).charAt(0) }}
          </el-avatar>
          <span class="mention-nickname">{{ user.nickname || user.username }}</span>
          <span class="mention-username">@{{ user.username }}</span>
        </div>
      </div>
    </div>

    <div class="editor-actions">
      <div class="actions-left">
        <span class="editor-hint">输入 <code>@</code> 可搜索并提及用户</span>
      </div>
      <div class="actions-right">
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!content.trim()"
          @click="handleSubmit"
        >
          {{ replyingTo ? '回复评论' : '回复主题' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-editor {
  background: var(--cp-bg-card);
  border: 1px solid var(--cp-border);
  border-radius: var(--el-border-radius-base);
  padding: 16px;
  margin-bottom: 24px;
  transition: border-color 0.2s;
}

.comment-editor:focus-within {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.reply-indicator {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 12px;
  background-color: var(--el-color-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.reply-indicator strong {
  color: var(--el-color-primary);
}

.textarea-wrap {
  position: relative;
}

.editor-native-textarea {
  width: 100%;
  resize: none;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--el-font-family-base);
  font-size: 15px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  box-sizing: border-box;
  padding: 0;
}

.mention-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 1000;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
  min-width: 220px;
  max-width: 320px;
  max-height: 240px;
  overflow-y: auto;
}

.mention-loading,
.mention-empty {
  padding: 12px 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 0.15s;
}

.mention-item:hover {
  background: var(--el-fill-color-light);
}

.mention-nickname {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mention-username {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: auto;
}

.editor-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.actions-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.editor-hint code {
  font-family: var(--el-font-family-monospace, 'Consolas', monospace);
}
</style>
