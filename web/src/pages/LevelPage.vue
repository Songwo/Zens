<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import { levelApi, type LevelInfo, type LevelExpRecord } from '@/api/level'
import { LEVEL_PRIVILEGES, levelTitle } from '@/utils/levelPrivileges'
import { timeAgo } from '@/utils/timeAgo'
import { useUserStore } from '@/store/user'
import { trustLevelApi, type TrustInfo } from '@/api/trustLevel'
import { trustLevelColor, trustLevelLabel } from '@/utils/trustLevel'
import { Lock, Select, TrophyBase, ArrowRight, Medal, ChatLineRound, Pointer, Calendar } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const info = ref<LevelInfo | null>(null)
const records = ref<LevelExpRecord[]>([])
const trustInfo = ref<TrustInfo | null>(null)
const loading = ref(true)

const currentLevel = computed(() => info.value?.level ?? 1)
const isMax = computed(() => currentLevel.value >= LEVEL_PRIVILEGES.length)
const toNext = computed(() =>
  info.value ? Math.max(0, info.value.nextLevelExp - info.value.experience) : 0
)
const currentTitle = computed(() => levelTitle(currentLevel.value))
const currentSpec = computed(() => LEVEL_PRIVILEGES[currentLevel.value - 1])
const nextSpec = computed(() => (currentLevel.value < LEVEL_PRIVILEGES.length ? LEVEL_PRIVILEGES[currentLevel.value] : null))
const currentTrustLevel = computed(() => trustInfo.value?.trustLevel ?? userStore.userInfo?.trustLevel ?? 0)

// Song：等级对应的主题色（从低到高，渐变升级感）
const LEVEL_COLORS = ['#909399', '#67c23a', '#409eff', '#909399', '#9c27b0', '#e6a23c', '#f56c6c', '#722ed1', '#13c2c2', '#fa541c']
const themeColor = computed(() => LEVEL_COLORS[Math.min(currentLevel.value - 1, LEVEL_COLORS.length - 1)])

const EXP_TIPS = [
  { action: '发布优质帖子', exp: '高', icon: Pointer },
  { action: '回答被采纳为最佳答案', exp: '+20', icon: Select },
  { action: '帖子/回答被点赞', exp: '+2', icon: ChatLineRound },
  { action: '每日签到（连续更多）', exp: '+5 起', icon: Calendar },
]

const fetchAll = async () => {
  loading.value = true
  try {
    const [i, r, t] = await Promise.allSettled([
      levelApi.getInfo(),
      levelApi.getExpRecords({ days: 0, page: 1, pageSize: 20 }),
      trustLevelApi.info(),
    ])
    if (i.status === 'fulfilled') info.value = i.value.data
    if (r.status === 'fulfilled') records.value = r.value.data.records || []
    if (t.status === 'fulfilled') trustInfo.value = t.value.data
  } finally {
    loading.value = false
  }
}

const fmt = (s: string) => timeAgo(s)

onMounted(fetchAll)
</script>

