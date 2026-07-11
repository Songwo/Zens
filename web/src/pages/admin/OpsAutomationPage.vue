<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  CircleClose,
  Document,
  Lock,
  Refresh,
  SwitchButton,
  Warning,
} from '@element-plus/icons-vue'
import { opsAdminApi, type OpsAutomationStatus, type OpsDraft } from '@/api/opsAdmin'
import { renderAsync } from '@/utils/markdownRenderer'

const statusLoading = ref(false)
const draftsLoading = ref(false)
const actionDraftId = ref<string | number | null>(null)
const circuitLoading = ref(false)
const statusError = ref('')
const draftsError = ref('')
const automationStatus = ref<OpsAutomationStatus | null>(null)
const drafts = ref<OpsDraft[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const statusFilter = ref('PENDING_APPROVAL')
const selectedDraftId = ref<string | number | null>(null)
const previewHtml = ref('')
const previewLoading = ref(false)
const lastRefreshedAt = ref('')
let previewRequestId = 0

const statusOptions = [
  { label: '待审批', value: 'PENDING_APPROVAL' },
  { label: '待提交', value: 'CREATED' },
  { label: '已批准', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '全部', value: '' },
]

const selectedDraft = computed(() => drafts.value.find(item => String(item.id) === String(selectedDraftId.value)) || null)

const circuitOpen = computed(() => {
  return automationStatus.value?.circuitOpen ?? false
})

const circuitReason = computed(() => {
  return toText(automationStatus.value?.circuitReason) || '暂无操作备注'
})

const publishedToday = computed(() => automationStatus.value?.todayPublishCount ?? 0)
const publishLimit = computed(() => automationStatus.value?.dailyPublishLimit ?? 1)
const repliesToday = computed(() => automationStatus.value?.todayReplyCount ?? 0)
const replyLimit = computed(() => automationStatus.value?.dailyReplyLimit ?? 10)
const firstApprovalCount = computed(() => automationStatus.value?.firstApprovalCount ?? 30)

const selectedContent = computed(() => {
  const row = selectedDraft.value
  return toText(row?.content ?? row?.markdown ?? row?.markdownContent ?? row?.body)
})

const selectedMetadata = computed(() => {
  const row = selectedDraft.value
  const parsed = parseMetadata(row?.metadataJson ?? row?.metadata ?? row?.meta)
  return Object.entries(parsed).filter(([, value]) => value !== undefined && value !== null && value !== '')
})

const selectedRiskFlags = computed(() => {
  const row = selectedDraft.value
  if (!row) return []
  const meta = parseMetadata(row.metadataJson ?? row.metadata ?? row.meta)
  const candidates = [row.riskFlags, row.risks, row.flags, meta.riskFlags, meta.risks, meta.flags]
  const values = candidates.flatMap(normalizeFlagList)
  return [...new Set(values.map(item => item.trim()).filter(Boolean))]
})

const isSelectedActionable = computed(() => {
  const status = normalizeStatus(selectedDraft.value?.status)
  return status === 'PENDING_APPROVAL' || status === 'PENDING' || status === 'DRAFT' || status === 'AWAITING_REVIEW' || status === 'REVIEW_PENDING'
})

const isSelectedApproved = computed(() => normalizeStatus(selectedDraft.value?.status) === 'APPROVED')

async function fetchStatus() {
  statusLoading.value = true
  statusError.value = ''
  try {
    const response = await opsAdminApi.getStatus()
    automationStatus.value = response.data || {}
    lastRefreshedAt.value = new Date().toLocaleTimeString()
  } catch (error: any) {
    statusError.value = error?.message || '自动运营状态加载失败'
  } finally {
    statusLoading.value = false
  }
}

async function fetchDrafts(options: { retainSelection?: boolean } = {}) {
  draftsLoading.value = true
  draftsError.value = ''
  try {
    const response = await opsAdminApi.getDrafts({
      status: statusFilter.value || undefined,
      page: page.value,
      size: size.value,
    })
    const normalized = normalizeDraftPage(response.data)
    drafts.value = normalized.items
    total.value = normalized.total
    const selectedStillExists = drafts.value.some(item => String(item.id) === String(selectedDraftId.value))
    if (!options.retainSelection || !selectedStillExists) {
      selectedDraftId.value = drafts.value[0]?.id ?? null
    }
    lastRefreshedAt.value = new Date().toLocaleTimeString()
  } catch (error: any) {
    draftsError.value = error?.message || '草稿列表加载失败'
    drafts.value = []
    total.value = 0
    selectedDraftId.value = null
  } finally {
    draftsLoading.value = false
  }
}

async function refreshAll() {
  await Promise.all([fetchStatus(), fetchDrafts({ retainSelection: true })])
}

function changeFilter() {
  page.value = 1
  selectedDraftId.value = null
  void fetchDrafts()
}

function changePage(nextPage: number) {
  page.value = nextPage
  void fetchDrafts()
}

async function approveDraft() {
  const draft = selectedDraft.value
  if (!draft || !isSelectedActionable.value) return
  try {
    const result = await ElMessageBox.prompt(
      `确认批准「${draftTitle(draft)}」？批准后系统才可按计划发布。`,
      '人工审批确认',
      {
        type: 'warning',
        confirmButtonText: '确认批准',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '填写审批依据或修改建议（选填，最多 500 字）',
        inputValidator: value => String(value || '').trim().length <= 500 || '审批备注不能超过 500 个字符',
      },
    )
    actionDraftId.value = draft.id
    await opsAdminApi.approveDraft(draft.id, String((result as any).value || '').trim())
    ElMessage.success('草稿已批准，操作已写入审计记录')
    await Promise.all([fetchStatus(), fetchDrafts()])
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '批准草稿失败')
  } finally {
    actionDraftId.value = null
  }
}

