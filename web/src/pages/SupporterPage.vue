<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Lock, Medal, RefreshRight } from '@element-plus/icons-vue'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import SupporterFeedbackPanel from '@/components/supporter/SupporterFeedbackPanel.vue'
import SupporterInsightsPanel from '@/components/supporter/SupporterInsightsPanel.vue'
import { supporterApi, type SupporterOrder, type SupporterPlan, type SupporterStatus } from '@/api/supporter'

const loading = ref(true)
const loadError = ref('')
const route = useRoute()
const router = useRouter()
const submitting = ref<string | null>(null)
const plans = ref<SupporterPlan[]>([])
const status = ref<SupporterStatus>({ active: false })
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

      <section class="hero">
        <div class="hero-copy">
          <p class="eyebrow">Support Zens</p>
          <h1>让认真表达，<br><span>被长久地看见。</span></h1>
          <p class="hero-description">
            你的支持会直接参与维持服务器、图片存储与社区日常维护。这里不出售流量特权，只为愿意同行的人留下一枚真实的支持者标记。
          </p>
          <div class="hero-points" aria-label="支持原则">
            <span><el-icon><Check /></el-icon>普通用户功能不缩水</span>
            <span><el-icon><Check /></el-icon>权益按方案真实交付</span>
            <span><el-icon><Check /></el-icon>支付结果以后端回调为准</span>
          </div>
        </div>

        <aside class="support-summary" aria-label="支持者状态">
          <div class="summary-mark" aria-hidden="true">Z</div>
          <p>{{ status.active ? '你正在支持 Zens' : '成为 Zens 支持者' }}</p>
          <strong v-if="status.active">{{ status.planName }}</strong>
          <strong v-else>{{ availablePlanCount || plans.length || '—' }} 个可选方案</strong>
          <span v-if="status.active">权益有效期至<br>{{ formatTime(status.expiresAt) }}</span>
          <span v-else>一次支持，一段共同建设社区的时间。</span>
        </aside>

        <div v-if="status.active" class="active-card" role="status">
          <el-icon><Medal /></el-icon>
          <div>
            <strong>支持者权益已生效</strong>
            <span>{{ status.planName }} · 有效期至 {{ formatTime(status.expiresAt) }}</span>
          </div>
        </div>
      </section>

      <div class="notice" :class="{ ready: paymentAvailable }" role="status">
        <el-icon><component :is="paymentAvailable ? Check : Lock" /></el-icon>
        <div>
          <strong>{{ paymentAvailable ? '安全支付已就绪' : '在线支付暂未开放' }}</strong>
          <span v-if="paymentAvailable">订单由 Zens 安全创建，支付完成后可能需要几秒确认，请勿重复付款。</span>
          <span v-else>你仍可查看完整方案；支付开放后，按钮会自动恢复。</span>
        </div>
      </div>

      <section class="plans-section" aria-labelledby="plans-title">
        <header class="section-heading">
          <div>
            <p class="eyebrow">Choose your support</p>
            <h2 id="plans-title">选择支持方式</h2>
          </div>
          <p>权益内容与期限以当前方案为准。支持到期后不会自动续费，也不会影响你的社区账号和已有内容。</p>
        </header>

        <div v-if="loading" class="plan-grid" aria-label="正在加载支持方案" aria-busy="true">
          <article v-for="index in 2" :key="index" class="plan-card skeleton-card">
            <i class="skeleton-line short"></i><i class="skeleton-line title"></i><i class="skeleton-line price-line"></i>
            <i class="skeleton-line"></i><i class="skeleton-line"></i><i class="skeleton-line button-line"></i>
          </article>
        </div>

        <div v-else-if="loadError" class="state-panel state-error" role="alert">
          <div>
            <strong>支持方案没有加载成功</strong>
            <span>{{ loadError }}</span>
          </div>
          <el-button round @click="load">重新加载</el-button>
        </div>

        <div v-else-if="plans.length === 0" class="state-panel">
          <div>
            <strong>新的支持方案正在准备</strong>
            <span>目前没有可选方案，社区的基础功能仍可正常使用。</span>
          </div>
        </div>

        <div v-else class="plan-grid">
          <article
            v-for="(plan, index) in plans"
            :key="plan.code"
            class="plan-card"
            :class="{ 'is-featured': index === plans.length - 1 && plans.length > 1, 'is-current': status.active && status.planCode === plan.code }"
          >
            <div class="plan-topline">
              <span>{{ plan.durationDays }} 天支持周期</span>
              <span v-if="status.active && status.planCode === plan.code" class="current-label">当前方案</span>
              <span v-else-if="index === plans.length - 1 && plans.length > 1" class="featured-label">长期同行</span>
            </div>
            <h3>{{ plan.name }}</h3>
            <div class="price"><span>{{ formatMoney(plan.priceCents) }}</span><small>一次支付 · {{ plan.durationDays }} 天</small></div>
            <p class="description">{{ plan.description }}</p>
            <div class="benefit-block">
              <strong>本方案包含</strong>
              <ul v-if="plan.benefits.length">
                <li v-for="benefit in plan.benefits" :key="benefit">
                  <el-icon><Check /></el-icon><span>{{ benefit }}</span>
                </li>
              </ul>
              <p v-else class="benefit-empty">当前方案暂无额外权益说明。</p>
            </div>
            <el-button
              type="primary"
              size="large"
              round
              :disabled="!plan.paymentAvailable"
              :loading="submitting === plan.code"
              @click="support(plan)"
            >
              {{ plan.paymentAvailable ? `选择 ${plan.name}` : '支付暂未开放' }}
            </el-button>
            <small class="payment-note">点击后将前往支付宝完成付款</small>
          </article>
        </div>
      </section>

      <section v-if="lastOrder" class="order-card" :class="`is-${orderState.type}`" aria-live="polite">
        <div class="order-status-mark" aria-hidden="true"><component :is="orderState.type === 'success' ? Check : RefreshRight" /></div>
        <div class="order-copy">
          <p class="eyebrow">Latest order</p>
          <strong>{{ orderState.title }} · {{ lastOrder.planName }}</strong>
          <span class="order-message">{{ orderState.message }}</span>
          <dl>
            <div><dt>订单金额</dt><dd>{{ formatMoney(lastOrder.amountCents) }}</dd></div>
            <div><dt>订单编号</dt><dd>{{ lastOrder.orderNo }}</dd></div>
            <div><dt>过期时间</dt><dd>{{ formatTime(lastOrder.expiresAt) }}</dd></div>
          </dl>
        </div>
        <el-button :icon="RefreshRight" :loading="orderLoading" round @click="refreshOrder">刷新状态</el-button>
      </section>

      <section v-if="status.active" class="supporter-tools" aria-label="支持者专属工具">
        <SupporterInsightsPanel :days="30" />
        <SupporterFeedbackPanel v-if="status.planCode === 'supporter_plus_30'" />
      </section>

      <section class="principles">
        <div class="principle-title">
          <p class="eyebrow">Our promise</p>
          <h2>支持有回报，社区无特权</h2>
        </div>
        <div class="principle-grid">
          <article><span>01</span><strong>不出售表达权</strong><p>审核优待、推荐排名和普通发帖权不属于付费商品。</p></article>
          <article><span>02</span><strong>只承诺已上线权益</strong><p>你实际获得的内容，始终以支付前方案中明确列出的权益为准。</p></article>
          <article><span>03</span><strong>支付可追溯</strong><p>订单金额、支付结果与权益发放均由服务端记录和确认。</p></article>
        </div>
      </section>
    </main>
  </MainLayout>
