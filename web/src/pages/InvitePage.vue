<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import { levelApi, type LevelInfo } from '@/api/level'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { Link, User, Trophy, CopyDocument, Lock, Star } from '@element-plus/icons-vue'
import api from '@/lib/api'
import type { Result } from '@/types'

const userStore = useUserStore()

interface InviteRecord {
  id: string
  code: string
  link: string
  status: number
  maxUses: number
  usedCount: number
  expireTime: string | null
  createTime: string
  remark: string | null
  invitee?: {
    id: string
    nickname: string
    avatar: string
    level: number
    createTime: string
  }
}

interface InviteStats {
  records: InviteRecord[]
  total: number
  userLevel: number
  minLevel: number
  canGenerate: boolean
}

const levelInfo = ref<LevelInfo | null>(null)
const inviteStats = ref<InviteStats | null>(null)
const loading = ref(false)
const generating = ref(false)

const isLoggedIn = computed(() => !!userStore.accessToken)
const canGenerate = computed(() => inviteStats.value?.canGenerate ?? false)
const minLevel = computed(() => inviteStats.value?.minLevel ?? 5)
const userLevel = computed(() => inviteStats.value?.userLevel ?? 0)
const expNeeded = computed(() => {
  if (!levelInfo.value || canGenerate.value) return 0
  return levelInfo.value.nextLevelExp - levelInfo.value.experience
})

const successCount = computed(() =>
  inviteStats.value?.records.filter(r => r.usedCount > 0).length ?? 0
)
const pendingCount = computed(() =>
  inviteStats.value?.records.filter(r => r.status === 0 && r.usedCount === 0).length ?? 0
)

