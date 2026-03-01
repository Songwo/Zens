<script setup lang="ts">
import AppTopbar from '@/components/layout/AppTopbar.vue'
</script>

<template>
  <div class="page">
    <AppTopbar />

    <div class="shell">
      <div class="grid">
        <!-- Main Content Stream -->
        <main class="main">
          <slot></slot>
        </main>

        <!-- Right Rail / Sidebar -->
        <aside class="right">
          <slot name="right-rail"></slot>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  background: var(--el-bg-color-page, #f5f7fa);
}

.shell {
  max-width: 1280px; /* Song：与主布局保持相同最大宽度 */
  margin: 0 auto;
  padding: 16px;
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px; /* Song：双列布局 */
  gap: 24px;
  align-items: start;
}

.main {
  min-width: 0; /* Song：防止内容撑破布局 */
  background: var(--el-bg-color-overlay);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-light);
  min-height: 500px;
}

.right {
  position: sticky;
  top: calc(56px + 16px);
  height: calc(100vh - 56px - 32px);
  overflow-y: auto;
  scrollbar-width: none; /* Song：说明 */
}

.right::-webkit-scrollbar {
  display: none; /* Song：说明 */
}

/* Song：说明 */
@media (max-width: 992px) {
  .grid {
    grid-template-columns: 1fr;
  }
  
  .right {
    display: none; /* Song：小屏隐藏侧边栏（或改为底部展示） */
  }
}
</style>
