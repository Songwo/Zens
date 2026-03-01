<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { postApi } from '@/api/post'
import { publicDataApi } from '@/api/publicData'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Close, 
  View, 
  EditPen,
  Promotion, 
  MagicStick,
  FullScreen,
  Crop
} from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import ImageUploader from '@/components/ImageUploader.vue'
import { usePostComposerStore } from '@/store/postComposer'
import { usePostDraft } from '@/composables/usePostDraft'
import { useMarkdownImagePaste } from '@/composables/useMarkdownImagePaste'
import { TOC_MARKDOWN_TAG, renderMarkdownWithToc } from '@/utils/markdownToc'

const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
const draft = usePostDraft()

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

// Song：说明
const isMaximized = ref(false)
const titleInputRef = ref<any>(null)
const tagInput = ref('')
const categories = ref<any[]>([])
const loading = ref(false)
const extracting = ref(false)
const isEditing = ref(false)
const editId = ref('')
const contentInputRef = ref<any>(null)
const {
  isPastingImage,
  bindPasteListener,
  unbindPasteListener
} = useMarkdownImagePaste({
  getTextarea: () => contentInputRef.value?.textarea as HTMLTextAreaElement | undefined,
  getContent: () => draft.form.content,
  setContent: value => {
    draft.form.content = value
  },
  module: 'post',
  maxImageSizeMb: 5
})

const renderedContent = computed(() => {
  return renderMarkdownWithToc(md, draft.form.content || '暂无内容')
})

// Song：说明
const fetchCategories = async () => {
  try {
    const res = await publicDataApi.getActiveSectionsCached()
    if (res.code === 2000 || res.code === 200) {
      categories.value = res.data || []
    }
  } catch (error) {
    ElMessage.error('获取分类失败')
  }
}

// Song：说明
const addTag = () => {
  const tag = tagInput.value.trim()
  if (!tag) return
  if (draft.form.tags.includes(tag)) {
    ElMessage.warning('标签已存在')
    return
  }
  if (draft.form.tags.length >= 5) {
    ElMessage.warning('最多添加5个标签')
    return
  }
  draft.form.tags.push(tag)
  tagInput.value = ''
}

const removeTag = (tag: string) => {
  draft.form.tags = draft.form.tags.filter(t => t !== tag)
}

// Song：说明
const handleAIAnalysis = async () => {
  if (!draft.form.content || extracting.value) return
  
  extracting.value = true
  try {
    const res = await postApi.extractTags({
      title: draft.form.title,
      content: draft.form.content
    })
    if (res.data && res.data.tags && res.data.tags.length > 0) {
      if (Array.isArray(res.data.tags)) {
        draft.form.tags = res.data.tags
      } else if (typeof res.data.tags === 'string') {
        draft.form.tags = (res.data.tags as string).split(',').filter(Boolean)
      }
      ElMessage.success('AI 分析完成')
    } else {
      ElMessage.warning('未能提取出有效标签')
    }
  } catch (error) {
    // Song：说明
  } finally {
    extracting.value = false
  }
}

// Song：说明
const publish = async () => {
  if (!draft.form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }

  if (!draft.form.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }

  if (!draft.form.sectionId) {
    ElMessage.warning('请选择板块')
    return
  }

  loading.value = true
  try {
    if (isEditing.value) {
      await postApi.update({
        postId: editId.value,
        title: draft.form.title,
        content: draft.form.content,
        sectionId: draft.form.sectionId,
        tags: draft.form.tags.join(','),
        coverImage: draft.form.coverImage || undefined
      })
      ElMessage.success('更新成功')
    } else {
      await postApi.create({
        title: draft.form.title,
        content: draft.form.content,
        sectionId: draft.form.sectionId,
        tags: draft.form.tags.join(','),
        coverImage: draft.form.coverImage || undefined,
        status: 1
      })
      ElMessage.success('发布成功')
    }

    draft.clearDraft() // Song：重要: 成功后清空草稿
    handleClose(true) // Song：无需二次确认直接关闭
    
    // Song：说明
    if (router.currentRoute.value.path === '/') {
      window.location.reload()
    } else {
      router.push('/')
    }
  } catch (error) {
    ElMessage.error('发布失败')
  } finally {
    loading.value = false
  }
}

