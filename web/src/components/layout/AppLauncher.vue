<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { ensureCurrentUserProfile } from '@/utils/sessionProfile'
import {
  metaverseSpaces,
  canAccessMetaverseSpace,
  buildStationEntryHref,
  isInternalHref,
  type MetaverseSpace,
} from '@/data/metaverseSpaces'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const visible = ref(false)
const profile = ref<any>(null)

const isLoggedIn = computed(() => userStore.isLoggedIn)

// 启动器只收录"主入口"卡片(featured),覆盖主站 + 三个第一方子站 + 关键工具。
const launchableSpaces = computed<MetaverseSpace[]>(() =>
  metaverseSpaces.filter(
    (space) => space.featured && canAccessMetaverseSpace(space, profile.value, isLoggedIn.value),
  ),
)

async function loadProfile() {
  if (!isLoggedIn.value) {
    profile.value = null
    return
  }
  try {
    profile.value = await ensureCurrentUserProfile()
  } catch {
    profile.value = null
  }
}

function openSpace(space: MetaverseSpace) {
  visible.value = false
  const href = buildStationEntryHref(space)
  if (isInternalHref(href)) {
    router.push(href)
  } else {
    window.open(href, '_blank', 'noopener,noreferrer')
  }
}

function viewAll() {
  visible.value = false
  router.push('/metaverse')
}

onMounted(loadProfile)
watch(isLoggedIn, loadProfile)
// 打开时按需刷新一次（拿到最新角色/信任等级）
watch(visible, (open) => {
  if (open) loadProfile()
})
</script>

<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-end"
    :width="320"
    trigger="click"
    popper-class="app-launcher-popover"
  >
    <template #reference>
      <el-button
        circle
        text
        class="icon-btn"
        :class="{ active: route.path.startsWith('/metaverse') }"
        title="Zens 生态应用"
      >
        <el-icon><Grid /></el-icon>
      </el-button>
    </template>

    <div class="launcher-panel">
      <div class="launcher-header">
        <span class="launcher-title">Zens 生态</span>
        <span class="launcher-sub">登录一次，畅通所有子站</span>
      </div>

      <div v-if="launchableSpaces.length" class="launcher-grid">
        <button
          v-for="space in launchableSpaces"
          :key="space.id"
          class="launcher-item"
          @click="openSpace(space)"
        >
          <span class="launcher-icon" :style="{ color: space.accent }">
            <el-icon :size="22"><component :is="space.icon" /></el-icon>
          </span>
          <span class="launcher-name">{{ space.title }}</span>
        </button>
      </div>
      <div v-else class="launcher-empty">登录后查看可访问的子站</div>

      <button class="launcher-all" @click="viewAll">查看全部 →</button>
    </div>
  </el-popover>
</template>

<style scoped>
.launcher-panel {
  padding: 4px 2px;
}

.launcher-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.launcher-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.launcher-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.launcher-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
}

.launcher-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 6px;
  border: none;
  background: transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.launcher-item:hover {
  background: var(--el-fill-color-light);
}

.launcher-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}

.launcher-name {
  font-size: 11px;
  line-height: 1.3;
  text-align: center;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.launcher-empty {
  padding: 20px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.launcher-all {
  width: 100%;
  margin-top: 8px;
  padding: 8px;
  border: none;
  background: transparent;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
}

.launcher-all:hover {
  color: var(--el-color-primary-light-3);
}
</style>
