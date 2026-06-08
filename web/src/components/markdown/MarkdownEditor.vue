<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { EditorSelection } from '@codemirror/state'
import type { EditorView } from '@codemirror/view'
import { Eye, SplitSquareHorizontal, Pencil } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import MarkdownEditorCore from './MarkdownEditorCore.vue'
import MarkdownToolbar from './MarkdownToolbar.vue'
import MarkdownPreview from './MarkdownPreview.vue'
import { uploadApi } from '@/api/upload'
import { userApi } from '@/api/user'
import { UPLOAD_IMAGE_MAX_SIZE_MB } from '@/constants/upload'
import { formatMarkdown } from '@/utils/markdownFormat'
import { TOC_MARKDOWN_TAG } from '@/utils/markdownToc'

type Mode = 'full' | 'compact'
type ViewMode = 'edit' | 'split' | 'preview'

interface Props {
  modelValue: string
  mode?: Mode
  placeholder?: string
  /** 上传图片时的模块名（决定后端存储路径），默认 'post' */
  module?: string
  /** 编辑器自带高度（CSS px）。compact 模式忽略此值。 */
  height?: number | string
  /** 起始视图：默认 split（full 模式）或 edit（compact 模式）*/
  defaultView?: ViewMode
  /** 紧凑模式下显示的最大高度，超出则滚动 */
  maxHeight?: number | string
  inlineToc?: boolean
  /** 是否启用 @提及（默认 true） */
  enableMention?: boolean
  /** 字数 / 阅读时长 footer 是否显示 */
  showStatus?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'full',
  placeholder: '使用 Markdown 编写内容…  支持 Ctrl/Cmd+B 加粗、Ctrl+K 链接、粘贴图片自动上传',
  module: 'post',
  height: 620,
  defaultView: undefined,
  maxHeight: undefined,
  inlineToc: true,
  enableMention: true,
  showStatus: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'submit'): void
  (e: 'focus'): void
  (e: 'blur'): void
}>()

const coreRef = ref<InstanceType<typeof MarkdownEditorCore> | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)

const isCompact = computed(() => props.mode === 'compact')
const initialView: ViewMode = props.defaultView ?? (isCompact.value ? 'edit' : 'split')
const viewMode = ref<ViewMode>(initialView)
const pastingImage = ref(false)
let cmView: EditorView | null = null

// =========================
// 字数 / 阅读时长统计
// =========================
const wordCount = computed(() => {
  const text = (props.modelValue || '').trim()
  if (!text) return 0
  // 中文按字符计；英文按词计；混合时近似为两者之和。
  const chinese = (text.match(/[一-鿿]/g) || []).length
  const english = (text.match(/[A-Za-z]+/g) || []).length
  return chinese + english
})
const readingMinutes = computed(() => {
  const w = wordCount.value
  if (w === 0) return 0
  return Math.max(1, Math.ceil(w / 350))  // 350 字 / 分钟（中英文均速）
})

// =========================
// 工具栏指令派发
// =========================
function runCmd(kind: string) {
  const core = coreRef.value
  if (!core) return
  switch (kind) {
    case 'h1': core.togglePrefix('# ', { unique: true }); break
    case 'h2': core.togglePrefix('## ', { unique: true }); break
    case 'h3': core.togglePrefix('### ', { unique: true }); break
    case 'bold': core.wrapInline('**', '**', '加粗'); break
    case 'italic': core.wrapInline('*', '*', '斜体'); break
    case 'strike': core.wrapInline('~~', '~~', '删除线'); break
    case 'quote': core.togglePrefix('> ', { unique: true }); break
    case 'ul': core.togglePrefix('- ', { unique: true }); break
    case 'ol': core.togglePrefix('1. ', { unique: true }); break
    case 'task': core.togglePrefix('- [ ] ', { unique: true }); break
    case 'link': core.insertLink(); break
    case 'codeInline': core.wrapInline('`', '`', 'code'); break
    case 'codeBlock': core.wrapCodeFence(); break
    case 'table': core.insertTable(); break
    case 'hr': core.insertDivider(); break
    case 'toc': insertToc(); break
    case 'format': applyFormat(); break
  }
}

function insertToc() {
  const core = coreRef.value
  if (!core) return
  if ((props.modelValue || '').includes(TOC_MARKDOWN_TAG)) {
    ElMessage.info('目录标签已存在')
    return
  }
  core.insertAtCursor(TOC_MARKDOWN_TAG, { ensureBlankLineBefore: true, ensureBlankLineAfter: true })
}

function applyFormat() {
  const core = coreRef.value
  if (!core) return
  const current = core.getValue()
  const next = formatMarkdown(current)
  if (next !== current) {
    core.replaceAll(next)
    ElMessage.success('已整理 Markdown 格式')
  } else {
    ElMessage.info('内容已是规范格式')
  }
}