async function rejectDraft() {
  const draft = selectedDraft.value
  if (!draft || !isSelectedActionable.value) return
  try {
    const result = await ElMessageBox.prompt(
      `确认拒绝「${draftTitle(draft)}」？该草稿不会进入发布队列。`,
      '拒绝草稿确认',
      {
        type: 'warning',
        confirmButtonText: '确认拒绝',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '请填写拒绝原因或修改方向',
        inputValidator: value => {
          const note = String(value || '').trim()
          if (!note) return '拒绝原因不能为空'
          if (note.length > 500) return '拒绝原因不能超过 500 个字符'
          return true
        },
      },
    )
    actionDraftId.value = draft.id
    await opsAdminApi.rejectDraft(draft.id, String((result as any).value || '').trim())
    ElMessage.success('草稿已拒绝，操作已写入审计记录')
    await Promise.all([fetchStatus(), fetchDrafts()])
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '拒绝草稿失败')
  } finally {
    actionDraftId.value = null
  }
}

async function publishDraft() {
  const draft = selectedDraft.value
  if (!draft || !isSelectedApproved.value || circuitOpen.value) return
  try {
    await ElMessageBox.confirm(
      `确认将「${draftTitle(draft)}」以官方账号“Zens运营”的身份立即对外发布？发布成功后内容将对社区用户可见。`,
      '人工发布确认',
      {
        type: 'warning',
        confirmButtonText: '确认以 Zens运营 发布',
        cancelButtonText: '取消',
        distinguishCancelAndClose: true,
      },
    )
    actionDraftId.value = draft.id
    const publishAttemptKey = `admin-publish:${draft.id}:${crypto.randomUUID()}`
    await opsAdminApi.publishDraft(draft.id, publishAttemptKey)
    ElMessage.success('内容已由 Zens运营 对外发布，操作已写入审计记录')
    await Promise.all([fetchStatus(), fetchDrafts()])
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '人工发布失败')
  } finally {
    actionDraftId.value = null
  }
}

