<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Lock, Medal, RefreshRight } from '@element-plus/icons-vue'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import SupporterFeedbackPanel from '@/components/supporter/SupporterFeedbackPanel.vue'
import SupporterInsightsPanel from '@/components/supporter/SupporterInsightsPanel.vue'
import SupporterVoucherPanel from '@/components/supporter/SupporterVoucherPanel.vue'
import { supporterApi, type SupporterOrder, type SupporterPlan, type SupporterStatus } from '@/api/supporter'

const loading = ref(true)
const loadError = ref('')
const route = useRoute()
const router = useRouter()
const submitting = ref<string | null>(null)
const plans = ref<SupporterPlan[]>([])
const status = ref<SupporterStatus>({ active: false })
const hasSession = ref(Boolean(
  localStorage.getItem('access_token') || sessionStorage.getItem('access_token'),
))
const lastOrder = ref<SupporterOrder | null>(null)
const orderLoading = ref(false)
const pollFinished = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | null = null
let resolvePollDelay: (() => void) | null = null
let pollingCancelled = false
const POLL_DELAYS = [0, 1500, 3000, 5000, 8000, 12000]

const paymentAvailable = computed(() => plans.value.some(plan => plan.paymentAvailable))
const availablePlanCount = computed(() => plans.value.filter(plan => plan.paymentAvailable).length)
const normalizedOrderStatus = computed(() => (lastOrder.value?.status || '').toUpperCase())
const orderState = computed(() => {
  if (normalizedOrderStatus.value === 'PAID') return { type: 'success', title: '支付成功', message: '支持者权益已经开通，感谢你帮助 Zens 持续运行。' }
  if (['FAILED', 'EXPIRED', 'CANCELLED', 'CLOSED'].includes(normalizedOrderStatus.value)) {
    return { type: 'danger', title: '订单未完成', message: normalizedOrderStatus.value === 'EXPIRED' ? '订单已过期，请重新发起支付。' : '支付失败或订单已关闭，本次不会开通权益。' }
  }
  return { type: 'warning', title: pollFinished.value ? '仍在确认支付结果' : '正在确认支付结果', message: pollFinished.value ? '支付回调可能稍有延迟，你可以稍后手动刷新订单状态。' : '已返回 Zens，正在等待支付平台的安全回调，请不要重复付款。' }
})

const formatMoney = (cents: number) => `¥${(cents / 100).toFixed(cents % 100 ? 2 : 0)}`
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const quotaBenefit = (plan: SupporterPlan) =>
  plan.benefits.find(benefit => /公益站|兑换码|额度/.test(benefit)) || ''
const quotaAmount = (plan: SupporterPlan) => quotaBenefit(plan).match(/\d+/)?.[0] || ''
const standardBenefits = (plan: SupporterPlan) => {
  const highlighted = quotaBenefit(plan)
  return highlighted ? plan.benefits.filter(benefit => benefit !== highlighted) : plan.benefits
}

const newIdempotencyKey = () => {
  const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID().replace(/-/g, '')
    : `${Date.now()}${Math.random().toString(36).slice(2)}`
  return `support_${random}`
}

const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const planRes = await supporterApi.plans()
    if (planRes.code === 2000) {
      plans.value = planRes.data || []
    } else {
      throw new Error(planRes.message || '支持方案加载失败')
    }
    try {
      const statusRes = await supporterApi.me()
      if (statusRes.code === 2000 && statusRes.data) status.value = statusRes.data
    } catch {
      // 未登录访客仍可浏览方案；下单时路由鉴权会引导登录。
    }
  } catch (error: any) {
    plans.value = []
    loadError.value = error?.response?.data?.message || error?.message || '暂时无法加载支持方案，请稍后重试'
  } finally {
    loading.value = false
  }
}

