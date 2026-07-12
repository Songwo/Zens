<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Check, Clock, RefreshRight } from '@element-plus/icons-vue'
import {
  supporterFeedbackApi,
  type SupporterFeedback,
  type SupporterFeedbackStatus,
} from '@/api/supporterFeedback'

const PAGE_SIZE = 6
const loading = ref(true)
const submitting = ref(false)
const accessDenied = ref(false)
const loadError = ref('')
const records = ref<SupporterFeedback[]>([])
const total = ref(0)
const page = ref(1)
const form = ref({ subject: '', content: '' })

const subjectLength = computed(() => form.value.subject.trim().length)
const contentLength = computed(() => form.value.content.trim().length)
const canSubmit = computed(() => subjectLength.value >= 4 && subjectLength.value <= 100
  && contentLength.value >= 10 && contentLength.value <= 2000 && !submitting.value)

const statusMeta: Record<SupporterFeedbackStatus, { label: string; type: 'warning' | 'success' | 'info'; icon: any }> = {
  OPEN: { label: '等待回复', type: 'warning', icon: Clock },
  ANSWERED: { label: '已回复', type: 'success', icon: Check },
  CLOSED: { label: '已结束', type: 'info', icon: Check },
}

const formatTime = (value?: string | null) => value
  ? new Date(value).toLocaleString('zh-CN', { hour12: false })
  : '—'

const errorMessage = (error: any, fallback: string) =>
  error?.response?.data?.message || error?.message || fallback