async function toggleCircuit() {
  const nextOpen = !circuitOpen.value
  try {
    const result = await ElMessageBox.prompt(
      nextOpen
        ? '打开熔断后，自动发布和自动回复将立即停止，已生成草稿仍可人工查看。'
        : '关闭熔断后，系统会恢复已配置的自动任务，但仍受审批和每日限额约束。',
      nextOpen ? '打开运营熔断' : '恢复自动运营',
      {
        type: nextOpen ? 'warning' : 'info',
        confirmButtonText: nextOpen ? '确认停止自动写入' : '确认恢复',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '请输入本次变更原因（必填）',
        inputValidator: value => {
          const reason = String(value || '').trim()
          if (!reason) return '变更原因不能为空'
          if (reason.length > 500) return '变更原因不能超过 500 个字符'
          return true
        },
      },
    )
    circuitLoading.value = true
    await opsAdminApi.setCircuit(nextOpen, String((result as any).value || '').trim())
    ElMessage.success(nextOpen ? '运营熔断已打开' : '自动运营已恢复')
    await fetchStatus()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '熔断状态更新失败')
  } finally {
    circuitLoading.value = false
  }
}

function selectDraft(draft: OpsDraft) {
  selectedDraftId.value = draft.id
}

function draftTitle(draft: OpsDraft) {
  return toText(draft.title) || `未命名草稿 #${draft.id}`
}

function draftType(draft: OpsDraft) {
  return toText(draft.type ?? draft.draftType ?? draft.contentType) || '原创内容'
}

function draftPlannedAt(draft: OpsDraft) {
  const metadata = parseMetadata(draft.metadataJson ?? draft.metadata ?? draft.meta)
  return formatDateTime(
    draft.plannedAt
      ?? draft.scheduledAt
      ?? draft.publishAt
      ?? draft.plannedPublishAt
      ?? metadata.scheduledAt
      ?? metadata.publishAt
      ?? draft.createdAt
      ?? draft.createTime,
  )
}

function normalizeStatus(value: unknown) {
  return toText(value).toUpperCase().replace(/[-\s]+/g, '_') || 'PENDING'
}

function statusLabel(value: unknown) {
  const status = normalizeStatus(value)
  const labels: Record<string, string> = {
    PENDING: '待审批',
    PENDING_APPROVAL: '待审批',
    CREATED: '待提交',
    DRAFT: '待审批',
    AWAITING_REVIEW: '待审批',
    REVIEW_PENDING: '待审批',
    APPROVED: '已批准',
    REJECTED: '已拒绝',
    PUBLISHED: '已发布',
    FAILED: '执行失败',
  }
  return labels[status] || toText(value) || '待审批'
}

function statusTagType(value: unknown) {
  const status = normalizeStatus(value)
  if (status === 'APPROVED' || status === 'PUBLISHED') return 'success'
  if (status === 'REJECTED' || status === 'FAILED') return 'danger'
  return 'warning'
}

function formatDateTime(value: unknown) {
  const text = toText(value)
  if (!text) return '未设置'
  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return text.replace('T', ' ').slice(0, 16)
  return date.toLocaleString()
}

function asRecord(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : {}
}

function toText(value: unknown) {
  return value === undefined || value === null ? '' : String(value)
}

function toBoolean(value: unknown, fallback: boolean) {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') {
    if (['true', '1', 'open', 'opened', 'on'].includes(value.toLowerCase())) return true
    if (['false', '0', 'closed', 'off'].includes(value.toLowerCase())) return false
  }
  return fallback
}

function toNumber(value: unknown, fallback: number) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function normalizeDraftPage(payload: unknown): { items: OpsDraft[]; total: number } {
  if (Array.isArray(payload)) return { items: payload as OpsDraft[], total: payload.length }
  const pageData = asRecord(payload)
  const candidates = [pageData.records, pageData.items, pageData.content, pageData.list, pageData.rows]
  const items = candidates.find(Array.isArray) as OpsDraft[] | undefined
  return {
    items: items || [],
    total: toNumber(pageData.total ?? pageData.totalElements ?? pageData.count, items?.length || 0),
  }
}

function parseMetadata(value: unknown): Record<string, any> {
  if (typeof value === 'string') {
    try {
      return asRecord(JSON.parse(value))
    } catch {
      return value.trim() ? { metadata: value } : {}
    }
  }
  return asRecord(value)
}

