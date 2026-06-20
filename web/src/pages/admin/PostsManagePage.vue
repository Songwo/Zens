<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { sectionApi, type Section } from '@/api/section'
import { useUserStore } from '@/store/user'
import { ensureCurrentUserProfile, hasAdminRole } from '@/utils/sessionProfile'
import type { Post } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, View, Delete, Top, Medal, CircleCheck, CircleClose, RefreshRight, Select } from '@element-plus/icons-vue'
import EmptyState from '@/components/common/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const posts = ref<Post[]>([])
const loading = ref(false)
const searchQuery = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const selectedPosts = ref<Post[]>([])
const batchLoading = ref(false)

// 板块筛选
const allSections = ref<Section[]>([])

const readRouteSectionId = () => {
  const raw = Array.isArray(route.query.sectionId) ? route.query.sectionId[0] : route.query.sectionId
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : undefined
}

const selectedSectionId = ref<number | undefined>(readRouteSectionId())

const isAdmin = computed(() => hasAdminRole(userStore.userInfo))
const moderatedSectionIds = computed<number[]>(() => {
  const rawIds = Array.isArray((userStore.userInfo as any)?.moderatedSectionIds)
    ? ((userStore.userInfo as any)?.moderatedSectionIds as Array<number | string>)
    : []
  return rawIds
    .map(id => Number(id))
    .filter(id => Number.isFinite(id) && id > 0)
})

const isTrashMode = computed(() => route.name === 'admin-posts-trash' || route.path.endsWith('/trash'))
const pageTitle = computed(() => isTrashMode.value ? '内容回收站' : '内容管理')
const pageSubtitle = computed(() => isTrashMode.value
  ? '集中查看已移入回收站的帖子，删除后 7 天内可恢复。'
  : '按当前账号可管理的板块范围加载内容，管理员可查看全部。')
const emptyTitle = computed(() => isTrashMode.value ? '回收站暂无内容' : '暂无帖子数据')
const emptyDescription = computed(() => isTrashMode.value ? '已删除的帖子会在这里集中显示' : '尝试调整搜索关键词')
const manageablePosts = computed(() => posts.value.filter(canManageRow))
const pendingCount = computed(() => posts.value.filter(post => post.auditStatus === 'PENDING').length)
const publishedCount = computed(() => posts.value.filter(post => post.auditStatus === 'APPROVED' || post.status === 1).length)
const deletedCount = computed(() => posts.value.filter(post => post.auditStatus === 'DELETED').length)
const featuredCount = computed(() => posts.value.filter(post => post.isFeatured === 1).length)
const selectedManageablePosts = computed(() => selectedPosts.value.filter(canManageRow))
const selectedPendingPosts = computed(() => selectedManageablePosts.value.filter(post => post.auditStatus === 'PENDING' || post.auditStatus === 'REJECTED'))
const selectedActivePosts = computed(() => selectedManageablePosts.value.filter(post => !isDeletedPost(post)))
const selectedDeletedPosts = computed(() => selectedManageablePosts.value.filter(isDeletedPost))

// 版主只看自己板块；管理员看全部
const availableSections = computed(() => {
  if (isAdmin.value) return allSections.value
  return allSections.value.filter(s => moderatedSectionIds.value.includes(Number(s.id)))
})

const moderatedSectionNames = computed(() => {
  const nameById = new Map(allSections.value.map(section => [Number(section.id), section.name]))
  return moderatedSectionIds.value.map(id => nameById.get(id) || `板块 #${id}`)
})

const scopeSummary = computed(() => {
  if (isAdmin.value) return '当前账号为管理员，可管理全部板块。'
  if (moderatedSectionNames.value.length === 0) return '当前账号尚未加载到可管理板块。'
  return `当前账号是 ${moderatedSectionNames.value.join('、')} 的版主。`
})

const canManageRow = (row: Post) => {
  if (isAdmin.value) return true
  return Boolean(row.sectionId && moderatedSectionIds.value.includes(Number(row.sectionId)))
}

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

