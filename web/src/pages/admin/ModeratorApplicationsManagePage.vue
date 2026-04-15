<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Check, Close, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { moderatorApi, type ModeratorApplicationItem } from '@/api/moderator'

const loading = ref(false)
const list = ref<ModeratorApplicationItem[]>([])

const pendingCount = computed(() => list.value.filter(item => item.status === 0).length)
const approvedCount = computed(() => list.value.filter(item => item.status === 1).length)
const rejectedCount = computed(() => list.value.filter(item => item.status === 2).length)

const formatDateTime = (value?: string | null) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await moderatorApi.getAllApplications()
    list.value = res.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '版主申请列表加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void fetchList()
})

const handleApprove = (row: ModeratorApplicationItem) => {
  ElMessageBox.prompt('请输入批准备注（选填）', '批准申请', {
    confirmButtonText: '批准',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '欢迎加入版主团队，请及时关注社区规范。'
  }).then(async (res: any) => {
    try {
      await moderatorApi.approve(row.id, String(res?.value || '').trim() || undefined)
      ElMessage.success('已批准该申请')
      await fetchList()
    } catch (error: any) {
      ElMessage.error(error?.message || '批准失败')
    }
  }).catch(() => {})
}

const handleReject = (row: ModeratorApplicationItem) => {
  ElMessageBox.prompt('请输入拒绝原因（必填）', '拒绝申请', {
    confirmButtonText: '拒绝',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputValidator: (val) => {
      if (!val || val.trim().length === 0) return '拒绝原因不能为空'
      if (val.trim().length > 500) return '拒绝原因不能超过 500 个字符'
      return true
    }
  }).then(async (res: any) => {
    try {
      await moderatorApi.reject(row.id, String(res?.value || '').trim())
      ElMessage.warning('已拒绝该申请')
      await fetchList()
    } catch (error: any) {
      ElMessage.error(error?.message || '拒绝失败')
    }
  }).catch(() => {})
}

const getStatusType = (status: number) => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const getStatusText = (status: number) => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '已批准'
    case 2: return '已拒绝'
    default: return '未知'
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h2>版主申请管理</h2>
        <p>审核用户的板块版主申请，并同步站内通知与邮件结果。</p>
      </div>
      <el-button :icon="RefreshRight" @click="fetchList">刷新列表</el-button>
    </div>

    <div class="summary-row">
      <el-card shadow="never" class="summary-card">
        <span class="summary-label">待审核</span>
        <span class="summary-value warning">{{ pendingCount }}</span>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <span class="summary-label">已批准</span>
        <span class="summary-value success">{{ approvedCount }}</span>
      </el-card>
      <el-card shadow="never" class="summary-card">
        <span class="summary-label">已拒绝</span>
        <span class="summary-value danger">{{ rejectedCount }}</span>
      </el-card>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" style="width: 100%" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <p><strong>申请理由：</strong>{{ row.reason }}</p>
              <p v-if="row.reviewNote"><strong>审核备注：</strong>{{ row.reviewNote }}</p>
              <p><strong>板块说明：</strong>{{ row.sectionDescription || '暂无说明' }}</p>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="id" label="申请ID" width="96" />

        <el-table-column label="申请人" min-width="220">
          <template #default="{ row }">
            <div class="applicant-cell">
              <el-avatar :size="36" :src="row.applicantAvatar">
                {{ (row.applicantNickname || row.applicantUsername || '?').charAt(0) }}
              </el-avatar>
              <div class="applicant-meta">
                <span class="primary">{{ row.applicantNickname || row.applicantUsername || row.userId }}</span>
                <span class="secondary">@{{ row.applicantUsername || row.userId }}</span>
                <span class="secondary" v-if="row.applicantEmail">{{ row.applicantEmail }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="当前信息" min-width="150">
          <template #default="{ row }">
            <div class="stack-cell">
              <span>等级：Lv{{ row.applicantLevel || 1 }}</span>
              <span>角色：{{ row.applicantRole || 'ROLE_USER' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="申请板块" min-width="150">
          <template #default="{ row }">
            <div class="stack-cell">
              <span>{{ row.sectionName || `板块#${row.sectionId}` }}</span>
              <span class="secondary">ID: {{ row.sectionId }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="审核时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.reviewedAt) }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button type="success" link :icon="Check" @click="handleApprove(row)">批准</el-button>
              <el-button type="danger" link :icon="Close" @click="handleReject(row)">拒绝</el-button>
            </template>
            <span v-else class="processed-text">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.header-left h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.header-left p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.summary-card {
  border-radius: 12px;
}

.summary-card :deep(.el-card__body) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.summary-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-value {
  font-size: 22px;
  font-weight: 800;
}

.summary-value.warning {
  color: var(--el-color-warning);
}

.summary-value.success {
  color: var(--el-color-success);
}

.summary-value.danger {
  color: var(--el-color-danger);
}

.table-card {
  border-radius: 12px;
}

.expand-content {
  padding: 16px 32px;
  background-color: var(--el-fill-color-light);
  border-radius: 8px;
  margin: 8px 32px;
}

.expand-content p {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.expand-content p:last-child {
  margin-bottom: 0;
}

.expand-content strong {
  color: var(--el-text-color-primary);
  margin-right: 8px;
}

.applicant-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.applicant-meta,
.stack-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.primary {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.secondary {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.processed-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-row {
    grid-template-columns: 1fr;
  }
}
</style>
