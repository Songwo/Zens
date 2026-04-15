<script setup lang="ts">
import { View, ChatDotRound, Star, Box } from '@element-plus/icons-vue'

const props = defineProps<{
  stats: {
    views: number
    replies: number
    likes: number
    bookmarks: number
    createdAt: string
    lastActive: string
  }
}>()
</script>

<template>
  <el-card shadow="never" class="topic-stats-card">
    <template #header>
      <div class="card-header">
        <span>主题行为分析</span>
      </div>
    </template>
    
    <div class="stats-grid">
      <div class="grid-item">
        <el-icon class="grid-icon bg-blue"><View /></el-icon>
          <div class="grid-info">
            <div class="grid-val">{{ stats.views }}</div>
            <div class="grid-lbl">浏览信号</div>
          </div>
        </div>
      
      <div class="grid-item">
        <el-icon class="grid-icon bg-green"><ChatDotRound /></el-icon>
          <div class="grid-info">
            <div class="grid-val">{{ stats.replies }}</div>
            <div class="grid-lbl">回复信号</div>
          </div>
        </div>
      
      <div class="grid-item">
        <el-icon class="grid-icon bg-red"><Star /></el-icon>
          <div class="grid-info">
            <div class="grid-val">{{ stats.likes }}</div>
            <div class="grid-lbl">点赞反馈</div>
          </div>
        </div>
      
      <div class="grid-item">
        <el-icon class="grid-icon bg-orange"><Box /></el-icon>
          <div class="grid-info">
            <div class="grid-val">{{ stats.bookmarks }}</div>
            <div class="grid-lbl">收藏沉淀</div>
          </div>
        </div>
    </div>

    <div class="time-infos">
      <div class="time-item">
        <span class="lbl">创建时间</span>
        <span class="val">{{ stats.createdAt }}</span>
      </div>
      <div class="time-item">
        <span class="lbl">最近行为活跃</span>
        <span class="val">{{ stats.lastActive }}</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.topic-stats-card {
  border-radius: var(--el-border-radius-base);
}

.card-header {
  font-weight: 600;
  font-size: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.grid-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.grid-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
}

.bg-blue { background-color: var(--el-color-primary); }
.bg-green { background-color: var(--el-color-success); }
.bg-red { background-color: var(--el-color-danger); }
.bg-orange { background-color: var(--el-color-warning); }

.grid-val {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.grid-lbl {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.time-infos {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.time-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.time-item .lbl {
  color: var(--el-text-color-regular);
}

.time-item .val {
  font-weight: 500;
  color: var(--el-text-color-primary);
}
</style>