const toggleMaximize = () => {
  isMaximized.value = !isMaximized.value
}

const insertTocTag = async () => {
  if (draft.form.content.includes(TOC_MARKDOWN_TAG)) {
    ElMessage.info('目录标签已存在')
    return
  }

  const textarea = contentInputRef.value?.textarea as HTMLTextAreaElement | undefined
  if (!textarea) {
    draft.form.content = draft.form.content
      ? `${TOC_MARKDOWN_TAG}\n\n${draft.form.content}`
      : `${TOC_MARKDOWN_TAG}\n`
    return
  }

  const start = textarea.selectionStart ?? draft.form.content.length
  const end = textarea.selectionEnd ?? draft.form.content.length
  const prefix = draft.form.content.slice(0, start)
  const suffix = draft.form.content.slice(end)
  const needsLeadingBreak = prefix.length > 0 && !prefix.endsWith('\n') ? '\n' : ''
  const needsTrailingBreak = suffix.length > 0 && !suffix.startsWith('\n') ? '\n' : ''
  const inserted = `${needsLeadingBreak}${TOC_MARKDOWN_TAG}${needsTrailingBreak}`

  draft.form.content = `${prefix}${inserted}${suffix}`

  await nextTick()
  const nextPosition = (prefix + inserted).length
  textarea.focus()
  textarea.setSelectionRange(nextPosition, nextPosition)
}

const handleClose = (force = false) => {
  if (isEditing.value) {
    if (!force && draft.isDirty.value && draft.hasContent.value) {
      ElMessageBox.confirm('编辑内容尚未保存，确定放弃修改吗？', '确认离开', {
        confirmButtonText: '确定离开',
        cancelButtonText: '继续编辑',
        type: 'warning'
      }).then(() => {
        draft.resetForm()
        composerStore.close()
      }).catch(() => {})
    } else {
      draft.resetForm()
      composerStore.close()
    }
  } else {
    // Song：说明
    if (!force && draft.isDirty.value && draft.hasContent.value) {
      ElMessageBox.confirm('内容尚未保存，确定离开吗？草稿将保留在本地。', '保存草稿', {
        confirmButtonText: '保存草稿并关闭',
        cancelButtonText: '直接舍弃',
        distinguishCancelAndClose: true,
        type: 'warning'
      }).then(() => {
        draft.saveDraft(true)
        composerStore.close()
      }).catch((action) => {
        if (action === 'cancel') {
          draft.resetForm()
          composerStore.close()
        }
      })
    } else {
      draft.resetForm() // Song：内容为空时重置表单
      composerStore.close()
    }
  }
}

// Song：说明
watch(() => composerStore.isOpen, async (newVal) => {
  if (newVal) {
    // Song：说明
    if (!userStore.accessToken) {
      ElMessage.error('请先登录再发起话题')
      composerStore.close()
      router.push('/auth/login')
      return
    }
    
    if (!categories.value.length) {
      fetchCategories()
    }

    // Song：说明
    if (composerStore.context.editId) {
        isEditing.value = true
        editId.value = composerStore.context.editId
        draft.form.title = composerStore.context.title
        draft.form.content = composerStore.context.content
        draft.form.sectionId = composerStore.context.sectionId
        draft.form.tags = composerStore.context.tags ? composerStore.context.tags.split(',') : []
        draft.form.coverImage = composerStore.context.coverImage

        composerStore.context.editId = '' // Song：消费一次性上下文
    } else {
        isEditing.value = false
        editId.value = ''

        // Song：说明
        if (composerStore.context.title) {
            draft.form.title = composerStore.context.title
            composerStore.context.title = '' // Song：消费一次性上下文
        }
        if (composerStore.context.sectionId) {
            draft.form.sectionId = composerStore.context.sectionId
            composerStore.context.sectionId = 0 // Song：消费一次性上下文
        }

        // Song：说明
        if (!draft.hasContent.value) {
            draft.loadDraft()
        }
    }

    await nextTick()
    if (titleInputRef.value) {
      titleInputRef.value.focus()
    }
    bindPasteListener()
  } else {
    unbindPasteListener()
  }
})