const fetchSections = async () => {
  try {
    const res = await sectionApi.getList()
    allSections.value = res.data || []
    // 版主若未指定板块，默认选第一个负责的板块
    if (!isAdmin.value && selectedSectionId.value === undefined && moderatedSectionIds.value.length > 0) {
      selectedSectionId.value = moderatedSectionIds.value[0]
    }
  } catch {
    // ignore
  }
}

const fetchPosts = async () => {
  loading.value = true
  try {
    const res = await postApi.getModerationList({
      page: page.value,
      pageSize: pageSize.value,
      needTotal: true,
      keyword: searchQuery.value || undefined,
      sectionId: selectedSectionId.value,
      status: undefined,
      auditStatus: isTrashMode.value ? 'DELETED' : undefined
    })

    const pageData = unwrapPageData<Post>(res)
    posts.value = pageData.records
    total.value = pageData.total
    selectedPosts.value = []
  } catch (error) {
    ElMessage.error('获取帖子列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchPosts()
}

const handleSectionFilter = () => {
  page.value = 1
  fetchPosts()
}

const handleModeChange = (mode: string | number | boolean) => {
  const path = mode === 'trash' ? '/admin/posts/trash' : '/admin/posts'
  router.push({
    path,
    query: selectedSectionId.value ? { sectionId: String(selectedSectionId.value) } : {}
  })
}

const handlePageChange = (val: number) => {
  page.value = val
  fetchPosts()
}

const isDeletedPost = (row: Post) => row.auditStatus === 'DELETED'

const handleSelectionChange = (rows: Post[]) => {
  selectedPosts.value = rows
}

const runBatch = async (
  rows: Post[],
  actionName: string,
  action: (post: Post) => Promise<unknown>,
  confirmText?: string
) => {
  if (rows.length === 0) {
    ElMessage.warning('请先选择可操作的帖子')
    return
  }
  try {
    if (confirmText) {
      await ElMessageBox.confirm(confirmText, `批量${actionName}`, {
        confirmButtonText: `批量${actionName}`,
        cancelButtonText: '取消',
        type: 'warning',
      })
    }
    batchLoading.value = true
    const results = await Promise.allSettled(rows.map(row => action(row)))
    const success = results.filter(result => result.status === 'fulfilled').length
    const failed = results.length - success
    if (failed > 0) {
      ElMessage.warning(`批量${actionName}完成：成功 ${success} 条，失败 ${failed} 条`)
    } else {
      ElMessage.success(`已批量${actionName} ${success} 条`)
    }
    await fetchPosts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`批量${actionName}失败`)
    }
  } finally {
    batchLoading.value = false
  }
}

const batchApprovePosts = () => {
  runBatch(selectedPendingPosts.value, '通过审核', post => postApi.approve(post.id))
}

const batchDeletePosts = () => {
  runBatch(
    selectedActivePosts.value,
    '移入回收站',
    post => postApi.delete(post.id),
    `确定将选中的 ${selectedActivePosts.value.length} 篇帖子移入回收站吗？7 天内可恢复。`
  )
}

const batchRestorePosts = () => {
  runBatch(
    selectedDeletedPosts.value,
    '恢复',
    post => postApi.restore(post.id),
    `确定恢复选中的 ${selectedDeletedPosts.value.length} 篇帖子吗？`
  )
}

