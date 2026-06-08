<script setup lang="ts">
import { ref, watch } from 'vue'
import { md } from '@/utils/markdownRenderer'
import { renderMarkdownWithTocAsync } from '@/utils/markdownToc'
import { renderGithubRichCards } from '@/utils/richLink'

interface Props {
  content: string
  /** 是否在预览里展开 [TOC] 目录（默认 true） */
  inlineToc?: boolean
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), {
  inlineToc: true,
  emptyText: '暂无内容',
})

const html = ref<string>('')
let renderToken = 0

async function render() {
  const myToken = ++renderToken
  const src = props.content?.trim() ? props.content : ''
  if (!src) {
    html.value = `<p class="cp-preview-empty">${props.emptyText}</p>`
    return
  }
  try {
    const { html: rendered } = await renderMarkdownWithTocAsync(md, src, { inlineToc: props.inlineToc })
    if (myToken !== renderToken) return  // 过时的渲染丢弃
    html.value = renderGithubRichCards(rendered)
  } catch (e) {
    if (myToken !== renderToken) return
    html.value = '<p class="cp-preview-empty">渲染失败</p>'
  }
}

watch(() => props.content, render, { immediate: true })
</script>

<template>
  <div class="cp-md-preview markdown-body" v-html="html" />
</template>

<style scoped>
.cp-md-preview {
  padding: 16px 20px 24px;
  overflow-y: auto;
  height: 100%;
  box-sizing: border-box;
}

.cp-md-preview :deep(.cp-preview-empty) {
  color: var(--el-text-color-secondary);
  font-style: italic;
  padding: 8px 0;
  margin: 0;
}
</style>
