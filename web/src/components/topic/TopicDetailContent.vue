<script setup lang="ts">
import { computed } from 'vue'
import { Edit, Share, Star, Warning } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const props = defineProps<{
  topic: {
    title: string
    content: string
    category: { name: string; color: string }
    tags: string[]
    createdAt: string
  }
}>()

const renderedContent = computed(() => {
  return md.render(props.topic.content || '')
})
</script>

<template>
  <div class="topic-detail-content">
    <!-- Header -->
    <header class="topic-header">
      <h1 class="topic-title">{{ topic.title }}</h1>
      
      <div class="topic-meta">
        <span class="category-badge">
          <span class="cat-dot" :style="{ backgroundColor: topic.category.color }"></span>
          {{ topic.category.name }}
        </span>
        
        <div class="tags-group">
          <el-tag 
            v-for="tag in topic.tags" 
            :key="tag" 
            size="small" 
            type="info"
            class="small-tag"
          >
            # {{ tag }}
          </el-tag>
        </div>
        
        <span class="publish-time">{{ topic.createdAt }} 发布</span>
      </div>
    </header>

    <!-- Markdown Body -->
    <article class="topic-body markdown-body" v-html="renderedContent"></article>

    <!-- Action Bar -->
    <div class="topic-actions">
      <el-button-group class="actions-group">
        <el-button plain :icon="Star">收藏主题</el-button>
        <el-button plain :icon="Share">分享</el-button>
        <el-button plain :icon="Edit" class="hidden-xs-only">编辑</el-button>
        <el-button plain :icon="Warning" type="danger">举报</el-button>
      </el-button-group>
    </div>
  </div>
</template>

<style scoped>
.topic-detail-content {
  background: var(--cp-bg-card);
  border-radius: var(--el-border-radius-base);
  padding: 32px;
  box-shadow: var(--el-box-shadow-lighter);
  margin-bottom: 24px;
}

.topic-header {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 16px;
  margin-bottom: 24px;
}

.topic-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  line-height: 1.4;
}

.topic-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.category-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  background: transparent;
  padding: 0;
}

.cat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.tags-group {
  display: flex;
  gap: 6px;
}

.small-tag {
  border: none;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}

.publish-time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-left: auto;
}

/* Song：说明 */
.topic-body {
  font-size: 15px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  min-height: 200px;
}

.topic-body :deep(h1),
.topic-body :deep(h2),
.topic-body :deep(h3) {
  margin-top: 1.8em;
  margin-bottom: 0.8em;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.topic-body :deep(p) {
  margin-bottom: 1.2em;
}

.topic-body :deep(pre) {
  background-color: var(--cp-bg-elevated);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  border: 1px solid var(--cp-border);
}

.topic-body :deep(code) {
  font-family: var(--el-font-family-mono, monospace);
  background-color: var(--cp-bg-elevated);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.85em;
  color: var(--cp-primary);
}

.topic-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid var(--cp-border);
}

.topic-body :deep(a) {
  color: var(--cp-primary);
  text-decoration: none;
}
.topic-body :deep(a:hover) {
  text-decoration: underline;
}

.topic-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

@media (max-width: 768px) {
  .topic-detail-content {
    padding: 20px;
  }
  .publish-time {
    margin-left: 0;
    width: 100%;
  }
}
</style>
