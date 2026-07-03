<script setup lang="ts">
import { ref } from 'vue'
import { Setting, Sunny, Moon, Monitor, FullScreen, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useUiStore, type ThemeName } from '@/store/ui'
import { storeToRefs } from 'pinia'

const uiStore = useUiStore()
const { themeName, isDark, isWide, colorMode } = storeToRefs(uiStore)

const isCollapsed = ref(false)

const handleThemeChange = (value: string) => {
  uiStore.setTheme(value as ThemeName)
}
</script>

<template>
  <div class="appearance-dock" :class="{ collapsed: isCollapsed }">
    <!-- Collapse Toggle -->
    <div class="dock-toggle" @click="isCollapsed = !isCollapsed" title="展开/收起设置">
      <el-icon><ArrowLeft v-if="!isCollapsed" /><ArrowRight v-else /></el-icon>
    </div>

    <!-- Main Content -->
    <div class="dock-content">
      <div class="dock-item">
        <el-icon class="dock-icon"><Setting /></el-icon>
        <span class="dock-label">主题</span>
        <el-select 
          v-model="themeName" 
          size="small" 
          @change="handleThemeChange"
          class="theme-select"
        >
          <el-option label="默认 (Amber)" value="default" />
          <el-option label="青墨 (Teal)" value="teal" />
          <el-option label="克制蓝 (Blue)" value="blue" />
        </el-select>
      </div>

      <div class="dock-splitter"></div>

      <div class="dock-item action" @click="uiStore.toggleDark" :title="colorMode === 'system' ? '跟随系统' : isDark ? '切换亮色模式' : '切换暗黑模式'">
        <el-icon class="dock-icon" :size="18">
          <Monitor v-if="colorMode === 'system'" />
          <Moon v-else-if="isDark" />
          <Sunny v-else />
        </el-icon>
        <span class="dock-label">{{ colorMode === 'system' ? '系统' : isDark ? '暗色' : '亮色' }}</span>
      </div>

      <div class="dock-splitter"></div>

      <div class="dock-item action" @click="uiStore.toggleWide" :title="isWide ? '切换居中阅读模式' : '切换宽屏沉浸模式'">
        <el-icon class="dock-icon" :size="18">
          <FullScreen v-if="isWide" />
          <Monitor v-else />
        </el-icon>
        <span class="dock-label">{{ isWide ? '宽屏' : '居中' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.appearance-dock {
  position: fixed;
  left: 24px;
  bottom: 24px;
  z-index: 9999;
  background-color: var(--cp-bg-elevated);
  border: 1px solid var(--cp-border);
  border-radius: 12px;
  box-shadow: var(--cp-shadow);
  display: flex;
  align-items: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  backdrop-filter: blur(8px);
}

/* Song：说明 */
@supports (backdrop-filter: blur(8px)) {
  .appearance-dock {
    background-color: var(--cp-bg-dock) !important;
  }
}

.appearance-dock.collapsed {
  transform: translateX(calc(-100% + 36px));
}

.dock-toggle {
  width: 36px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background-color: var(--cp-bg-surface);
  color: var(--cp-text-muted);
  transition: all 0.2s;
  order: 2; /* Song：切换按钮放在右侧 */
}

.dock-toggle:hover {
  color: var(--cp-primary);
  background-color: var(--cp-hover);
}

.dock-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  height: 48px;
  order: 1;
}

.dock-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.dock-item.action {
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

.dock-item.action:hover {
  background-color: var(--cp-hover);
  color: var(--cp-primary);
}

.dock-icon {
  font-size: 16px;
}

.dock-label {
  font-size: 13px;
  font-weight: 500;
  max-width: 0;
  opacity: 0;
  overflow: hidden;
  transition: all 0.3s ease;
}

.appearance-dock:hover .dock-label {
  max-width: 50px;
  opacity: 1;
}

.theme-select {
  width: 110px;
}

.dock-splitter {
  width: 1px;
  height: 24px;
  background-color: var(--el-border-color-lighter);
}

@media (max-width: 900px) {
  .appearance-dock {
    left: 10px;
    bottom: calc(var(--cp-mobile-nav-height, 62px) + env(safe-area-inset-bottom, 0px) + 18px);
    border-radius: 999px;
    width: 38px;
    height: 38px;
    max-width: none;
    opacity: 0.82;
  }

  .appearance-dock.collapsed,
  .appearance-dock:not(:hover):not(:focus-within) {
    transform: none;
  }

  .dock-toggle {
    width: 38px;
    height: 38px;
    border-radius: 999px;
    order: 1;
    background-color: transparent;
  }

  .dock-content {
    display: none;
  }
}
</style>
