<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { postApi } from '@/api/post'
import { sectionApi, type Section } from '@/api/section'
import { useUserStore } from '@/store/user'
import { hasAdminRole } from '@/utils/sessionProfile'
import type { Post } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, View, Delete, Top, Medal, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import EmptyState from '@/components/common/EmptyState.vue'

const route = useRoute()
const userStore = useUserStore()

const posts = ref<Post[]>([])
const loading = ref(false)
const searchQuery = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 板块筛选
const allSections = ref<Section[]>([])
const selectedSectionId = ref<number | undefined>(
  route.query.sectionId ? Number(route.query.sectionId) : undefined
)

const isAdmin = computed(() => hasAdminRole(userStore.userInfo))
const moderatedSectionIds = computed<number[]>(
  () => (userStore.userInfo as any)?.moderatedSectionIds || []
)

// 版主只看自己板块；管理员看全部
const availableSections = computed(() => {
  if (isAdmin.value) return allSections.value
  return allSections.value.filter(s => moderatedSectionIds.value.includes(Number(s.id)))
})

const fetchSections = async () => {
  try {
    const res = await sectionApi.getActiveList()
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
      status: undefined
    })

    posts.value = res.data.records || []
    total.value = res.data.total || 0
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

const handlePageChange = (val: number) => {
  page.value = val
  fetchPosts()
}

const deletePost = async (postId: string) => {
  try {
    await ElMessageBox.confirm('确定要永久删除这篇帖子吗？此操作不可撤销。', '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })

    await postApi.delete(postId)
    ElMessage.success('删除成功')
    fetchPosts()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleView = (postId: string) => {
  window.open(`/p/${postId}`, '_blank')
}

const toggleCategoryPin = async (row: Post) => {
  try {
    await postApi.setCategoryPin(row.id)
    row.categoryPin = row.categoryPin === 1 ? 0 : 1
    ElMessage.success(row.categoryPin === 1 ? '已设为板块置顶' : '已取消板块置顶')
  } catch {
    ElMessage.error('操作失败')
  }
}

const toggleFeature = async (row: Post) => {
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

onMounted(async () => {
  await fetchSections()
  fetchPosts()
})
</script>

<template>
  <div class="manage-page">
    <div class="page-header">
      <div class="header-info">
        <h1 class="title">内容管理</h1>
        <p class="subtitle">按当前账号可管理的板块范围加载内容，管理员可查看全部。</p>
      </div>
      
      <div class="header-actions">
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

    <el-card shadow="never" class="table-card">
      <el-table 
        v-loading="loading" 
        :data="posts" 
        style="width: 100%"
        :header-cell-style="{ backgroundColor: 'var(--el-fill-color-light)', fontWeight: '800' }"
      >
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
              :type="row.auditStatus === 'APPROVED' || row.status === 1 ? 'success' : row.auditStatus === 'REJECTED' ? 'danger' : row.auditStatus === 'DRAFT' ? 'info' : 'warning'"
            >
              {{ row.auditStatus === 'APPROVED' || row.status === 1 ? '已发布' : row.auditStatus === 'REJECTED' ? '已打回' : row.auditStatus === 'DRAFT' ? '草稿' : '待审核' }}
            </el-tag>
            <div v-if="row.auditStatus === 'REJECTED' && row.rejectReason" class="reject-reason-tip">{{ row.rejectReason }}</div>
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
              <span class="stat-pill">👀 {{ row.viewCount }}</span>
              <span class="stat-pill">❤️ {{ row.likeCount }}</span>
              <span class="stat-pill">💬 {{ row.commentCount }}</span>
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
              <el-tooltip :content="row.categoryPin === 1 ? '取消板块置顶' : '板块置顶'" placement="top">
                <el-button
                  circle
                  size="small"
                  :type="row.categoryPin === 1 ? 'warning' : 'default'"
                  :icon="Top"
                  @click="toggleCategoryPin(row)"
                />
              </el-tooltip>
              <el-tooltip :content="row.isFeatured ? '取消精华' : '设为精华'" placement="top">
                <el-button
                  circle
                  size="small"
                  :type="row.isFeatured ? 'success' : 'default'"
                  :icon="Medal"
                  @click="toggleFeature(row)"
                />
              </el-tooltip>
              <el-tooltip v-if="row.status !== 1" content="通过审核" placement="top">
                <el-button
                  circle
                  size="small"
                  type="success"
                  :icon="CircleCheck"
                  @click="approvePost(row.id)"
                />
              </el-tooltip>
              <el-tooltip content="打回修改" placement="top">
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
              <el-tooltip content="删除" placement="top">
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
            title="暂无帖子数据" 
            description="尝试调整搜索关键词"
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
}

.search-input {
  width: 320px;
}

.table-card {
  border-radius: 12px;
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
</style>
