<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Delete, Lock, Unlock, UserFilled } from '@element-plus/icons-vue'
import api from '@/lib/api'
import { useUserStore } from '@/store/user'
import { sectionApi, type Section } from '@/api/section'
import { userApi } from '@/api/user'
import UserBadge from '@/components/common/UserBadge.vue'

const userStore = useUserStore()
const users = ref<any[]>([])
const sections = ref<Section[]>([])
const loading = ref(true)
const sectionsLoading = ref(false)
const searchKeyword = ref('')
const roleDialogVisible = ref(false)
const roleDialogUser = ref<any>(null)
const selectedRole = ref('')
const selectedModeratedSectionIds = ref<number[]>([])
const permissionSaving = ref(false)
const badgeText = ref('')
const badgeColor = ref('')
const badgeStyle = ref('solid')

const ROLE_LEVEL: Record<string, number> = {
  'ROLE_USER': 1,
  'ROLE_MODERATOR': 2,
  'ROLE_ADMIN': 3,
  'ROLE_SUPER_ADMIN': 4
}

const currentUserRole = computed(() => {
  const roles = userStore.userInfo?.roles || []
  let maxLevel = 0
  let maxRole = 'ROLE_USER'
  for (const r of roles) {
    const level = ROLE_LEVEL[r] || 0
    if (level > maxLevel) {
      maxLevel = level
      maxRole = r
    }
  }
  return maxRole
})

const currentUserLevel = computed(() => ROLE_LEVEL[currentUserRole.value] || 0)
const currentUserId = computed(() => userStore.userId || userStore.userInfo?.id || '')

const availableRoles = computed(() => {
  const all = [
    { value: 'ROLE_USER', label: '普通用户', level: 1 },
    { value: 'ROLE_ADMIN', label: '管理员', level: 3 },
    { value: 'ROLE_SUPER_ADMIN', label: '超级管理员', level: 4 },
  ]
  return all.filter(r => r.level < currentUserLevel.value)
})

const sectionNameMap = computed(() => {
  return new Map(sections.value.map(section => [Number(section.id), section.name]))
})

const canOperateUser = (user: any) => {
  if (user.id === currentUserId.value) return false
  const targetRole = (user.roles && user.roles[0]) || 'ROLE_USER'
  return currentUserLevel.value > (ROLE_LEVEL[targetRole] || 0)
}

const isSelf = (user: any) => user.id === currentUserId.value

const isSuperAdmin = (user: any) => {
  return (user.roles || []).includes('ROLE_SUPER_ADMIN')
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await api.get<any, any>('/user/all')
    const userList = res.data || res || []
    users.value = userList.map((user: any) => ({
      ...user,
      roles: user.roles || [],
      moderatedSectionIds: Array.isArray(user.moderatedSectionIds)
        ? user.moderatedSectionIds
            .map((id: number | string) => Number(id))
            .filter((id: number) => Number.isFinite(id) && id > 0)
        : []
    }))
  } catch (error) {
    ElMessage.error('获取用户列表失败')
    users.value = []
  } finally {
    loading.value = false
  }
}

const fetchSections = async () => {
  sectionsLoading.value = true
  try {
    const res = await sectionApi.getList()
    sections.value = res.data || []
  } catch {
    ElMessage.error('获取板块列表失败')
    sections.value = []
  } finally {
    sectionsLoading.value = false
  }
}

const filteredUsers = () => {
  if (!searchKeyword.value) return users.value
  const kw = searchKeyword.value.toLowerCase()
  return users.value.filter((u: any) =>
    (u.username || '').toLowerCase().includes(kw) ||
    (u.nickname || '').toLowerCase().includes(kw) ||
    (u.email || '').toLowerCase().includes(kw) ||
    ((kw.includes('版主') || kw.includes('moderator')) && hasSectionModeratorRole(u))
  )
}

const hasSectionModeratorRole = (user: any) => {
  return Array.isArray(user.moderatedSectionIds) && user.moderatedSectionIds.length > 0
}

