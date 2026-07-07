<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { pointsApi, type PointSummary, type PointTxn } from '@/api/points'
import { useUserStore } from '@/store/user'
import { Coin, Refresh } from '@element-plus/icons-vue'

const userStore = useUserStore()
const summary = ref<PointSummary | null>(null)
const records = ref<PointTxn[]>([])
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const refreshing = ref(false)

const displayName = computed(() => userStore.userInfo?.nickname || userStore.userInfo?.username || 'Zens 用户')
const hasMore = computed(() => records.value.length < total.value)

const sourceLabelMap: Record<string, string> = {
  'main-site': '社区主站',
  'zdc-shop': '积分商城',
  'campus-lottery-station': '评论抽奖',
  'cdk-airdrop': 'CDK 空投',
}

const bizTypeLabelMap: Record<string, string> = {
  checkin: '每日签到',
  'shop.order': '商城兑换',
  'shop.refund': '商城退款',
  'lottery.join': '抽奖参与',
  'lottery.refund': '抽奖退款',
  'admin.adjust': '管理员调整',
}

const sourceLabel = (s: string) => sourceLabelMap[s] || s
const bizTypeLabel = (s: string) => bizTypeLabelMap[s] || s

const formatReason = (txn: PointTxn) => {
  if (txn.reason && txn.reason !== txn.bizType) return txn.reason
  return bizTypeLabel(txn.bizType)
}

const formatTime = (iso: string) => {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    if (Number.isNaN(d.getTime())) return iso
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return iso
  }
}

const fetchSummary = async () => {
  try {
    const res = await pointsApi.getSummary()
    if (res.code === 2000) summary.value = res.data
  } catch {
    /* 忽略,余额非阻塞 */
  }
}

