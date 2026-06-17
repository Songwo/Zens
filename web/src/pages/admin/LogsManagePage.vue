<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Download, FolderOpened, Reading, Refresh, Search, Timer } from '@element-plus/icons-vue'
import {
  logAdminApi,
  type LogCleanupResult,
  type LogContent,
  type LogFileSummary,
  type LogOverview,
} from '@/api/logAdmin'
import { ResultCode } from '@/types'

const overviewLoading = ref(false)
const filesLoading = ref(false)
const contentLoading = ref(false)
const backupLoading = ref(false)
const cleanupLoading = ref(false)
const deletingFile = ref('')

const overview = ref<LogOverview | null>(null)
const files = ref<LogFileSummary[]>([])
const selectedFile = ref('')
const keyword = ref('')
const previewMode = ref<'head' | 'tail'>('tail')
const previewLines = ref(200)
const content = ref<LogContent | null>(null)
const autoRefresh = ref(false)

let autoRefreshTimer: number | null = null

const selectedMeta = computed(() => {
  return files.value.find(item => item.relativePath === selectedFile.value) || null
})

const canAutoRefresh = computed(() => {
  return Boolean(selectedMeta.value?.active && selectedMeta.value?.previewTailSupported && previewMode.value === 'tail')
})

const statCards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    {
      key: 'total',
      label: '受控日志文件',
      value: String(data.totalFiles),
      hint: `扫描上限 ${data.listLimit} 个`,
      icon: FolderOpened,
    },
    {
      key: 'active',
      label: '当前活跃日志',
      value: formatBytes(data.activeSizeBytes),
      hint: data.activeFileAccessible ? data.activeFileName : '当前不可访问',
      icon: Document,
    },
    {
      key: 'archive',
      label: '归档 / 压缩',
      value: String(data.archiveFiles),
      hint: `其中压缩 ${data.compressedFiles} 个`,
      icon: Reading,
    },
    {
      key: 'backup',
      label: '备份池',
      value: String(data.backupFiles),
      hint: `保留 ${data.backupRetentionDays} 天`,
      icon: Timer,
    },
  ]
})

const policyEntries = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { label: '日志目录', value: data.baseDir },
    { label: '备份目录', value: data.backupDir },
    { label: '当前日志', value: data.activeFilePath },
    { label: '滚动归档', value: data.archivePattern || '未配置' },
    { label: '单文件上限', value: data.rollingMaxFileSize || '未配置' },
    { label: '保留历史', value: data.rollingMaxHistory ? `${data.rollingMaxHistory} 份` : '未配置' },
    { label: '总容量上限', value: data.rollingTotalSizeCap || '未配置' },
    { label: '启动清理', value: data.cleanHistoryOnStart ? '开启' : '关闭' },
    { label: '自动备份', value: data.autoBackupEnabled ? `开启 · ${data.autoBackupCron}` : '关闭' },
    { label: '定时清理', value: data.cleanupCron || '未配置' },
    { label: '预览限制', value: `${data.previewMaxLines} 行 / ${formatBytes(data.previewMaxBytes)}` },
    { label: '备份池上限', value: `${data.maxBackupFiles} 个 / ${formatBytes(data.maxBackupTotalSizeBytes)}` },
  ]
})

const fetchOverview = async () => {
  overviewLoading.value = true
  try {
    const res = await logAdminApi.getOverview()
    if (res.code === ResultCode.SUCCESS) {
      overview.value = res.data
      previewLines.value = clamp(previewLines.value, 20, res.data.previewMaxLines || 400)
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '获取日志策略失败')
  } finally {
    overviewLoading.value = false
  }
}

const fetchFiles = async (preserveSelection = true) => {
  filesLoading.value = true
  try {
    const res = await logAdminApi.listFiles(keyword.value)
    if (res.code === ResultCode.SUCCESS) {
      files.value = res.data || []
      const exists = files.value.some(item => item.relativePath === selectedFile.value)
      if (!preserveSelection || !exists) {
        selectedFile.value = (files.value.find(item => item.active) || files.value[0])?.relativePath || ''
      }
      if (!selectedFile.value) {
        content.value = null
      }
    }
  } catch (error: any) {
    files.value = []
    content.value = null
    selectedFile.value = ''
    ElMessage.error(error?.message || '获取日志列表失败')
  } finally {
    filesLoading.value = false
  }
}

