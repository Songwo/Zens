<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'

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
const editorRef = ref<HTMLElement | null>(null)

const handleSubmit = () => {
  if (!content.value.trim()) return
  emit('submit', content.value)
  content.value = ''
}

// Song：说明
watch(() => props.replyingTo, async (val) => {
  if (val) {
    await nextTick()
    editorRef.value?.querySelector('textarea')?.focus()
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

    <el-input
      v-model="content"
      type="textarea"
      :rows="4"
      :placeholder="replyingTo ? `回复 ${replyName}...` : '写下你的回复...'"
      resize="none"
      class="editor-textarea"
    />
    <div class="editor-actions">
      <div class="actions-left">
        <el-button link type="info" size="small">支持 Markdown 语法</el-button>
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

.editor-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 0;
  background-color: transparent;
  font-family: var(--el-font-family-base);
  font-size: 15px;
  line-height: 1.6;
}

.editor-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.editor-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
</style>
