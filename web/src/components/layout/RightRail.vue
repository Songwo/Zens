<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Document,
  Flag,
  MagicStick,
  Medal,
  Reading,
  TrendCharts,
} from '@element-plus/icons-vue'
import { publicDataApi, type HomeBootstrapPayload } from '@/api/publicData'
import { usePostComposerStore } from '@/store/postComposer'
import { encodePostId } from '@/utils/shortId'

const router = useRouter()
const composerStore = usePostComposerStore()

const bootstrap = ref<HomeBootstrapPayload | null>(null)
const loading = ref(true)

const stats = computed(() => bootstrap.value?.siteStats || {
  totalPosts: 0,
  totalUsers: 0,
  totalComments: 0,
  todayPosts: 0,
})

const hotPosts = computed(() => (bootstrap.value?.hotRank || []).slice(0, 5))
const hotTags = computed(() => (bootstrap.value?.hotTags || []).slice(0, 8))

const briefingItems = computed(() => [
  { label: '今日新帖', value: stats.value.todayPosts, hint: '适合快速巡检新讨论' },
  { label: '累计互动', value: stats.value.totalComments, hint: '评论和回复沉淀' },
  { label: '活跃成员', value: stats.value.totalUsers, hint: '社区用户规模' },
])

const tasks = [
  { label: '选择兴趣标签', path: '/onboarding' },
  { label: '读一篇精华帖', path: '/featured' },
  { label: '回答待解决问题', path: '/?sort=unsolved' },
  { label: '查看本周热榜', path: '/hot' },
]

const quickActions = [
  { label: '阅读指南', icon: Reading, path: '/guide' },
  { label: '精华主题', icon: Document, path: '/featured' },
  { label: '举报处理', icon: Flag, path: '/admin/reports' },
  { label: '福利中心', icon: Medal, path: '/benefits' },
]

const formatMetric = (value: unknown) => {
  const num = Number(value) || 0
  if (num >= 10000) return `${(num / 10000).toFixed(1)}w`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}k`
  return `${num}`
}

const goPost = (postId: string) => {
  router.push(`/t/${encodePostId(postId)}`)
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
    <section class="rail-section briefing-section">
      <div class="section-head">
        <div>
          <span class="eyebrow">社区简报</span>
          <h3>本周运营脉搏</h3>
        </div>
        <el-icon><TrendCharts /></el-icon>
      </div>

      <div v-if="loading" class="briefing-loading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else class="briefing-grid">
        <div v-for="item in briefingItems" :key="item.label" class="briefing-item">
          <strong>{{ formatMetric(item.value) }}</strong>
          <span>{{ item.label }}</span>
          <small>{{ item.hint }}</small>
        </div>
      </div>
    </section>

    <section class="rail-section">
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
      <div class="action-grid">
        <button
          v-for="item in quickActions"
          :key="item.label"
          class="action-btn"
          type="button"
          @click="router.push(item.path)"
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

    <section v-if="hotTags.length" class="rail-section tags-section">
      <div class="section-head compact">
        <div>
          <span class="eyebrow">发现话题</span>
          <h3>热门标签</h3>
        </div>
      </div>
      <div class="tags-cloud">
        <router-link v-for="tag in hotTags" :key="tag.id || tag.name" :to="`/tag/${tag.name}`">
          #{{ tag.name }}
        </router-link>
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

.briefing-section {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-primary-light-9) 70%, transparent), var(--el-bg-color-overlay));
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

.briefing-grid {
  display: grid;
  gap: 9px;
}

.briefing-item {
  display: grid;
  gap: 2px;
  padding: 10px;
  border: 1px solid color-mix(in srgb, var(--el-border-color-light) 72%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-bg-color) 86%, transparent);
}

.briefing-item strong {
  color: var(--el-text-color-primary);
  font-size: 22px;
  line-height: 1;
}

.briefing-item span,
.task-item span,
.action-btn span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.briefing-item small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
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

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.action-btn {
  display: flex;
  min-width: 0;
  min-height: 58px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  cursor: pointer;
}

.action-btn .el-icon {
  color: var(--el-color-primary);
  font-size: 18px;
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

.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags-cloud a {
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  padding: 4px 9px;
  text-decoration: none;
}

.tags-cloud a:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
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