</template>

<style scoped>
.supporter-page { --green-900:#203c35; --green-800:#294d43; --green-700:#3e685c; --green-100:#e7efeb; --green-50:#f3f7f5; --ink:#1e2926; --muted:#65726d; max-width:1120px; min-height:100dvh; margin:0 auto; padding:28px 18px 76px; color:var(--ink); }
.eyebrow { margin:0 0 9px; color:var(--green-700); font-size:11px; font-weight:800; letter-spacing:.16em; text-transform:uppercase; }
.hero { position:relative; display:grid; grid-template-columns:minmax(0,1.55fr) minmax(230px,.55fr); gap:50px; margin-top:18px; padding:52px; overflow:hidden; border:1px solid #d9e4df; border-radius:34px 34px 34px 10px; background:linear-gradient(125deg,#f7faf8 0%,#edf4f0 64%,#e1ece7 100%); }
.hero::after { content:""; position:absolute; right:-80px; bottom:-130px; width:320px; height:320px; border:1px solid rgba(62,104,92,.14); border-radius:50%; box-shadow:0 0 0 42px rgba(62,104,92,.04),0 0 0 92px rgba(62,104,92,.025); pointer-events:none; }
.hero-copy,.support-summary { position:relative; z-index:1; }
.hero h1 { margin:0; font-size:clamp(38px,5.2vw,66px); line-height:1.04; letter-spacing:-.055em; }.hero h1 span { color:var(--green-700); }
.hero-description { max-width:680px; margin:22px 0 0; color:#52625c; font-size:16px; line-height:1.85; }
.hero-points { display:flex; flex-wrap:wrap; gap:10px 18px; margin-top:28px; }.hero-points span { display:flex; gap:6px; align-items:center; color:#40564e; font-size:13px; }.hero-points .el-icon { color:var(--green-700); }
.support-summary { align-self:center; min-height:250px; padding:27px 24px; border:1px solid rgba(255,255,255,.75); border-radius:9px 28px 28px 28px; background:rgba(255,255,255,.7); box-shadow:0 22px 50px rgba(42,77,67,.09); backdrop-filter:blur(12px); }.summary-mark { display:grid; width:44px; height:44px; place-items:center; border-radius:50%; background:var(--green-800); color:#fff; font-size:20px; font-weight:900; }.support-summary p { margin:28px 0 8px; color:var(--muted); font-size:13px; }.support-summary strong { display:block; font-size:23px; letter-spacing:-.025em; }.support-summary > span { display:block; margin-top:15px; color:var(--muted); font-size:13px; line-height:1.65; }
.active-card { position:relative; z-index:1; grid-column:1/-1; display:flex; gap:12px; align-items:center; width:fit-content; margin-top:-22px; padding:13px 17px; border:1px solid #c8dcd3; border-radius:15px; background:#fff; color:var(--green-800); }.active-card div { display:flex; flex-direction:column; gap:3px; }.active-card span { color:var(--muted); font-size:12px; }
.notice { display:flex; align-items:flex-start; gap:11px; margin:20px 0 48px; padding:14px 18px; border:1px solid #e2e7e5; border-radius:15px; background:#f7f8f8; color:var(--muted); }.notice.ready { border-color:#cee0d8; background:var(--green-50); color:var(--green-800); }.notice .el-icon { flex:none; margin-top:3px; }.notice div { display:flex; flex-direction:column; gap:2px; }.notice strong { font-size:13px; }.notice span { font-size:12px; line-height:1.65; }
.section-heading { display:grid; grid-template-columns:1fr minmax(280px,430px); gap:40px; align-items:end; margin-bottom:22px; }.section-heading h2,.principles h2 { margin:0; font-size:clamp(27px,3.5vw,40px); letter-spacing:-.04em; }.section-heading > p { margin:0; color:var(--muted); font-size:13px; line-height:1.75; }
.plan-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px; align-items:stretch; }
.plan-card { display:flex; flex-direction:column; min-width:0; padding:31px; border:1px solid #dfe6e3; border-radius:10px 28px 28px 28px; background:#fff; box-shadow:0 16px 40px rgba(32,60,53,.055); transition:transform .25s cubic-bezier(.16,1,.3,1),border-color .25s ease,box-shadow .25s ease; }.plan-card:hover { transform:translateY(-4px); border-color:#adc8bd; box-shadow:0 22px 52px rgba(32,60,53,.1); }.plan-card.is-featured { border-color:#9fbbaf; background:linear-gradient(155deg,#fff 55%,#f1f6f3); }.plan-card.is-current { outline:2px solid var(--green-700); outline-offset:3px; }
.plan-topline { display:flex; justify-content:space-between; gap:12px; min-height:25px; color:var(--green-700); font-size:11px; font-weight:800; letter-spacing:.08em; text-transform:uppercase; }.featured-label,.current-label { padding:4px 9px; border-radius:999px; background:var(--green-100); letter-spacing:0; }.current-label { background:var(--green-800); color:#fff; }
.plan-card h3 { margin:17px 0 0; font-size:26px; letter-spacing:-.035em; }.price { display:flex; gap:12px; align-items:baseline; margin-top:13px; }.price > span { font-size:46px; font-weight:850; letter-spacing:-.055em; }.price small { color:#7c8984; font-size:12px; font-weight:600; }
.description { min-height:50px; margin:15px 0 0; color:var(--muted); font-size:14px; line-height:1.7; }.benefit-block { flex:1; margin-top:24px; padding-top:20px; border-top:1px solid #e6ebe9; }.benefit-block > strong { font-size:12px; letter-spacing:.05em; }.plan-card ul { display:flex; flex-direction:column; gap:11px; min-height:112px; margin:15px 0 0; padding:0; list-style:none; }.plan-card li { display:flex; gap:9px; color:#42524c; font-size:14px; line-height:1.55; }.plan-card li .el-icon { flex:none; margin-top:4px; color:var(--green-700); }.benefit-empty { color:var(--muted); font-size:13px; }
.plan-card .el-button { width:100%; height:46px; margin-top:24px; border-color:var(--green-800); background:var(--green-800); font-weight:750; transition:transform .15s ease,background-color .2s ease; }.plan-card .el-button:not(.is-disabled):hover { border-color:var(--green-700); background:var(--green-700); }.plan-card .el-button:not(.is-disabled):active { transform:scale(.98); }.plan-card .el-button:focus-visible { outline:3px solid rgba(62,104,92,.35); outline-offset:3px; }.payment-note { display:block; margin-top:10px; color:#89948f; text-align:center; }
.state-panel { display:flex; justify-content:space-between; gap:20px; align-items:center; min-height:130px; padding:28px; border:1px dashed #bacbc4; border-radius:18px; background:var(--green-50); }.state-panel > div { display:flex; flex-direction:column; gap:7px; }.state-panel span { color:var(--muted); font-size:13px; }.state-error { border-color:#e4beb7; background:#fff8f6; }
.skeleton-card { pointer-events:none; }.skeleton-line { display:block; height:13px; margin-bottom:16px; border-radius:8px; background:linear-gradient(90deg,#edf1ef 25%,#f7f9f8 50%,#edf1ef 75%); background-size:200% 100%; animation:skeleton 1.5s infinite linear; }.skeleton-line.short { width:28%; }.skeleton-line.title { width:48%; height:26px; }.skeleton-line.price-line { width:38%; height:43px; margin:16px 0 28px; }.skeleton-line.button-line { width:100%; height:46px; margin-top:30px; }
.order-card { display:grid; grid-template-columns:46px minmax(0,1fr) auto; gap:18px; align-items:start; margin-top:26px; padding:25px; border:1px solid #e2e7e5; border-radius:20px; }.order-status-mark { display:grid; width:42px; height:42px; place-items:center; border-radius:50%; background:#edf1ef; }.order-status-mark svg { width:20px; }.order-copy { min-width:0; }.order-copy > strong { display:block; font-size:18px; }.order-message { display:block; margin-top:7px; color:#52615c; font-size:13px; line-height:1.6; }.order-card.is-success { border-color:#b8d7c9; background:#f5faf7; }.order-card.is-success .order-status-mark { background:#dcece4; color:var(--green-800); }.order-card.is-danger { border-color:#edc6bf; background:#fff9f8; }.order-card.is-warning { border-color:#e8d7ad; background:#fffcf5; }.order-copy dl { display:flex; flex-wrap:wrap; gap:12px 28px; margin:17px 0 0; }.order-copy dl div { min-width:130px; }.order-copy dt { color:#89948f; font-size:11px; }.order-copy dd { margin:3px 0 0; overflow-wrap:anywhere; color:#42524c; font-size:12px; }
.supporter-tools { display:grid; gap:22px; margin-top:30px; }
.principles { display:grid; grid-template-columns:minmax(220px,.7fr) 1.5fr; gap:52px; margin-top:30px; padding:36px; border-radius:28px 10px 28px 28px; background:var(--green-900); color:#fff; }.principles .eyebrow { color:#a9c6ba; }.principle-grid { display:grid; grid-template-columns:repeat(3,1fr); gap:25px; }.principle-grid article { padding-left:16px; border-left:1px solid rgba(255,255,255,.15); }.principle-grid article > span { color:#9fbaaf; font-size:11px; }.principle-grid strong { display:block; margin-top:24px; font-size:14px; }.principle-grid p { margin:8px 0 0; color:#bfd0c9; font-size:12px; line-height:1.75; }
@keyframes skeleton { to { background-position:-200% 0; } }
@media (max-width:820px) { .hero { grid-template-columns:1fr; gap:28px; padding:38px 30px; }.support-summary { min-height:auto; }.active-card { margin-top:0; }.principles { grid-template-columns:1fr; gap:28px; }.section-heading { grid-template-columns:1fr; gap:12px; } }
@media (max-width:680px) { .supporter-page { padding:18px 11px 52px; }.hero { padding:30px 22px; border-radius:24px 24px 24px 8px; }.hero h1 { font-size:clamp(36px,12vw,49px); }.hero-points { flex-direction:column; }.notice { margin-bottom:38px; }.plan-grid,.principle-grid { grid-template-columns:1fr; }.plan-card { padding:25px 22px; }.description,.plan-card ul { min-height:auto; }.price { align-items:flex-end; flex-direction:column; gap:2px; }.price > span { align-self:flex-start; }.price small { align-self:flex-start; }.order-card { grid-template-columns:42px minmax(0,1fr); }.order-card > .el-button { grid-column:1/-1; width:100%; }.order-copy dl { flex-direction:column; gap:10px; }.principles { padding:30px 23px; }.principle-grid { gap:0; }.principle-grid article { padding:18px 0; border-left:0; border-top:1px solid rgba(255,255,255,.15); }.principle-grid strong { margin-top:10px; }.state-panel { align-items:stretch; flex-direction:column; } }
@media (prefers-reduced-motion:reduce) { .plan-card,.plan-card .el-button { transition:none; }.plan-card:hover { transform:none; }.skeleton-line { animation:none; } }
</style>
