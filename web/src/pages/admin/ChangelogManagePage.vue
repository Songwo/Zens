<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changelogApi, type ChangelogItem } from '@/api/changelog'

const loading = ref(false)
const list = ref<ChangelogItem[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)

const roadmapStatusOptions = [
  { label: '已上线', value: 'released', stageNo: '01', sortOrder: 300 },
  { label: '建设中', value: 'building', stageNo: '02', sortOrder: 200 },
  { label: '下一阶段', value: 'planned', stageNo: '03', sortOrder: 100 }
]

const createDefaultForm = (): Partial<ChangelogItem> => ({
  version: '',
  title: '',
  content: '',
  stageNo: '01',
  stageLabel: '已上线',
  roadmapStatus: 'released',
  highlights: '',
  actionPath: '',
  upgradeEnabled: 0,
  upgradeUrl: '',
  timestamp: '已上线',
  sortOrder: 300,
  status: 1
})

const form = ref<Partial<ChangelogItem>>(createDefaultForm())

const fetchList = async () => {
  loading.value = true
  try {
    const res = await changelogApi.getAdminList()
    list.value = res.data || []
  } catch (error) {
    console.error('Failed to fetch changelog:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchList()
})

const handleAdd = () => {
  form.value = createDefaultForm()
  dialogVisible.value = true
}

const handleEdit = (row: ChangelogItem) => {
  form.value = {
    ...createDefaultForm(),
    ...row,
    upgradeEnabled: row.upgradeEnabled ?? 0
  }
  dialogVisible.value = true
}

const handleRoadmapStatusChange = (value: string) => {
  const option = roadmapStatusOptions.find(item => item.value === value)
  if (!option) return
  form.value.stageLabel = option.label
  form.value.timestamp = option.label
  if (!form.value.stageNo || ['01', '02', '03'].includes(form.value.stageNo)) {
    form.value.stageNo = option.stageNo
  }
  if (!form.value.sortOrder || [100, 200, 300].includes(form.value.sortOrder)) {
    form.value.sortOrder = option.sortOrder
  }
}

const getRoadmapTagType = (status?: string) => {
  if (status === 'building') return 'warning'
  if (status === 'planned') return 'primary'
  return 'success'
}

const getRoadmapStatusLabel = (status?: string, fallback?: string) => {
  return roadmapStatusOptions.find(item => item.value === status)?.label || fallback || '已上线'
}

const handleDelete = (row: ChangelogItem) => {
  ElMessageBox.confirm(`确定要删除发展历程 "${row.title}" 吗？此操作不可恢复。`, '警告', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await changelogApi.delete(row.id)
      ElMessage.success('删除成功')
      fetchList()
    } catch (error) {
      console.error(error)
    }
  }).catch(() => {})
}

const handleSubmit = async () => {
  if (!form.value.title || !form.value.content || !form.value.version) {
    ElMessage.warning('请填写完整的标题、版本号和内容')
    return
  }
  if (!form.value.stageNo || !form.value.stageLabel || !form.value.roadmapStatus) {
    ElMessage.warning('请填写完整的阶段编号、阶段标签和上线状态')
    return
  }

  const payload = {
    ...form.value,
    timestamp: form.value.timestamp || form.value.stageLabel,
    upgradeEnabled: form.value.upgradeEnabled ? 1 : 0
  }

  submitting.value = true
  try {
    if (form.value.id) {
      await changelogApi.update(form.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await changelogApi.create(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    console.error(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h2>发展历程管理</h2>
        <p>配置关于站点页面的社区路线图、上线状态和后续升级入口</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增路线图</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" style="width: 100%">
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column prop="stageNo" label="阶段" width="86">
          <template #default="{ row }">
            <span class="stage-no">{{ row.stageNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上线状态" width="116">
          <template #default="{ row }">
            <el-tag :type="getRoadmapTagType(row.roadmapStatus)">
              {{ getRoadmapStatusLabel(row.roadmapStatus, row.stageLabel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本号" width="112" />
        <el-table-column prop="title" label="路线图标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="highlights" label="高亮信息" min-width="140" show-overflow-tooltip />
        <el-table-column label="展示状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '展示中' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线升级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.upgradeEnabled === 1 ? 'warning' : 'info'">
              {{ row.upgradeEnabled === 1 ? '已开启' : '未开启' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑路线图节点' : '新增路线图节点'"
      width="720px"
    >
      <el-form :model="form" label-width="100px" @submit.prevent>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="阶段编号" required>
              <el-input v-model="form.stageNo" placeholder="01" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上线状态" required>
              <el-select v-model="form.roadmapStatus" placeholder="选择状态" @change="handleRoadmapStatusChange">
                <el-option
                  v-for="item in roadmapStatusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="阶段标签" required>
              <el-input v-model="form.stageLabel" placeholder="已上线" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="版本号" required>
              <el-input v-model="form.version" placeholder="例如: v1.0.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" placeholder="例如: 基础社区功能" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="内容描述" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            placeholder="描述该版本的更新内容或未来规划..."
          />
        </el-form-item>
        <el-form-item label="高亮信息">
          <el-input v-model="form.highlights" placeholder="例如: 发帖/评论/标签" />
        </el-form-item>
        <el-form-item label="详情路径">
          <el-input v-model="form.actionPath" placeholder="例如: /roadmap 或外部链接，可留空" />
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <div class="form-tip">数值越大越靠前，建议 300 / 200 / 100</div>
        </el-form-item>
        <el-form-item label="展示状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">展示</el-radio>
            <el-radio :label="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="在线升级">
          <el-switch v-model="form.upgradeEnabled" :active-value="1" :inactive-value="0" />
          <div class="form-tip">预留后续在线升级功能入口，当前仅控制配置展示</div>
        </el-form-item>
        <el-form-item v-if="form.upgradeEnabled === 1" label="升级地址">
          <el-input v-model="form.upgradeUrl" placeholder="填写在线升级地址或说明链接" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
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

.stage-no {
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 12px;
  line-height: 1.5;
}

:deep(.el-select) {
  width: 100%;
}

:deep(.el-switch) {
  --el-switch-on-color: var(--el-color-warning);
}
</style>