const fetchFirst = async () => {
  loading.value = true
  page.value = 1
  try {
    const res = await pointsApi.getTransactions({ page: 1, pageSize })
    if (res.code === 2000) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

const fetchMore = async () => {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const next = page.value + 1
    const res = await pointsApi.getTransactions({ page: next, pageSize })
    if (res.code === 2000) {
      records.value.push(...(res.data.records || []))
      total.value = res.data.total || total.value
      page.value = next
    }
  } finally {
    loadingMore.value = false
  }
}

const refreshAll = async () => {
  refreshing.value = true
  await Promise.allSettled([fetchSummary(), fetchFirst()])
  refreshing.value = false
}

const { sentinel } = useInfiniteScroll(fetchMore, {
  canLoadMore: () => hasMore.value && !loadingMore.value && !loading.value,
})
void sentinel

onMounted(async () => {
  await Promise.allSettled([fetchSummary(), fetchFirst()])
})
</script>

<template>
  <MainLayout>
    <div class="points-page" v-loading="loading">
      <PageBackButton class="points-back" fallback="/me" />

      <section class="hero">
        <div class="hero-copy">
          <p class="eyebrow">zens points ledger</p>
          <h1>{{ displayName }} 的积分明细</h1>
          <p class="hero-sub">
            所有积分变动(签到、商城兑换、抽奖参与、退款)都记在同一本账里。主站是唯一余额来源,子站只消费主站钱包,这里就是你账本的全貌。
          </p>
        </div>

        <div class="wallet">
          <span class="wallet-label">当前积分</span>
          <strong>{{ (summary?.points ?? 0).toLocaleString() }}</strong>
          <span class="wallet-unit">PTS</span>
          <button class="refresh-btn" type="button" :disabled="refreshing" @click="refreshAll">
            <el-icon><Refresh /></el-icon>
            {{ refreshing ? '刷新中' : '刷新' }}
          </button>
        </div>
      </section>

      <section class="stat-row">
        <div class="stat">
          <span class="stat-label">本月收入</span>
          <strong class="stat-value earn">+{{ summary?.monthEarned ?? 0 }}</strong>
        </div>
        <div class="stat">
          <span class="stat-label">本月支出</span>
          <strong class="stat-value spend">−{{ summary?.monthSpent ?? 0 }}</strong>
        </div>
        <div class="stat">
          <span class="stat-label">本月净增</span>
          <strong
            class="stat-value"
            :class="(summary?.monthEarned ?? 0) - (summary?.monthSpent ?? 0) >= 0 ? 'earn' : 'spend'"
          >
            {{ (summary?.monthEarned ?? 0) - (summary?.monthSpent ?? 0) >= 0 ? '+' : '' }}{{ (summary?.monthEarned ?? 0) - (summary?.monthSpent ?? 0) }}
          </strong>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <p class="eyebrow">transactions</p>
            <h2>流水明细</h2>
          </div>
          <span class="section-note">共 {{ total }} 条</span>
        </div>

        <div v-if="records.length" class="txn-list">
          <div v-for="txn in records" :key="txn.id" class="txn-row">
            <div class="txn-main">
              <span class="txn-title">{{ formatReason(txn) }}</span>
              <span class="txn-meta">
                <span class="txn-source">{{ sourceLabel(txn.source) }}</span>
                <span class="txn-dot">·</span>
                <span>{{ bizTypeLabel(txn.bizType) }}</span>
                <template v-if="txn.orderId">
                  <span class="txn-dot">·</span>
                  <span class="txn-order">{{ txn.orderId }}</span>
                </template>
              </span>
              <span class="txn-time">{{ formatTime(txn.createdAt) }}</span>
            </div>
            <div class="txn-tail">
              <strong class="txn-delta" :class="{ negative: txn.delta < 0 }">
                {{ txn.delta > 0 ? '+' : '' }}{{ txn.delta }}
              </strong>
              <span class="txn-balance">余额 {{ txn.balanceAfter }}</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="还没有积分流水,签到或兑换一笔就会出现" :image-size="80" />

        <div v-if="records.length" ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>
        <div v-if="loadingMore" class="loading-more">加载中…</div>
        <div v-else-if="records.length && !hasMore" class="list-end">
          <Coin class="list-end-icon" />已到底部,共 {{ total }} 条流水
        </div>
      </section>
    </div>
  </MainLayout>
</template>

<style scoped>
.points-page {
  max-width: 880px;
  margin: 0 auto;
  padding: 28px 16px 64px;
}

.points-back {
  margin-bottom: 16px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #9a6b00;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 28px;
  align-items: stretch;
  padding: 32px;
  border-radius: 24px;
  border: 1px solid #f3dfaa;
  background:
    radial-gradient(circle at 85% 10%, rgba(244, 180, 0, 0.18), transparent 34%),
    linear-gradient(135deg, #fffaf0 0%, #ffffff 68%);
}

.hero h1 {
  margin: 0;
  color: #1f2937;
  font-size: clamp(24px, 3.5vw, 36px);
  font-weight: 900;
  line-height: 1.15;
}

.hero-sub {
  max-width: 520px;
  margin: 14px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.8;
}

.wallet {
  min-height: 180px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(244, 180, 0, 0.18);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 22px;
}

.wallet-label,
.wallet-unit {
  color: #8a8f98;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.wallet strong {
  margin-top: 8px;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 40px;
  line-height: 1;
}

.refresh-btn {
  width: fit-content;
  margin-top: 18px;
  border: 0;
  border-radius: 999px;
  background: #fff4d6;
  color: #7a5700;
  font-weight: 800;
  padding: 7px 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border-radius: 16px;
  background: #edf0f3;
  margin-top: 24px;
}

.stat {
  background: #fff;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  color: #9ca3af;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.stat-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 22px;
  font-weight: 900;
}

.stat-value.earn {
  color: #b77900;
}

.stat-value.spend {
  color: #9ca3af;
}

.section {
  margin-top: 28px;
}

.section-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.section h2 {
  margin: 0;
  color: #1f2937;
  font-size: 20px;
  font-weight: 900;
}

.section-note {
  color: #9ca3af;
  font-size: 13px;
}

.txn-list {
  display: flex;
  flex-direction: column;
}

.txn-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #f1f3f5;
}

.txn-row:last-child {
  border-bottom: 0;
}

.txn-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.txn-title {
  color: #1f2937;
  font-weight: 800;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.txn-meta {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.txn-source {
  color: #7a5700;
  background: #fff4d6;
  border-radius: 999px;
  padding: 1px 8px;
  font-weight: 800;
}

.txn-dot {
  color: #d1d5db;
}

.txn-order {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.txn-time {
  color: #c0c4cc;
  font-size: 12px;
}

.txn-tail {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.txn-delta {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 18px;
  font-weight: 900;
  color: #b77900;
}

.txn-delta.negative {
  color: #9ca3af;
}

.txn-balance {
  color: #c0c4cc;
  font-size: 12px;
}

.infinite-sentinel {
  height: 1px;
}

.loading-more,
.list-end {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 18px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.list-end-icon {
  font-size: 14px;
}

@media (max-width: 720px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .wallet {
    min-height: 150px;
  }

  .stat-row {
    grid-template-columns: 1fr;
  }

  .txn-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .txn-tail {
    flex-direction: row;
    align-items: baseline;
    width: 100%;
    justify-content: space-between;
  }
}
</style>