// Song：说明
const handleKeydown = (e: KeyboardEvent) => {
  if (!composerStore.isOpen) return

  // Song：说明
  if (e.key === 'Escape') {
    e.preventDefault()
    handleClose()
  }

  // Song：说明
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    publish()
  }

  // Song：说明
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    draft.saveDraft()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  unbindPasteListener()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="composer-fade">
      <div v-if="composerStore.isOpen" class="composer-overlay" @mousedown.self="handleClose(false)">
        <div 
          class="composer-modal" 
          :class="{'is-maximized': isMaximized}"
        >
          <!-- HEADER -->
          <div class="composer-header">
            <div class="header-left">
              <span class="header-title">{{ isEditing ? '编辑话题' : '创建话题' }}</span>
            </div>
            <div class="header-right">
              <el-button link class="header-icon-btn" @click="toggleMaximize" :title="isMaximized ? '还原' : '最大化'">
                <el-icon><Crop v-if="isMaximized"/><FullScreen v-else/></el-icon>
              </el-button>
              <el-button link class="header-icon-btn close-btn" @click="handleClose(false)" title="关闭 (Esc)">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>

          <!-- BODY -->
          <div class="composer-body">
            <el-form :model="draft.form" label-position="top" class="compose-form">
              <!-- Title Input -->
              <el-form-item>
                <el-input
                  ref="titleInputRef"
                  v-model="draft.form.title"
                  placeholder="输入话题标题..."
                  class="title-input"
                  maxlength="100"
                  show-word-limit
                />
              </el-form-item>

              <div class="meta-row">
                <!-- Category -->
                <el-form-item label="发布板块" class="meta-item">
                  <el-select v-model="draft.form.sectionId" placeholder="选择发布的板块" class="full-width">
                    <el-option
                      v-for="cat in categories"
                      :key="cat.id"
                      :label="cat.name"
                      :value="cat.id"
                    />
                  </el-select>
                </el-form-item>

                <!-- Tags -->
                <el-form-item label="话题标签 (最多5个)" class="meta-item">
                  <div class="tag-input-wrapper">
                    <el-input
                      v-model="tagInput"
                      placeholder="输入标签按回车"
                      @keyup.enter="addTag"
                    >
                      <template #append>
                        <el-button @click="addTag">添加</el-button>
                      </template>
                    </el-input>
                    <div class="tags-list" v-if="draft.form.tags.length > 0">
                      <el-tag
                        v-for="tag in draft.form.tags"
                        :key="tag"
                        closable
                        size="small"
                        class="tag-pill"
                        @close="removeTag(tag)"
                      >
                        {{ tag }}
                      </el-tag>
                    </div>
                  </div>
                </el-form-item>
              </div>

              <!-- Cover Image -->
              <el-form-item label="封面图片 (可选)">
                <ImageUploader v-model="draft.form.coverImage" />
              </el-form-item>

              <!-- Content Editor -->
              <el-form-item class="editor-form-item">
                <template #label>
                  <div class="editor-label">
                    <span>正文内容 (支持 Markdown)</span>
                    <div class="editor-actions">
                      <el-button link type="primary" @click="insertTocTag">
                        插入目录标签
                      </el-button>
                      <span v-if="isPastingImage" class="paste-status">正在上传剪贴板图片...</span>
                      <el-button 
                        v-if="draft.form.content.length > 10"
                        link 
                        type="primary" 
                        :icon="MagicStick"
                        :loading="extracting"
                        @click="handleAIAnalysis"
                      >
                        AI 提取标签
                      </el-button>
                    </div>
                  </div>
                </template>
                
                <div class="editor-wrapper split-view">
                  <div class="editor-pane">
                    <div class="pane-header"><el-icon><EditPen /></el-icon> 编辑</div>
                    <el-input
                      ref="contentInputRef"
                      v-model="draft.form.content"
                      type="textarea"
                      placeholder="使用 Markdown 编写你的内容... 输入 [TOC] 可自动生成目录，支持 Ctrl/Cmd + V 粘贴图片"
                      class="markdown-editor"
                    />
                  </div>
                  <div class="preview-pane">
                    <div class="pane-header"><el-icon><View /></el-icon> 预览</div>
                    <div class="markdown-preview markdown-body" v-html="renderedContent"></div>
                  </div>
                </div>
              </el-form-item>
            </el-form>
          </div>

          <!-- FOOTER -->
          <div class="composer-footer">
            <div class="footer-left">
              <el-checkbox v-model="draft.form.isAnonymous" :true-label="1" :false-label="0">匿名发布</el-checkbox>
            </div>
            <div class="footer-right">
              <span class="draft-status" v-if="draft.isDirty.value && draft.hasContent.value">有未保存更改</span>
              <span class="draft-status saved" v-else-if="draft.hasContent.value && !isEditing">草稿已保存</span>
              
              <el-button v-if="!isEditing" link @click="() => draft.saveDraft()">保存草稿</el-button>
              <el-button :icon="Promotion" type="primary" :loading="loading" @click="publish">{{ isEditing ? '更新话题' : '创建话题' }}</el-button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Song：说明 */