const support = async (plan: SupporterPlan) => {
  if (!plan.paymentAvailable) {
    ElMessage.info('在线支付仍处于合规接入阶段，当前不会产生扣款')
    return
  }
  submitting.value = plan.code
  try {
    const res = await supporterApi.createOrder(plan.code, newIdempotencyKey())
    if (res.code !== 2000 || !res.data) {
      ElMessage.error(res.message || '支付订单创建失败')
      return
    }
    lastOrder.value = res.data
    if (res.data.checkoutUrl) {
      window.location.assign(res.data.checkoutUrl)
    } else {
      ElMessage.info('订单已创建，正在等待支付渠道返回收银台')
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '支付订单创建失败')
  } finally {
    submitting.value = null
  }
}

const fetchOrder = async (orderNo: string) => {
  orderLoading.value = true
  try {
    const res = await supporterApi.order(orderNo)
    if (res.code === 2000 && res.data) {
      lastOrder.value = res.data
      if (res.data.status === 'PAID') await load()
    }
  } finally {
    orderLoading.value = false
  }
}

const refreshOrder = async () => {
  if (!lastOrder.value) return
  await fetchOrder(lastOrder.value.orderNo)
}

const restoreReturnedOrder = async (orderNo: string) => {
  for (let index = 0; index < POLL_DELAYS.length; index += 1) {
    if (pollingCancelled) return
    const delay = POLL_DELAYS[index] ?? 0
    if (delay > 0) {
      await new Promise<void>((resolve) => {
        resolvePollDelay = resolve
        pollTimer = setTimeout(() => {
          pollTimer = null
          resolvePollDelay = null
          resolve()
        }, delay)
      })
    }
    if (pollingCancelled) return
    await fetchOrder(orderNo)
    if (lastOrder.value && normalizedOrderStatus.value !== 'PENDING') break
  }
  pollFinished.value = normalizedOrderStatus.value === 'PENDING'
}

onMounted(async () => {
  await load()
  const queryOrderNo = Array.isArray(route.query.orderNo) ? route.query.orderNo[0] : route.query.orderNo
  const orderNo = typeof queryOrderNo === 'string' ? queryOrderNo.trim() : ''
  if (orderNo) {
    try {
      await restoreReturnedOrder(orderNo)
    } catch (error: any) {
      pollFinished.value = true
      ElMessage.error(error?.response?.data?.message || '订单状态查询失败，请稍后重试')
    } finally {
      const query = { ...route.query }
      delete query.orderNo
      await router.replace({ query }).catch(() => undefined)
    }
  }
})

onBeforeUnmount(() => {
  pollingCancelled = true
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
  resolvePollDelay?.()
  resolvePollDelay = null
})
</script>