function normalizeFlagList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.flatMap(item => typeof item === 'object' ? [toText(asRecord(item).label ?? asRecord(item).name ?? JSON.stringify(item))] : [toText(item)])
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return []
    try {
      return normalizeFlagList(JSON.parse(trimmed))
    } catch {
      return trimmed.split(/[,，;；|]/)
    }
  }
  if (value && typeof value === 'object') {
    return Object.entries(asRecord(value)).filter(([, enabled]) => toBoolean(enabled, Boolean(enabled))).map(([key]) => key)
  }
  return []
}

function displayMetadataValue(value: unknown) {
  if (typeof value === 'object' && value !== null) {
    try { return JSON.stringify(value) } catch { return String(value) }
  }
  return toText(value)
}

function hardenPreviewHtml(html: string) {
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img').forEach(image => {
    const raw = image.getAttribute('src') || ''
    try {
      const url = new URL(raw, window.location.origin)
      const trusted = url.origin === window.location.origin || url.hostname === 'media.allinsong.top'
      if (!trusted || url.protocol !== 'https:') {
        image.removeAttribute('src')
        image.setAttribute('alt', `${image.getAttribute('alt') || '外部图片'}（预览已阻止加载）`)
      } else {
        image.setAttribute('loading', 'lazy')
        image.setAttribute('referrerpolicy', 'no-referrer')
      }
    } catch {
      image.removeAttribute('src')
    }
  })
  template.content.querySelectorAll('a').forEach(link => {
    link.setAttribute('rel', 'noopener noreferrer nofollow')
  })
  return template.innerHTML
}

watch(selectedContent, async content => {
  const requestId = ++previewRequestId
  previewHtml.value = ''
  if (!content) return
  previewLoading.value = true
  try {
    const html = await renderAsync(content)
    if (requestId !== previewRequestId) return
    const sanitized = DOMPurify.sanitize(html, {
      ALLOWED_TAGS: [
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'br', 'hr', 'ul', 'ol', 'li',
        'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
        'a', 'img', 'strong', 'em', 'del', 's', 'mark', 'kbd', 'sup', 'sub',
        'details', 'summary', 'div', 'span', 'button',
      ],
      ALLOWED_ATTR: [
        'href', 'src', 'alt', 'title', 'class', 'id', 'target', 'rel', 'loading', 'decoding', 'referrerpolicy',
        'type', 'aria-label', 'aria-live', 'role', 'tabindex', 'style', 'data-lang',
        'data-line', 'data-highlighted-chars', 'data-highlighted-chars-id',
      ],
      ALLOW_DATA_ATTR: false,
    })
    previewHtml.value = hardenPreviewHtml(sanitized)
  } catch {
    if (requestId === previewRequestId) previewHtml.value = ''
  } finally {
    if (requestId === previewRequestId) previewLoading.value = false
  }
}, { immediate: true })

onMounted(() => {
  void refreshAll()
})
</script>

