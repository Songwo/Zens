<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { ChatLineRound, View } from '@element-plus/icons-vue'
import { publicDataApi } from '@/api/publicData'

interface Section {
  id: number
  name: string
  description: string
  icon: string
  postCount: number
  todayCount: number
}

const router = useRouter()
const sections = ref<Section[]>([])
const loading = ref(true)

const fetchSections = async () => {
  loading.value = true
  try {
    const res = await publicDataApi.getActiveSectionsCached()
    if (res.code === 2000 || res.code === 200) {
      sections.value = res.data
    }
  } catch (error) {
    console.error('Failed to fetch sections:', error)
  } finally {
    loading.value = false
  }
}

const goToSection = (id: number) => {
  router.push(`/s/${id}`)
}

onMounted(() => {
  fetchSections()
})
</script>

<template>
  <MainLayout>
    <div class="sections-overview">
      <div class="page-header">
        <h1 class="page-title">🏫 板块全景</h1>
        <p class="page-desc">探索校园社区的每一个角落，发现你感兴趣的内容</p>
      </div>

      <div v-loading="loading" class="sections-grid">
        <div 
          v-for="section in sections" 
          :key="section.id" 
          class="section-card"
          @click="goToSection(section.id)"
        >
          <div class="card-header">
            <div class="icon-box">
              <span class="section-icon">{{ section.icon || '📁' }}</span>
            </div>
            <div v-if="section.todayCount > 0" class="new-badge">
              +{{ section.todayCount }} 今日
            </div>
          </div>

          <div class="card-body">
            <h3 class="section-name">{{ section.name }}</h3>
            <p class="section-desc">{{ section.description || '暂无详细描述信息' }}</p>
          </div>

          <div class="card-footer">
            <div class="stat-item">
              <el-icon><ChatLineRound /></el-icon>
              <span>{{ section.postCount || 0 }} 帖子</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <el-icon><View /></el-icon>
              <span>进入板块</span>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="sections.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无板块信息" />
        </div>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.sections-overview {
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 32px;
  text-align: center;
}

.page-title {
  font-size: 32px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
}

.page-desc {
  font-size: 16px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

.sections-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
}

.section-card {
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.section-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--el-box-shadow-light);
  border-color: var(--el-color-primary-light-5);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.icon-box {
  width: 56px;
  height: 56px;
  background: var(--el-color-primary-light-9);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  transition: transform 0.3s;
}

.section-card:hover .icon-box {
  transform: scale(1.1) rotate(5deg);
}

.new-badge {
  background: var(--el-color-danger);
  color: white;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 4px 8px rgba(245, 108, 108, 0.3);
}

.card-body {
  flex: 1;
}

.section-name {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.section-desc {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-extra-light);
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.stat-item .el-icon {
  font-size: 16px;
}

.stat-divider {
  width: 1px;
  height: 14px;
  background: var(--el-border-color-lighter);
}

.section-card:hover .stat-item:last-child {
  color: var(--el-color-primary);
  font-weight: 600;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 80px 0;
}

@media (max-width: 640px) {
  .sections-grid {
    grid-template-columns: 1fr;
  }
}
</style>
