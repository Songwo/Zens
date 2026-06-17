<script setup lang="ts">
import { computed } from 'vue'
import {
  Heading1, Heading2, Heading3,
  Bold, Italic, Strikethrough,
  Quote, List, ListOrdered, ListChecks,
  Link2, Image as ImageIcon, Code, FileCode,
  Table as TableIcon, Minus,
  Smile, Hash, Paintbrush, AtSign,
} from 'lucide-vue-next'
import EmojiPicker from './EmojiPicker.vue'

interface Props {
  /** compact 模式：只显示评论场景必备的子集（B/I/链接/代码/代码块/引用/列表/emoji/at） */
  compact?: boolean
  pastingImage?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  compact: false,
  pastingImage: false,
})

const emit = defineEmits<{
  (e: 'cmd', kind: string, payload?: any): void
  (e: 'emoji', emoji: string): void
  (e: 'upload-image'): void
  (e: 'mention'): void
}>()

const showFull = computed(() => !props.compact)

const onClick = (kind: string, payload?: any) => emit('cmd', kind, payload)
const onEmoji = (emoji: string) => emit('emoji', emoji)
</script>

<template>
  <div class="cp-md-toolbar" :class="{ 'is-compact': compact }">
    <!-- Group 1: Headings + inline styles -->
    <div v-if="showFull" class="tb-group">
      <button class="tb-btn" type="button" title="一级标题 (H1)" @click="onClick('h1')">
        <Heading1 :size="16" />
      </button>
      <button class="tb-btn" type="button" title="二级标题 (H2)" @click="onClick('h2')">
        <Heading2 :size="16" />
      </button>
      <button class="tb-btn" type="button" title="三级标题 (H3)" @click="onClick('h3')">
        <Heading3 :size="16" />
      </button>
    </div>

    <div class="tb-group">
      <button class="tb-btn" type="button" title="粗体 (Ctrl+B)" @click="onClick('bold')">
        <Bold :size="16" />
      </button>
      <button class="tb-btn" type="button" title="斜体 (Ctrl+I)" @click="onClick('italic')">
        <Italic :size="16" />
      </button>
      <button v-if="showFull" class="tb-btn" type="button" title="删除线" @click="onClick('strike')">
        <Strikethrough :size="16" />
      </button>
    </div>

    <!-- Group: Quote / Lists -->
    <div class="tb-group">
      <button class="tb-btn" type="button" title="引用 (Ctrl+Shift+.)" @click="onClick('quote')">
        <Quote :size="16" />
      </button>
      <button class="tb-btn" type="button" title="无序列表 (Ctrl+Shift+8)" @click="onClick('ul')">
        <List :size="16" />
      </button>
      <button v-if="showFull" class="tb-btn" type="button" title="有序列表 (Ctrl+Shift+7)" @click="onClick('ol')">
        <ListOrdered :size="16" />
      </button>
      <button v-if="showFull" class="tb-btn" type="button" title="任务列表" @click="onClick('task')">
        <ListChecks :size="16" />
      </button>
    </div>

    <!-- Group: Link / Image / Code -->
    <div class="tb-group">
      <button class="tb-btn" type="button" title="链接 (Ctrl+K)" @click="onClick('link')">
        <Link2 :size="16" />
      </button>
      <button class="tb-btn" type="button" title="图片上传" @click="emit('upload-image')">
        <ImageIcon :size="16" />
      </button>
      <button class="tb-btn" type="button" title="行内代码 (Ctrl+E)" @click="onClick('codeInline')">
        <Code :size="16" />
      </button>
      <button class="tb-btn" type="button" title="代码块 (Ctrl+Shift+K)" @click="onClick('codeBlock')">
        <FileCode :size="16" />
      </button>
    </div>

    <div v-if="showFull" class="tb-group">
      <button class="tb-btn" type="button" title="表格" @click="onClick('table')">
        <TableIcon :size="16" />
      </button>
      <button class="tb-btn" type="button" title="分割线" @click="onClick('hr')">
        <Minus :size="16" />
      </button>
      <button class="tb-btn" type="button" title="插入目录 [TOC]" @click="onClick('toc')">
        <Hash :size="16" />
      </button>
    </div>

    <div class="tb-group">
      <EmojiPicker @select="onEmoji">
        <template #trigger>
          <button class="tb-btn" type="button" title="插入 Emoji">
            <Smile :size="16" />
          </button>
        </template>
      </EmojiPicker>
      <button class="tb-btn" type="button" title="提及用户 (@)" @click="emit('mention')">
        <AtSign :size="16" />
      </button>
    </div>

    <div v-if="showFull" class="tb-group tb-group--end">
      <button class="tb-btn" type="button" title="一键格式化" @click="onClick('format')">
        <Paintbrush :size="16" />
      </button>
    </div>

    <span v-if="pastingImage" class="tb-status">上传中…</span>
  </div>
</template>

<style scoped>
.cp-md-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-bg-color-overlay);
}

.cp-md-toolbar.is-compact {
  padding: 4px 6px;
  border-bottom: none;
  background: transparent;
}

.tb-group {
  display: flex;
  align-items: center;
  gap: 1px;
  padding: 0 6px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.tb-group:last-of-type,
.tb-group--end {
  border-right: none;
  margin-left: auto;
}

.cp-md-toolbar.is-compact .tb-group {
  padding: 0 4px;
}

.tb-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--el-text-color-regular);
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.tb-btn:hover {
  background-color: var(--el-fill-color);
  color: var(--el-text-color-primary);
}

.tb-btn:active {
  background-color: var(--el-fill-color-dark);
}

.tb-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tb-status {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-color-primary);
  padding: 0 8px;
}

html.dark .cp-md-toolbar {
  background-color: rgba(255, 255, 255, 0.025);
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

html.dark .tb-group {
  border-right-color: rgba(255, 255, 255, 0.06);
}

html.dark .tb-btn:hover {
  background-color: rgba(255, 255, 255, 0.08);
}
</style>
