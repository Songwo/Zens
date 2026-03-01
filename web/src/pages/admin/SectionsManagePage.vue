<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/lib/api'

// Song：说明
interface Section {
  id: string
  name: string
  description: string
  icon: string
  sortOrder: number
  status: number
  createdAt: string
  postCount?: number
  todayCount?: number
}

const loading = ref(false)
const sections = ref<Section[]>([])
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref()

// Song：说明
const formData = reactive({
  id: '',
  name: '',
  description: '',
  icon: '',
  sortOrder: 0,
})

const rules = {
  name: [
    { required: true, message: '请输入板块名称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入板块描述', trigger: 'blur' }
  ],
  icon: [
    { required: true, message: '请输入图标代码(如 el-icon-xxx 或 URL)', trigger: 'blur' }
  ]
}

// Song：说明
const fetchSections = async () => {
  loading.value = true
  try {
    const res = await api.get<any, { code: number, data: Section[] }>('/section/list')
    if (res.code === 2000 || res.code === 200) {
      sections.value = res.data
    }
  } catch (error) {
    ElMessage.error('获取板块列表失败')
  } finally {
    loading.value = false
  }
}

// Song：说明
const handleAdd = () => {
  dialogType.value = 'add'
  formData.id = ''
  formData.name = ''
  formData.description = ''
  formData.icon = ''
  formData.sortOrder = 0
  dialogVisible.value = true
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// Song：说明
const handleEdit = (row: Section) => {
  dialogType.value = 'edit'
  formData.id = row.id
  formData.name = row.name
  formData.description = row.description
  formData.icon = row.icon
  formData.sortOrder = row.sortOrder
  dialogVisible.value = true
}

// Song：说明
const submitForm = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        if (dialogType.value === 'add') {
          await api.post('/section', formData)
          ElMessage.success('添加成功')
        } else {
          await api.put(`/section/${formData.id}`, formData)
          ElMessage.success('更新成功')
        }
        dialogVisible.value = false
        fetchSections()
      } catch (error) {
        ElMessage.error('操作失败')
      }
    }
  })
}

// Song：说明
const handleDelete = (row: Section) => {
  ElMessageBox.confirm(
    `确定要删除板块 "${row.name}" 吗？此操作不可恢复！`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await api.delete(`/section/${row.id}`)
      ElMessage.success('删除成功')
      fetchSections()
    } catch (error) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

// Song：说明
const handleToggleStatus = async (row: Section) => {
  try {
    await api.put(`/section/${row.id}/status?status=${row.status}`)
    ElMessage.success('状态切换成功')
    fetchSections()
  } catch (error) {
    ElMessage.error('切换状态失败')
    row.status = row.status === 1 ? 0 : 1 // Song：失败时回滚
  }
}

onMounted(() => {
  fetchSections()
})
</script>

<template>
  <div class="sections-manage-container">
    <div class="header-actions">
      <h2>板块管理</h2>
      <el-button type="primary" @click="handleAdd">
        新增板块
      </el-button>
    </div>

    <!-- Data Table -->
    <el-card class="table-card">
      <el-table 
        v-loading="loading" 
        :data="sections" 
        style="width: 100%"
        border
        stripe
      >
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        
        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <span style="font-size: 20px;">{{ row.icon }}</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="name" label="板块名称" width="150" />
        
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />

        <el-table-column prop="postCount" label="帖子数" width="100" align="center" />

        <el-table-column prop="todayCount" label="今日新增" width="100" align="center" />
        
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        
        <el-table-column prop="createdAt" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN', {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit',
              hour12: false
            }) : '-' }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dialog for Add/Edit -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增板块' : '编辑板块'"
      width="500px"
    >
      <el-form 
        ref="formRef" 
        :model="formData" 
        :rules="rules" 
        label-width="100px"
      >
        <el-form-item label="板块名称" prop="name">
          <el-input v-model="formData.name" placeholder="如: 技术交流" maxlength="20" show-word-limit />
        </el-form-item>
        
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model="formData.description" 
            type="textarea" 
            :rows="3" 
            placeholder="描述该板块的内容..." 
            maxlength="100" 
            show-word-limit 
          />
        </el-form-item>
        
        <el-form-item label="图标 (Emoji)" prop="icon">
          <el-input v-model="formData.icon" placeholder="输入 Emoji, 如 💻" />
        </el-form-item>

        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" />
          <div style="margin-left: 10px; font-size: 12px; color: #999;">数字越小越靠前</div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sections-manage-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions h2 {
  margin: 0;
  color: var(--el-text-color-primary);
}

.table-card {
  border-radius: 8px;
  overflow: hidden;
}
</style>