const ensurePreviewMode = () => {
  if (!selectedMeta.value?.previewTailSupported && previewMode.value === 'tail') {
    previewMode.value = 'head'
  }
}

const loadContent = async (silent = false) => {
  if (!selectedFile.value) {
    content.value = null
    return
  }

  ensurePreviewMode()
  contentLoading.value = !silent
  try {
    const res = await logAdminApi.getContent(
      selectedFile.value,
      previewMode.value,
      previewLines.value,
      overview.value?.previewMaxBytes,
    )
    if (res.code === ResultCode.SUCCESS) {
      content.value = res.data
    }
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.message || '读取日志内容失败')
    }
  } finally {
    contentLoading.value = false
  }
}

const selectFile = async (item: LogFileSummary) => {
  if (selectedFile.value === item.relativePath) return
  selectedFile.value = item.relativePath
  await loadContent()
}

const refreshAll = async () => {
  await Promise.all([fetchOverview(), fetchFiles(true)])
  await loadContent(true)
}

const searchFiles = async () => {
  await fetchFiles(false)
  await loadContent(true)
}

const createBackup = async () => {
  backupLoading.value = true
  try {
    const res = await logAdminApi.createBackup()
    if (res.code === ResultCode.SUCCESS) {
      ElMessage.success(`备份已生成：${res.data.fileName}`)
      await refreshAll()
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '创建日志备份失败')
  } finally {
    backupLoading.value = false
  }
}

const cleanupLogs = async () => {
  await ElMessageBox.confirm(
    '将按当前保留天数、备份数量和容量上限清理过期日志与归档文件，是否继续？',
    '确认清理',
    { type: 'warning' },
  )

  cleanupLoading.value = true
  try {
    const res = await logAdminApi.cleanup()
    if (res.code === ResultCode.SUCCESS) {
      const data: LogCleanupResult = res.data
      ElMessage.success(`清理完成：删除 ${data.deletedFiles} 个文件，释放 ${formatBytes(data.deletedBytes)}`)
      await refreshAll()
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '执行日志清理失败')
  } finally {
    cleanupLoading.value = false
  }
}

const deleteFile = async (item: LogFileSummary) => {
  await ElMessageBox.confirm(`确认删除日志文件「${item.fileName}」？此操作不可恢复。`, '删除确认', { type: 'warning' })
  deletingFile.value = item.relativePath
  try {
    await logAdminApi.deleteFile(item.relativePath)
    ElMessage.success('日志文件已删除')
    if (selectedFile.value === item.relativePath) {
      selectedFile.value = ''
      content.value = null
    }
    await fetchFiles(false)
    await loadContent(true)
  } catch (error: any) {
    ElMessage.error(error?.message || '删除日志文件失败')
  } finally {
    deletingFile.value = ''
  }
}

const downloadFile = async (item: LogFileSummary) => {
  try {
    const blob = await logAdminApi.downloadFile(item.relativePath)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.fileName
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error?.message || '下载日志文件失败')
  }
}

const copyPreview = async () => {
  if (!content.value?.content) {
    ElMessage.warning('当前没有可复制的日志内容')
    return
  }
  try {
    await navigator.clipboard.writeText(content.value.content)
    ElMessage.success('日志内容已复制')
  } catch {
    ElMessage.error('复制失败，请重试')
  }
}

const updateAutoRefreshTimer = () => {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
  if (!autoRefresh.value || !canAutoRefresh.value) {
    return
  }
  autoRefreshTimer = window.setInterval(() => {
    loadContent(true)
  }, 5000)
}

watch([autoRefresh, canAutoRefresh], updateAutoRefreshTimer, { immediate: true })