<template>
  <div class="ops-automation-page">
    <header class="page-header">
      <div>
        <div class="eyebrow">Zens 社区运营控制台</div>
        <h1>自动运营审批</h1>
        <p>只读 Agent 负责研究与选题，所有主站写入都经过限额、审计和人工审批边界。</p>
      </div>
      <div class="header-actions">
        <span v-if="lastRefreshedAt" class="refresh-time" aria-live="polite">上次刷新 {{ lastRefreshedAt }}</span>
        <el-button :icon="Refresh" :loading="statusLoading || draftsLoading" @click="refreshAll">刷新全部</el-button>
      </div>
    </header>

    <el-alert
      class="review-policy"
      type="warning"
      show-icon
      :closable="false"
      title="冷启动人工确认边界"
      :description="`系统配置为前 ${firstApprovalCount} 篇草稿逐篇人工确认；定时任务不会跳过审批，后续仍可按风险策略保留人工门禁。`"
    />

    <section class="status-section" aria-labelledby="ops-runtime-title">
      <div class="section-heading">
        <div>
          <h2 id="ops-runtime-title">运行状态与安全边界</h2>
          <p>每日发布和评论达到上限后自动停止写入；紧急情况下可一键打开熔断。</p>
        </div>
        <el-button
          :type="circuitOpen ? 'success' : 'danger'"
          :plain="!circuitOpen"
          :icon="circuitOpen ? SwitchButton : Lock"
          :loading="circuitLoading"
          @click="toggleCircuit"
        >
          {{ circuitOpen ? '恢复自动运营' : '打开运营熔断' }}
        </el-button>
      </div>

      <div v-if="statusLoading && !automationStatus" class="status-grid" aria-label="正在加载运行状态">
        <el-skeleton v-for="index in 3" :key="index" animated :rows="2" class="metric-card" />
      </div>
      <el-result v-else-if="statusError && !automationStatus" icon="error" title="运行状态加载失败" :sub-title="statusError">
        <template #extra><el-button type="primary" @click="fetchStatus">重新加载</el-button></template>
      </el-result>
      <div v-else class="status-grid" aria-live="polite">
        <article class="metric-card circuit-card" :class="{ open: circuitOpen }">
          <div class="metric-label"><el-icon><Warning /></el-icon>写入熔断</div>
          <strong>{{ circuitOpen ? '已打开' : '正常运行' }}</strong>
          <small>{{ circuitReason }}</small>
        </article>
        <article class="metric-card">
          <div class="metric-label"><el-icon><Document /></el-icon>今日发布</div>
          <strong>{{ publishedToday }} <span>/ {{ publishLimit }}</span></strong>
          <el-progress :percentage="Math.min(100, Math.round((publishedToday / Math.max(1, publishLimit)) * 100))" :show-text="false" />
          <small>达到上限后不再发布新内容</small>
        </article>
        <article class="metric-card">
          <div class="metric-label"><el-icon><Document /></el-icon>今日官方回复</div>
          <strong>{{ repliesToday }} <span>/ {{ replyLimit }}</span></strong>
          <el-progress :percentage="Math.min(100, Math.round((repliesToday / Math.max(1, replyLimit)) * 100))" :show-text="false" />
          <small>只跟进需要澄清或持续讨论的内容</small>
        </article>
      </div>
    </section>

    <section class="workspace" aria-labelledby="draft-review-title">
      <div class="draft-list-panel">
        <div class="panel-header">
          <div>
            <h2 id="draft-review-title">草稿审批队列</h2>
            <p>共 {{ total }} 条记录</p>
          </div>
          <el-select v-model="statusFilter" aria-label="按草稿状态筛选" @change="changeFilter">
            <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>

        <div v-if="draftsLoading" class="draft-skeletons" aria-label="正在加载草稿">
          <el-skeleton v-for="index in 4" :key="index" animated :rows="3" />
        </div>
        <el-result v-else-if="draftsError" icon="error" title="草稿列表加载失败" :sub-title="draftsError">
          <template #extra><el-button type="primary" @click="() => fetchDrafts()">重新加载</el-button></template>
        </el-result>
        <el-empty v-else-if="drafts.length === 0" description="当前筛选条件下没有草稿" :image-size="92" />
        <div v-else class="draft-list" role="listbox" aria-label="自动运营草稿">
          <button
            v-for="draft in drafts"
            :key="draft.id"
            type="button"
            class="draft-item"
            :class="{ selected: String(selectedDraftId) === String(draft.id) }"
            :aria-selected="String(selectedDraftId) === String(draft.id)"
            role="option"
            @click="selectDraft(draft)"
          >
            <span class="draft-item-top">
              <el-tag :type="statusTagType(draft.status)" effect="plain" size="small">{{ statusLabel(draft.status) }}</el-tag>
              <span class="draft-type">{{ draftType(draft) }}</span>
            </span>
            <strong>{{ draftTitle(draft) }}</strong>
            <small>计划时间：{{ draftPlannedAt(draft) }}</small>
            <span v-if="normalizeFlagList(draft.riskFlags ?? draft.risks ?? draft.flags).length" class="inline-risk">
              <el-icon><Warning /></el-icon> 含风险标记
            </span>
          </button>
        </div>

        <el-pagination
          v-if="total > size"
          class="pagination"
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="changePage"
        />
      </div>

      <article class="preview-panel" aria-labelledby="draft-preview-title">
        <el-empty v-if="!selectedDraft" description="选择一篇草稿查看完整内容" :image-size="96" />
        <template v-else>
          <header class="preview-header">
            <div class="preview-title-block">
              <div class="preview-badges">
                <el-tag :type="statusTagType(selectedDraft.status)">{{ statusLabel(selectedDraft.status) }}</el-tag>
                <el-tag effect="plain">{{ draftType(selectedDraft) }}</el-tag>
              </div>
              <h2 id="draft-preview-title">{{ draftTitle(selectedDraft) }}</h2>
              <p>计划时间：{{ draftPlannedAt(selectedDraft) }}</p>
            </div>
            <div v-if="isSelectedActionable" class="review-actions">
              <el-button
                type="danger"
                plain
                :icon="CircleClose"
                :loading="actionDraftId === selectedDraft.id"
                @click="rejectDraft"
              >拒绝</el-button>
              <el-button
                type="success"
                :icon="Check"
                :loading="actionDraftId === selectedDraft.id"
                @click="approveDraft"
              >批准入队</el-button>
            </div>
            <div v-else-if="isSelectedApproved" class="publish-action-wrap">
              <el-tooltip
                :disabled="!circuitOpen"
                content="运营熔断已打开，需先恢复自动运营写入后才能人工发布"
                placement="bottom-end"
              >
                <span class="disabled-button-anchor">
                  <el-button
                    type="primary"
                    :icon="Document"
                    :disabled="circuitOpen"
                    :loading="actionDraftId === selectedDraft.id"
                    aria-label="以 Zens运营 官方账号人工发布当前草稿"
                    @click="publishDraft"
                  >人工发布</el-button>
                </span>
              </el-tooltip>
              <small v-if="circuitOpen" class="circuit-block-hint" role="status">熔断开启中，发布已禁用</small>
            </div>
          </header>

          <div v-if="selectedRiskFlags.length" class="risk-box" role="alert">
            <div><el-icon><Warning /></el-icon><strong>需重点核验</strong></div>
            <el-tag v-for="flag in selectedRiskFlags" :key="flag" type="danger" effect="plain">{{ flag }}</el-tag>
          </div>

          <details v-if="selectedMetadata.length" class="metadata-box">
            <summary>查看生成元数据与审计上下文</summary>
            <dl>
              <template v-for="([key, value]) in selectedMetadata" :key="key">
                <dt>{{ key }}</dt>
                <dd>{{ displayMetadataValue(value) }}</dd>
              </template>
            </dl>
          </details>

          <section class="content-preview" aria-label="Markdown 内容预览" aria-live="polite">
            <div class="content-label">Markdown 最终展示预览</div>
            <el-skeleton v-if="previewLoading" animated :rows="8" />
            <div v-else-if="previewHtml" class="markdown-body" v-html="previewHtml"></div>
            <el-empty v-else description="该草稿暂无正文内容" :image-size="80" />
          </section>
        </template>
      </article>
    </section>
  </div>
