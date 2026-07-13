<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Hide, Link, RefreshRight, Tickets, View } from '@element-plus/icons-vue'
import { supporterVoucherApi, type SupporterVoucherGrant } from '@/api/supporterVoucher'

const loading = ref(true)
const loadError = ref('')
const grants = ref<SupporterVoucherGrant[]>([])
const visibleCodes = ref<Set<number>>(new Set())

const formatTime = (value?: string | null) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '—'

const load = async () => {
  loading.value = true
  loadError.value = ''
  visibleCodes.value = new Set()
  try {
    const res = await supporterVoucherApi.mine()
    if (res.code !== 2000) throw new Error(res.message || '公益站额度加载失败')
    grants.value = res.data || []
  } catch (error: any) {
    grants.value = []
    loadError.value = error?.response?.data?.message || error?.message || '暂时无法加载公益站额度，请稍后重试'
  } finally {
    loading.value = false
  }
}

const toggleCode = (id: number) => {
  const next = new Set(visibleCodes.value)
  next.has(id) ? next.delete(id) : next.add(id)
  visibleCodes.value = next
}

const copyCode = async (code?: string | null) => {
  if (!code) return
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('兑换码已复制，请注意保管')
  } catch {
    ElMessage.error('复制失败，请显示兑换码后手动复制')
  }
}

onMounted(load)
</script>

<template>
  <section class="voucher-panel" aria-labelledby="voucher-title">
    <header class="panel-heading">
      <div class="heading-icon" aria-hidden="true"><el-icon><Tickets /></el-icon></div>
      <div class="heading-copy">
        <p>PUBLIC SERVICE QUOTA</p>
        <h2 id="voucher-title">公益站额度</h2>
        <span>支持周期对应的额度会以兑换码发放，过往记录也会保留在这里。</span>
      </div>
      <el-button text :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button>
    </header>

    <div v-if="loading" class="voucher-list" aria-busy="true" aria-label="正在加载公益站额度">
      <article v-for="index in 2" :key="index" class="voucher-card skeleton">
        <i></i><i></i><i></i>
      </article>
    </div>

    <div v-else-if="loadError" class="panel-state error" role="alert">
      <strong>额度记录没有加载成功</strong>
      <p>{{ loadError }}</p>
      <el-button round @click="load">重新加载</el-button>
    </div>

    <div v-else-if="grants.length === 0" class="panel-state">
      <strong>还没有可展示的额度</strong>
      <p>支付并开通支持者权益后，对应的发放记录会出现在这里。</p>
    </div>

    <div v-else class="voucher-list" aria-live="polite">
      <article v-for="grant in grants" :key="grant.id" class="voucher-card" :class="`is-${grant.status.toLowerCase()}`">
        <div class="voucher-topline">
          <div class="quota-mark"><strong>{{ grant.quota }}</strong><span>额度</span></div>
          <div class="voucher-meta">
            <strong>{{ grant.status === 'ISSUED' ? '已发放' : '库存补发中' }}</strong>
            <span>{{ grant.status === 'ISSUED' ? `发放于 ${formatTime(grant.issuedAt)}` : `记录于 ${formatTime(grant.grantedAt)}` }}</span>
          </div>
          <span class="status-dot" aria-hidden="true"></span>
        </div>

        <template v-if="grant.status === 'ISSUED' && grant.code">
          <div class="code-row">
            <div class="code-value" :class="{ revealed: visibleCodes.has(grant.id) }">
              <span>{{ visibleCodes.has(grant.id) ? grant.code : '••••—••••—••••' }}</span>
            </div>
            <el-button
              circle
              :icon="visibleCodes.has(grant.id) ? Hide : View"
              :aria-label="visibleCodes.has(grant.id) ? '隐藏兑换码' : '显示兑换码'"
              @click="toggleCode(grant.id)"
            />
            <el-button circle :icon="CopyDocument" aria-label="复制兑换码" @click="copyCode(grant.code)" />
          </div>
          <a class="redeem-link" :href="grant.redemptionUrl" target="_blank" rel="noopener noreferrer">
            <el-icon><Link /></el-icon><span>前往公益站使用额度</span><span aria-hidden="true">↗</span>
          </a>
        </template>

        <div v-else class="pending-note" role="status">
          <strong>兑换码尚未入库</strong>
          <p>系统会在有可用库存后补发到本记录。当前无法承诺具体发放时间，无需重复下单。</p>
        </div>

        <footer>来源订单 <span>{{ grant.sourceOrderNo }}</span></footer>
      </article>
    </div>

    <p class="privacy-note">兑换码默认遮罩，仅在当前页面临时显示。请勿将兑换码发布在帖子、评论或截图中。</p>
  </section>
</template>

