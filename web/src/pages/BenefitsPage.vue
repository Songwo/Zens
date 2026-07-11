<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import { ensureCurrentUserProfile } from '@/utils/sessionProfile'
import { stationUrl } from '@/config/stations'
import { levelApi, type LevelExpRecord } from '@/api/level'
import { subsiteEventApi, type SubsiteEvent } from '@/api/subsiteEvent'
import { timeAgo } from '@/utils/timeAgo'
import {
  Aim,
  ArrowRight,
  Coin,
  Connection,
  Finished,
  Present,
  Refresh,
  Shop,
  Ticket,
} from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(true)
const profile = ref<any>(null)
const records = ref<LevelExpRecord[]>([])
const events = ref<SubsiteEvent[]>([])

const points = computed(() => Number(profile.value?.points ?? 0))
const displayName = computed(() => profile.value?.nickname || profile.value?.username || 'Zens 用户')

const shopUrl = stationUrl('shop')
const shopOrdersUrl = stationUrl('shop', '/orders')
const cdkUrl = stationUrl('cdk', '/local-claim')
const lotteryUrl = stationUrl('lottery')

const quickEntries = [
  {
    title: '积分商城',
    desc: '用主站积分兑换权益、兑换码和社区福利',
    icon: Shop,
    accent: '#f4b400',
    href: shopUrl,
    tag: '积分强一致',
  },
  {
    title: '我的兑换',
    desc: '查看商城订单、发放状态和兑换码',
    icon: Finished,
    accent: '#10b981',
    href: shopOrdersUrl,
    tag: '订单闭环',
  },
  {
    title: 'CDK 空投',
    desc: '领取活动资格、节点、测试码和特别权益',
    icon: Ticket,
    accent: '#d97757',
    href: cdkUrl,
    tag: '福利领取',
  },
  {
    title: '评论抽奖',
    desc: '从原帖评论读取参与者并公开开奖',
    icon: Aim,
    accent: '#2cb1a6',
    href: lotteryUrl,
    tag: '透明开奖',
  },
]

const integrationItems = [
  { label: '身份', value: '主站 SSO 统一登录' },
  { label: '积分', value: '主站钱包为唯一余额来源' },
  { label: '订单', value: '商城发放失败自动补偿退款' },
  { label: '通知', value: '子站事件回流主站通知中心' },
]

const pointRecords = computed(() =>
  records.value.filter((item) => {
    const reason = String(item.reason || '')
    return reason.includes('shop:') || reason.includes('签到') || reason.includes('打赏')
  }).slice(0, 8)
)

const benefitEvents = computed(() => events.value.slice(0, 8))

const openExternal = (href: string) => {
  window.open(href, '_blank', 'noopener,noreferrer')
}

const fetchAll = async () => {
  loading.value = true
  try {
    const [profileRes, recordRes, eventRes] = await Promise.allSettled([
      ensureCurrentUserProfile({ force: true }),
      levelApi.getExpRecords({ days: 0, page: 1, pageSize: 20 }),
      subsiteEventApi.my({ page: 1, pageSize: 12 }),
    ])
    if (profileRes.status === 'fulfilled') {
      profile.value = profileRes.value
    }
    if (recordRes.status === 'fulfilled' && recordRes.value.code === 2000) {
      records.value = recordRes.value.data.records || []
    }
    if (eventRes.status === 'fulfilled' && eventRes.value.code === 2000) {
      events.value = eventRes.value.data.records || []
    }
  } finally {
    loading.value = false
  }
}

const sourceLabelMap: Record<string, string> = {
  'zdc-shop': '积分商城',
  'cdk-airdrop': 'CDK 空投',
  'campus-lottery-station': '评论抽奖',
}

const statusLabelMap: Record<string, string> = {
  delivered: '已发放',
  refunded: '已退款',
  manual_required: '待人工处理',
  recorded: '已记录',
}

const formatSource = (source: string) => sourceLabelMap[source] || source

const formatStatus = (status: string) => statusLabelMap[status] || status

const eventToneClass = (severity: string) => {
  if (severity === 'success') return 'success'
  if (severity === 'warning') return 'warning'
  if (severity === 'danger' || severity === 'error') return 'danger'
  return 'info'
}

const formatReason = (reason: string) => {
  if (!reason) return '积分变动'
  if (reason.startsWith('shop:consume:')) return '商城兑换扣减'
  if (reason.startsWith('shop:credit:')) return '商城退款返还'
  if (reason.includes('签到')) return '每日签到奖励'
  return reason.replace(/^shop:/, '商城 · ')
}

onMounted(fetchAll)
</script>