const deletePost = async (postId: string) => {
  const target = posts.value.find(post => post.id === postId)
  if (target && !canManageRow(target)) {
    ElMessage.warning('只能管理自己负责板块内的帖子')
    return
  }
  try {
    await ElMessageBox.confirm('确定要将这篇帖子移入回收站吗？7 天内可恢复。', '软删除确认', {
      confirmButtonText: '移入回收站',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })

    await postApi.delete(postId)
    ElMessage.success('已移入回收站')
    fetchPosts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const restorePost = async (postId: string) => {
  const target = posts.value.find(post => post.id === postId)
  if (target && !canManageRow(target)) {
    ElMessage.warning('只能恢复自己负责板块内的帖子')
    return
  }
  try {
    await postApi.restore(postId)
    ElMessage.success('帖子已恢复')
    fetchPosts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('恢复失败')
    }
  }
}

const handleView = (postId: string) => {
  window.open(`/p/${postId}`, '_blank')
}

const toggleCategoryPin = async (row: Post) => {
  if (!canManageRow(row)) {
    ElMessage.warning('只能管理自己负责板块内的帖子')
    return
  }
  try {
    await postApi.setCategoryPin(row.id)
    row.categoryPin = row.categoryPin === 1 ? 0 : 1
    ElMessage.success(row.categoryPin === 1 ? '已设为板块置顶' : '已取消板块置顶')
  } catch {
    ElMessage.error('操作失败')
  }
}

const toggleFeature = async (row: Post) => {
  if (!canManageRow(row)) {
    ElMessage.warning('只能管理自己负责板块内的帖子')
    return
  }
  try {
    await postApi.feature(row.id)
    row.isFeatured = row.isFeatured === 1 ? 0 : 1
    ElMessage.success(row.isFeatured === 1 ? '已设为精华' : '已取消精华')
  } catch {
    ElMessage.error('操作失败')
  }
}

const rejectDialogVisible = ref(false)
const rejectingPostId = ref('')
const rejectReason = ref('')

const openRejectDialog = (postId: string) => {
  const target = posts.value.find(post => post.id === postId)
  if (target && !canManageRow(target)) {
    ElMessage.warning('只能管理自己负责板块内的帖子')
    return
  }
  rejectingPostId.value = postId
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写打回原因')
    return
  }
  try {
    await postApi.reject(rejectingPostId.value, rejectReason.value)
    ElMessage.success('已打回该帖子，并通知作者')
    rejectDialogVisible.value = false
    fetchPosts()
  } catch {
    ElMessage.error('操作失败')
  }
}

