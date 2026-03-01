<script setup lang="ts">
import { ref } from 'vue'

const popularItems = ref([
  { id: 1, title: '如何准备秋招面试？', views: 1240 },
  { id: 2, title: '分享一个好用的代码片段', views: 890 },
  { id: 3, title: '食堂二楼新开的窗口评分', views: 560 }
])
</script>

<template>
  <div class="app-right-rail">
    <!-- Notice/Ad Card -->
    <el-card shadow="never" class="rail-card welcome-card">
      <h3 class="card-title">Zens</h3>
      <p class="card-desc">连接校园，分享智慧。</p>
      <div class="card-action">
        <el-button type="primary" size="small" class="w-full">了解更多</el-button>
      </div>
    </el-card>

    <!-- Trending Posts -->
    <el-card shadow="never" class="rail-card top-posts-card">
      <template #header>
        <div class="card-header">
          <span>阅读排行</span>
        </div>
      </template>
      <div class="trending-list">
        <div 
          v-for="(item, index) in popularItems" 
          :key="item.id"
          class="trending-item"
        >
          <div class="item-rank" :class="{ 'top-3': index < 3 }">{{ index + 1 }}</div>
          <div class="item-content">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">{{ item.views }} 阅读</div>
          </div>
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
.app-right-rail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rail-card {
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
}

.welcome-card .card-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.welcome-card .card-desc {
  margin: 0 0 16px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.card-header {
  font-size: 14px;
  font-weight: 600;
}

.trending-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trending-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  cursor: pointer;
}

.item-rank {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-placeholder);
  width: 16px;
  text-align: center;
}

.top-3 {
  color: var(--el-color-primary);
}

.item-content {
  flex: 1;
}

.item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trending-item:hover .item-title {
  color: var(--el-color-primary);
}

.item-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.w-full {
  width: 100%;
}

.rail-footer {
  padding: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
}

.rail-footer a {
  color: inherit;
  text-decoration: none;
}

.rail-footer a:hover {
  text-decoration: underline;
}

.copyright {
  margin: 4px 0 0 0;
}
</style>
