<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changelogApi, type ChangelogItem } from '@/api/changelog'

const loading = ref(false)
const list = ref<ChangelogItem[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)

const form = ref<Partial<ChangelogItem>>({
  version: '',
  title: '',
  content: '',
  sortOrder: 100,
  status: 1
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await changelogApi.getList()
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
  form.value = {
    version: '',
    title: '',
    content: '',
    sortOrder: 100,
    status: 1
  }
  dialogVisible.value = true
}

const handleEdit = (row: ChangelogItem) => {
  form.value = { ...row }
  dialogVisible.value = true
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

  submitting.value = true
  try {
    if (form.value.id) {
      await changelogApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await changelogApi.create(form.value)
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
        <p>管理在展示在"关于站点"页面的发展时间线</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增历程</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" style="width: 100%">
        <el-table-column prop="sortOrder" label="排序权重" width="100" />
        <el-table-column prop="version" label="版本号" width="120" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '草稿' }}
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
      :title="form.id ? '编辑发展历程' : '新增发展历程'"
      width="600px"
    >
      <el-form :model="form" label-width="100px" @submit.prevent>
        <el-form-item label="版本号" required>
          <el-input v-model="form.version" placeholder="例如: v1.0.0" />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="例如: 🎉 v1.0.0 震撼发布" />
        </el-form-item>
        <el-form-item label="内容描述" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="描述该版本的更新内容或未来展望..."
          />
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <div class="form-tip">数值越大越靠前 (>=100 为高亮大节点)</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">发布</el-radio>
            <el-radio :label="0">草稿 (隐藏)</el-radio>
          </el-radio-group>
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

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 12px;
  line-height: 1.5;
}
</style>