onMounted(async () => {
  await fetchOverview()
  await fetchFiles(false)
  await loadContent(true)
})

onUnmounted(() => {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
  }
})

const formatBytes = (value: number) => {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size >= 100 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`
}

const clamp = (value: number, min: number, max: number) => {
  return Math.max(min, Math.min(value, max))
}

const typeTag = (type: string) => {
  if (type === 'active') return 'danger'
  if (type === 'backup') return 'warning'
  if (type === 'archive') return 'success'
  return 'info'
}

const typeLabel = (item: LogFileSummary) => {
  if (item.active) return '活跃'
  if (item.type === 'backup') return '备份'
  if (item.type === 'archive') return '归档'
  return '日志'
}
</script>

<template>
  <div class="logs-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">日志管理</h1>
        <p class="page-subtitle">查看运行日志、归档状态、手动备份与定时清理策略，避免日志文件无限增长拖慢服务。</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="refreshAll" :loading="overviewLoading || filesLoading">刷新总览</el-button>
        <el-button type="primary" :icon="Download" @click="createBackup" :loading="backupLoading">立即备份</el-button>
        <el-button type="danger" plain :icon="Delete" @click="cleanupLogs" :loading="cleanupLoading">按策略清理</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stats-row">
      <el-col v-for="item in statCards" :key="item.key" :xs="12" :md="6">
        <el-card class="stat-card" shadow="never">
          <div class="stat-top">
            <div class="stat-icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-hint">{{ item.hint }}</div>
          </div>
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="policy-card" shadow="never" v-loading="overviewLoading">
      <template #header>
        <div class="card-header">
          <span>归档与保留策略</span>
          <span class="card-header-sub">滚动切分、备份池和预览限制都在这里可见</span>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item v-for="item in policyEntries" :key="item.label" :label="item.label">
          <span class="desc-value">{{ item.value }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <div class="workspace">
      <el-card class="file-panel" shadow="never">
        <template #header>
          <div class="card-header compact">
            <span>日志文件</span>
            <el-button link :icon="Refresh" @click="fetchFiles(true)">刷新列表</el-button>
          </div>
        </template>

        <div class="search-row">
          <el-input
            v-model="keyword"
            placeholder="按文件名或路径筛选，例如 archive / logfile / gz"
            clearable
            @keyup.enter="searchFiles"
            @clear="searchFiles"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button @click="searchFiles">筛选</el-button>
        </div>

        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="panel-alert"
          title="列表只加载元数据；预览只读取限定行数和字节，避免后台一次性读取整份大日志。"
        />

        <el-scrollbar height="560px" v-loading="filesLoading">
          <div v-if="files.length" class="file-list">
            <button
              v-for="item in files"
              :key="item.relativePath"
              type="button"
              class="file-item"
              :class="{ active: selectedFile === item.relativePath }"
              @click="selectFile(item)"
            >
              <div class="file-main">
                <div class="file-title-row">
                  <span class="file-name">{{ item.fileName }}</span>
                  <el-tag size="small" :type="typeTag(item.type)">{{ typeLabel(item) }}</el-tag>
                </div>
                <div class="file-meta">
                  <span>{{ item.modifiedTime }}</span>
                  <span>{{ formatBytes(item.sizeBytes) }}</span>
                  <span v-if="item.compressed">压缩</span>
                </div>
                <div class="file-path">{{ item.relativePath }}</div>
              </div>
            </button>
          </div>
          <el-empty v-else description="当前没有可管理的日志文件" />
        </el-scrollbar>
      </el-card>

      <el-card class="viewer-panel" shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <div class="viewer-title">{{ selectedMeta?.fileName || '日志预览窗口' }}</div>
              <div class="viewer-subtitle">{{ selectedMeta?.relativePath || '选择左侧日志文件后可查看内容' }}</div>
            </div>
            <div class="viewer-actions" v-if="selectedMeta">
              <el-button :icon="Download" @click="downloadFile(selectedMeta)">下载</el-button>
              <el-button
                v-if="selectedMeta.deletable"
                type="danger"
                plain
                :icon="Delete"
                :loading="deletingFile === selectedMeta.relativePath"
                @click="deleteFile(selectedMeta)"
              >
                删除
              </el-button>
            </div>
          </div>
        </template>

        <div v-if="selectedMeta" class="viewer-shell" v-loading="contentLoading">
          <div class="toolbar">
            <el-radio-group v-model="previewMode" size="small" @change="() => loadContent()">
              <el-radio-button label="tail" :disabled="!selectedMeta.previewTailSupported">尾部预览</el-radio-button>
              <el-radio-button label="head">头部预览</el-radio-button>
            </el-radio-group>

            <div class="toolbar-inline">
              <span class="toolbar-label">行数</span>
              <el-input-number
                v-model="previewLines"
                :min="20"
                :max="overview?.previewMaxLines || 400"
                :step="20"
                size="small"
                @change="() => loadContent()"
              />
            </div>

            <div class="toolbar-inline">
              <span class="toolbar-label">自动刷新</span>
              <el-switch v-model="autoRefresh" :disabled="!canAutoRefresh" />
            </div>

            <el-button size="small" :icon="Refresh" @click="() => loadContent()">刷新内容</el-button>
            <el-button size="small" @click="copyPreview">复制内容</el-button>
          </div>

          <div class="preview-note">
            <span>模式：{{ content?.mode || previewMode }}</span>
            <span>返回 {{ content?.lineCount || 0 }} 行</span>
            <span>文件大小 {{ formatBytes(selectedMeta.sizeBytes) }}</span>
            <span v-if="content?.truncated" class="warn">已按限制截断</span>
            <span v-if="selectedMeta.compressed" class="warn">压缩日志仅支持头部预览</span>
          </div>

          <el-alert
            type="warning"
            show-icon
            :closable="false"
            class="panel-alert"
            title="实时查看默认只拉取尾部摘要，不直接读取整个日志文件；大文件请优先下载分析。"
          />

          <pre class="log-preview">{{ content?.content || '当前日志暂无可显示内容' }}</pre>
        </div>

        <el-empty v-else description="选择一个日志文件开始查看" />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.logs-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 900;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0;
  max-width: 780px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.page-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.stats-row {
  margin-bottom: 0;
}

.stat-card {
  min-height: 132px;
  border-radius: 14px;
}

.stat-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  color: #92400e;
  font-size: 20px;
}

.stat-hint {
  max-width: 150px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.stat-value {
  margin-top: 16px;
  font-size: 28px;
  font-weight: 900;
  color: var(--el-text-color-primary);
}

.stat-label {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.policy-card,
.file-panel,
.viewer-panel {
  border-radius: 14px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-header.compact {
  align-items: center;
}

.card-header-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.desc-value {
  word-break: break-all;
}

.workspace {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.panel-alert {
  margin-bottom: 12px;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-item {
  width: 100%;
  text-align: left;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
  cursor: pointer;
}

.file-item:hover {
  border-color: var(--el-color-primary-light-5);
  transform: translateY(-1px);
}

.file-item.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.12);
}

.file-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.file-name {
  font-weight: 800;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.file-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.file-path {
  margin-top: 8px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  word-break: break-all;
}

.viewer-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.viewer-subtitle {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  word-break: break-all;
}

.viewer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.viewer-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.toolbar-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.preview-note {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.preview-note .warn {
  color: var(--el-color-warning-dark-2);
  font-weight: 700;
}

.log-preview {
  min-height: 520px;
  max-height: 720px;
  overflow: auto;
  margin: 0;
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(180deg, #111827, #0f172a);
  color: #e5eefc;
  font-size: 12px;
  line-height: 1.6;
  font-family: 'Cascadia Code', 'JetBrains Mono', Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid rgba(148, 163, 184, 0.24);
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .search-row {
    grid-template-columns: 1fr;
  }

  .toolbar {
    align-items: stretch;
  }

  .toolbar-inline {
    width: 100%;
    justify-content: space-between;
  }

  .log-preview {
    min-height: 420px;
  }
}
</style>
