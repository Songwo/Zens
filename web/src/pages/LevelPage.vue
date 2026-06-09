<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import { levelApi, type LevelInfo, type LevelExpRecord } from '@/api/level'
import { LEVEL_PRIVILEGES, levelTitle } from '@/utils/levelPrivileges'
import { timeAgo } from '@/utils/timeAgo'
import { useUserStore } from '@/store/user'
import { Lock, Select, TrophyBase } from '@element-plus/icons-vue'

const userStore = useUserStore()
const info = ref<LevelInfo | null>(null)
const records = ref<LevelExpRecord[]>([])
const loading = ref(true)

const currentLevel = computed(() => info.value?.level ?? 1)
const isMax = computed(() => currentLevel.value >= LEVEL_PRIVILEGES.length)
const toNext = computed(() =>
  info.value ? Math.max(0, info.value.nextLevelExp - info.value.experience) : 0
)
const currentTitle = computed(() => levelTitle(currentLevel.value))

const EXP_TIPS = [
  { action: '发布优质帖子', exp: '高' },
  { action: '回答被采纳为最佳答案', exp: '+20' },
  { action: '帖子/回答被点赞', exp: '+2' },
  { action: '每日签到（连续更多）', exp: '+5 起' },
]

const fetchAll = async () => {
  loading.value = true
  try {
    const [i, r] = await Promise.allSettled([
      levelApi.getInfo(),
      levelApi.getExpRecords({ days: 0, page: 1, pageSize: 20 }),
    ])
    if (i.status === 'fulfilled') info.value = i.value.data
    if (r.status === 'fulfilled') records.value = r.value.data.records || []
  } finally {
    loading.value = false
  }
}

const fmt = (s: string) => timeAgo(s)

onMounted(fetchAll)
</script>

<template>
  <MainLayout>
    <div class="level-page">
      <PageBackButton class="level-back" fallback="/me" />

      <!-- 当前等级概览 -->
      <div class="level-hero" v-loading="loading">
        <div class="hero-badge">
          <span class="hero-lv">Lv.{{ currentLevel }}</span>
        </div>
        <div class="hero-main">
          <div class="hero-name">
            <span class="hero-nick">{{ userStore.userInfo?.nickname || '我' }}</span>
            <span class="hero-title">{{ currentTitle }}</span>
          </div>
          <div class="hero-progress">
            <el-progress
              :percentage="info?.progress ?? 0"
              :stroke-width="10"
              :show-text="false"
              color="var(--el-color-primary)"
            />
          </div>
          <div class="hero-hint">
            <template v-if="isMax">已达到最高等级，传奇玩家 🎉</template>
            <template v-else>
              当前经验 {{ info?.experience ?? 0 }} · 距 Lv.{{ currentLevel + 1 }} 还差
              <strong>{{ toNext }}</strong> 经验
            </template>
          </div>
        </div>
      </div>

      <!-- 等级路线图 -->
      <div class="level-card">
        <div class="card-title">
          <el-icon><TrophyBase /></el-icon>
          <span>等级路线图</span>
        </div>
        <div class="roadmap">
          <div
            v-for="lv in LEVEL_PRIVILEGES"
            :key="lv.level"
            class="road-row"
            :class="{ unlocked: lv.level <= currentLevel, current: lv.level === currentLevel }"
          >
            <div class="road-node">
              <el-icon v-if="lv.level <= currentLevel" class="road-icon done"><Select /></el-icon>
              <el-icon v-else class="road-icon locked"><Lock /></el-icon>
            </div>
            <div class="road-body">
              <div class="road-head">
                <span class="road-lv">Lv.{{ lv.level }}</span>
                <span class="road-name">{{ lv.name }}</span>
                <span class="road-exp">{{ lv.exp }} 经验</span>
                <span v-if="lv.level === currentLevel" class="road-tag">当前</span>
              </div>
              <div class="road-privileges">
                <span v-for="(p, i) in lv.privileges" :key="i" class="privilege-chip">{{ p }}</span>
              </div>
            </div>
          </div>
        </div>
        <p class="roadmap-note">特权为成长路线展示，部分权益将随社区建设逐步开放。</p>
      </div>

      <!-- 经验来源 -->
      <div class="level-card">
        <div class="card-title">
          <span>经验明细</span>
        </div>
        <div v-if="records.length" class="exp-list">
          <div v-for="rec in records" :key="rec.id" class="exp-row">
            <span class="exp-reason">{{ rec.reason }}</span>
            <span class="exp-delta">+{{ rec.expDelta }}</span>
            <span class="exp-time">{{ fmt(rec.createTime) }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无经验记录" :image-size="80" />

        <div class="exp-tips">
          <div class="tips-title">如何获得经验</div>
          <div class="tips-grid">
            <div v-for="(t, i) in EXP_TIPS" :key="i" class="tip-item">
              <span class="tip-action">{{ t.action }}</span>
              <span class="tip-exp">{{ t.exp }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.level-page {
  max-width: 720px;
  margin: 0 auto;
}

.level-back {
  margin-bottom: 12px;
}

.level-hero {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-8) 0%, var(--el-bg-color-overlay) 70%);
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 16px;
}

.hero-badge {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px var(--el-color-primary-light-5);
}

.hero-lv {
  font-size: 18px;
  font-weight: 800;
}

.hero-main {
  flex: 1;
  min-width: 0;
}

.hero-name {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.hero-nick {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.hero-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  padding: 2px 10px;
  border-radius: 999px;
}

.hero-hint {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hero-hint strong {
  color: var(--el-color-primary);
}

.level-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 16px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-bottom: 14px;
}

.roadmap {
  display: flex;
  flex-direction: column;
}

.road-row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.road-row:last-child {
  border-bottom: none;
}

.road-node {
  flex-shrink: 0;
  padding-top: 2px;
}

.road-icon {
  font-size: 18px;
}

.road-icon.done {
  color: var(--el-color-primary);
}

.road-icon.locked {
  color: var(--el-text-color-placeholder);
}

.road-row:not(.unlocked) .road-body {
  opacity: 0.6;
}

.road-row.current {
  background: var(--el-color-primary-light-9);
  border-radius: 10px;
  padding: 10px 12px;
  border-bottom: none;
}

.road-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.road-lv {
  font-size: 13px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.road-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.road-exp {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.road-tag {
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: var(--el-color-primary);
  padding: 1px 8px;
  border-radius: 999px;
}

.road-privileges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.privilege-chip {
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 2px 8px;
}

.roadmap-note {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.exp-list {
  display: flex;
  flex-direction: column;
}

.exp-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  font-size: 13px;
}

.exp-row:last-child {
  border-bottom: none;
}

.exp-reason {
  flex: 1;
  color: var(--el-text-color-regular);
}

.exp-delta {
  font-weight: 700;
  color: var(--el-color-success);
}

.exp-time {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  white-space: nowrap;
}

.exp-tips {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.tips-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}

.tips-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.tip-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}

.tip-action {
  color: var(--el-text-color-regular);
}

.tip-exp {
  font-weight: 700;
  color: var(--el-color-primary);
  white-space: nowrap;
}

@media (max-width: 640px) {
  .tips-grid {
    grid-template-columns: 1fr;
  }
}
</style>