<template>
  <MainLayout>
    <main class="supporter-page">
      <PageBackButton fallback="/benefits" />

      <section class="hero" aria-labelledby="supporter-title">
        <div class="hero-copy">
          <p class="eyebrow">Zens supporter program</p>
          <h1 id="supporter-title">让值得留下的内容，<span>在这里生长得更久。</span></h1>
          <p class="hero-description">
            支持会用于社区的服务器、图片存储和持续维护。你获得明确、可验证的支持者权益，但不会购买到推荐排名、审核优待或额外表达权。
          </p>
          <a class="hero-link" href="#supporter-plans">查看支持方案 <span aria-hidden="true">→</span></a>
        </div>

        <div class="hero-art" aria-hidden="true">
          <svg viewBox="0 0 420 320" role="presentation">
            <path class="orbit orbit-one" d="M52 198C92 82 258 40 372 119" />
            <path class="orbit orbit-two" d="M73 260C174 165 291 172 378 237" />
            <path class="thread" d="M91 235C144 201 164 86 248 91C321 95 318 194 364 211" />
            <circle cx="91" cy="235" r="8" />
            <circle cx="248" cy="91" r="13" />
            <circle cx="364" cy="211" r="7" />
          </svg>
          <div class="art-card">
            <span>{{ status.active ? '当前支持状态' : '支持计划' }}</span>
            <strong v-if="status.active">{{ status.planName }}</strong>
            <strong v-else>{{ loading ? '读取中' : `${availablePlanCount || plans.length} 个方案` }}</strong>
            <small v-if="status.active">有效期至 {{ formatTime(status.expiresAt) }}</small>
            <small v-else>30 天为一个支持周期</small>
          </div>
        </div>

        <div v-if="status.active" class="active-card" role="status">
          <el-icon><Medal /></el-icon>
          <div><strong>权益正在生效</strong><span>{{ status.planName }} · {{ status.remainingDays ?? '—' }} 天剩余</span></div>
        </div>
      </section>

      <section class="trust-rail" aria-label="支付与权益说明" aria-live="polite">
        <article :class="{ ready: paymentAvailable }">
          <el-icon><component :is="paymentAvailable ? Check : Lock" /></el-icon>
          <div><strong>{{ loading ? '正在确认支付状态' : paymentAvailable ? '支付宝安全收银台' : '在线支付暂未开放' }}</strong><span>{{ paymentAvailable ? '支付完成后由服务端确认结果' : '开放后按钮会自动恢复' }}</span></div>
        </article>
        <article><span class="trust-index">02</span><div><strong>不会自动续费</strong><span>到期后由你决定是否继续支持</span></div></article>
        <article><span class="trust-index">03</span><div><strong>权益真实可追踪</strong><span>订单、期限与发放状态均有记录</span></div></article>
      </section>

      <section id="supporter-plans" class="plans-section" aria-labelledby="plans-title">
        <header class="section-heading">
          <div><p class="eyebrow">Choose your support</p><h2 id="plans-title">选一份适合你的支持</h2></div>
          <p>两档方案都不会改变内容分发规则。价格、周期和权益以付款前页面展示为准。</p>
        </header>

        <div v-if="loading" class="plan-grid" aria-label="正在加载支持方案" aria-busy="true">
          <article v-for="index in 2" :key="index" class="plan-card skeleton-card">
            <i class="skeleton-line short"></i><i class="skeleton-line title"></i><i class="skeleton-line price-line"></i>
            <i class="skeleton-line"></i><i class="skeleton-line"></i><i class="skeleton-line button-line"></i>
          </article>
        </div>

        <div v-else-if="loadError" class="state-panel state-error" role="alert">
          <div><strong>支持方案没有加载成功</strong><span>{{ loadError }}</span></div>
          <el-button round @click="load">重新加载</el-button>
        </div>

        <div v-else-if="plans.length === 0" class="state-panel">
          <div><strong>新的支持方案正在准备</strong><span>目前没有可选方案，社区的基础功能仍可正常使用。</span></div>
        </div>

        <div v-else class="plan-grid">
          <article
            v-for="(plan, index) in plans"
            :key="plan.code"
            class="plan-card"
            :class="{ 'is-featured': index === plans.length - 1 && plans.length > 1, 'is-current': status.active && status.planCode === plan.code }"
          >
            <header class="plan-header">
              <div class="plan-topline">
                <span>{{ plan.durationDays }} 天</span>
                <span v-if="status.active && status.planCode === plan.code" class="current-label">当前方案</span>
                <span v-else-if="index === plans.length - 1 && plans.length > 1" class="featured-label">更多共建权益</span>
              </div>
              <h3>{{ plan.name }}</h3>
              <p>{{ plan.description }}</p>
            </header>

            <div class="price-row"><strong>{{ formatMoney(plan.priceCents) }}</strong><span>一次支付<br>{{ plan.durationDays }} 天有效</span></div>

            <div v-if="quotaBenefit(plan)" class="quota-highlight">
              <span class="quota-value">{{ quotaAmount(plan) || '公益' }}<small>额度</small></span>
              <div><strong>每周期公益站额度</strong><p>{{ quotaBenefit(plan) }}</p></div>
            </div>

            <div class="benefit-block">
              <strong>你将获得</strong>
              <ul v-if="standardBenefits(plan).length">
                <li v-for="benefit in standardBenefits(plan)" :key="benefit"><el-icon><Check /></el-icon><span>{{ benefit }}</span></li>
              </ul>
              <p v-else class="benefit-empty">当前方案暂无额外权益说明。</p>
            </div>

            <el-button type="primary" size="large" :disabled="!plan.paymentAvailable" :loading="submitting === plan.code" @click="support(plan)">
              {{ plan.paymentAvailable ? `以 ${formatMoney(plan.priceCents)} 支持` : '支付暂未开放' }}
            </el-button>
            <small class="payment-note"><span aria-hidden="true">↗</span> 前往支付宝完成付款，确认后自动开通</small>
          </article>
        </div>
      </section>

      <section v-if="lastOrder" class="order-card" :class="`is-${orderState.type}`" aria-live="polite">
        <div class="order-status-mark" aria-hidden="true"><component :is="orderState.type === 'success' ? Check : RefreshRight" /></div>
        <div class="order-copy">
          <p class="eyebrow">Latest order</p><strong>{{ orderState.title }} · {{ lastOrder.planName }}</strong>
          <span class="order-message">{{ orderState.message }}</span>
          <dl><div><dt>订单金额</dt><dd>{{ formatMoney(lastOrder.amountCents) }}</dd></div><div><dt>订单编号</dt><dd>{{ lastOrder.orderNo }}</dd></div><div><dt>过期时间</dt><dd>{{ formatTime(lastOrder.expiresAt) }}</dd></div></dl>
        </div>
        <el-button :icon="RefreshRight" :loading="orderLoading" round @click="refreshOrder">刷新状态</el-button>
      </section>

      <section v-if="hasSession" class="supporter-tools" aria-labelledby="supporter-tools-title">
        <header class="tools-heading"><p class="eyebrow">Your supporter space</p><h2 id="supporter-tools-title">你的支持记录与工具</h2><span>历史兑换码在权益到期后仍可查看；数据和反馈不会影响推荐或审核。</span></header>
        <SupporterVoucherPanel />
        <SupporterInsightsPanel v-if="status.active" :days="30" />
        <SupporterFeedbackPanel v-if="status.active && status.planCode === 'supporter_plus_30'" />
      </section>

      <section class="principles">
        <div class="principle-title"><p class="eyebrow">Our promise</p><h2>支持有回报，社区无特权</h2></div>
        <div class="principle-grid">
          <article><span>01</span><strong>不出售表达权</strong><p>审核优待、推荐排名和基础发帖权永远不是付费商品。</p></article>
          <article><span>02</span><strong>只承诺已上线权益</strong><p>实际获得的内容，以支付前明确列出的方案权益为准。</p></article>
          <article><span>03</span><strong>支付全程可追溯</strong><p>订单、支付结果和权益发放都由服务端记录确认。</p></article>
        </div>
      </section>
    </main>
  </MainLayout>
