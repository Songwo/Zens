<script setup lang="ts">
import { ref } from 'vue'
import { View, ChatDotRound } from '@element-plus/icons-vue'
import RecommendExplainCard from './RecommendExplainCard.vue'

// Song：说明
const hotPosts = ref([
  { id: 1, title: '关于 Vue3 响应式原理深度解析', views: 4230, comments: 128 },
  { id: 2, title: '如何写出优雅的 TypeScript 代码', views: 3100, comments: 95 },
  { id: 3, title: '2026 校招前端面试总结 (附面经)', views: 2890, comments: 340 },
  { id: 4, title: 'Element Plus 最佳实践指南', views: 1950, comments: 76 },
  { id: 5, title: '记一次线上内存泄漏排查全过程', views: 1800, comments: 55 }
])

const hotTags = ref([
  { name: '前端', weight: 98 },
  { name: 'Vue.js', weight: 87 },
  { name: '求职面经', weight: 76 },
  { name: '后端开发', weight: 65 },
  { name: '吐槽', weight: 54 },
  { name: '考研', weight: 43 },
  { name: '二手闲置', weight: 32 }
])
</script>

<template>
  <div class="right-rail-container">
    
    <!-- Smart Recommendation Explain -->
    <RecommendExplainCard />

    <!-- 24h Hot Posts -->
    <el-card class="rail-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">24h 热门</span>
        </div>
      </template>
      <div class="hot-list">
        <div v-for="(post, index) in hotPosts" :key="post.id" class="hot-item">
          <div class="rank-number" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
          <div class="post-content">
            <router-link :to="`/p/${post.id}`" class="post-title">{{ post.title }}</router-link>
            <div class="post-meta">
              <span><el-icon><View /></el-icon> {{ post.views }}</span>
              <span><el-icon><ChatDotRound /></el-icon> {{ post.comments }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Hot Tags -->
    <el-card class="rail-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">热门标签</span>
        </div>
      </template>
      <div class="tags-cloud">
        <router-link 
          v-for="tag in hotTags" 
          :key="tag.name" 
          :to="`/t/${tag.name}`"
          class="tag-item"
        >
          #{{ tag.name }}
        </router-link>
      </div>
    </el-card>

    <!-- Site Stats -->
    <el-card class="rail-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">站点统计</span>
        </div>
      </template>
      <div style="display: flex; justify-content: space-between;">
        <div style="text-align: center;">
          <div style="font-size: 18px; font-weight: bold; color: var(--el-text-color-primary);">12.5k</div>
          <div style="font-size: 12px; color: var(--el-text-color-secondary);">帖子总数</div>
        </div>
        <div style="text-align: center;">
          <div style="font-size: 18px; font-weight: bold; color: var(--el-text-color-primary);">3,402</div>
          <div style="font-size: 12px; color: var(--el-text-color-secondary);">活跃用户</div>
        </div>
      </div>
    </el-card>

    <!-- Footer Links -->
    <div class="rail-footer">
      <a href="#">关于我们</a> • 
      <a href="#">用户协议</a> • 
      <a href="#">隐私政策</a>
      <p class="copyright">© 2026 Zens</p>
    </div>
  </div>
</template>

<style scoped>
.right-rail-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.rail-card {
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
  background-color: var(--el-bg-color);
}

.rail-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.rail-card :deep(.el-card__body) {
  padding: 16px;
}

.card-header .title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

/* Song：说明 */
.hot-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hot-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.rank-number {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  margin-top: 2px;
}

.rank-1 { background-color: #fef0f0; color: var(--el-color-danger); }
.rank-2 { background-color: #fdf6ec; color: var(--el-color-warning); }
.rank-3 { background-color: #f0f9eb; color: var(--el-color-success); }

.post-content {
  flex: 1;
  min-width: 0;
}

.post-title {
  display: block;
  font-size: 14px;
  color: var(--el-text-color-primary);
  text-decoration: none;
  line-height: 1.4;
  margin-bottom: 6px;
  transition: color 0.2s;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.post-title:hover {
  color: var(--el-color-primary);
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.post-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Song：说明 */
.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-item {
  display: inline-block;
  padding: 4px 10px;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  border-radius: 12px;
  font-size: 12px;
  text-decoration: none;
  transition: all 0.2s;
}

.tag-item:hover {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

/* Song：说明 */
.rail-footer {
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  padding: 0 16px;
}

.rail-footer a {
  color: var(--el-text-color-secondary);
  text-decoration: none;
  transition: color 0.2s;
}

.rail-footer a:hover {
  color: var(--el-text-color-primary);
}

.copyright {
  margin-top: 8px;
}
</style>