.composer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  z-index: 1500;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

html.dark .composer-overlay {
  background-color: rgba(0, 0, 0, 0.6);
}

.composer-modal {
  background-color: var(--cp-bg-surface);
  border-radius: var(--el-border-radius-base);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  width: 100%;
  max-width: 1200px;
  height: min(92vh, 900px);
  display: flex;
  flex-direction: column;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  border: 1px solid var(--cp-border);
}

html.dark .composer-modal {
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  border-color: var(--el-border-color-dark);
}

.composer-modal.is-maximized {
  max-width: 100vw;
  max-height: 100vh;
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  border: none;
}

/* Song：说明 */
.composer-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--cp-border);
  background-color: var(--cp-bg-card);
  border-radius: var(--el-border-radius-base) var(--el-border-radius-base) 0 0;
}

.composer-modal.is-maximized .composer-header {
  border-radius: 0;
}

html.dark .composer-header {
  border-bottom-color: var(--el-border-color-dark);
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cp-text);
}

.header-right {
  display: flex;
  gap: 8px;
}

.header-icon-btn {
  font-size: 16px;
  color: var(--cp-text-muted);
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
}

.header-icon-btn:hover {
  background-color: var(--cp-hover);
  color: var(--cp-text);
}

.close-btn:hover {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

html.dark .close-btn:hover {
  background-color: rgba(245, 108, 108, 0.2);
}

/* Song：说明 */
.composer-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.compose-form {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.title-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 700;
  height: 44px;
  padding: 0;
  border-bottom: 2px solid var(--cp-border);
  border-radius: 0;
  color: var(--cp-text);
  background: transparent;
}
html.dark .title-input :deep(.el-input__inner) {
  border-bottom-color: var(--el-border-color-dark);
}

.title-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent;
}

.meta-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 16px;
}

.full-width {
  width: 100%;
}

.tag-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.editor-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.paste-status {
  font-size: 12px;
  color: var(--el-color-primary);
}