<template>
  <MainLayout>
    <div class="benefits-page" v-loading="loading">
      <PageBackButton class="benefits-back" fallback="/me" />

      <section class="hero">
        <div class="hero-copy">
          <p class="eyebrow">Zens benefits</p>
          <h1>{{ displayName }} 的福利中心</h1>
          <p class="hero-sub">
            主站积分、兑换订单、CDK、抽奖和子站通知统一收在这里。你在各子项目里的权益，都应该回到社区主站可追踪。
          </p>
          <div class="hero-actions">
            <el-button type="primary" size="large" round @click="openExternal(shopUrl)">
              <el-icon><Shop /></el-icon>
              进入积分商城
            </el-button>
            <el-button size="large" round @click="router.push('/metaverse')">
              <el-icon><Connection /></el-icon>
              查看星港
            </el-button>
            <el-button size="large" round @click="router.push('/supporter')">
              <el-icon><Coin /></el-icon>
              支持 Zens
            </el-button>
          </div>
        </div>

        <div class="wallet">
          <span class="wallet-label">当前积分</span>
          <strong>{{ points.toLocaleString() }}</strong>
          <span class="wallet-unit">PTS</span>
          <button class="refresh-btn" type="button" @click="fetchAll">
            <el-icon><Refresh /></el-icon>
            刷新
          </button>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <p class="eyebrow">quick access</p>
            <h2>常用权益入口</h2>
          </div>
          <span class="section-note">外部子站会继续校验 SSO 与权限</span>
        </div>

        <div class="entry-grid">
          <button
            v-for="entry in quickEntries"
            :key="entry.title"
            type="button"
            class="entry"
            @click="openExternal(entry.href)"
          >
            <span class="entry-icon" :style="{ color: entry.accent, backgroundColor: `${entry.accent}14` }">
              <el-icon><component :is="entry.icon" /></el-icon>
            </span>
            <span class="entry-body">
              <span class="entry-title">{{ entry.title }}</span>
              <span class="entry-desc">{{ entry.desc }}</span>
            </span>
            <span class="entry-tag">{{ entry.tag }}</span>
            <el-icon class="entry-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <section class="two-col">
        <div class="section panel">
          <div class="section-head compact">
            <div>
              <p class="eyebrow">ledger</p>
              <h2>权益轨迹</h2>
            </div>
            <el-button link type="primary" @click="fetchAll">刷新轨迹</el-button>
          </div>

          <div v-if="benefitEvents.length" class="ledger event-ledger">
            <div
              v-for="item in benefitEvents"
              :key="item.id"
              class="ledger-row event-row"
            >
              <div class="ledger-main">
                <span class="ledger-title">{{ item.title }}</span>
                <span class="ledger-time">{{ item.content }}</span>
                <span class="event-meta">
                  {{ formatSource(item.source) }} · {{ item.eventType }} · {{ timeAgo(item.createdAt) }}
                </span>
              </div>
              <span class="event-status" :class="eventToneClass(item.severity)">
                {{ formatStatus(item.status) }}
              </span>
            </div>
          </div>
          <el-empty v-else description="暂无子站权益事件" :image-size="72" />
        </div>

        <div class="section panel">
          <div class="section-head compact">
            <div>
              <p class="eyebrow">closed loop</p>
              <h2>企业级闭环状态</h2>
            </div>
          </div>

          <div class="loop-list">
            <div v-for="item in integrationItems" :key="item.label" class="loop-row">
              <span class="loop-label">{{ item.label }}</span>
              <span class="loop-value">{{ item.value }}</span>
            </div>
          </div>

          <div v-if="pointRecords.length" class="mini-ledger">
            <div class="mini-ledger-head">
              <span>最近积分流水</span>
              <button type="button" @click="router.push('/points')">完整账本</button>
            </div>
            <div v-for="item in pointRecords.slice(0, 4)" :key="item.id" class="mini-ledger-row">
              <span>{{ formatReason(item.reason) }}</span>
              <strong :class="{ negative: item.expDelta < 0 }">
                {{ item.expDelta > 0 ? '+' : '' }}{{ item.expDelta }}
              </strong>
            </div>
          </div>

          <div class="ops-note">
            <el-icon><Coin /></el-icon>
            <span>积分商城已接入主站扣减、退款补偿和通知回流；后续 CDK、抽奖可按同一模式统一纳入。</span>
          </div>
        </div>
      </section>

      <section class="section ops-strip">
        <div>
          <p class="eyebrow">operations</p>
          <h2>运营管理入口</h2>
          <p>管理员可以从星港进入 SSO、商城、CDK、媒体和主站治理后台，统一维护子系统接入配置。</p>
        </div>
        <div class="ops-actions">
          <el-button round @click="router.push('/admin/metaverse')">
            <el-icon><Connection /></el-icon>
            星港配置
          </el-button>
          <el-button round @click="router.push('/admin/sso')">
            <el-icon><Present /></el-icon>
            SSO 应用
          </el-button>
        </div>
      </section>
    </div>
  </MainLayout>
</template>

