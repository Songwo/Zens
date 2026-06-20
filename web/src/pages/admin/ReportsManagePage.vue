<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Select, WarningFilled } from '@element-plus/icons-vue'
import { reportApi, type ReportManageItem } from '@/api/report'
import { sectionApi, type Section } from '@/api/section'
import { useUserStore } from '@/store/user'
import { ensureCurrentUserProfile, hasAdminRole } from '@/utils/sessionProfile'

const route = useRoute()
const userStore = useUserStore()
const reports = ref<ReportManageItem[]>([])
const loading = ref(true)
const current = ref(1)
const total = ref(0)
const pageSize = ref(10)
const statusFilter = ref<number | undefined>(undefined)
const allSections = ref<Section[]>([])
const selectedReports = ref<ReportManageItem[]>([])
const batchLoading = ref(false)
const selectedSectionId = ref<number | undefined>(
  route.query.sectionId ? Number(route.query.sectionId) : undefined
)

const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectingRow = ref<ReportManageItem | null>(null)
const rejectLoading = ref(false)

const isAdmin = computed(() => hasAdminRole(userStore.userInfo))
const isGlobalModerator = computed(() => {
  const roles = (userStore.userInfo as any)?.roles || []
  return roles.includes('ROLE_MODERATOR')
})
const moderatedSectionIds = computed<number[]>(() => {
  const rawIds = Array.isArray((userStore.userInfo as any)?.moderatedSectionIds)
    ? ((userStore.userInfo as any)?.moderatedSectionIds as Array<number | string>)
    : []
  return rawIds
    .map(id => Number(id))
    .filter(id => Number.isFinite(id) && id > 0)
})

const availableSections = computed(() => {
  if (isAdmin.value || isGlobalModerator.value) return allSections.value
  return allSections.value.filter(section => moderatedSectionIds.value.includes(Number(section.id)))
})
const pendingReports = computed(() => reports.value.filter(report => report.status === 0))
const highRiskReports = computed(() => reports.value.filter(report => (report.flagWeight ?? 1) >= 3 && report.status === 0))
const commentReports = computed(() => reports.value.filter(report => report.targetType === 'comment'))
const selectedPendingReports = computed(() => selectedReports.value.filter(report => report.status === 0))

const unwrapPageData = <T,>(res: any): { records: T[]; total: number } => {
  const pageData = res?.data?.records
    ? res.data
    : res?.records
      ? res
      : res?.data?.data?.records
        ? res.data.data
        : null
  return {
    records: Array.isArray(pageData?.records) ? pageData.records : [],
    total: Number(pageData?.total || 0),
  }
}

const normalizeSelectedSection = () => {
  if (selectedSectionId.value != null && !Number.isFinite(Number(selectedSectionId.value))) {
    selectedSectionId.value = undefined
  }
  if (isAdmin.value || isGlobalModerator.value) {
    return
  }
  if (selectedSectionId.value == null && moderatedSectionIds.value.length > 0) {
    selectedSectionId.value = moderatedSectionIds.value[0]
    return
  }
  if (selectedSectionId.value != null && !moderatedSectionIds.value.includes(Number(selectedSectionId.value))) {
    selectedSectionId.value = undefined
  }
}

const fetchSections = async () => {
  try {
    const res = await sectionApi.getActiveList()
    allSections.value = res.data || []
    normalizeSelectedSection()
  } catch {
    allSections.value = []
  }
}

const fetchReports = async () => {
  loading.value = true
  try {
    const res = await reportApi.getList(current.value, pageSize.value, statusFilter.value, selectedSectionId.value)
    const pageData = unwrapPageData<ReportManageItem>(res)
    reports.value = pageData.records
    total.value = pageData.total
    selectedReports.value = []
  } catch (error: any) {
    reports.value = []
    ElMessage.error(error?.response?.data?.message || '举报列表加载失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (val: number) => {
  current.value = val
  void fetchReports()
}

const handleFilterChange = () => {
  current.value = 1
  void fetchReports()
}

const handleSectionFilter = () => {
  normalizeSelectedSection()
  current.value = 1
  void fetchReports()
}

const handleResolve = async (row: ReportManageItem, status: number) => {
  try {
    await reportApi.resolve(row.id, status)
    ElMessage.success(status === 1 ? '已处理' : '已忽略')
    await fetchReports()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '操作失败')
  }
}

const handleSelectionChange = (rows: ReportManageItem[]) => {
  selectedReports.value = rows
}

const runBatchResolve = async (status: 1 | 2) => {
  const rows = selectedPendingReports.value
  if (rows.length === 0) {
    ElMessage.warning('请先选择待处理举报')
    return
  }
  const actionName = status === 1 ? '标记已处理' : '忽略'
  try {
    await ElMessageBox.confirm(`确定将选中的 ${rows.length} 条待处理举报批量${actionName}吗？`, `批量${actionName}`, {
      confirmButtonText: `批量${actionName}`,
      cancelButtonText: '取消',
      type: status === 1 ? 'success' : 'warning',
    })
    batchLoading.value = true
    const results = await Promise.allSettled(rows.map(row => reportApi.resolve(row.id, status)))
    const success = results.filter(result => result.status === 'fulfilled').length
    const failed = results.length - success
    if (failed > 0) {
      ElMessage.warning(`批量${actionName}完成：成功 ${success} 条，失败 ${failed} 条`)
    } else {
      ElMessage.success(`已批量${actionName} ${success} 条`)
    }
    await fetchReports()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`批量${actionName}失败`)
    }
  } finally {
    batchLoading.value = false
  }
}

