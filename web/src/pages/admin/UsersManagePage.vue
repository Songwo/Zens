<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Delete, Lock, Unlock } from '@element-plus/icons-vue'
import api from '@/lib/api'

const users = ref<any[]>([])
const loading = ref(true)
const searchKeyword = ref('')

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await api.get<any, any>('/user/all')
    const userList = res.data || res || []
    // Song：说明
    users.value = userList.map((user: any) => ({
      ...user,
      roles: user.roles || []
    }))
  } catch (error) {
    ElMessage.error('获取用户列表失败')
    users.value = []
  } finally {
    loading.value = false
  }
}

const filteredUsers = () => {
  if (!searchKeyword.value) return users.value
  const kw = searchKeyword.value.toLowerCase()
  return users.value.filter((u: any) =>
    (u.username || '').toLowerCase().includes(kw) ||
    (u.nickname || '').toLowerCase().includes(kw) ||
    (u.email || '').toLowerCase().includes(kw)
  )
}

const handleBan = async (user: any) => {
  try {
    await ElMessageBox.confirm(`确定要封禁用户「${user.username || user.nickname}」吗？`, '确认封禁', {
      confirmButtonText: '封禁',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await api.post(`/user/ban/${user.id}`)
    ElMessage.success('用户已封禁')
    fetchUsers()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const handleUnban = async (user: any) => {
  try {
    await api.post(`/user/unban/${user.id}`)
    ElMessage.success('用户已解封')
    fetchUsers()
  } catch {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (user: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${user.username || user.nickname}」吗？此操作不可恢复！`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'error',
    })
    await api.delete(`/user/${user.id}`)
    ElMessage.success('用户已删除')
    fetchUsers()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const getRoleType = (role: string) => {
  if (role === 'ROLE_SUPER_ADMIN' || role === 'ROLE_ADMIN') return 'danger'
  if (role === 'ROLE_MODERATOR') return 'warning'
  return 'info'
}

const getRoleLabel = (role: string) => {
  const roleMap: Record<string, string> = {
    'ROLE_SUPER_ADMIN': '超级管理员',
    'ROLE_ADMIN': '管理员',
    'ROLE_MODERATOR': '版主',
    'ROLE_USER': '普通用户'
  }
  return roleMap[role] || role
}

onMounted(() => {
  fetchUsers()
})
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <h1 class="title">用户管理</h1>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索用户名/昵称/邮箱"
        :prefix-icon="Search"
        clearable
        class="search-input"
      />
    </div>

    <el-table :data="filteredUsers()" v-loading="loading" stripe border class="data-table">
      <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="nickname" label="昵称" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column prop="roles" label="角色" width="150">
        <template #default="{ row }">
          <el-tag
            v-for="role in (row.roles || [])"
            :key="role"
            :type="getRoleType(role)"
            size="small"
            style="margin-right: 4px;"
          >
            {{ getRoleLabel(role) }}
          </el-tag>
          <el-tag v-if="!row.roles || row.roles.length === 0" type="info" size="small">
            普通用户
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'danger' : 'success'" size="small">
            {{ row.status === 0 ? '封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 0"
            size="small"
            type="warning"
            plain
            :icon="Lock"
            @click="handleBan(row)"
          >
            封禁
          </el-button>
          <el-button
            v-else
            size="small"
            type="success"
            plain
            :icon="Unlock"
            @click="handleUnban(row)"
          >
            解封
          </el-button>
          <el-button
            size="small"
            type="danger"
            plain
            :icon="Delete"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
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
.search-input {
  width: 280px;
}
.data-table {
  border-radius: 8px;
}
</style>
