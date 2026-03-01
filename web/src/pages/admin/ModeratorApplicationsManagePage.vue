<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Check, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { moderatorApi, type ModeratorApplicationItem } from '@/api/moderator'
import { sectionApi } from '@/api/section'

const loading = ref(false)
const list = ref<ModeratorApplicationItem[]>([])
const sections = ref<Record<number, string>>({})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await moderatorApi.getAllApplications()
    list.value = res.data || []
  } catch (error) {
    console.error('Failed to fetch applications:', error)
  } finally {
    loading.value = false
  }
}

const fetchSections = async () => {
  try {
    const res = await sectionApi.getList()
    const sectionList = res.data || []
    const map: Record<number, string> = {}
    sectionList.forEach((s: any) => {
      map[s.id] = s.name
    })
    sections.value = map
  } catch (error) {
    console.error('Failed to fetch sections:', error)
  }
}

onMounted(() => {
  fetchSections()
  fetchList()
})

const handleApprove = (row: ModeratorApplicationItem) => {
  ElMessageBox.prompt('请输入批准备注 (选填)', '批准申请', {
    confirmButtonText: '批准',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '欢迎加入版主团队！'
  }).then(async (res: any) => {
    try {
      await moderatorApi.approve(row.id, res.value)
      ElMessage.success('已批准该申请')
      fetchList()
    } catch (error) {
      console.error(error)
    }
  }).catch(() => {})
}

const handleReject = (row: ModeratorApplicationItem) => {
  ElMessageBox.prompt('请输入拒绝原因 (必填)', '拒绝申请', {
    confirmButtonText: '拒绝',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputValidator: (val) => {
      if (!val || val.trim().length === 0) return '拒绝原因不能为空'
      return true
    }
  }).then(async (res: any) => {
    try {
      await moderatorApi.reject(row.id, res.value)
      ElMessage.warning('已拒绝该申请')
      fetchList()
    } catch (error) {
      console.error(error)
    }
  }).catch(() => {})
}

const getStatusType = (status: number) => {
  switch (status) {
    case 0: return 'warning' // Song：待处理
    case 1: return 'success' // Song：已通过
    case 2: return 'danger' // Song：已拒绝
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
        <p>审核用户的板块版主申请请求</p>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" style="width: 100%" default-expand-all>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <p><strong>申请理由：</strong>{{ row.reason }}</p>
              <p v-if="row.reviewNote"><strong>审核回复：</strong>{{ row.reviewNote }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="申请ID" width="100" />
        <el-table-column label="用户ID" width="120">
          <template #default="{ row }">
            <el-tag type="info">User #{{ row.userId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请板块" min-width="150">
          <template #default="{ row }">
            <span v-if="sections[row.sectionId]">{{ sections[row.sectionId] }}</span>
            <span v-else>板块 #{{ row.sectionId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="180" />
        <el-table-column label="状态" width="120">
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
            <span v-else class="processed-text">已处理 ({{ row.updateTime }})</span>
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
  margin-bottom: 24px;
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

.processed-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