<template>
  <MainLayout>
    <div class="level-page" v-loading="loading">
      <PageBackButton class="level-back" fallback="/me" />

      <!-- ============ 1. 等级英雄卡 ============ -->
      <div class="hero-card" :style="{ '--theme-color': themeColor }">
        <div class="hero-orb orb-1"></div>
        <div class="hero-orb orb-2"></div>

        <div class="hero-content">
          <div class="hero-badge-wrap">
            <div class="hero-ring"></div>
            <div class="hero-ring hero-ring-2"></div>
            <div class="hero-badge-core">
              <span class="hero-lv-num">{{ currentLevel }}</span>
              <span class="hero-lv-suffix">Lv</span>
            </div>
          </div>

          <div class="hero-meta">
            <div class="hero-greeting">{{ userStore.userInfo?.nickname || '我' }} 的资历</div>
            <h1 class="hero-title">
              <span class="hero-title-name">{{ currentTitle }}</span>
              <span v-if="isMax" class="hero-crown">👑</span>
            </h1>

            <div class="hero-progress" v-if="!isMax">
              <div class="prog-head">
                <span class="prog-label">距 Lv.{{ currentLevel + 1 }} 还需</span>
                <span class="prog-need">{{ toNext }} 经验</span>
              </div>
              <div class="prog-track">
                <div class="prog-fill" :style="{ width: (info?.progress ?? 0) + '%' }"></div>
              </div>
              <div class="prog-sub">
                当前 {{ info?.experience ?? 0 }} / {{ info?.nextLevelExp ?? 0 }} 经验
              </div>
            </div>
            <div class="hero-progress hero-max" v-else>
              <el-icon class="hero-max-icon"><TrophyBase /></el-icon>
              <span>已登顶 · 社区传奇</span>
            </div>

            <div class="hero-sub" @click="router.push('/trust')">
              <span class="sub-chip" :style="{ background: trustLevelColor(currentTrustLevel) }">
                TL{{ currentTrustLevel }} · {{ trustLevelLabel(currentTrustLevel) }}
              </span>
              <span class="sub-label">信任等级（管功能权限）</span>
              <el-icon class="sub-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 2. 当前等级特权 ============ -->
      <section class="section" v-if="currentSpec">
        <div class="section-head">
          <h2 class="section-title">
            <el-icon class="section-icon"><Medal /></el-icon>Lv.{{ currentLevel }} 特权
          </h2>
          <span class="section-sub">{{ currentTitle }} 专属</span>
        </div>
        <div class="priv-grid">
          <div v-for="(p, i) in currentSpec.privileges" :key="i" class="priv-card">
            <div class="priv-check"><el-icon><Select /></el-icon></div>
            <span>{{ p }}</span>
          </div>
        </div>
        <div v-if="nextSpec" class="next-hint">
          <el-icon><Lock /></el-icon>
          <span>升级到 Lv.{{ nextSpec.level }} · {{ nextSpec.name }} 后解锁更多特权</span>
        </div>
      </section>

      <!-- ============ 3. 等级路线图 ============ -->
      <section class="section">
        <div class="section-head">
          <h2 class="section-title">
            <el-icon class="section-icon"><TrophyBase /></el-icon>成长路线
          </h2>
          <span class="section-sub">Lv.1 → Lv.10</span>
        </div>
        <div class="roadmap">
          <div
            v-for="lv in LEVEL_PRIVILEGES"
            :key="lv.level"
            class="road-row"
            :class="{ unlocked: lv.level <= currentLevel, current: lv.level === currentLevel }"
          >
            <div class="road-node" :style="{ '--node-color': LEVEL_COLORS[lv.level - 1] }">
              <el-icon v-if="lv.level <= currentLevel"><Select /></el-icon>
              <el-icon v-else><Lock /></el-icon>
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
      </section>

      <!-- ============ 4. 经验获取 ============ -->
      <section class="section">
        <div class="section-head">
          <h2 class="section-title">如何获取经验</h2>
        </div>
        <div class="tips-grid">
          <div v-for="(t, i) in EXP_TIPS" :key="i" class="tip-card">
            <div class="tip-icon-wrap">
              <el-icon class="tip-icon"><component :is="t.icon" /></el-icon>
            </div>
            <div class="tip-body">
              <div class="tip-action">{{ t.action }}</div>
              <div class="tip-exp">{{ t.exp }} <span>经验</span></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 5. 经验明细 ============ -->
      <section class="section">
        <div class="section-head">
          <h2 class="section-title">最近经验记录</h2>
        </div>
        <div v-if="records.length" class="exp-list">
          <div v-for="rec in records" :key="rec.id" class="exp-row">
            <span class="exp-reason">{{ rec.reason }}</span>
            <span class="exp-delta">+{{ rec.expDelta }}</span>
            <span class="exp-time">{{ fmt(rec.createTime) }}</span>
          </div>
        </div>
        <el-empty v-else description="还没有经验记录，发个帖子试试吧" :image-size="80" />
      </section>
    </div>
  </MainLayout>
</template>

<style scoped>
.level-page {
  max-width: min(100%, var(--cp-profile-page-width, 1080px));
  margin: 0 auto;
  padding: 32px 16px 60px;
}

.level-back {
  margin-bottom: 16px;
}

/* ============ 英雄卡 ============ */
.hero-card {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  padding: 36px 32px;
  margin-bottom: 32px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--theme-color) 14%, var(--el-bg-color-overlay)) 0%, var(--el-bg-color-overlay) 65%);
  border: 1px solid color-mix(in srgb, var(--theme-color) 24%, var(--el-border-color-lighter));
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(50px);
  opacity: 0.3;
  pointer-events: none;
}
.orb-1 {
  width: 220px;
  height: 220px;
  background: var(--theme-color);
  top: -80px;
  right: -50px;
}
.orb-2 {
  width: 160px;
  height: 160px;
  background: var(--theme-color);
  bottom: -70px;
  left: 15%;
  opacity: 0.16;
}

.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 28px;
  align-items: center;
}

