<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Document,
  Flag,
  MagicStick,
  Medal,
  Reading,
} from '@element-plus/icons-vue'
import { publicDataApi, type HomeBootstrapPayload } from '@/api/publicData'
import { usePostComposerStore } from '@/store/postComposer'
import { useUserStore } from '@/store/user'
import { encodePostId } from '@/utils/shortId'
import { hasBackofficeAccess } from '@/utils/sessionProfile'

const router = useRouter()
const composerStore = usePostComposerStore()
const userStore = useUserStore()

const bootstrap = ref<HomeBootstrapPayload | null>(null)
const loading = ref(true)

const hotPosts = computed(() => (bootstrap.value?.hotRank || []).slice(0, 5))

const tasks = [
  { label: '选择兴趣标签', path: '/onboarding' },
  { label: '读一篇精华帖', path: '/featured' },
  { label: '回答待解决问题', path: '/?sort=unsolved' },
  { label: '查看本周热榜', path: '/hot' },
]

const quickActions = computed(() => {
  const actions = [
    { label: '发布帖子', icon: Document, action: 'compose' },
    { label: '阅读指南', icon: Reading, path: '/guide' },
    { label: '精华主题', icon: Document, path: '/featured' },
    { label: '福利中心', icon: Medal, path: '/benefits' },
  ]
  if (hasBackofficeAccess(userStore.userInfo as any)) {
    actions.splice(3, 0, { label: '举报处理', icon: Flag, path: '/admin/reports' })
  }
  return actions
})

const currentLevel = computed(() => {
  const level = Number(userStore.userInfo?.level ?? 1)
  return Number.isFinite(level) && level > 0 ? level : 1
})

const showNewUserPath = computed(() => !userStore.isLoggedIn || currentLevel.value <= 1)

const formatMetric = (value: unknown) => {
  const num = Number(value) || 0
  if (num >= 10000) return `${(num / 10000).toFixed(1)}w`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}k`
  return `${num}`
}

const goPost = (postId: string) => {
  router.push(`/t/${encodePostId(postId)}`)
}

const runQuickAction = (item: { path?: string; action?: string }) => {
  if (item.action === 'compose') {
    composerStore.open()
    return
  }
  if (item.path) {
    router.push(item.path)
  }
}

onMounted(async () => {
  try {
    const res = await publicDataApi.getHomeBootstrapCached(12, 5, 'WEEK')
    bootstrap.value = res.data || null
  } catch {
    bootstrap.value = null
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="right-rail">
    <section v-if="showNewUserPath" class="rail-section">
      <div class="section-head compact">
        <div>
          <span class="eyebrow">新人路径</span>
          <h3>今天可以做什么</h3>
        </div>
        <button class="compose-mini" type="button" @click="composerStore.open()">发帖</button>
      </div>
      <div class="task-list">
        <button v-for="task in tasks" :key="task.label" class="task-item" type="button" @click="router.push(task.path)">
          <span>{{ task.label }}</span>
        </button>
      </div>
    </section>

    <section class="rail-section">
      <div class="section-head compact">
        <div>
          <span class="eyebrow">快捷入口</span>
          <h3>社区工具</h3>
        </div>
        <el-icon><MagicStick /></el-icon>
      </div>
      <div class="action-list">
        <button
          v-for="item in quickActions"
          :key="item.label"
          class="action-btn"
          type="button"
          @click="runQuickAction(item)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </div>
    </section>

    <section class="rail-section trending-section">
      <div class="section-head compact">
        <div>
          <span class="eyebrow">热度排序</span>
          <h3>本周行为热榜</h3>
        </div>
      </div>

      <div v-if="loading" class="trending-list">
        <el-skeleton v-for="i in 4" :key="i" :rows="1" animated />
      </div>
      <div v-else-if="hotPosts.length" class="trending-list">
        <button
          v-for="(item, index) in hotPosts"
          :key="item.postId"
          class="trending-item"
          type="button"
          @click="goPost(item.postId)"
        >
          <span class="rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
          <span class="trend-copy">
            <strong>{{ item.title }}</strong>
            <small>
              <span>{{ formatMetric(item.viewCount) }} 阅读</span>
              <span>{{ formatMetric(item.commentCount || 0) }} 互动</span>
            </small>
          </span>
        </button>
      </div>
      <div v-else class="empty-panel">
        <strong>热榜蓄势待发</strong>
        <span>优质讨论获得浏览和回复后会在这里浮现。</span>
      </div>
    </section>

    <div class="rail-footer">
      <router-link to="/about">关于我们</router-link>
      <router-link to="/terms">用户协议</router-link>
      <router-link to="/privacy">隐私政策</router-link>
      <router-link to="/contact">联系管理</router-link>
      <p>© 2026 Zens</p>
    </div>
  </div>
</template>

<style scoped>
.right-rail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rail-section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
  padding: 16px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head.compact {
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 4px 0 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.3;
}

.eyebrow {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 800;
}

.section-head .el-icon {
  color: var(--el-color-primary);
  font-size: 20px;
}

.task-item span,
.action-btn span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.compose-mini {
  border: 1px solid rgba(244, 180, 0, 0.36);
  border-radius: 999px;
  background: #fff3d4;
  color: #7a5700;
  cursor: pointer;
  font-size: 12px;
  font-weight: 800;
  min-height: 28px;
  padding: 0 12px;
}

.task-list {
  display: grid;
  gap: 8px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
  cursor: pointer;
  padding: 0 11px;
  text-align: left;
}

.task-item::after {
  content: '>';
  color: var(--el-text-color-placeholder);
  font-size: 18px;
}

.action-list {
  display: grid;
  gap: 8px;
}

.action-btn {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  min-width: 0;
  min-height: 40px;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
  cursor: pointer;
  padding: 0 11px;
  text-align: left;
}

.action-btn .el-icon {
  color: var(--el-color-primary);
  font-size: 17px;
}

.trending-list {
  display: grid;
  gap: 8px;
}

.trending-item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 9px;
  width: 100%;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  padding: 8px;
  text-align: left;
}

.trending-item:hover,
.task-item:hover,
.action-btn:hover {
  background: var(--el-fill-color-light);
}

.rank {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 7px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.rank.top {
  background: #fff3d4;
  color: #8a5a00;
}

.trend-copy {
  min-width: 0;
}

.trend-copy strong {
  display: -webkit-box;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.trend-copy small {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.empty-panel {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
}

.empty-panel strong {
  color: var(--el-text-color-primary);
}

.rail-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  padding: 4px 4px 16px;
}

.rail-footer a,
.rail-footer p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-decoration: none;
}
</style>
