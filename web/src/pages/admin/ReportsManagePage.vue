<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/lib/api'

const reports = ref<any[]>([])
const loading = ref(true)
const current = ref(1)
const total = ref(0)
const pageSize = ref(10)

const fetchReports = async () => {
  loading.value = true
  try {
    const res = await api.get<any, any>(`/report/list?current=${current.value}&size=${pageSize.value}`)
    if (res.data.code === 200) {
      reports.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch {
    reports.value = []
  } finally {
    loading.value = false
  }
}

const handlePageChange = (val: number) => {
  current.value = val
  fetchReports()
}

const handleResolve = async (row: any, status: number) => {
  try {
    await api.post(`/report/resolve/${row.id}?status=${status}`)
    ElMessage.success(status === 1 ? '已处理' : '已忽略')
    fetchReports()
  } catch {
    ElMessage.error('操作失败')
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  fetchReports()
})
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h1 class="title">举报管理</h1>
    </div>

    <el-table :data="reports" v-loading="loading" stripe border class="data-table">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="reporterId" label="举报人" width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.reporterId }}
        </template>
      </el-table-column>
      <el-table-column prop="targetType" label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.targetType === 'post' ? '帖子' : row.targetType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="举报原因" min-width="150" show-overflow-tooltip />
      <el-table-column prop="details" label="详细说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 0" type="warning" size="small">待处理</el-tag>
          <el-tag v-else-if="row.status === 1" type="success" size="small">已处理</el-tag>
          <el-tag v-else type="info" size="small">已忽略</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="举报时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button
              size="small"
              type="primary"
              link
              @click="handleResolve(row, 1)"
            >
              已处理
            </el-button>
            <el-button
              size="small"
              type="info"
              link
              @click="handleResolve(row, 2)"
            >
              忽略
            </el-button>
          </template>
          <span v-else class="text-placeholder">已完成</span>
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
  </div>
</template>

<style scoped>
.manage-page {
  padding: 0;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.page-header .title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}
.data-table {
  border-radius: 8px;
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
</style>
