<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, CopyDocument, Delete, Refresh } from '@element-plus/icons-vue'
import api from '@/lib/api'
import type { Result } from '@/types'

interface InviteCode {
  id: string
  code: string
  creatorId: string
  usedByUserId: string | null
  status: number
  maxUses: number
  usedCount: number
  expireTime: string | null
  remark: string | null
  createTime: string
}

const codes = ref<InviteCode[]>([])
const loading = ref(false)
const generating = ref(false)

const genForm = ref({
  count: 1,
  maxUses: 1,
  expireDays: 7,
  remark: ''
})

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未使用', type: 'success' },
  1: { label: '已用完', type: 'info' },
  2: { label: '已禁用', type: 'danger' },
}

const totalCount = computed(() => codes.value.length)
const usedCount = computed(() => codes.value.filter(c => c.status === 1 || c.usedCount > 0).length)
const availableCount = computed(() => codes.value.filter(c => c.status === 0).length)

const fetchCodes = async () => {
  loading.value = true
  try {
    const res = await api.get<any, Result<InviteCode[]>>('/invite/list')
    if (res.code === 2000) codes.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const generate = async () => {
  generating.value = true
  try {
    const res = await api.post<any, Result<string[]>>('/invite/generate', genForm.value)
    if (res.code === 2000) {
      ElMessage.success(`成功生成 ${res.data?.length || 0} 个邀请码`)
      await fetchCodes()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const copyCode = (code: string) => {
  navigator.clipboard.writeText(code).then(() => {
    ElMessage.success('邀请码已复制')
  }).catch(() => {
    ElMessage.warning(`请手动复制: ${code}`)
  })
}

const copyLink = (code: string) => {
  const link = `${window.location.origin}/auth?type=register&invite=${code}`
  navigator.clipboard.writeText(link).then(() => {
    ElMessage.success('邀请链接已复制')
  }).catch(() => {
    ElMessage.warning(`请手动复制: ${link}`)
  })
}

const disableCode = async (code: string) => {
  await ElMessageBox.confirm('确定禁用此邀请码？', '确认', { type: 'warning' })
  try {
    await api.post('/invite/disable', { code })
    ElMessage.success('已禁用')
    await fetchCodes()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const formatDate = (d: string | null) => {
  if (!d) return '永不过期'
  const dt = new Date(d)
  return dt.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const isExpired = (c: InviteCode) => {
  if (!c.expireTime) return false
  return new Date(c.expireTime) < new Date()
}

onMounted(fetchCodes)
</script>

<template>
  <div class="invite-page">
    <div class="page-header">
      <h2 class="page-title">邀请码管理</h2>
      <p class="page-desc">生成邀请码并分发给用户，被邀请人注册成功后邀请人获得 <strong>30 经验值</strong>奖励。</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-val">{{ totalCount }}</div>
        <div class="stat-label">总邀请码</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-val success">{{ availableCount }}</div>
        <div class="stat-label">可用</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-val used">{{ usedCount }}</div>
        <div class="stat-label">已使用/禁用</div>
      </el-card>
    </div>

    <!-- 生成表单 -->
    <el-card shadow="never" class="gen-card">
      <template #header>
        <span>生成新邀请码</span>
      </template>
      <div class="gen-form">
        <el-form :model="genForm" inline label-width="80px">
          <el-form-item label="数量">
            <el-input-number v-model="genForm.count" :min="1" :max="50" style="width:100px" />
          </el-form-item>
          <el-form-item label="最大用次">
            <el-input-number v-model="genForm.maxUses" :min="1" :max="100" style="width:100px" />
          </el-form-item>
          <el-form-item label="有效天数">
            <el-input-number v-model="genForm.expireDays" :min="0" :max="365" style="width:100px" />
            <span class="hint">（0=永不过期）</span>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="genForm.remark" placeholder="可选备注" style="width:160px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="generating" :icon="Plus" @click="generate">生成</el-button>
            <el-button :icon="Refresh" @click="fetchCodes" :loading="loading">刷新</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- 邀请码列表 -->
    <el-card shadow="never" class="list-card">
      <template #header><span>邀请码记录</span></template>
      <el-table :data="codes" v-loading="loading" stripe size="small">
        <el-table-column label="邀请码" width="160">
          <template #default="{ row }">
            <code class="code-text">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="isExpired(row) ? 'warning' : statusMap[row.status]?.type" size="small">
              {{ isExpired(row) ? '已过期' : statusMap[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="使用" width="80">
          <template #default="{ row }">
            {{ row.usedCount }} / {{ row.maxUses === 0 ? '∞' : row.maxUses }}
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="130">
          <template #default="{ row }">{{ formatDate(row.expireTime) }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="100" show-overflow-tooltip />
        <el-table-column label="创建时间" width="130">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :icon="CopyDocument" @click="copyCode(row.code)">复制码</el-button>
            <el-button link type="success" size="small" @click="copyLink(row.code)">复制链接</el-button>
            <el-button v-if="row.status === 0" link type="danger" size="small" :icon="Delete" @click="disableCode(row.code)">禁用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.invite-page { padding: 24px; max-width: 1100px; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 1.4rem; margin: 0 0 6px; }
.page-desc { color: var(--el-text-color-secondary); margin: 0; font-size: 0.9rem; }
.stat-row { display: flex; gap: 12px; margin-bottom: 20px; }
.stat-card { flex: 1; text-align: center; }
.stat-val { font-size: 2rem; font-weight: 700; color: var(--el-text-color-primary); }
.stat-val.success { color: var(--el-color-success); }
.stat-val.used { color: var(--el-text-color-secondary); }
.stat-label { font-size: 0.85rem; color: var(--el-text-color-secondary); margin-top: 4px; }
.gen-card { margin-bottom: 16px; }
.gen-form .hint { font-size: 0.8rem; color: var(--el-text-color-placeholder); margin-left: 6px; }
.list-card { }
.code-text { font-family: monospace; font-size: 0.95rem; letter-spacing: 1px; color: var(--el-color-primary); }
</style>