.editor-form-item {
  margin-bottom: 0;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.editor-form-item :deep(.el-form-item__content) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-wrapper {
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
  border: 1px solid var(--cp-border);
  flex: 1;
  min-height: 0;
  display: flex;
}

html.dark .editor-wrapper {
  border-color: var(--el-border-color-dark);
}

.editor-wrapper.split-view {
  background-color: var(--cp-bg-surface);
  width: 100%;
}

.editor-pane, .preview-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  width: 50%;
  min-height: 0;
}
.editor-pane {
  border-right: 1px solid var(--cp-border);
}
html.dark .editor-pane {
  border-color: var(--el-border-color-dark);
}
.pane-header {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 700;
  background-color: var(--cp-bg-card);
  border-bottom: 1px solid var(--cp-border);
  color: var(--cp-text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}
html.dark .pane-header {
  border-color: var(--el-border-color-dark);
}

.markdown-editor {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.markdown-editor :deep(.el-textarea) {
  flex: 1;
  min-height: 0;
}

.markdown-editor :deep(.el-textarea__inner) {
  border: none;
  border-radius: 0;
  font-family: var(--el-font-family-mono);
  font-size: 14px;
  line-height: 1.6;
  padding: 16px;
  background-color: transparent;
  color: var(--cp-text);
  resize: none;
  height: 100%;
  min-height: 400px;
  overflow-y: auto !important;
}

.markdown-editor :deep(.el-textarea__inner)::-webkit-scrollbar {
  width: 8px;
}

.markdown-editor :deep(.el-textarea__inner)::-webkit-scrollbar-track {
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.markdown-editor :deep(.el-textarea__inner)::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 4px;
}

.markdown-editor :deep(.el-textarea__inner)::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

.markdown-preview {
  flex: 1;
  min-height: 400px;
  overflow-y: auto;
  padding: 16px;
  background-color: transparent;
  color: var(--cp-text);
  line-height: 1.8;
}

.markdown-preview::-webkit-scrollbar {
  width: 8px;
}

.markdown-preview::-webkit-scrollbar-track {
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.markdown-preview::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 4px;
}

.markdown-preview::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

/* Song：说明 */
.composer-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-top: 1px solid var(--cp-border);
  background-color: var(--cp-bg-card);
  border-radius: 0 0 var(--el-border-radius-base) var(--el-border-radius-base);
}

.composer-modal.is-maximized .composer-footer {
  border-radius: 0;
}

html.dark .composer-footer {
  border-top-color: var(--el-border-color-dark);
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.draft-status {
  font-size: 12px;
  color: var(--el-color-warning);
}
.draft-status.saved {
  color: var(--el-color-success);
}

/* Song：说明 */
.composer-fade-enter-active,
.composer-fade-leave-active {
  transition: opacity 0.25s ease;
}

.composer-fade-enter-from,
.composer-fade-leave-to {
  opacity: 0;
}

.composer-fade-enter-active .composer-modal {
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.composer-fade-enter-from .composer-modal {
  transform: translateY(20px) scale(0.98);
}

.composer-fade-leave-active .composer-modal {
  transition: transform 0.25s ease;
}

.composer-fade-leave-to .composer-modal {
  transform: translateY(10px) scale(0.98);
}

/* Song：说明 */
@media (max-width: 1024px) {
  .composer-overlay {
    padding: 0;
    align-items: flex-end; /* Song：移动端抽屉样式 */
  }

  .composer-modal {
    max-height: 90vh;
    border-radius: 12px 12px 0 0;
    border-bottom: none;
    border-left: none;
    border-right: none;
  }

  .composer-header {
    border-radius: 12px 12px 0 0;
  }

  .meta-row {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* Song：说明 */
:deep(.markdown-body) {
  font-size: 15px;
  line-height: 1.7;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  font-weight: 700;
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  color: var(--cp-text);
  border-bottom: 1px solid var(--cp-border);
  padding-bottom: 0.3em;
}

html.dark :deep(.markdown-body h1),
html.dark :deep(.markdown-body h2),
html.dark :deep(.markdown-body h3) {
  border-bottom-color: var(--el-border-color-dark);
}

:deep(.markdown-body p) {
  margin-bottom: 1em;
}

:deep(.markdown-body code) {
  background: var(--cp-bg-card);
  color: var(--el-color-danger);
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-size: 0.9em;
  border: 1px solid var(--cp-border);
}

html.dark :deep(.markdown-body code) {
  background: #2a2a2b;
  color: #f56c6c;
  border-color: #414243;
}

:deep(.markdown-body pre) {
  background: var(--cp-bg-card);
  padding: 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin: 1.5em 0;
  border: 1px solid var(--cp-border);
}

html.dark :deep(.markdown-body pre) {
  background: #1d1e1f;
  border-color: #414243;
}

:deep(.markdown-body pre code) {
  background: transparent;
  padding: 0;
  border: none;
  color: var(--cp-text);
}

:deep(.markdown-body a) {
  color: var(--el-color-primary);
  text-decoration: none;
}
:deep(.markdown-body a:hover) {
  text-decoration: underline;
}

:deep(.markdown-body blockquote) {
  border-left: 4px solid var(--el-color-info-light-5);
  padding-left: 1em;
  margin: 1em 0;
  color: var(--cp-text-muted);
}
</style>