</template>

<style scoped>
.supporter-page { --forest:#183f35; --forest-2:#285e4f; --leaf:#4f806d; --mint:#dfeee7; --paper:#fbfcfa; --ink:#1d2925; --muted:#68756f; max-width:1120px; min-height:100dvh; margin:0 auto; padding:28px 18px 78px; color:var(--ink); }
.eyebrow { margin:0 0 10px; color:var(--leaf); font-size:10px; font-weight:850; letter-spacing:.17em; text-transform:uppercase; }
.hero { position:relative; display:grid; grid-template-columns:minmax(0,1.25fr) minmax(280px,.75fr); min-height:410px; margin-top:17px; overflow:hidden; border:1px solid #d6e2dc; border-radius:30px 30px 30px 8px; background:#f2f7f4; isolation:isolate; }
.hero::before { content:""; position:absolute; inset:0; z-index:-1; background:linear-gradient(115deg,rgba(255,255,255,.9),rgba(225,239,232,.54)); }
.hero-copy { display:flex; flex-direction:column; justify-content:center; padding:54px 18px 54px 52px; }
.hero h1 { max-width:670px; margin:0; font-size:clamp(38px,4.5vw,60px); line-height:1.08; letter-spacing:-.052em; }.hero h1 span { display:block; color:var(--forest-2); }
.hero-description { max-width:630px; margin:22px 0 0; color:#50615a; font-size:15px; line-height:1.85; }
.hero-link { width:fit-content; margin-top:27px; padding-bottom:4px; border-bottom:1px solid #8ca99d; color:var(--forest); font-size:13px; font-weight:800; text-decoration:none; transition:color .2s ease,border-color .2s ease; }.hero-link:hover { color:var(--leaf); border-color:var(--leaf); }.hero-link:focus-visible { outline:3px solid rgba(40,94,79,.24); outline-offset:5px; }
.hero-art { position:relative; display:grid; min-height:100%; place-items:center; overflow:hidden; background:var(--forest); }.hero-art::after { content:""; position:absolute; inset:0; background:linear-gradient(160deg,transparent 45%,rgba(255,255,255,.06)); }.hero-art svg { width:118%; max-width:470px; fill:#b6d2c6; }.orbit,.thread { fill:none; stroke-linecap:round; }.orbit { stroke:rgba(207,231,220,.22); stroke-width:1.4; }.thread { stroke:#a9cfbf; stroke-width:2.2; }.hero-art circle { stroke:#183f35; stroke-width:4; }
.art-card { position:absolute; right:28px; bottom:27px; z-index:1; width:min(220px,72%); padding:19px 20px; border:1px solid rgba(255,255,255,.2); border-radius:5px 20px 20px 20px; background:rgba(244,250,247,.92); box-shadow:0 20px 45px rgba(5,29,23,.2); }.art-card span,.art-card small { display:block; color:#607069; font-size:11px; }.art-card strong { display:block; margin:7px 0; font-size:20px; letter-spacing:-.025em; }
.active-card { position:absolute; left:52px; bottom:18px; z-index:2; display:flex; gap:10px; align-items:center; padding:11px 14px; border:1px solid #c8dcd3; border-radius:12px; background:#fff; color:var(--forest); }.active-card div { display:flex; flex-direction:column; gap:2px; }.active-card span { color:var(--muted); font-size:11px; }
.trust-rail { display:grid; grid-template-columns:1.15fr 1fr 1fr; margin:16px 0 54px; overflow:hidden; border:1px solid #e0e7e3; border-radius:16px; background:#fff; }.trust-rail article { display:flex; gap:11px; align-items:center; min-width:0; padding:16px 18px; border-left:1px solid #e7ece9; }.trust-rail article:first-child { border-left:0; }.trust-rail article.ready { color:var(--forest); background:#f4f8f6; }.trust-rail .el-icon,.trust-index { display:grid; flex:0 0 27px; height:27px; place-items:center; border-radius:50%; background:#edf3f0; color:var(--forest-2); font-size:11px; font-weight:900; }.trust-rail div { min-width:0; }.trust-rail strong,.trust-rail span { display:block; }.trust-rail strong { font-size:12px; }.trust-rail div span { margin-top:3px; overflow:hidden; color:var(--muted); font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.section-heading { display:grid; grid-template-columns:1fr minmax(280px,410px); gap:40px; align-items:end; margin-bottom:24px; }.section-heading h2,.principles h2,.tools-heading h2 { margin:0; font-size:clamp(27px,3.2vw,38px); letter-spacing:-.042em; }.section-heading > p { margin:0; color:var(--muted); font-size:13px; line-height:1.75; }
.plan-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:18px; align-items:stretch; }
.plan-card { position:relative; display:flex; flex-direction:column; min-width:0; padding:31px; overflow:hidden; border:1px solid #dde5e1; border-radius:8px 25px 25px 25px; background:#fff; box-shadow:0 14px 42px rgba(24,63,53,.055); transition:transform .24s cubic-bezier(.16,1,.3,1),border-color .24s ease,box-shadow .24s ease; }.plan-card::before { content:""; position:absolute; top:0; right:0; width:74px; height:5px; border-radius:0 0 0 5px; background:#b7cec4; }.plan-card:hover { transform:translateY(-3px); border-color:#aac5b9; box-shadow:0 22px 54px rgba(24,63,53,.1); }.plan-card.is-featured { border-color:#91b0a3; background:linear-gradient(152deg,#fff 65%,#f0f6f3); }.plan-card.is-featured::before { width:116px; background:var(--forest-2); }.plan-card.is-current { outline:2px solid var(--forest-2); outline-offset:3px; }
.plan-header { min-height:138px; }.plan-topline { display:flex; justify-content:space-between; gap:10px; min-height:25px; color:var(--leaf); font-size:10px; font-weight:850; letter-spacing:.1em; text-transform:uppercase; }.featured-label,.current-label { padding:4px 9px; border-radius:999px; background:#e6f0eb; letter-spacing:0; text-transform:none; }.current-label { background:var(--forest); color:#fff; }.plan-card h3 { margin:15px 0 0; font-size:25px; letter-spacing:-.035em; }.plan-header p { margin:10px 0 0; color:var(--muted); font-size:13px; line-height:1.7; }
.price-row { display:flex; gap:14px; align-items:center; padding:19px 0; border-top:1px solid #e8edea; border-bottom:1px solid #e8edea; }.price-row > strong { font-size:46px; line-height:1; letter-spacing:-.06em; }.price-row > span { color:#7b8882; font-size:11px; line-height:1.5; }
.quota-highlight { display:grid; grid-template-columns:auto 1fr; gap:17px; align-items:center; margin-top:19px; padding:16px 18px; border:1px solid #c9ded5; border-radius:15px 5px 15px 15px; background:#edf6f1; }.quota-value { color:var(--forest); font-size:29px; font-weight:900; letter-spacing:-.04em; }.quota-value small { margin-left:3px; font-size:11px; font-weight:800; letter-spacing:0; }.quota-highlight strong { font-size:13px; }.quota-highlight p { margin:4px 0 0; color:#5e7169; font-size:11px; line-height:1.5; }
.benefit-block { flex:1; margin-top:22px; }.benefit-block > strong { font-size:11px; letter-spacing:.05em; }.plan-card ul { display:flex; flex-direction:column; gap:10px; margin:14px 0 0; padding:0; list-style:none; }.plan-card li { display:flex; gap:9px; color:#45564f; font-size:13px; line-height:1.55; }.plan-card li .el-icon { flex:none; margin-top:3px; color:var(--leaf); }.benefit-empty { color:var(--muted); font-size:12px; }
.plan-card .el-button { width:100%; height:47px; margin-top:25px; border:1px solid var(--forest); border-radius:10px; background:var(--forest); font-weight:800; transition:transform .15s ease,background-color .2s ease; }.plan-card .el-button:not(.is-disabled):hover { border-color:var(--forest-2); background:var(--forest-2); }.plan-card .el-button:not(.is-disabled):active { transform:scale(.98); }.plan-card .el-button:focus-visible { outline:3px solid rgba(40,94,79,.28); outline-offset:3px; }.payment-note { display:block; margin-top:10px; color:#8b9691; font-size:10px; text-align:center; }
.state-panel { display:flex; justify-content:space-between; gap:20px; align-items:center; min-height:130px; padding:28px; border:1px dashed #bacbc4; border-radius:18px; background:#f4f8f6; }.state-panel > div { display:flex; flex-direction:column; gap:7px; }.state-panel span { color:var(--muted); font-size:13px; }.state-error { border-color:#e4beb7; background:#fff8f6; }
.skeleton-card { pointer-events:none; }.skeleton-line { display:block; height:13px; margin-bottom:16px; border-radius:8px; background:linear-gradient(90deg,#edf1ef 25%,#f8faf9 50%,#edf1ef 75%); background-size:200% 100%; animation:skeleton 1.5s infinite linear; }.skeleton-line.short { width:28%; }.skeleton-line.title { width:48%; height:26px; }.skeleton-line.price-line { width:38%; height:43px; margin:16px 0 28px; }.skeleton-line.button-line { width:100%; height:46px; margin-top:30px; }
.order-card { display:grid; grid-template-columns:46px minmax(0,1fr) auto; gap:18px; align-items:start; margin-top:26px; padding:25px; border:1px solid #e2e7e5; border-radius:20px; }.order-status-mark { display:grid; width:42px; height:42px; place-items:center; border-radius:50%; background:#edf1ef; }.order-status-mark svg { width:20px; }.order-copy { min-width:0; }.order-copy > strong { display:block; font-size:18px; }.order-message { display:block; margin-top:7px; color:#52615c; font-size:13px; line-height:1.6; }.order-card.is-success { border-color:#b8d7c9; background:#f5faf7; }.order-card.is-success .order-status-mark { background:#dcece4; color:var(--forest); }.order-card.is-danger { border-color:#edc6bf; background:#fff9f8; }.order-card.is-warning { border-color:#e8d7ad; background:#fffcf5; }.order-copy dl { display:flex; flex-wrap:wrap; gap:12px 28px; margin:17px 0 0; }.order-copy dl div { min-width:130px; }.order-copy dt { color:#89948f; font-size:11px; }.order-copy dd { margin:3px 0 0; overflow-wrap:anywhere; color:#42524c; font-size:12px; }
.supporter-tools { display:grid; gap:18px; margin-top:46px; }.tools-heading { padding:0 3px 4px; }.tools-heading span { display:block; margin-top:9px; color:var(--muted); font-size:13px; }.supporter-tools :deep(.insights-panel),.supporter-tools :deep(.feedback-panel) { margin-top:0; }
.principles { display:grid; grid-template-columns:minmax(230px,.75fr) 1.55fr; gap:46px; margin-top:34px; padding:36px; border-radius:25px 8px 25px 25px; background:var(--forest); color:#fff; }.principles .eyebrow { color:#a9c9bb; }.principle-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:22px; }.principle-grid article { padding-left:15px; border-left:1px solid rgba(255,255,255,.16); }.principle-grid article > span { color:#9fc0b2; font-size:10px; }.principle-grid strong { display:block; margin-top:20px; font-size:13px; }.principle-grid p { margin:8px 0 0; color:#bfd2ca; font-size:11px; line-height:1.75; }
@keyframes skeleton { to { background-position:-200% 0; } }
@media (max-width:900px) { .hero { grid-template-columns:1fr minmax(245px,.65fr); }.hero-copy { padding-left:34px; }.active-card { left:34px; }.trust-rail { grid-template-columns:1fr; }.trust-rail article { border-top:1px solid #e7ece9; border-left:0; }.trust-rail article:first-child { border-top:0; }.principles { grid-template-columns:1fr; gap:28px; }.section-heading { grid-template-columns:1fr; gap:12px; } }
@media (max-width:700px) { .supporter-page { padding:18px 11px 52px; }.hero { grid-template-columns:1fr; }.hero-copy { padding:34px 23px 38px; }.hero h1 { font-size:clamp(35px,11vw,49px); }.hero-art { min-height:245px; }.hero-art svg { width:105%; }.active-card { position:relative; left:auto; bottom:auto; grid-column:1/-1; margin:0 20px 20px; }.trust-rail { margin-bottom:42px; }.plan-grid,.principle-grid { grid-template-columns:1fr; }.plan-card { padding:25px 21px; }.plan-header { min-height:auto; }.price-row { margin-top:20px; }.order-card { grid-template-columns:42px minmax(0,1fr); }.order-card > .el-button { grid-column:1/-1; width:100%; }.order-copy dl { flex-direction:column; gap:10px; }.principles { padding:29px 22px; }.principle-grid { gap:0; }.principle-grid article { padding:17px 0; border-top:1px solid rgba(255,255,255,.15); border-left:0; }.principle-grid strong { margin-top:8px; }.state-panel { align-items:stretch; flex-direction:column; } }
@media (max-width:420px) { .quota-highlight { grid-template-columns:1fr; gap:7px; }.art-card { right:18px; bottom:18px; }.trust-rail div span { white-space:normal; }.section-heading h2,.principles h2,.tools-heading h2 { font-size:28px; } }
@media (prefers-reduced-motion:reduce) { .plan-card,.plan-card .el-button,.hero-link { transition:none; }.plan-card:hover { transform:none; }.skeleton-line { animation:none; } }
</style>
