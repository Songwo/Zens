<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Connection, Cpu, MagicStick, Monitor, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { agentApi, type AgentAdminStatus, type AgentSmokeTestResult } from '@/api/agent'
import { ResultCode } from '@/types'

const loading = ref(false)
const autoRefresh = ref(false)
const lastRefreshAt = ref('')
const status = ref<AgentAdminStatus | null>(null)
const smokeQuestion = ref('deepseek')
const smokeLoading = ref(false)
const smokeResult = ref<AgentSmokeTestResult | null>(null)

let refreshTimer: number | null = null

const health = computed(() => status.value?.health || null)
const serviceOnline = computed(() => {
  const current = status.value
  if (!current?.enabled || !current.reachable) return false
  const s = String(current.status || '').toLowerCase()
  return s === 'ok' || s === 'healthy' || s === 'up'
})
const serviceTagType = computed(() => (serviceOnline.value ? 'success' : status.value?.enabled ? 'danger' : 'warning'))
const serviceTagText = computed(() => {
  if (!status.value?.enabled) return '未启用'
  return serviceOnline.value ? '在线' : '不可用'
})

const llmStatus = computed(() =>
  String(status.value?.llmStatus || health.value?.llm_status || 'unknown')
)
const backendName = computed(() =>
  String(status.value?.backend || health.value?.backend || 'unknown')
)
const statusCards = computed(() => [
  {
    label: 'Java 网关',
    value: status.value?.enabled ? '已启用' : '未启用',
    detail: status.value?.baseUrl || '-',
    type: status.value?.enabled ? 'success' : 'warning',
    icon: Connection,
  },
  {
    label: 'Python Agent',
    value: status.value?.reachable ? '可达' : '不可达',
    detail: status.value?.latencyMs != null ? `${status.value.latencyMs} ms` : status.value?.error || '-',
    type: serviceOnline.value ? 'success' : 'danger',
    icon: Monitor,
  },
  {
    label: '检索后端',
    value: backendName.value,
    detail: `MySQL ${status.value?.mysql || health.value?.mysql || '-'} / PostgreSQL ${status.value?.postgres || health.value?.postgres || '-'}`,
    type: statusType(status.value?.mysql || health.value?.mysql || status.value?.postgres || health.value?.postgres),
    icon: Search,
  },
  {
    label: 'LLM',
    value: llmStatusLabel(llmStatus.value),
    detail: String(status.value?.llmModel || health.value?.llm_model || '兜底回答'),
    type: llmStatus.value === 'ready' ? 'success' : llmStatus.value === 'missing_api_key' ? 'warning' : 'info',
    icon: Cpu,
  },
])

const configRows = computed(() => [
  { label: 'Agent Base URL', value: status.value?.baseUrl || '-' },
  { label: '连接超时', value: `${status.value?.connectTimeoutMs ?? '-'} ms` },
  { label: '读取超时', value: `${status.value?.readTimeoutMs ?? '-'} ms` },
  { label: '服务运行时长', value: formatUptime(Number(health.value?.uptime_seconds || 0)) },
  { label: '默认检索条数', value: String(health.value?.default_search_limit ?? '-') },
  { label: '最短问题长度', value: String(health.value?.min_question_length ?? '-') },
])

const dependencyRows = computed(() => [
  { label: 'MySQL', value: status.value?.mysql || health.value?.mysql || '-', type: statusType(status.value?.mysql || health.value?.mysql) },
  { label: 'PostgreSQL', value: status.value?.postgres || health.value?.postgres || '-', type: statusType(status.value?.postgres || health.value?.postgres) },
  { label: 'Search Backend', value: health.value?.search_backend || backendName.value, type: 'info' },
  { label: 'LLM Enabled', value: formatBool(Boolean(status.value?.llmEnabled || health.value?.llm_enabled)), type: status.value?.llmEnabled || health.value?.llm_enabled ? 'success' : 'info' },
  { label: 'LLM Configured', value: formatBool(Boolean(status.value?.llmConfigured || health.value?.llm_configured)), type: status.value?.llmConfigured || health.value?.llm_configured ? 'success' : 'warning' },
])