const approvePost = async (postId: string) => {
  const target = posts.value.find(post => post.id === postId)
  if (target && !canManageRow(target)) {
    ElMessage.warning('只能管理自己负责板块内的帖子')
    return
  }
  try {
    await postApi.approve(postId)
    ElMessage.success('已通过审核')
    fetchPosts()
  } catch {
    ElMessage.error('操作失败')
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getAuditStatusType = (row: Post) => {
  if (row.auditStatus === 'DELETED') return 'danger'
  if (row.auditStatus === 'REJECTED') return 'danger'
  if (row.auditStatus === 'DRAFT' || row.status === 0) return 'info'
  if (row.auditStatus === 'PENDING') return 'warning'
  if (row.auditStatus === 'APPROVED' || row.status === 1) return 'success'
  return 'info'
}

const getAuditStatusLabel = (row: Post) => {
  if (row.auditStatus === 'DELETED') return '已删除'
  if (row.auditStatus === 'REJECTED') return '已打回'
  if (row.auditStatus === 'DRAFT' || row.status === 0) return '草稿'
  if (row.auditStatus === 'PENDING') return '待审核'
  if (row.auditStatus === 'APPROVED' || row.status === 1) return '已发布'
  return '未知状态'
}

onMounted(async () => {
  await ensureCurrentUserProfile({ force: true })
  await fetchSections()
  fetchPosts()
})

watch(
  () => [route.name, route.query.sectionId],
  () => {
    selectedSectionId.value = readRouteSectionId()
    page.value = 1
    fetchPosts()
  }
)
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <div class="header-info">
        <h1 class="title">{{ pageTitle }}</h1>
        <p class="subtitle">{{ pageSubtitle }}</p>
        <p class="scope-line">{{ scopeSummary }}</p>
        <div v-if="!isAdmin && moderatedSectionNames.length > 0" class="scope-tags">
          <el-tag
            v-for="name in moderatedSectionNames"
            :key="name"
            size="small"
            type="success"
            effect="plain"
          >
            {{ name }}
          </el-tag>
        </div>
      </div>
      
      <div class="header-actions">
        <el-radio-group :model-value="isTrashMode ? 'trash' : 'list'" @change="(mode?: string | number | boolean) => handleModeChange(mode ?? 'list')">
          <el-radio-button label="list">全部内容</el-radio-button>
          <el-radio-button label="trash">回收站</el-radio-button>
        </el-radio-group>
        <el-select
          v-model="selectedSectionId"
          placeholder="全部板块"
          clearable
          style="width: 160px"
          @change="handleSectionFilter"
          @clear="handleSectionFilter"
        >
          <el-option
            v-for="s in availableSections"
            :key="s.id"
            :label="s.name"
            :value="Number(s.id)"
          />
        </el-select>
        <el-input
          v-model="searchQuery"
          placeholder="搜索帖子标题或内容..."
          clearable
          class="search-input"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
      </div>
    </div>

    <div class="admin-summary-grid">
      <div class="summary-card">
        <span class="summary-label">当前页可管理</span>
        <strong>{{ manageablePosts.length }}</strong>
      </div>
      <div class="summary-card warning">
        <span class="summary-label">待审核</span>
        <strong>{{ pendingCount }}</strong>
      </div>
      <div class="summary-card success">
        <span class="summary-label">已发布</span>
        <strong>{{ publishedCount }}</strong>
      </div>
      <div class="summary-card">
        <span class="summary-label">{{ isTrashMode ? '已删除' : '精华内容' }}</span>
        <strong>{{ isTrashMode ? deletedCount : featuredCount }}</strong>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <div class="bulk-toolbar" :class="{ active: selectedPosts.length > 0 }">
        <div class="bulk-info">
          <el-icon><Select /></el-icon>
          <span>已选择 {{ selectedPosts.length }} 条，可操作 {{ selectedManageablePosts.length }} 条</span>
        </div>
        <div class="bulk-actions">
          <el-button
            v-if="!isTrashMode"
            size="small"
            type="success"
            plain
            :disabled="selectedPendingPosts.length === 0"
            :loading="batchLoading"
            :icon="CircleCheck"
            @click="batchApprovePosts"
          >
            批量通过 {{ selectedPendingPosts.length || '' }}
          </el-button>
          <el-button
            v-if="!isTrashMode"
            size="small"
            type="danger"
            plain
            :disabled="selectedActivePosts.length === 0"
            :loading="batchLoading"
            :icon="Delete"
            @click="batchDeletePosts"
          >
            批量移入回收站 {{ selectedActivePosts.length || '' }}
          </el-button>
          <el-button
            v-else
            size="small"
            type="success"
            plain
            :disabled="selectedDeletedPosts.length === 0"
            :loading="batchLoading"
            :icon="RefreshRight"
            @click="batchRestorePosts"
          >
            批量恢复 {{ selectedDeletedPosts.length || '' }}
          </el-button>
        </div>
      </div>
      <el-table 
        v-loading="loading" 
        :data="posts" 
        style="width: 100%"
        :header-cell-style="{ backgroundColor: 'var(--el-fill-color-light)', fontWeight: '800' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="44" :selectable="canManageRow" />
        <el-table-column label="标题" min-width="250">
          <template #default="{ row }">
            <el-link 
              :href="`/p/${row.id}`" 
              target="_blank" 
              type="primary" 
              class="post-title"
              :underline="false"
            >
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        
        <el-table-column prop="authorName" label="作者" width="120" />
        
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.sectionName || '未分类' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="getAuditStatusType(row)"
            >
              {{ getAuditStatusLabel(row) }}
            </el-tag>
            <div v-if="row.auditStatus === 'REJECTED' && row.rejectReason" class="reject-reason-tip">{{ row.rejectReason }}</div>
            <div v-if="row.auditStatus === 'DELETED'" class="deleted-tip">7 天内可恢复</div>
          </template>
        </el-table-column>

        <el-table-column label="特殊标志" width="120" align="center">
          <template #default="{ row }">
            <div style="display: flex; flex-direction: column; gap: 4px; align-items: center;">
              <el-tag v-if="row.globalPin === 1" size="small" type="danger" effect="dark">全站置顶</el-tag>
              <el-tag v-if="row.categoryPin === 1" size="small" type="warning" effect="dark">板块置顶</el-tag>
              <el-tag v-if="row.isFeatured === 1" size="small" type="success" effect="dark">精华</el-tag>
              <span v-if="row.globalPin !== 1 && row.categoryPin !== 1 && row.isFeatured !== 1" class="time-text">—</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="互动数据" width="180">
          <template #default="{ row }">
            <div class="stat-tags">
              <span class="stat-pill">阅 {{ row.viewCount }}</span>
              <span class="stat-pill">赞 {{ row.likeCount }}</span>
              <span class="stat-pill">评 {{ row.commentCount }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-btns">
              <el-tooltip v-if="!isDeletedPost(row) && canManageRow(row)" :content="row.categoryPin === 1 ? '取消板块置顶' : '板块置顶'" placement="top">
                <el-button
                  circle
                  size="small"
                  :type="row.categoryPin === 1 ? 'warning' : 'default'"
                  :icon="Top"
                  @click="toggleCategoryPin(row)"
                />
              </el-tooltip>
              <el-tooltip v-if="!isDeletedPost(row) && canManageRow(row)" :content="row.isFeatured ? '取消精华' : '设为精华'" placement="top">
                <el-button
                  circle
                  size="small"
                  :type="row.isFeatured ? 'success' : 'default'"
                  :icon="Medal"
                  @click="toggleFeature(row)"
                />
              </el-tooltip>
              <el-tooltip v-if="!isDeletedPost(row) && canManageRow(row) && (row.auditStatus === 'PENDING' || row.auditStatus === 'REJECTED')" content="通过审核" placement="top">
                <el-button
                  circle
                  size="small"
                  type="success"
                  :icon="CircleCheck"
                  @click="approvePost(row.id)"
                />
              </el-tooltip>
              <el-tooltip v-if="!isDeletedPost(row) && canManageRow(row)" content="打回修改" placement="top">
                <el-button
                  circle
                  size="small"
                  type="warning"
                  :icon="CircleClose"
                  @click="openRejectDialog(row.id)"
                />
              </el-tooltip>
              <el-tooltip content="预览" placement="top">
                <el-button
                  circle
                  size="small"
                  :icon="View"
                  @click="handleView(row.id)"
                />
              </el-tooltip>
              <el-tooltip v-if="isDeletedPost(row) && canManageRow(row)" content="恢复" placement="top">
                <el-button
                  circle
                  size="small"
                  type="success"
                  plain
                  :icon="RefreshRight"
                  @click="restorePost(row.id)"
                />
              </el-tooltip>
              <el-tooltip v-else-if="canManageRow(row)" content="移入回收站" placement="top">
                <el-button
                  circle
                  size="small"
                  type="danger"
                  plain
                  :icon="Delete"
                  @click="deletePost(row.id)"
                />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState 
            v-if="!loading"
            :title="emptyTitle"
            :description="emptyDescription"
          />
        </template>
      </el-table>

      <div class="pagination-container" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchPosts"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 打回原因对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="打回帖子" width="420px" append-to-body>
      <p style="color:var(--el-text-color-secondary);margin-bottom:12px">请填写打回原因，作者将收到通知。</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="例如：内容违规、标题党、重复发布..."
        maxlength="200"
        show-word-limit
      />
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmReject">确认打回</el-button>
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
  gap: 18px;
}

.header-info .title {
  margin: 0;
  font-size: 28px;
  font-weight: 900;
  color: var(--el-text-color-primary);
  letter-spacing: -0.02em;
}

.header-info .subtitle {
  margin: 4px 0 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.search-input {
  width: 320px;
}

.table-card {
  border-radius: 12px;
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

.summary-card.success strong {
  color: var(--el-color-success);
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

.post-title {
  font-weight: 700;
  color: var(--el-text-color-primary) !important;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.post-title:hover {
  color: var(--el-color-primary) !important;
}

.stat-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.stat-pill {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 100px;
  white-space: nowrap;
}

.time-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.action-btns {
  display: flex;
  gap: 8px;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table__row) {
  height: 70px;
}

.reject-reason-tip {
  font-size: 11px;
  color: var(--el-color-danger);
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

.deleted-tip {
  font-size: 11px;
  color: var(--el-color-danger);
  margin-top: 3px;
}

@media (max-width: 768px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions,
  .search-input {
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