const loadData = async () => {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const [lvRes, invRes] = await Promise.all([
      levelApi.getInfo(),
      api.get<any, Result<InviteStats>>('/invite/my')
    ])
    if (lvRes.code === 2000) levelInfo.value = lvRes.data
    if (invRes.code === 2000) inviteStats.value = invRes.data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const generateInvite = async () => {
  generating.value = true
  try {
    const res = await api.post<any, Result<{ code: string; link: string; expireDays: number }>>('/invite/generate-self', {})
    if (res.code === 2000) {
      ElMessage.success('邀请码已生成，快去分享吧！')
      await loadData()
    } else {
      ElMessage.warning(res.message || '生成失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const copyLink = (link: string) => {
  navigator.clipboard.writeText(link).then(() => {
    ElMessage.success('邀请链接已复制')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

const formatDate = (d: string) => {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' })
}

const statusLabel = (r: InviteRecord) => {
  if (r.status === 2) return { text: '已禁用', type: 'danger' }
  if (r.usedCount > 0) return { text: '已使用', type: 'success' }
  if (r.expireTime && new Date(r.expireTime) < new Date()) return { text: '已过期', type: 'warning' }
  return { text: '待使用', type: 'primary' }
}

onMounted(loadData)
</script>

<template>
  <MainLayout>
    <div class="invite-page">
      <!-- Header -->
      <div class="invite-hero">
        <div class="hero-icon"><el-icon :size="36"><Link /></el-icon></div>
        <h1 class="hero-title">邀请好友</h1>
        <p class="hero-sub">邀请新用户加入，双方共同获得经验奖励</p>
      </div>

      <!-- 未登录 -->
      <div v-if="!isLoggedIn" class="lock-card">
        <el-icon :size="48" class="lock-icon"><Lock /></el-icon>
        <p>请先登录后查看邀请功能</p>
      </div>

      <template v-else-if="!loading && inviteStats">
        <!-- 等级限制提示 -->
        <div v-if="!canGenerate" class="level-gate">
          <el-icon :size="32" class="gate-icon"><Trophy /></el-icon>
          <div class="gate-info">
            <div class="gate-title">解锁邀请功能需要 <strong>Lv{{ minLevel }}</strong></div>
            <div class="gate-desc">当前等级 Lv{{ userLevel }}，还需 <strong>{{ expNeeded }} 点经验</strong>升级</div>
            <el-progress
              :percentage="levelInfo ? Math.round(levelInfo.progress * 100) : 0"
              :stroke-width="8"
              striped-flow
              class="gate-progress"
            />
            <el-button type="primary" plain size="small" @click="$router.push('/connect')">
              查看升级方法
            </el-button>
          </div>
        </div>

        <!-- 统计卡片 -->
        <div class="stats-row">
          <div class="stat-card">
            <div class="stat-num">{{ inviteStats.total }}</div>
            <div class="stat-label">生成总数</div>
          </div>
          <div class="stat-card success">
            <div class="stat-num">{{ successCount }}</div>
            <div class="stat-label">注册成功</div>
          </div>
          <div class="stat-card primary">
            <div class="stat-num">{{ successCount * 30 }}</div>
            <div class="stat-label">获得经验</div>
          </div>
          <div class="stat-card warning">
            <div class="stat-num">{{ pendingCount }}</div>
            <div class="stat-label">待使用</div>
          </div>
        </div>

        <!-- 生成按钮 -->
        <div v-if="canGenerate" class="generate-section">
          <el-button
            type="primary"
            :icon="Link"
            :loading="generating"
            size="large"
            @click="generateInvite"
          >
            生成邀请链接
          </el-button>
          <span class="generate-hint">每次生成1个，有效期30天，最多同时保留5个未使用</span>
        </div>

        <!-- 邀请记录 -->
        <div class="records-section">
          <div class="section-title">邀请记录</div>
          <div v-if="inviteStats.records.length === 0" class="empty-hint">
            <el-icon :size="32"><Star /></el-icon>
            <p>还没有邀请记录，快去生成邀请链接吧</p>
          </div>
          <div v-else class="record-list">
            <div v-for="r in inviteStats.records" :key="r.id" class="record-card">
              <div class="record-top">
                <el-tag :type="statusLabel(r).type as any" size="small">{{ statusLabel(r).text }}</el-tag>
                <span class="record-time">{{ formatDate(r.createTime) }}</span>
                <span v-if="r.expireTime" class="record-expire">过期：{{ formatDate(r.expireTime) }}</span>
              </div>
              <div class="record-link">
                <code class="link-text">{{ r.link }}</code>
                <el-button :icon="CopyDocument" size="small" circle @click="copyLink(r.link)" />
              </div>
              <!-- 被邀请人 -->
              <div v-if="r.invitee" class="invitee-row">
                <el-avatar :size="28" :src="r.invitee.avatar">{{ r.invitee.nickname?.charAt(0) }}</el-avatar>
                <span class="invitee-name">{{ r.invitee.nickname }}</span>
                <el-tag type="success" size="small">Lv{{ r.invitee.level }}</el-tag>
                <el-tag type="warning" size="small">+30 经验</el-tag>
                <span class="invitee-time">注册于 {{ formatDate(r.invitee.createTime) }}</span>
              </div>
              <div v-else-if="r.status === 0" class="invitee-pending">
                <el-icon><User /></el-icon> 等待好友注册...
              </div>
            </div>
          </div>
        </div>
      </template>

      <div v-else-if="loading" class="loading-wrap">
        <el-skeleton :rows="4" animated />
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.invite-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 24px 16px 60px;
}
.invite-hero {
  text-align: center;
  margin-bottom: 32px;
}
.hero-icon { color: var(--el-color-primary); margin-bottom: 12px; }
.hero-title { font-size: 26px; font-weight: 700; margin: 0 0 6px; }
.hero-sub { color: var(--el-text-color-secondary); margin: 0; }
.lock-card {
  text-align: center;
  padding: 60px 0;
  color: var(--el-text-color-secondary);
}
.lock-icon { margin-bottom: 16px; opacity: 0.4; }
.level-gate {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}
.gate-icon { color: var(--el-color-warning); flex-shrink: 0; margin-top: 4px; }
.gate-title { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.gate-desc { color: var(--el-text-color-secondary); font-size: 13px; margin-bottom: 12px; }
.gate-progress { margin-bottom: 12px; }
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}
.stat-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
}
.stat-num { font-size: 28px; font-weight: 700; color: var(--el-color-primary); }
.stat-card.success .stat-num { color: var(--el-color-success); }
.stat-card.warning .stat-num { color: var(--el-color-warning); }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
.generate-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
  flex-wrap: wrap;
}
.generate-hint { font-size: 12px; color: var(--el-text-color-placeholder); }
.section-title { font-size: 16px; font-weight: 600; margin-bottom: 16px; }
.empty-hint {
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
}
.record-list { display: flex; flex-direction: column; gap: 12px; }
.record-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.record-top { display: flex; align-items: center; gap: 8px; }
.record-time { font-size: 12px; color: var(--el-text-color-secondary); margin-left: auto; }
.record-expire { font-size: 12px; color: var(--el-color-warning); }
.record-link {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--el-fill-color);
  border-radius: 6px;
  padding: 6px 10px;
}
.link-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.invitee-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.invitee-name { font-weight: 500; }
.invitee-time { font-size: 12px; color: var(--el-text-color-secondary); margin-left: auto; }
.invitee-pending {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  display: flex;
  align-items: center;
  gap: 6px;
}
.loading-wrap { padding: 40px 0; }
@media (max-width: 600px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