const openRejectDialog = (row: ReportManageItem) => {
  rejectingRow.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const handleReject = async () => {
  if (!rejectingRow.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写打回原因')
    return
  }
  rejectLoading.value = true
  try {
    await reportApi.reject(rejectingRow.value.id, rejectReason.value.trim())
    ElMessage.success('已打回帖子并通知作者修改')
    rejectDialogVisible.value = false
    await fetchReports()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '操作失败')
  } finally {
    rejectLoading.value = false
  }
}

const statusLabel = (status: number) => {
  switch (status) {
    case 0:
      return '待处理'
    case 1:
      return '已处理'
    case 2:
      return '已忽略'
    case 3:
      return '已打回'
    case 10:
      return '排队中'
    case 11:
      return '处理中'
    default:
      return '未知'
  }
}

const statusType = (status: number) => {
  switch (status) {
    case 0:
      return 'warning'
    case 1:
      return 'success'
    case 2:
      return 'info'
    case 3:
      return 'danger'
    case 10:
      return 'info'
    case 11:
      return 'primary'
    default:
      return 'info'
  }
}

const targetTypeLabel = (targetType: string) => {
  return targetType === 'comment' ? '评论' : '帖子'
}

// Song：flag 权重越高（来自更高信任等级用户）越应优先处理
const flagWeightType = (weight?: number) => {
  const w = weight ?? 1
  if (w >= 5) return 'danger'
  if (w >= 3) return 'warning'
  return 'info'
}

const canRejectPost = (row: ReportManageItem) => {
  return row.status === 0 && row.targetType === 'post'
}

const canSelectReport = (row: ReportManageItem) => row.status === 0

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

watch(
  () => route.query.sectionId,
  (sectionId) => {
    const nextSectionId = Number(sectionId)
    selectedSectionId.value = Number.isFinite(nextSectionId) && nextSectionId > 0 ? nextSectionId : undefined
    normalizeSelectedSection()
    current.value = 1
    void fetchReports()
  }
)

