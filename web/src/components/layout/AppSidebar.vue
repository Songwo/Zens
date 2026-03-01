<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { HomeFilled, Compass, User, Setting } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const emit = defineEmits(['navigated'])

const go = (path: string) => {
  router.push(path)
  emit('navigated')
}
</script>

<template>
  <div class="app-sidebar">
    <el-menu
      :default-active="route.path"
      class="sidebar-menu"
      :router="false"
    >
      <el-menu-item index="/" @click="go('/')">
        <el-icon><HomeFilled /></el-icon>
        <span>发现</span>
      </el-menu-item>
      
      <el-menu-item index="/trends" @click="go('/trends')">
        <el-icon><Compass /></el-icon>
        <span>热榜</span>
      </el-menu-item>
      
      <el-menu-item index="/me" @click="go('/me')">
        <el-icon><User /></el-icon>
        <span>我的</span>
      </el-menu-item>
      
      <el-menu-item index="/settings" @click="go('/settings')">
        <el-icon><Setting /></el-icon>
        <span>设置</span>
      </el-menu-item>
    </el-menu>

    <!-- Popular Tags or quick links -->
    <div class="sidebar-footer">
      <div class="footer-title">热门标签</div>
      <div class="footer-tags">
        <el-tag size="small" type="info" class="quick-tag" @click="go('/t/技术')"># 技术</el-tag>
        <el-tag size="small" type="info" class="quick-tag" @click="go('/t/生活')"># 生活</el-tag>
        <el-tag size="small" type="info" class="quick-tag" @click="go('/t/求职')"># 求职</el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.app-sidebar {
  display: flex;
  flex-direction: column;
  gap: 24px;
  height: 100%;
}

.sidebar-menu {
  border-right: none;
  background-color: transparent;
}

.sidebar-menu .el-menu-item {
  border-radius: var(--el-border-radius-base);
  margin-bottom: 8px;
  height: 48px;
  font-weight: 500;
}

.sidebar-menu .el-menu-item.is-active {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.sidebar-footer {
  margin-top: auto;
  padding: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.footer-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
  font-weight: 600;
}

.footer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.quick-tag:hover {
  background-color: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}
</style>
