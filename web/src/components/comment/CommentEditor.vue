<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import MarkdownEditor from '@/components/markdown/MarkdownEditor.vue'

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
const editorRef = ref<InstanceType<typeof MarkdownEditor> | null>(null)

function handleSubmit() {
  const text = content.value.trim()
  if (!text || props.loading) return
  emit('submit', content.value)
  content.value = ''
}

watch(() => props.replyingTo, async (val) => {
  if (val) {
    await nextTick()
    editorRef.value?.focus()
  }
})
</script>

<template>
  <div class="comment-editor">
    <!-- Reply indicator -->
    <div v-if="replyingTo && replyName" class="reply-indicator">
      <span>回复 <strong>{{ replyName }}</strong></span>
      <el-button link type="info" size="small" :icon="Close" @click="emit('cancel')">取消</el-button>
    </div>

    <MarkdownEditor
      ref="editorRef"
      v-model="content"
      mode="compact"
      module="comment"
      :placeholder="replyingTo ? `回复 ${replyName}…  Ctrl+Enter 发送，支持 Markdown` : '写下你的回复…  Ctrl+Enter 发送，支持 Markdown'"
      :max-height="220"
      @submit="handleSubmit"
    />

    <div class="editor-actions">
      <div class="actions-left">
        <span class="editor-hint">输入 <code>@</code> 提及用户 · <code>Ctrl+B</code> 加粗 · <code>Ctrl+Shift+K</code> 代码块</span>
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
  padding: 14px;
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

.editor-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.actions-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.editor-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.editor-hint code {
  font-family: 'JetBrains Mono', var(--el-font-family-monospace, 'Consolas', monospace);
  padding: 1px 5px;
  background-color: var(--el-fill-color);
  border-radius: 3px;
  font-size: 11px;
}

/* 让 MarkdownEditor 在评论场景中无外边框，融入 comment-editor 自身的边框 */
.comment-editor :deep(.cp-md-editor) {
  border: none;
  border-radius: 0;
  background: transparent;
}
.comment-editor :deep(.cp-md-editor:focus-within) {
  box-shadow: none;
}
</style>