.hero-badge-wrap {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero-ring,
.hero-ring-2 {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid var(--theme-color);
  opacity: 0.25;
}
.hero-ring-2 {
  inset: -14px;
  opacity: 0.12;
}
.hero-badge-core {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--theme-color);
  box-shadow: 0 12px 32px color-mix(in srgb, var(--theme-color) 45%, transparent);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.hero-lv-num {
  font-size: 38px;
  font-weight: 800;
  line-height: 1;
}
.hero-lv-suffix {
  font-size: 11px;
  margin-top: 2px;
  opacity: 0.9;
  letter-spacing: 1px;
}

.hero-meta {
  flex: 1;
  min-width: 0;
}
.hero-greeting {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 2px;
}
.hero-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  margin: 0 0 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.hero-crown {
  font-size: 20px;
}
.hero-title-name {
  background: linear-gradient(90deg, var(--theme-color), color-mix(in srgb, var(--theme-color) 60%, var(--el-color-warning)));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-progress {
  background: var(--el-fill-color-light);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 10px;
}
.prog-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
}
.prog-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.prog-need {
  font-size: 16px;
  font-weight: 800;
  color: var(--theme-color);
}
.prog-track {
  height: 8px;
  background: var(--el-fill-color-dark);
  border-radius: 4px;
  overflow: hidden;
}
.prog-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--theme-color), color-mix(in srgb, var(--theme-color) 70%, #fff));
  border-radius: 4px;
  transition: width 0.6s ease;
}
.prog-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 6px;
}
.hero-max {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--theme-color);
  font-weight: 600;
}
.hero-max-icon {
  font-size: 20px;
}

.hero-sub {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 0;
  transition: opacity 0.2s;
}
.hero-sub:hover {
  opacity: 0.8;
}
.sub-chip {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 6px;
}
.sub-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sub-arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ============ section ============ */
.section {
  margin-bottom: 32px;
}
.section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
}
.section-title {
  font-size: 17px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.section-icon {
  color: var(--theme-color);
  font-size: 18px;
}
.section-sub {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* ============ 特权卡片 ============ */
.priv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.priv-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  transition: all 0.2s;
}
.priv-card:hover {
  border-color: var(--theme-color);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}
.priv-check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--theme-color) 16%, transparent);
  color: var(--theme-color);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.next-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
}

/* ============ 路线图 ============ */
.roadmap {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.road-row {
  display: flex;
  gap: 14px;
  padding: 14px 0;
  border-left: 2px solid var(--el-border-color-lighter);
  margin-left: 14px;
  padding-left: 22px;
  position: relative;
  transition: background 0.2s;
}
.road-row.current {
  border-left-color: var(--theme-color);
}
.road-node {
  position: absolute;
  left: -14px;
  top: 14px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-placeholder);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--el-bg-color);
  font-size: 12px;
}
.road-row.unlocked .road-node {
  background: var(--node-color);
  color: #fff;
}
.road-row.current {
  background: color-mix(in srgb, var(--theme-color) 4%, transparent);
  border-radius: 0 12px 12px 0;
  margin-right: -8px;
  padding-right: 16px;
}
.road-body {
  flex: 1;
  min-width: 0;
}
.road-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.road-lv {
  font-size: 14px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}
.road-row.current .road-lv {
  color: var(--theme-color);
}
.road-name {
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-weight: 600;
}
.road-exp {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.road-tag {
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: var(--theme-color);
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
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.road-row.unlocked .privilege-chip {
  opacity: 0.85;
}

/* ============ 经验获取 ============ */
.tips-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.tip-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.2s;
}
.tip-card:hover {
  border-color: var(--theme-color);
  transform: translateY(-2px);
}
.tip-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--theme-color) 12%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.tip-icon {
  font-size: 20px;
  color: var(--theme-color);
}
.tip-body {
  min-width: 0;
}
.tip-action {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.tip-exp {
  font-size: 13px;
  color: var(--theme-color);
  font-weight: 700;
  margin-top: 2px;
}
.tip-exp span {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}

/* ============ 经验明细 ============ */
.exp-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  overflow: hidden;
}
.exp-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.exp-row:last-child {
  border-bottom: none;
}
.exp-reason {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.exp-delta {
  font-size: 14px;
  font-weight: 800;
  color: var(--el-color-success);
}
.exp-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

@media (max-width: 640px) {
  .hero-content {
    flex-direction: column;
    text-align: center;
  }
  .hero-badge-wrap {
    margin: 0 auto;
  }
  .hero-greeting,
  .hero-title {
    justify-content: center;
  }
  .tips-grid,
  .priv-grid {
    grid-template-columns: 1fr;
  }
}
</style>