function handleEmoji(emoji: string) {
  coreRef.value?.insertAtCursor(emoji)
}

function handleMentionToolbar() {
  // 工具栏的 @ 按钮：在光标插入 @，让后续输入触发搜索面板
  coreRef.value?.insertAtCursor('@')
}

// =========================
// 图片上传：点击上传 + 粘贴 + 拖拽
// =========================
function triggerImageUpload() {
  fileInputRef.value?.click()
}

async function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  await uploadOne(file)
}

async function uploadOne(file: File) {
  const maxBytes = UPLOAD_IMAGE_MAX_SIZE_MB * 1024 * 1024
  if (file.size > maxBytes) {
    ElMessage.warning(`图片超过 ${UPLOAD_IMAGE_MAX_SIZE_MB}MB，已跳过`)
    return
  }
  pastingImage.value = true
  try {
    const url = await uploadApi.uploadImage(file, props.module)
    if (!url) throw new Error('图片上传失败')
    coreRef.value?.insertImageSyntax(url, file.name || 'image')
    ElMessage.success('图片已插入')
  } catch (err: any) {
    if (!err?.response) {
      ElMessage.error(err?.message || '图片上传失败')
    }
  } finally {
    pastingImage.value = false
  }
}

async function uploadMany(files: File[]) {
  for (const f of files) await uploadOne(f)
}

function onPasteFiles(files: File[]) {
  void uploadMany(files)
}

function onDropFiles(files: File[]) {
  void uploadMany(files)
}

// =========================
// @ 提及
// =========================
const mentionVisible = ref(false)
const mentionKeyword = ref('')
const mentionUsers = ref<Array<{ id: string; username: string; nickname: string; avatar: string }>>([])
const mentionLoading = ref(false)
const mentionPos = ref<{ left: number; top: number }>({ left: 0, top: 0 })
const mentionActiveIndex = ref(0)
let mentionAtStart = -1
let mentionSearchTimer: ReturnType<typeof setTimeout> | null = null

function closeMention() {
  mentionVisible.value = false
  mentionKeyword.value = ''
  mentionUsers.value = []
  mentionAtStart = -1
  mentionActiveIndex.value = 0
}

function refreshMentionPosition() {
  if (!cmView || !containerRef.value) return
  const pos = cmView.state.selection.main.head
  const coords = cmView.coordsAtPos(pos)
  if (!coords) return
  const containerRect = containerRef.value.getBoundingClientRect()
  mentionPos.value = {
    left: coords.left - containerRect.left,
    top: coords.bottom - containerRect.top + 4,
  }
}

function detectMention() {
  if (!props.enableMention || !cmView) return
  const selection = cmView.state.selection.main
  if (!selection.empty) {
    closeMention()
    return
  }
  const cursor = selection.head
  const doc = cmView.state.doc
  const lineObj = doc.lineAt(cursor)
  const colInLine = cursor - lineObj.from
  const lineText = lineObj.text.slice(0, colInLine)

  // 从光标往左找最近的 @
  let atIdx = -1
  for (let i = lineText.length - 1; i >= 0; i--) {
    if (lineText[i] === '@') {
      const prev = i > 0 ? lineText[i - 1] : ''
      if (i === 0 || /[\s]/.test(prev)) atIdx = i
      break
    }
    if (/[\s]/.test(lineText[i])) break
  }

  if (atIdx >= 0) {
    const keyword = lineText.slice(atIdx + 1)
    if (keyword.length > 20) {
      closeMention()
      return
    }
    mentionAtStart = lineObj.from + atIdx
    mentionKeyword.value = keyword
    refreshMentionPosition()
    triggerMentionSearch(keyword)
  } else {
    closeMention()
  }
}

function triggerMentionSearch(keyword: string) {
  if (mentionSearchTimer) clearTimeout(mentionSearchTimer)
  mentionSearchTimer = setTimeout(async () => {
    mentionLoading.value = true
    mentionVisible.value = true
    try {
      const res = await userApi.searchUsers(keyword)
      mentionUsers.value = res.data || []
      mentionActiveIndex.value = 0
    } catch {
      mentionUsers.value = []
    } finally {
      mentionLoading.value = false
    }
  }, 200)
}

function selectMention(user: { id: string; username: string; nickname: string }) {
  if (!cmView || mentionAtStart < 0) {
    closeMention()
    return
  }
  const cursor = cmView.state.selection.main.head
  const name = user.nickname || user.username
  const insert = `@${name} `
  cmView.dispatch({
    changes: { from: mentionAtStart, to: cursor, insert },
    selection: EditorSelection.cursor(mentionAtStart + insert.length),
  })
  cmView.focus()
  closeMention()
}