<style scoped>
.voucher-panel { padding:30px; border:1px solid #dce6e1; border-radius:9px 26px 26px 26px; background:#fbfdfc; color:#21342e; }
.panel-heading { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:15px; align-items:start; padding-bottom:23px; border-bottom:1px solid #e2e9e6; }
.heading-icon { display:grid; width:45px; height:45px; place-items:center; border-radius:50%; background:#294d43; color:#fff; }
.heading-copy p { margin:0 0 5px; color:#4f7569; font-size:10px; font-weight:800; letter-spacing:.14em; }.heading-copy h2 { margin:0; font-size:24px; letter-spacing:-.03em; }.heading-copy span { display:block; margin-top:7px; color:#687771; font-size:13px; line-height:1.6; }
.voucher-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:13px; margin-top:22px; }
.voucher-card { position:relative; min-width:0; padding:20px; overflow:hidden; border:1px solid #dfe7e3; border-radius:17px; background:#fff; }.voucher-card.is-issued { border-color:#bfd7cc; }.voucher-card.is-pending { background:#fafbf9; }
.voucher-topline { display:grid; grid-template-columns:auto minmax(0,1fr) auto; gap:13px; align-items:center; }.quota-mark { display:flex; flex-direction:column; justify-content:center; width:56px; height:56px; border-radius:15px 5px 15px 15px; background:#e6f1ec; color:#214d40; text-align:center; }.quota-mark strong { font-size:23px; line-height:1; }.quota-mark span { margin-top:4px; font-size:10px; }.voucher-meta strong,.voucher-meta span { display:block; }.voucher-meta strong { font-size:14px; }.voucher-meta span { margin-top:5px; color:#7a8782; font-size:11px; line-height:1.45; }.status-dot { width:9px; height:9px; border-radius:50%; background:#b5beb9; }.is-issued .status-dot { background:#4f806d; }
.code-row { display:grid; grid-template-columns:minmax(0,1fr) auto auto; gap:7px; margin-top:18px; }.code-value { display:flex; min-width:0; align-items:center; padding:0 13px; overflow:hidden; border:1px solid #dce5e1; border-radius:10px; background:#f5f8f6; color:#53635d; font-family:ui-monospace,SFMono-Regular,Consolas,monospace; font-size:13px; letter-spacing:.09em; }.code-value span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.code-value.revealed { color:#183f35; letter-spacing:.025em; }.code-row :deep(.el-button) { margin-left:0; }.code-row :deep(.el-button:active) { transform:scale(.96); }.code-row :deep(.el-button:focus-visible),.redeem-link:focus-visible { outline:3px solid rgba(41,77,67,.24); outline-offset:2px; }
.redeem-link { display:flex; gap:7px; align-items:center; width:fit-content; margin-top:14px; color:#285e4f; font-size:12px; font-weight:750; text-decoration:none; }.redeem-link span:last-child { transition:transform .18s ease; }.redeem-link:hover span:last-child { transform:translate(2px,-2px); }
.pending-note { margin-top:17px; padding:14px 15px; border-left:3px solid #b7c2bd; border-radius:4px 12px 12px 4px; background:#f1f4f2; }.pending-note strong { font-size:12px; }.pending-note p { margin:6px 0 0; color:#68756f; font-size:11px; line-height:1.65; }
.voucher-card footer { margin-top:17px; padding-top:12px; overflow:hidden; border-top:1px solid #edf0ee; color:#8b9691; font-size:10px; text-overflow:ellipsis; white-space:nowrap; }.voucher-card footer span { font-family:ui-monospace,SFMono-Regular,Consolas,monospace; }
.privacy-note { margin:15px 2px 0; color:#7e8a85; font-size:11px; line-height:1.65; }
.panel-state { padding:38px 20px 28px; text-align:center; }.panel-state strong { font-size:17px; }.panel-state p { max-width:500px; margin:8px auto 0; color:#75827d; font-size:13px; line-height:1.65; }.panel-state.error { margin-top:20px; border-radius:14px; background:#fff6f4; }.panel-state .el-button { margin-top:15px; }
.skeleton i { display:block; height:11px; margin-bottom:12px; border-radius:8px; background:linear-gradient(90deg,#edf1ef 25%,#f8faf9 50%,#edf1ef 75%); background-size:200% 100%; animation:skeleton 1.5s infinite linear; }.skeleton i:first-child { width:32%; height:25px; }.skeleton i:nth-child(2) { width:75%; }.skeleton i:last-child { width:52%; margin:24px 0 0; }
@keyframes skeleton { to { background-position:-200% 0; } }
@media (max-width:720px) { .voucher-panel { padding:22px 15px; }.voucher-list { grid-template-columns:1fr; }.panel-heading { grid-template-columns:auto minmax(0,1fr); }.panel-heading > .el-button { grid-column:1/-1; justify-self:end; margin-top:-8px; } }
@media (max-width:420px) { .voucher-card { padding:17px 14px; }.code-row { grid-template-columns:minmax(0,1fr) auto auto; }.code-value { padding:0 9px; font-size:11px; }.quota-mark { width:50px; height:50px; } }
@media (prefers-reduced-motion:reduce) { .skeleton i { animation:none; }.redeem-link span:last-child,.code-row :deep(.el-button) { transition:none; }.redeem-link:hover span:last-child,.code-row :deep(.el-button:active) { transform:none; } }
</style>