<style scoped>
.benefits-page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 28px 16px 64px;
}

.benefits-back {
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
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 28px;
  align-items: stretch;
  padding: 34px;
  border-radius: 24px;
  border: 1px solid #f3dfaa;
  background:
    radial-gradient(circle at 85% 10%, rgba(244, 180, 0, 0.18), transparent 34%),
    linear-gradient(135deg, #fffaf0 0%, #ffffff 68%);
}

.hero h1 {
  margin: 0;
  color: #1f2937;
  font-size: clamp(28px, 4vw, 48px);
  font-weight: 900;
  line-height: 1.08;
  letter-spacing: 0;
}

.hero-sub {
  max-width: 620px;
  margin: 16px 0 0;
  color: #6b7280;
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

.wallet {
  min-height: 220px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(244, 180, 0, 0.18);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 24px;
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
  font-size: 46px;
  line-height: 1;
}

.refresh-btn {
  width: fit-content;
  margin-top: 22px;
  border: 0;
  border-radius: 999px;
  background: #fff4d6;
  color: #7a5700;
  font-weight: 800;
  padding: 8px 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

.section-head.compact {
  align-items: center;
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

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  overflow: hidden;
  border-radius: 18px;
  background: #edf0f3;
}

.entry {
  min-height: 108px;
  border: 0;
  background: #fff;
  color: inherit;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto 18px;
  align-items: center;
  gap: 14px;
  padding: 20px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.entry:hover {
  background: #f9fafb;
}

.entry-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.entry-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.entry-title {
  color: #1f2937;
  font-size: 15px;
  font-weight: 900;
}

.entry-desc {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.5;
}

.entry-tag {
  border-radius: 999px;
  background: #f6f7f9;
  color: #6b7280;
  font-size: 12px;
  font-weight: 800;
  padding: 5px 9px;
}

.entry-arrow {
  color: #9ca3af;
}

.two-col {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
  gap: 22px;
}

.panel {
  border-radius: 18px;
  background: #fff;
  border: 1px solid #edf0f3;
  padding: 22px;
}

.ledger,
.loop-list {
  display: flex;
  flex-direction: column;
}

.ledger-row,
.loop-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 0;
  border-bottom: 1px solid #f1f3f5;
}

.event-row {
  align-items: flex-start;
  padding: 15px 0;
}

.ledger-row:last-child,
.loop-row:last-child {
  border-bottom: 0;
}

.ledger-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ledger-title,
.loop-label {
  color: #1f2937;
  font-weight: 800;
  font-size: 14px;
}

.ledger-time,
.loop-value {
  color: #6b7280;
  font-size: 13px;
}

.event-meta {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.4;
}

.event-status {
  flex-shrink: 0;
  min-width: 72px;
  border-radius: 999px;
  padding: 5px 9px;
  text-align: center;
  font-size: 12px;
  font-weight: 900;
  background: #f6f7f9;
  color: #6b7280;
}

.event-status.success {
  background: #ecfdf5;
  color: #059669;
}

.event-status.warning {
  background: #fff7ed;
  color: #d97706;
}

.event-status.danger {
  background: #fef2f2;
  color: #dc2626;
}

.ledger-delta {
  color: #10b981;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 900;
}

.ledger-delta.negative {
  color: #f59e0b;
}

.mini-ledger {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #f1f3f5;
}

.mini-ledger-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.mini-ledger-head span {
  color: #1f2937;
  font-size: 14px;
  font-weight: 900;
}

.mini-ledger-head button {
  border: 0;
  background: transparent;
  color: #b77900;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.mini-ledger-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  color: #6b7280;
  font-size: 13px;
}

.mini-ledger-row strong {
  color: #10b981;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.mini-ledger-row strong.negative {
  color: #f59e0b;
}

.ops-note {
  margin-top: 18px;
  padding: 14px;
  border-radius: 14px;
  background: #fff8e7;
  color: #7a5700;
  font-size: 13px;
  line-height: 1.7;
  display: flex;
  gap: 10px;
}

.ops-strip {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #edf0f3;
  padding: 22px;
}

.ops-strip p:last-child {
  margin: 8px 0 0;
  color: #6b7280;
  line-height: 1.7;
}

.ops-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .hero,
  .two-col {
    grid-template-columns: 1fr;
  }

  .entry-grid {
    grid-template-columns: 1fr;
  }

  .wallet {
    min-height: 170px;
  }

  .ops-strip {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .benefits-page {
    padding: 18px 10px 48px;
  }

  .hero {
    padding: 24px 20px;
    border-radius: 20px;
  }

  .entry {
    grid-template-columns: 38px minmax(0, 1fr) 18px;
  }

  .entry-tag {
    grid-column: 2 / 3;
    width: fit-content;
  }

  .entry-arrow {
    grid-column: 3;
    grid-row: 1;
  }
}
</style>