const fetchStatus = async () => {
  loading.value = true
  try {
    const res = await agentApi.adminStatus()
    if (res.code !== ResultCode.SUCCESS || !res.data) {
      ElMessage.error(res.message || '拉取 Agent 状态失败')
      return
    }
    status.value = res.data
    lastRefreshAt.value = new Date().toLocaleTimeString()
  } catch (err: any) {
    ElMessage.error(err?.message || '拉取 Agent 状态失败')
  } finally {
    loading.value = false
  }
}

const runSmokeTest = async () => {
  const question = smokeQuestion.value.trim()
  if (!question) {
    ElMessage.warning('请输入烟测关键词')
    return
  }
  smokeLoading.value = true
  try {
    const res = await agentApi.adminSmokeTest({
      question,
      retrievalQuery: question,
      limit: 6,
      includeComments: true,
      commentsPerPost: 2,
    })
    if (res.code !== ResultCode.SUCCESS || !res.data) {
      ElMessage.error(res.message || 'Agent 烟测失败')
      return
    }
    smokeResult.value = res.data
    if (res.data.ok) {
      ElMessage.success('Agent 烟测完成')
    } else {
      ElMessage.warning('Agent 烟测未通过')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || 'Agent 烟测失败')
  } finally {
    smokeLoading.value = false
  }
}

const toggleAutoRefresh = (value: boolean) => {
  autoRefresh.value = value
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (value) {
    refreshTimer = window.setInterval(fetchStatus, 8000)
  }
}

const statusType = (value: unknown) => {
  const v = String(value || '').toLowerCase()
  if (v === 'ok' || v === 'up' || v === 'ready') return 'success'
  if (v === 'down' || v === 'error' || v === 'missing_api_key') return 'danger'
  if (v === 'disabled' || v === 'not_configured') return 'info'
  return 'warning'
}

const llmStatusLabel = (value: string) => {
  if (value === 'ready') return '可用'
  if (value === 'disabled') return '未启用'
  if (value === 'missing_api_key') return '缺少 Key'
  return value || '-'
}

const formatBool = (value: boolean) => (value ? '是' : '否')

const formatUptime = (seconds: number) => {
  if (!seconds) return '-'
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return `${hours} 小时 ${rest} 分`
}

onMounted(fetchStatus)
onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})
</script>

