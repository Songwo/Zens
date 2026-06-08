<script setup lang="ts">
import { ref, computed } from 'vue'
import { md } from '@/utils/markdownRenderer'

const visible = ref(false)

const open = () => {
  visible.value = true
}

const rawMarkdown = `
### 1. 标题
\`\`\`markdown
# 一级标题
## 二级标题
### 三级标题
\`\`\`

### 2. 加粗与斜体
\`\`\`markdown
**这是加粗文字**
*这是斜体文字*
\`\`\`

### 3. 列表
\`\`\`markdown
- 无序列表项 1
- 无序列表项 2

1. 有序列表项 1
2. 有序列表项 2
\`\`\`

### 4. 引用与代码
\`\`\`markdown
> 这是引用文字

\`单行代码\`

\\\`\\\`\\\`javascript
// 多行代码块
const msg = "Hello World!";
console.log(msg);
\\\`\\\`\\\`
\`\`\`

### 5. 链接与图片
\`\`\`markdown
[文字链接](https://example.com)
![图片替代文字](图片地址URL)
\`\`\`

### 6. 其他高级特性
你可以直接粘贴图片到编辑器中，系统会自动上传并插入 Markdown 语法。

输入 \`[TOC]\` 会自动为你生成一个漂亮的目录！
`

const renderedTutorial = computed(() => {
  return md.render(rawMarkdown.replace(/\\`\\`\\`/g, '```'))
})

const handleMarkdownClick = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (target.classList.contains('copy-btn')) {
    const code = target.parentElement?.parentElement?.querySelector('code')?.innerText
    if (code) {
      navigator.clipboard.writeText(code)
      const originalText = target.innerText
      target.innerText = '已复制!'
      setTimeout(() => { target.innerText = originalText }, 2000)
    }
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="Markdown 快速入门教程" width="700px" append-to-body class="markdown-tutorial-dialog">
    <div class="tutorial-content markdown-body" v-html="renderedTutorial" @click="handleMarkdownClick"></div>
    <template #footer>
      <el-button type="primary" @click="visible = false">我学会了</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tutorial-content {
  max-height: 60vh;
  overflow-y: auto;
  padding: 10px 0;
}
.tutorial-content h3 {
  margin-top: 1.5em;
  color: var(--el-color-primary);
}
.tutorial-content pre {
  margin: 10px 0;
  padding: 15px;
}
</style>