const load = async () => {
  loading.value = true
  loadError.value = ''
  accessDenied.value = false
  try {
    const res = await supporterFeedbackApi.mine(page.value, PAGE_SIZE)
    if (res.code !== 2000 || !res.data) throw new Error(res.message || '反馈记录加载失败')
    records.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } catch (error: any) {
    const code = Number(error?.response?.data?.code)
    accessDenied.value = code === 3003
    records.value = []
    total.value = 0
    if (!accessDenied.value) loadError.value = errorMessage(error, '反馈记录加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!canSubmit.value) {
    ElMessage.warning('主题至少 4 字，反馈内容至少 10 字')
    return
  }
  submitting.value = true
  try {
    const res = await supporterFeedbackApi.create(form.value.subject.trim(), form.value.content.trim())
    if (res.code !== 2000) throw new Error(res.message || '反馈提交失败')
    ElMessage.success('反馈已进入共建队列')
    form.value = { subject: '', content: '' }
    page.value = 1
    await load()
  } catch (error: any) {
    ElMessage.error(errorMessage(error, '反馈提交失败，请稍后重试'))
  } finally {
    submitting.value = false
  }
}

const changePage = (next: number) => {
  page.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <section class="feedback-panel" aria-labelledby="feedback-title">
    <header class="panel-heading">
      <div class="heading-icon" aria-hidden="true"><el-icon><ChatDotRound /></el-icon></div>
      <div>
        <p>CO-BUILDER CHANNEL</p>
        <h2 id="feedback-title">共建反馈专属通道</h2>
        <span>面向有效的 Zens 共建支持者。每条反馈都会保留处理状态与官方回复。</span>
      </div>
    </header>

    <div v-if="accessDenied" class="feedback-state" role="status">
      <strong>当前账号暂不可使用</strong>
      <p>此通道仅向有效期内的 Zens 共建支持者开放，普通支持者权益不受影响。</p>
    </div>

    <template v-else>
      <form class="feedback-form" @submit.prevent="submit">
        <label for="feedback-subject">反馈主题</label>
        <el-input
          id="feedback-subject"
          v-model="form.subject"
          maxlength="100"
          show-word-limit
          placeholder="用一句话概括你希望改善的问题"
          :disabled="submitting"
        />
        <small :class="{ invalid: subjectLength > 0 && subjectLength < 4 }">4—100 字</small>

        <label for="feedback-content">具体内容</label>
        <el-input
          id="feedback-content"
          v-model="form.content"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          resize="vertical"
          placeholder="请描述使用场景、遇到的问题、期望结果；越具体越容易被准确处理。"
          :disabled="submitting"
        />
        <small :class="{ invalid: contentLength > 0 && contentLength < 10 }">10—2000 字，请勿提交密码、身份证或支付凭据</small>

        <div class="submit-row">
          <p>每个账号每小时最多提交 3 条，避免重复提交同一问题。</p>
          <el-button native-type="submit" type="primary" round :loading="submitting" :disabled="!canSubmit">
            提交共建反馈
          </el-button>
        </div>
      </form>

      <div class="history-heading">
        <div><strong>我的反馈</strong><span>{{ total }} 条记录</span></div>
        <el-button text :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button>
      </div>

      <div v-if="loading" class="feedback-list" aria-busy="true" aria-label="正在加载反馈记录">
        <div v-for="index in 2" :key="index" class="feedback-card skeleton">
          <i></i><i></i><i></i>
        </div>
      </div>

      <div v-else-if="loadError" class="feedback-state error" role="alert">
        <strong>记录加载失败</strong><p>{{ loadError }}</p>
        <el-button round @click="load">重新加载</el-button>
      </div>

      <div v-else-if="records.length === 0" class="feedback-state">
        <strong>还没有共建反馈</strong>
        <p>你提交的建议和官方回复会在这里形成可追踪的记录。</p>
      </div>

      <div v-else class="feedback-list" aria-live="polite">
        <article v-for="item in records" :key="item.id" class="feedback-card">
          <div class="card-topline">
            <span>#{{ item.id }} · {{ formatTime(item.createdAt) }}</span>
            <el-tag :type="statusMeta[item.status]?.type || 'info'" effect="plain" round>
              <el-icon><component :is="statusMeta[item.status]?.icon || Clock" /></el-icon>
              {{ statusMeta[item.status]?.label || item.status }}
            </el-tag>
          </div>
          <h3>{{ item.subject }}</h3>
          <p class="feedback-content">{{ item.content }}</p>
          <div v-if="item.adminReply" class="official-reply">
            <strong>Zens 官方回复</strong>
            <p>{{ item.adminReply }}</p>
            <span>{{ formatTime(item.repliedAt) }}</span>
          </div>
        </article>
      </div>

      <el-pagination
        v-if="total > PAGE_SIZE"
        class="feedback-pagination"
        layout="prev, pager, next"
        :current-page="page"
        :page-size="PAGE_SIZE"
        :total="total"
        background
        @current-change="changePage"
      />
    </template>
  </section>
</template>

<style scoped>
.feedback-panel { margin-top:28px; padding:30px; border:1px solid #dce6e1; border-radius:26px 9px 26px 26px; background:#fbfdfc; color:#21342e; }
.panel-heading { display:flex; gap:15px; align-items:flex-start; padding-bottom:23px; border-bottom:1px solid #e2e9e6; }.heading-icon { display:grid; flex:none; width:45px; height:45px; place-items:center; border-radius:50%; background:#294d43; color:#fff; }.panel-heading p { margin:0 0 5px; color:#4f7569; font-size:10px; font-weight:800; letter-spacing:.14em; }.panel-heading h2 { margin:0; font-size:24px; letter-spacing:-.03em; }.panel-heading span { display:block; margin-top:7px; color:#687771; font-size:13px; line-height:1.6; }
.feedback-form { display:grid; grid-template-columns:1fr auto; gap:7px 12px; margin-top:24px; padding:23px; border:1px solid #e1e8e5; border-radius:17px; background:#fff; }.feedback-form label { grid-column:1/-1; margin-top:8px; font-size:13px; font-weight:750; }.feedback-form :deep(.el-input),.feedback-form :deep(.el-textarea) { grid-column:1/-1; }.feedback-form small { grid-column:1/-1; color:#89948f; font-size:11px; }.feedback-form small.invalid { color:#b5473d; }.submit-row { grid-column:1/-1; display:flex; justify-content:space-between; gap:18px; align-items:center; margin-top:15px; }.submit-row p { margin:0; color:#7a8782; font-size:11px; line-height:1.5; }.submit-row :deep(.el-button) { background:#294d43; border-color:#294d43; }.submit-row :deep(.el-button:not(.is-disabled):active) { transform:scale(.98); }.submit-row :deep(.el-button:focus-visible) { outline:3px solid rgba(41,77,67,.25); outline-offset:2px; }
.history-heading { display:flex; justify-content:space-between; align-items:center; margin:28px 0 12px; }.history-heading > div { display:flex; gap:10px; align-items:baseline; }.history-heading span { color:#89948f; font-size:12px; }
.feedback-list { display:flex; flex-direction:column; gap:12px; }.feedback-card { padding:20px; border:1px solid #e0e7e4; border-radius:16px; background:#fff; }.card-topline { display:flex; justify-content:space-between; gap:15px; align-items:center; color:#89948f; font-size:11px; }.card-topline :deep(.el-tag) { display:inline-flex; gap:4px; align-items:center; }.feedback-card h3 { margin:14px 0 7px; font-size:16px; }.feedback-content { margin:0; white-space:pre-wrap; overflow-wrap:anywhere; color:#596963; font-size:13px; line-height:1.75; }.official-reply { margin-top:17px; padding:15px 17px; border-left:3px solid #52776c; border-radius:4px 12px 12px 4px; background:#f0f5f2; }.official-reply strong { color:#294d43; font-size:12px; }.official-reply p { margin:7px 0; white-space:pre-wrap; overflow-wrap:anywhere; color:#43564f; font-size:13px; line-height:1.7; }.official-reply span { color:#82908b; font-size:10px; }
.feedback-state { padding:36px 20px; text-align:center; }.feedback-state strong { font-size:17px; }.feedback-state p { margin:8px auto 0; max-width:480px; color:#75827d; font-size:13px; line-height:1.65; }.feedback-state.error { margin-top:20px; border-radius:14px; background:#fff6f4; }.feedback-state .el-button { margin-top:15px; }.feedback-pagination { justify-content:center; margin-top:20px; }
.skeleton i { display:block; height:11px; margin-bottom:12px; border-radius:8px; background:#edf2ef; }.skeleton i:first-child { width:30%; }.skeleton i:nth-child(2) { width:58%; height:18px; }.skeleton i:last-child { width:88%; }
@media (max-width:640px) { .feedback-panel { padding:22px 15px; }.panel-heading { align-items:center; }.feedback-form { padding:18px 15px; }.submit-row { align-items:stretch; flex-direction:column; }.submit-row .el-button { width:100%; }.card-topline { align-items:flex-start; flex-direction:column; gap:8px; } }
@media (prefers-reduced-motion:reduce) { .submit-row :deep(.el-button) { transition:none; } }
</style>