function onMentionKey(e: KeyboardEvent) {
  if (!mentionVisible.value || mentionUsers.value.length === 0) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    mentionActiveIndex.value = (mentionActiveIndex.value + 1) % mentionUsers.value.length
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    mentionActiveIndex.value = (mentionActiveIndex.value - 1 + mentionUsers.value.length) % mentionUsers.value.length
  } else if (e.key === 'Enter' || e.key === 'Tab') {
    e.preventDefault()
    const u = mentionUsers.value[mentionActiveIndex.value]
    if (u) selectMention(u)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    closeMention()
  }
}

// =========================
// CodeMirror 就绪 & 事件
// =========================
function onCmReady(v: EditorView) {
  cmView = v
}

function onModelInput(next: string) {
  emit('update:modelValue', next)
  // mention 检测延后到 modelValue 同步后
  nextTick(() => detectMention())
}

function handleSubmit() {
  emit('submit')
}

// =========================
// 视图切换
// =========================
const showEditor = computed(() => viewMode.value === 'edit' || viewMode.value === 'split')
const showPreview = computed(() => viewMode.value === 'preview' || viewMode.value === 'split')

function setView(v: ViewMode) {
  viewMode.value = v
  // 切换后让编辑器重新计算尺寸（避免 codemirror 折叠时残留宽度）
  nextTick(() => {
    coreRef.value?.getView()?.requestMeasure?.()
  })
}

// =========================
// 容器高度
// =========================
const containerStyle = computed(() => {
  if (isCompact.value) {
    const max = props.maxHeight ?? '180px'
    return {
      minHeight: '120px',
      maxHeight: typeof max === 'number' ? `${max}px` : max,
    }
  }
  const h = props.height
  return {
    height: typeof h === 'number' ? `${h}px` : h,
    minHeight: '480px',
  }
})

// =========================
// 全局键盘监听用于 mention 导航
// =========================
function onGlobalKeydown(e: KeyboardEvent) {
  if (mentionVisible.value) onMentionKey(e)
}
onMounted(() => window.addEventListener('keydown', onGlobalKeydown, true))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown, true))

// =========================
// 暴露 API
// =========================
defineExpose({
  focus: () => coreRef.value?.focus(),
  insertAtCursor: (text: string, opts?: { ensureBlankLineBefore?: boolean; ensureBlankLineAfter?: boolean }) =>
    coreRef.value?.insertAtCursor(text, opts),
  insertImageSyntax: (url: string, alt?: string) => coreRef.value?.insertImageSyntax(url, alt),
  insertTocTag: () => insertToc(),
  getValue: () => coreRef.value?.getValue() ?? '',
  applyFormat,
  triggerImageUpload,
})
</script>