const getModeratedSectionNames = (user: any) => {
  const ids = Array.isArray(user.moderatedSectionIds) ? user.moderatedSectionIds : []
  return ids
    .map((id: number | string) => {
      const numericId = Number(id)
      return sectionNameMap.value.get(numericId) || `板块 #${numericId}`
    })
    .filter(Boolean)
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
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const handleUnban = async (user: any) => {
  try {
    await api.post(`/user/unban/${user.id}`)
    ElMessage.success('用户已解封')
    fetchUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
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
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const openRoleDialog = (user: any) => {
  roleDialogUser.value = user
  selectedRole.value = (user.roles && user.roles[0]) || 'ROLE_USER'
  selectedModeratedSectionIds.value = Array.isArray(user.moderatedSectionIds)
    ? user.moderatedSectionIds.map((id: number | string) => Number(id)).filter((id: number) => Number.isFinite(id) && id > 0)
    : []
  badgeText.value = user.badgeText || ''
  badgeColor.value = user.badgeColor || ''
  badgeStyle.value = user.badgeStyle || 'solid'
  roleDialogVisible.value = true
}

const submitRoleChange = async () => {
  if (!roleDialogUser.value) return
  permissionSaving.value = true
  try {
    await api.post(`/user/${roleDialogUser.value.id}/role`, null, {
      params: { roleCode: selectedRole.value }
    })
    await userApi.updateModeratedSections(roleDialogUser.value.id, selectedModeratedSectionIds.value)
    await userApi.updateBadge(roleDialogUser.value.id, { badgeText: badgeText.value.trim(), badgeColor: badgeColor.value, badgeStyle: badgeStyle.value })
    ElMessage.success('权限设置成功')
    roleDialogVisible.value = false
    fetchUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '权限设置失败')
  } finally {
    permissionSaving.value = false
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
    'ROLE_MODERATOR': '全局版主',
    'ROLE_USER': '普通用户'
  }
  return roleMap[role] || role
}

onMounted(() => {
  fetchSections()
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
      <el-table-column prop="username" label="用户名" width="140">
        <template #default="{ row }">
          {{ row.username }}
          <el-tag v-if="isSelf(row)" size="small" type="primary" style="margin-left: 4px;">（我）</el-tag>
        </template>
      </el-table-column>
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
      <el-table-column label="版主板块" min-width="180">
        <template #default="{ row }">
          <div v-if="getModeratedSectionNames(row).length > 0" class="section-tags">
            <el-tag
              v-for="name in getModeratedSectionNames(row)"
              :key="name"
              size="small"
              type="success"
              effect="plain"
            >
              {{ name }}
            </el-tag>
          </div>
          <span v-else class="muted-text">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="badgeText" label="徽章" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <UserBadge v-if="row.badgeText" :text="row.badgeText" :color="row.badgeColor" :effect="row.badgeStyle" />
          <span v-else class="muted-text">—</span>
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
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-tooltip
            :disabled="canOperateUser(row)"
            :content="isSelf(row) ? '不能操作自己' : isSuperAdmin(row) ? '无法操作超级管理员' : '无权操作该用户'"
          >
            <el-button
              size="small"
              type="primary"
              plain
              :icon="UserFilled"
              :disabled="!canOperateUser(row)"
              @click="openRoleDialog(row)"
            >
              设置角色
            </el-button>
          </el-tooltip>

          <el-tooltip
            :disabled="canOperateUser(row)"
            :content="isSelf(row) ? '不能操作自己' : isSuperAdmin(row) ? '无法操作超级管理员' : '无权操作该用户'"
          >
            <el-button
              v-if="row.status !== 0"
              size="small"
              type="warning"
              plain
              :icon="Lock"
              :disabled="!canOperateUser(row)"
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
              :disabled="!canOperateUser(row)"
              @click="handleUnban(row)"
            >
              解封
            </el-button>
          </el-tooltip>

          <el-tooltip
            :disabled="canOperateUser(row)"
            :content="isSelf(row) ? '不能操作自己' : isSuperAdmin(row) ? '无法操作超级管理员' : '无权操作该用户'"
          >
            <el-button
              size="small"
              type="danger"
              plain
              :icon="Delete"
              :disabled="!canOperateUser(row)"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 权限设置弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="设置用户权限与徽章" width="520px" append-to-body>
      <div style="margin-bottom: 12px; color: var(--el-text-color-secondary); font-size: 14px;">
        用户：{{ roleDialogUser?.nickname || roleDialogUser?.username }}
      </div>

      <div class="permission-block">
        <div class="permission-title">全局角色</div>
        <el-select v-model="selectedRole" placeholder="选择角色" style="width: 100%;">
          <el-option
            v-for="r in availableRoles"
            :key="r.value"
            :label="r.label"
            :value="r.value"
          />
        </el-select>
      </div>

      <div class="permission-block">
        <div class="permission-title">板块版主身份</div>
        <div class="permission-hint">选择后，该用户只会获得对应板块的内容管理与举报处理权限。</div>
        <el-select
          v-model="selectedModeratedSectionIds"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          :loading="sectionsLoading"
          placeholder="请选择负责的板块"
          style="width: 100%;"
        >
          <el-option
            v-for="section in sections"
            :key="section.id"
            :label="section.name"
            :value="Number(section.id)"
          >
            <span>{{ section.name }}</span>
            <span v-if="section.status === 0" class="option-note">已禁用</span>
          </el-option>
        </el-select>
      </div>

      <div class="permission-block">
        <div class="permission-title">用户徽章</div>
        <div class="permission-hint">纯文字徽章（如「你可以访问L站」），跟随该用户显示在帖子/评论/私信处。留空表示清除，最多 20 字。</div>
        <el-input
          v-model="badgeText"
          maxlength="20"
          show-word-limit
          clearable
          placeholder="例如：你可以访问L站"
        />
        <div class="badge-style-row">
          <el-select v-model="badgeStyle" style="width: 150px;">
            <el-option label="纯色" value="solid" />
            <el-option label="七彩跑马 🌈" value="rainbow" />
          </el-select>
          <el-color-picker v-model="badgeColor" :disabled="badgeStyle === 'rainbow'" />
          <span class="badge-style-hint">{{ badgeStyle === 'rainbow' ? '七彩动效，忽略颜色' : '纯色徽章的颜色（留空=默认紫）' }}</span>
        </div>
        <div v-if="badgeText.trim()" class="badge-preview">
          预览：<UserBadge :text="badgeText.trim()" :color="badgeColor" :effect="badgeStyle" />
        </div>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permissionSaving" @click="submitRoleChange">保存权限</el-button>
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
.section-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.muted-text {
  color: var(--el-text-color-placeholder);
}
.user-badge-cell {
  color: #6d28d9;
  font-weight: 600;
  font-size: 12px;
}
:deep(html.dark) .user-badge-cell {
  color: #a78bfa;
}
.permission-block {
  margin-top: 16px;
}
.permission-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.permission-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.badge-style-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.badge-style-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.badge-preview {
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.option-note {
  float: right;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
