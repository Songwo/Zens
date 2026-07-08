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

    <!-- Footer Links -->
    <div class="rail-footer">
      <router-link to="/about">关于我们</router-link> •
      <router-link to="/terms">用户协议</router-link> •
      <router-link to="/privacy">隐私政策</router-link> •
      <router-link to="/contact">联系管理</router-link>
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