<template>
  <div
    ref="containerRef"
    class="cp-md-editor"
    :class="{ 'is-compact': isCompact, [`view-${viewMode}`]: true }"
    :style="containerStyle"
  >
    <!-- Toolbar / View toggle -->
    <div class="cp-md-header" :class="{ 'is-compact': isCompact }">
      <MarkdownToolbar
        :compact="isCompact"
        :pasting-image="pastingImage"
        @cmd="runCmd"
        @emoji="handleEmoji"
        @upload-image="triggerImageUpload"
        @mention="handleMentionToolbar"
      />
      <div v-if="!isCompact" class="cp-md-view-toggle">
        <button
          type="button"
          class="vt-btn"
          :class="{ active: viewMode === 'edit' }"
          title="纯编辑"
          @click="setView('edit')"
        >
          <Pencil :size="14" />
          <span>编辑</span>
        </button>
        <button
          type="button"
          class="vt-btn"
          :class="{ active: viewMode === 'split' }"
          title="分屏"
          @click="setView('split')"
        >
          <SplitSquareHorizontal :size="14" />
          <span>分屏</span>
        </button>
        <button
          type="button"
          class="vt-btn"
          :class="{ active: viewMode === 'preview' }"
          title="纯预览"
          @click="setView('preview')"
        >
          <Eye :size="14" />
          <span>预览</span>
        </button>
      </div>
    </div>

    <!-- Body -->
    <div class="cp-md-body">
      <div v-show="showEditor" class="cp-md-pane cp-md-pane-edit">
        <MarkdownEditorCore
          ref="coreRef"
          :model-value="modelValue"
          :placeholder="placeholder"
          @update:model-value="onModelInput"
          @focus="emit('focus')"
          @blur="emit('blur')"
          @submit="handleSubmit"
          @paste-files="onPasteFiles"
          @drop-files="onDropFiles"
          @cm-ready="onCmReady"
        />
      </div>
      <div v-show="showPreview" class="cp-md-pane cp-md-pane-preview">
        <MarkdownPreview :content="modelValue" :inline-toc="inlineToc" />
      </div>
    </div>

    <!-- Status footer -->
    <div v-if="showStatus && !isCompact" class="cp-md-status">
      <span>{{ wordCount }} 字</span>
      <span class="dot">·</span>
      <span>约 {{ readingMinutes }} 分钟阅读</span>
      <span class="dot">·</span>
      <span class="hint">Ctrl/Cmd + Enter 提交，Ctrl + K 链接，Ctrl + Shift + K 代码块</span>
    </div>

    <!-- Hidden file input for image upload -->
    <input
      ref="fileInputRef"
      class="cp-md-file-input"
      type="file"
      accept="image/*"
      @change="handleFileSelect"
    />

    <!-- Mention popup -->
    <div
      v-if="mentionVisible"
      class="cp-md-mention"
      :style="{ left: mentionPos.left + 'px', top: mentionPos.top + 'px' }"
    >
      <div v-if="mentionLoading" class="cp-md-mention-empty">搜索中…</div>
      <div v-else-if="mentionUsers.length === 0" class="cp-md-mention-empty">没有找到用户</div>
      <template v-else>
        <div
          v-for="(u, i) in mentionUsers"
          :key="u.id"
          class="cp-md-mention-item"
          :class="{ active: i === mentionActiveIndex }"
          @mouseenter="mentionActiveIndex = i"
          @mousedown.prevent="selectMention(u)"
        >
          <img v-if="u.avatar" :src="u.avatar" class="cp-md-mention-avatar" />
          <span v-else class="cp-md-mention-avatar text">{{ (u.nickname || u.username).charAt(0) }}</span>
          <span class="cp-md-mention-name">{{ u.nickname || u.username }}</span>
          <span class="cp-md-mention-username">@{{ u.username }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.cp-md-editor {
  position: relative;
  display: flex;
  flex-direction: column;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 0.18s, box-shadow 0.18s;
}

.cp-md-editor:focus-within {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.cp-md-editor.is-compact {
  border-radius: 8px;
}

html.dark .cp-md-editor:focus-within {
  box-shadow: 0 0 0 3px rgba(245, 192, 76, 0.18);
}

.cp-md-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.cp-md-header.is-compact {
  border-bottom: none;
}

.cp-md-view-toggle {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  gap: 2px;
  background-color: var(--el-bg-color-overlay);
}

.vt-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12.5px;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.vt-btn:hover {
  background-color: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.vt-btn.active {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

html.dark .vt-btn.active {
  background-color: rgba(245, 192, 76, 0.16);
  color: #f5c04c;
}

html.dark .cp-md-header {
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .cp-md-view-toggle {
  background-color: rgba(255, 255, 255, 0.025);
}

.cp-md-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.cp-md-pane {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.view-split .cp-md-pane-edit {
  border-right: 1px solid var(--el-border-color-lighter);
}

html.dark .view-split .cp-md-pane-edit {
  border-right-color: rgba(255, 255, 255, 0.06);
}

.cp-md-pane-preview {
  background-color: var(--el-fill-color-lighter);
}

html.dark .cp-md-pane-preview {
  background-color: rgba(255, 255, 255, 0.02);
}

.cp-md-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-fill-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.cp-md-status .dot {
  opacity: 0.4;
}

.cp-md-status .hint {
  margin-left: auto;
  opacity: 0.7;
}

html.dark .cp-md-status {
  border-top-color: rgba(255, 255, 255, 0.06);
  background-color: rgba(255, 255, 255, 0.02);
}

.cp-md-file-input {
  display: none;
}

/* Compact 模式适配评论 */
.cp-md-editor.is-compact .cp-md-body {
  border-top: 1px dashed var(--el-border-color-lighter);
}

html.dark .cp-md-editor.is-compact .cp-md-body {
  border-top-color: rgba(255, 255, 255, 0.08);
}

/* Mention popup */
.cp-md-mention {
  position: absolute;
  z-index: 1000;
  min-width: 220px;
  max-width: 320px;
  max-height: 240px;
  overflow-y: auto;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.10);
}

html.dark .cp-md-mention {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.4);
}

.cp-md-mention-empty {
  padding: 10px 14px;
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
}

.cp-md-mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 13px;
  transition: background-color 0.12s;
}

.cp-md-mention-item.active,
.cp-md-mention-item:hover {
  background-color: var(--el-fill-color);
}

.cp-md-mention-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-color-primary-light-7);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.cp-md-mention-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.cp-md-mention-username {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
