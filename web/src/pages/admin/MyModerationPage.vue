<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { sectionApi, type Section } from '@/api/section'
import { Document, Flag, Grid } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const sections = ref<Section[]>([])
const loading = ref(false)

const moderatedSectionIds = computed<number[]>(
  () => (userStore.userInfo as any)?.moderatedSectionIds || []
)

const moderatedSections = computed(() =>
  sections.value.filter(s => moderatedSectionIds.value.includes(Number(s.id)))
)

const fetchSections = async () => {
  loading.value = true
  try {
    const res = await sectionApi.getActiveList()
    sections.value = res.data || []
  } catch {
    ElMessage.error('板块信息加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void fetchSections()
})

const goToPosts = (sectionId: string) => {
  router.push({ path: '/admin/posts', query: { sectionId } })
}

const goToReports = (sectionId: string) => {
  router.push({ path: '/admin/reports', query: { sectionId } })
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h2>我的板块</h2>
        <p>以下是您担任版主的板块，可进行内容管理与举报处理。</p>
      </div>
    </div>

    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="moderatedSections.length === 0" class="empty-wrap">
      <el-empty description="暂未负责任何板块">
        <template #description>
          <p>您尚未通过任何板块的版主审核，或版主资格已失效。</p>
        </template>
      </el-empty>
    </div>

    <div v-else class="section-grid">
      <el-card
        v-for="section in moderatedSections"
        :key="section.id"
        shadow="never"
        class="section-card"
      >
        <div class="card-header">
          <div class="icon-box">
            <span class="section-icon">{{ section.icon || '📁' }}</span>
          </div>
          <div class="card-info">
            <h3 class="section-name">{{ section.name }}</h3>
            <p class="section-desc">{{ section.description || '暂无描述' }}</p>
          </div>
          <el-tag type="success" size="small" class="moderator-badge">版主</el-tag>
        </div>

        <div class="card-stats">
          <span class="stat-item">
            <el-icon><Grid /></el-icon>
            {{ section.postCount || 0 }} 篇帖子
          </span>
        </div>

        <div class="card-actions">
          <el-button type="primary" plain :icon="Document" @click="goToPosts(section.id)">
            内容管理
          </el-button>
          <el-button type="warning" plain :icon="Flag" @click="goToReports(section.id)">
            举报管理
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 0;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.page-header p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.loading-wrap,
.empty-wrap {
  padding: 40px 0;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.section-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.icon-box {
  width: 48px;
  height: 48px;
  background: var(--el-color-primary-light-9);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.section-name {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.section-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.moderator-badge {
  flex-shrink: 0;
}

.card-stats {
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.card-actions {
  display: flex;
  gap: 10px;
}

.card-actions .el-button {
  flex: 1;
}
</style>