</template>

<style scoped>
.ops-automation-page {
  display: grid;
  gap: 18px;
  color: var(--el-text-color-primary);
}

.page-header,
.section-heading,
.panel-header,
.preview-header,
.header-actions,
.review-actions,
.publish-action-wrap,
.preview-badges,
.draft-item-top,
.metric-label,
.risk-box > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-header,
.section-heading,
.panel-header,
.preview-header {
  justify-content: space-between;
}

.page-header {
  align-items: flex-end;
}

.eyebrow {
  margin-bottom: 6px;
  color: var(--cp-primary-dark, var(--el-color-primary));
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1,
h2,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 7px;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

h2 {
  margin-bottom: 5px;
  font-size: 16px;
  font-weight: 900;
}

.page-header p,
.section-heading p,
.panel-header p,
.preview-header p {
  margin-bottom: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.refresh-time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.review-policy {
  border-radius: 8px;
}

.status-section,
.draft-list-panel,
.preview-panel {
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 10px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
}

.status-section {
  padding: 16px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.metric-card {
  min-width: 0;
  padding: 15px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--cp-bg-surface, var(--el-bg-color));
}

.metric-card.open {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}

.metric-label {
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.metric-card strong {
  display: block;
  margin-bottom: 9px;
  font-size: 23px;
  font-weight: 900;
}

.metric-card strong span {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.metric-card small {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(290px, 0.72fr) minmax(0, 1.55fr);
  gap: 14px;
  min-height: 620px;
}

.draft-list-panel,
.preview-panel {
  min-width: 0;
  padding: 15px;
}

.panel-header {
  margin-bottom: 12px;
}

.panel-header .el-select {
  width: 118px;
}

.draft-skeletons,
.draft-list {
  display: grid;
  gap: 8px;
}

.draft-skeletons > * {
  padding: 12px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
}

.draft-item {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--cp-bg-surface, var(--el-bg-color));
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, transform 0.18s ease;
}

.draft-item:hover,
.draft-item.selected {
  border-color: var(--cp-primary, var(--el-color-primary));
  background: var(--el-color-primary-light-9);
}

.draft-item:active {
  transform: scale(0.985);
}

.draft-item:focus-visible,
summary:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.draft-item-top {
  justify-content: space-between;
  margin-bottom: 8px;
}

.draft-type {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.draft-item strong {
  display: -webkit-box;
  overflow: hidden;
  margin-bottom: 7px;
  font-size: 14px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.draft-item small,
.inline-risk {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.inline-risk {
  margin-top: 7px;
  color: var(--el-color-danger);
  font-weight: 700;
}

.pagination {
  justify-content: center;
  margin-top: 14px;
}

.preview-panel {
  overflow: hidden;
}

.preview-header {
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--cp-divider, var(--el-border-color-lighter));
}

.preview-title-block {
  min-width: 0;
}

.preview-title-block h2 {
  margin-top: 10px;
  margin-bottom: 5px;
  font-size: 20px;
  line-height: 1.45;
}

.review-actions {
  flex-shrink: 0;
}

.publish-action-wrap {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: 6px;
}

.disabled-button-anchor {
  display: inline-flex;
}

.circuit-block-hint {
  color: var(--el-color-danger);
  font-size: 11px;
  font-weight: 700;
}

.risk-box {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
}

.metadata-box {
  margin-top: 14px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--cp-bg-surface, var(--el-bg-color));
}

.metadata-box summary {
  padding: 11px 13px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.metadata-box dl {
  display: grid;
  grid-template-columns: minmax(100px, 0.3fr) minmax(0, 1fr);
  margin: 0;
  padding: 0 13px 13px;
  font-size: 12px;
}

.metadata-box dt,
.metadata-box dd {
  margin: 0;
  padding: 7px 0;
  border-top: 1px dashed var(--cp-border, var(--el-border-color-lighter));
  overflow-wrap: anywhere;
}

.metadata-box dt {
  color: var(--el-text-color-secondary);
  font-weight: 700;
}

.content-preview {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--cp-bg-surface, var(--el-bg-color));
}

.content-label {
  margin-bottom: 16px;
  padding-bottom: 9px;
  border-bottom: 1px solid var(--cp-divider, var(--el-border-color-lighter));
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.content-preview :deep(.markdown-body) {
  max-width: 78ch;
}

@media (max-width: 1120px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .draft-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-header,
  .section-heading,
  .preview-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions,
  .review-actions {
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .status-grid,
  .draft-list {
    grid-template-columns: 1fr;
  }

  .metadata-box dl {
    grid-template-columns: 1fr;
  }

  .metadata-box dd {
    padding-top: 0;
    border-top: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .draft-item {
    transition: none;
  }
}
</style>