<template>
  <div class="agent-status-page" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <h1 class="title">Agent 服务状态</h1>
        <el-tag :type="serviceTagType" round>{{ serviceTagText }}</el-tag>
        <span v-if="status?.checkedAt" class="service-meta">检测：{{ new Date(status.checkedAt).toLocaleString() }}</span>
      </div>
      <div class="header-right">
        <span v-if="lastRefreshAt" class="refresh-meta">上次刷新：{{ lastRefreshAt }}</span>
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          @change="(value: string | number | boolean) => toggleAutoRefresh(Boolean(value))"
        />
        <el-button type="primary" :icon="Refresh" @click="fetchStatus">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="status && !serviceOnline"
      class="warn-alert"
      type="warning"
      show-icon
      :closable="false"
      title="Agent 服务当前不可稳定使用"
      :description="status.error || '请查看下方依赖状态和建议动作。'"
    />

    <div class="status-grid">
      <div v-for="item in statusCards" :key="item.label" class="status-card">
        <div class="status-head">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <el-tag :type="item.type as any" size="small" effect="plain">{{ item.value }}</el-tag>
        </div>
        <div class="status-detail">{{ item.detail }}</div>
      </div>
    </div>

    <div class="panel-grid">
      <section class="ops-panel">
        <div class="panel-title">
          <el-icon><Connection /></el-icon>
          <span>运行配置</span>
        </div>
        <div class="info-list">
          <div v-for="item in configRows" :key="item.label" class="info-row">
            <span>{{ item.label }}</span>
            <code>{{ item.value }}</code>
          </div>
        </div>
      </section>

      <section class="ops-panel">
        <div class="panel-title">
          <el-icon><CircleCheck /></el-icon>
          <span>依赖健康</span>
        </div>
        <div class="info-list">
          <div v-for="item in dependencyRows" :key="item.label" class="info-row">
            <span>{{ item.label }}</span>
            <el-tag :type="item.type as any" effect="plain" size="small">{{ item.value }}</el-tag>
          </div>
        </div>
      </section>
    </div>

    <section class="ops-panel smoke-panel">
      <div class="panel-title-row">
        <div class="panel-title">
          <el-icon><MagicStick /></el-icon>
          <span>问答烟测</span>
        </div>
        <div class="smoke-actions">
          <el-input v-model="smokeQuestion" placeholder="输入关键词或问题" clearable />
          <el-button type="primary" :loading="smokeLoading" :icon="MagicStick" @click="runSmokeTest">运行</el-button>
        </div>
      </div>

      <div v-if="smokeResult" class="smoke-result">
        <div class="smoke-summary">
          <el-tag :type="smokeResult.ok ? 'success' : 'danger'" effect="plain">
            {{ smokeResult.ok ? '通过' : '失败' }}
          </el-tag>
          <span>{{ smokeResult.latencyMs }} ms</span>
          <span>{{ smokeResult.question }}</span>
        </div>
        <div v-if="smokeResult.response" class="answer-preview">
          <div class="answer-text">{{ smokeResult.response.answer || '无回答内容' }}</div>
          <div class="trace-row">
            <span>backend: {{ smokeResult.response.trace?.backend || smokeResult.response.backend || '-' }}</span>
            <span>hits: {{ smokeResult.response.trace?.hit_count ?? '-' }}</span>
            <span v-if="smokeResult.response.fallback_reason">fallback: {{ smokeResult.response.fallback_reason }}</span>
          </div>
        </div>
        <el-alert
          v-else
          type="error"
          :closable="false"
          :title="smokeResult.error || '烟测失败'"
          show-icon
        />
      </div>
      <div v-else class="empty-hint">尚未运行烟测</div>
    </section>

    <section v-if="status?.advice?.length" class="ops-panel advice-panel">
      <div class="panel-title">
        <el-icon><Warning /></el-icon>
        <span>建议动作</span>
      </div>
      <ul class="advice-list">
        <li v-for="item in status.advice" :key="item">{{ item }}</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.agent-status-page {
  display: grid;
  gap: 14px;
}

.page-header,
.panel-title-row,
.status-head,
.smoke-summary,
.trace-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-header {
  justify-content: space-between;
}

.header-left,
.header-right,
.smoke-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 22px;
  font-weight: 900;
}

.service-meta,
.refresh-meta,
.empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.warn-alert {
  border-radius: 8px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card,
.ops-panel {
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  padding: 14px;
}

.status-head {
  justify-content: space-between;
  color: var(--el-text-color-primary);
  font-weight: 800;
}

.status-head .el-icon {
  color: var(--cp-primary-dark, var(--el-color-primary));
}

.status-detail {
  margin-top: 10px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.panel-title,
.panel-title-row {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 900;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.panel-title-row {
  justify-content: space-between;
  margin-bottom: 12px;
}

.info-list {
  display: grid;
  gap: 8px;
}

.info-row {
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px dashed var(--cp-border, var(--el-border-color-lighter));
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.info-row:last-child {
  border-bottom: 0;
}

.info-row code {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.smoke-actions .el-input {
  width: 260px;
}

.smoke-result {
  display: grid;
  gap: 10px;
}

.smoke-summary {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 700;
}

.answer-preview {
  border: 1px solid var(--cp-border, var(--el-border-color-lighter));
  border-radius: 8px;
  background: var(--cp-bg-surface, var(--el-bg-color));
  padding: 12px;
}

.answer-text {
  display: -webkit-box;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  line-clamp: 4;
  white-space: pre-line;
}

.trace-row {
  margin-top: 10px;
  flex-wrap: wrap;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.advice-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.8;
}

@media (max-width: 980px) {
  .status-grid,
  .panel-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .panel-title-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-right,
  .smoke-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .smoke-actions .el-input {
    width: 100%;
  }
}
</style>