onMounted(async () => {
  await ensureCurrentUserProfile({ force: true })
  await fetchSections()
  await fetchReports()
})
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <div class="header-info">
        <h1 class="title">举报管理</h1>
        <p class="subtitle">按当前账号可处理的板块范围加载举报，版主仅能处理自己负责板块的内容。</p>
      </div>
      <div class="header-actions">
        <el-select
          v-model="selectedSectionId"
          placeholder="全部板块"
          clearable
          class="section-filter"
          @change="handleSectionFilter"
          @clear="handleSectionFilter"
        >
          <el-option
            v-for="section in availableSections"
            :key="section.id"
            :label="section.name"
            :value="Number(section.id)"
          />
        </el-select>
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          class="status-filter"
          @change="handleFilterChange"
          @clear="handleFilterChange"
        >
          <el-option :value="0" label="待处理" />
          <el-option :value="1" label="已处理" />
          <el-option :value="2" label="已忽略" />
          <el-option :value="3" label="已打回" />
          <el-option :value="10" label="排队中" />
          <el-option :value="11" label="处理中" />
        </el-select>
      </div>
    </div>

    <div class="admin-summary-grid">
      <div class="summary-card warning">
        <span class="summary-label">待处理</span>
        <strong>{{ pendingReports.length }}</strong>
      </div>
      <div class="summary-card danger">
        <span class="summary-label">高权重举报</span>
        <strong>{{ highRiskReports.length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">评论举报</span>
        <strong>{{ commentReports.length }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">当前筛选总数</span>
        <strong>{{ total }}</strong>
      </div>
    </div>

    <div class="bulk-toolbar" :class="{ active: selectedReports.length > 0 }">
      <div class="bulk-info">
        <el-icon><Select /></el-icon>
        <span>已选择 {{ selectedReports.length }} 条，待处理 {{ selectedPendingReports.length }} 条</span>
      </div>
      <div class="bulk-actions">
        <el-button
          size="small"
          type="primary"
          plain
          :icon="Check"
          :disabled="selectedPendingReports.length === 0"
          :loading="batchLoading"
          @click="runBatchResolve(1)"
        >
          批量标记已处理
        </el-button>
        <el-button
          size="small"
          type="warning"
          plain
          :icon="Close"
          :disabled="selectedPendingReports.length === 0"
          :loading="batchLoading"
          @click="runBatchResolve(2)"
        >
          批量忽略
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="highRiskReports.length > 0 && (statusFilter === undefined || statusFilter === 0)"
      type="warning"
      :closable="false"
      class="priority-alert"
      show-icon
    >
      <template #title>
        当前列表有 {{ highRiskReports.length }} 条来自较高信任等级用户的举报，建议优先处理。
      </template>
    </el-alert>

    <el-table
      :data="reports"
      v-loading="loading"
      stripe
      border
      class="data-table"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="44" :selectable="canSelectReport" />
      <el-table-column prop="id" label="ID" width="90" />
      <el-table-column prop="reporterId" label="举报人" width="200" show-overflow-tooltip />
      <el-table-column label="举报权重" width="110" align="center">
        <template #default="{ row }">
          <el-tag
            :type="flagWeightType(row.flagWeight)"
            size="small"
            effect="dark"
          >
            <el-icon v-if="(row.flagWeight ?? 1) >= 3" class="flag-icon"><WarningFilled /></el-icon>
            {{ row.flagWeight ?? 1 }}x
            <span v-if="row.reporterTrustLevel !== undefined" class="flag-tl">TL{{ row.reporterTrustLevel }}</span>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="所属板块" width="140" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.sectionName || '未知板块' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标内容" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="target-cell">
            <div class="target-header">
              <el-tag size="small">{{ targetTypeLabel(row.targetType) }}</el-tag>
              <span class="target-title">{{ row.targetTitle || '目标内容已不存在' }}</span>
            </div>
            <div v-if="row.targetPreview" class="target-preview">{{ row.targetPreview }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="举报原因" min-width="150" show-overflow-tooltip />
      <el-table-column prop="details" label="详细说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="举报时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" type="primary" link @click="handleResolve(row, 1)">
              已处理
            </el-button>
            <el-button v-if="canRejectPost(row)" size="small" type="warning" link @click="openRejectDialog(row)">
              打回修改
            </el-button>
            <el-button size="small" type="info" link @click="handleResolve(row, 2)">
              忽略
            </el-button>
          </template>
          <span v-else class="text-placeholder">{{ statusLabel(row.status) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper" v-if="total > 0">
      <el-pagination
        v-model:current-page="current"
        :page-size="pageSize"
        layout="prev, pager, next, total"
        :total="total"
        @current-change="handlePageChange"
      />
    </div>

    <el-empty v-if="!loading && reports.length === 0" description="暂无举报记录" />

    <el-dialog
      v-model="rejectDialogVisible"
      title="打回帖子"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          帖子将被设为不可见的打回修改状态并通知作者修改，作者完成修改后需要重新发布提交审核。
        </template>
      </el-alert>
      <el-form label-position="top">
        <el-form-item label="打回原因（将发送给作者）" required>
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="4"
            placeholder="请输入打回原因，例如：帖子内容存在人身攻击，请修改后重新发布"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="rejectLoading" @click="handleReject">
          确认打回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-page {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  gap: 16px;
}

.header-info {
  min-width: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header .title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.page-header .subtitle {
  margin: 6px 0 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.section-filter,
.status-filter {
  width: 160px;
}

.data-table {
  border-radius: 8px;
}

.admin-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-card {
  min-height: 74px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--cp-bg-card);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.summary-card strong {
  font-size: 26px;
  line-height: 1;
  color: var(--el-text-color-primary);
}

.summary-card.warning strong {
  color: var(--el-color-warning);
}

.summary-card.danger strong {
  color: var(--el-color-danger);
}

.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.bulk-toolbar {
  min-height: 46px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.bulk-toolbar.active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.bulk-info,
.bulk-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bulk-info {
  font-size: 13px;
  font-weight: 700;
  color: var(--el-text-color-regular);
}

.priority-alert {
  margin-bottom: 12px;
  border-radius: 8px;
}

.target-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.target-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.target-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.target-preview {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.flag-tl {
  margin-left: 4px;
  opacity: 0.85;
  font-weight: 600;
}

.flag-icon {
  margin-right: 3px;
  vertical-align: -2px;
}

.text-placeholder {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .section-filter,
  .status-filter {
    width: 100%;
  }

  .admin-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bulk-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
