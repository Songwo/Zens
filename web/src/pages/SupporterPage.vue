<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Lock, Medal, RefreshRight } from '@element-plus/icons-vue'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import { supporterApi, type SupporterOrder, type SupporterPlan, type SupporterStatus } from '@/api/supporter'

const loading = ref(true)
const submitting = ref<string | null>(null)
const plans = ref<SupporterPlan[]>([])
const status = ref<SupporterStatus>({ active: false })
const lastOrder = ref<SupporterOrder | null>(null)

const paymentAvailable = computed(() => plans.value.some(plan => plan.paymentAvailable))

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
  try {
    const planRes = await supporterApi.plans()
    if (planRes.code === 2000) plans.value = planRes.data || []
    try {
      const statusRes = await supporterApi.me()
      if (statusRes.code === 2000 && statusRes.data) status.value = statusRes.data
    } catch {
      // 未登录访客仍可浏览方案；下单时路由鉴权会引导登录。
    }
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

const refreshOrder = async () => {
  if (!lastOrder.value) return
  const res = await supporterApi.order(lastOrder.value.orderNo)
  if (res.code === 2000 && res.data) {
    lastOrder.value = res.data
    if (res.data.status === 'PAID') await load()
  }
}

onMounted(load)
</script>

<template>
  <MainLayout>
    <div class="supporter-page" v-loading="loading">
      <PageBackButton fallback="/benefits" />

      <section class="hero">
        <p class="eyebrow">support zens</p>
        <h1>让一个真实、开放的社区走得更久</h1>
        <p>
          支持费用用于服务器、对象存储、邮件、内容整理与持续维护。付费不会带来审核优待、推荐排名或更高发言权，普通用户的基础阅读与表达体验保持完整。
        </p>
        <div v-if="status.active" class="active-card">
          <el-icon><Medal /></el-icon>
          <div>
            <strong>{{ status.planName }}</strong>
            <span>有效期至 {{ formatTime(status.expiresAt) }}</span>
          </div>
        </div>
      </section>

      <div class="notice" :class="{ ready: paymentAvailable }">
        <el-icon><component :is="paymentAvailable ? Check : Lock" /></el-icon>
        <span v-if="paymentAvailable">支付渠道已开放。订单金额由服务端生成，只有验签成功的服务商回调才会开通权益。</span>
        <span v-else>方案已经准备好，但在线支付默认关闭；在正式商户通道完成签约、验签和退款配置前，本站不会产生真实扣款。</span>
      </div>

      <section class="plan-grid">
        <article v-for="plan in plans" :key="plan.code" class="plan-card">
          <p class="eyebrow">{{ plan.durationDays }} days</p>
          <h2>{{ plan.name }}</h2>
          <div class="price">{{ formatMoney(plan.priceCents) }}<small>/ {{ plan.durationDays }} 天</small></div>
          <p class="description">{{ plan.description }}</p>
          <ul>
            <li v-for="benefit in plan.benefits" :key="benefit">
              <el-icon><Check /></el-icon><span>{{ benefit }}</span>
            </li>
          </ul>
          <el-button
            type="primary"
            size="large"
            round
            :disabled="!plan.paymentAvailable"
            :loading="submitting === plan.code"
            @click="support(plan)"
          >
            {{ plan.paymentAvailable ? '支持 Zens' : '支付接入中' }}
          </el-button>
        </article>
      </section>

      <section v-if="lastOrder" class="order-card">
        <div>
          <p class="eyebrow">latest order</p>
          <strong>{{ lastOrder.planName }} · {{ lastOrder.status }}</strong>
          <span>订单号 {{ lastOrder.orderNo }}，订单过期时间 {{ formatTime(lastOrder.expiresAt) }}</span>
        </div>
        <el-button :icon="RefreshRight" round @click="refreshOrder">刷新状态</el-button>
      </section>

      <section class="principles">
        <h2>收费边界</h2>
        <div class="principle-grid">
          <p><strong>不会售卖：</strong>审核优待、内容推荐排名、普通发帖权、用户数据或虚假身份。</p>
          <p><strong>逐步提供：</strong>支持者身份、运营透明简报、内测参与和真实的社区工具增值服务。</p>
          <p><strong>支付安全：</strong>金额以后端订单为准，回调验签、事件幂等、权益发放和订单状态均留审计记录。</p>
        </div>
      </section>
    </div>
  </MainLayout>
</template>

<style scoped>
.supporter-page { max-width: 1080px; margin: 0 auto; padding: 28px 16px 72px; }
.hero { margin-top: 18px; padding: 42px; border: 1px solid #e8ddc5; border-radius: 28px; background: radial-gradient(circle at 85% 8%, rgba(244,180,0,.18), transparent 32%), linear-gradient(135deg,#fffaf0,#fff); }
.eyebrow { margin: 0 0 8px; color: #9a6b00; font-size: 12px; font-weight: 900; letter-spacing: .11em; text-transform: uppercase; }
.hero h1 { max-width: 760px; margin: 0; color: #20242b; font-size: clamp(32px,5vw,58px); line-height: 1.08; }
.hero > p:not(.eyebrow) { max-width: 720px; margin: 18px 0 0; color: #667085; line-height: 1.8; }
.active-card { width: fit-content; margin-top: 24px; display: flex; gap: 12px; align-items: center; padding: 14px 18px; border-radius: 16px; background: #fff4cf; color: #765500; }
.active-card div { display: flex; flex-direction: column; gap: 3px; }.active-card span { font-size: 13px; }
.notice { margin: 22px 0; display: flex; align-items: flex-start; gap: 10px; padding: 15px 18px; border-radius: 16px; background: #f5f6f8; color: #667085; line-height: 1.65; }.notice.ready { background: #ecfdf3; color: #067647; }
.plan-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 20px; }
.plan-card { padding: 30px; border: 1px solid #e7e9ed; border-radius: 22px; background: #fff; box-shadow: 0 18px 45px rgba(30,35,45,.05); }
.plan-card h2 { margin: 0; font-size: 25px; color: #20242b; }.price { margin-top: 18px; font-size: 42px; font-weight: 900; color: #20242b; }.price small { margin-left: 6px; color: #98a2b3; font-size: 13px; font-weight: 700; }
.description { min-height: 52px; color: #667085; line-height: 1.7; }.plan-card ul { min-height: 114px; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 10px; }.plan-card li { display: flex; gap: 8px; color: #475467; line-height: 1.55; }.plan-card li .el-icon { flex: 0 0 auto; margin-top: 4px; color: #12a150; }.plan-card .el-button { width: 100%; margin-top: 12px; }
.order-card { margin-top: 22px; padding: 22px; border: 1px solid #e7e9ed; border-radius: 18px; display: flex; justify-content: space-between; gap: 18px; align-items: center; }.order-card div { display: flex; flex-direction: column; gap: 6px; }.order-card span { color: #667085; font-size: 13px; }
.principles { margin-top: 28px; padding: 28px; border-radius: 22px; background: #20242b; color: #fff; }.principles h2 { margin-top: 0; }.principle-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 20px; }.principle-grid p { margin: 0; color: #cbd0d8; line-height: 1.75; }.principle-grid strong { color: #fff; }
@media (max-width: 760px) { .supporter-page { padding: 18px 10px 48px; }.hero { padding: 28px 22px; }.plan-grid,.principle-grid { grid-template-columns: 1fr; }.order-card { align-items: stretch; flex-direction: column; }.plan-card ul,.description { min-height: auto; } }
</style>
