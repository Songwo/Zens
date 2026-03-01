<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import { sectionApi } from '@/api/section'
import type { PostSearchRequest } from '@/types'

const route = useRoute()
const sectionInfo = ref<any>(null)
const topicListRef = ref<any>(null)

const query = ref<PostSearchRequest>({
  sectionId: parseInt(route.params.id as string)
})

const fetchSectionInfo = async () => {
  try {
    const res = await sectionApi.getById(route.params.id as string)
    sectionInfo.value = res.data
  } catch (error) {
    console.error('Failed to fetch section info:', error)
  }
}

watch(() => route.params.id, (newId) => {
  if (newId) {
    query.value = { sectionId: parseInt(newId as string) }
    fetchSectionInfo()
    if (topicListRef.value) {
      topicListRef.value.refreshLatest()
    }
  }
})

onMounted(() => {
  fetchSectionInfo()
})
</script>

<template>
  <MainLayout>
    <div class="section-page">
      <div v-if="sectionInfo" class="section-header">
        <div class="header-main">
          <div class="icon-box">
            <span class="section-icon">{{ sectionInfo.icon || '📁' }}</span>
          </div>
          <div class="info-content">
            <h1 class="section-name">{{ sectionInfo.name }}</h1>
            <p class="section-desc">{{ sectionInfo.description || '暂无板块描述' }}</p>
          </div>
        </div>
        
        <div class="section-stats">
          <div class="stat-item">
            <span class="stat-value">{{ sectionInfo.postCount || 0 }}</span>
            <span class="stat-label">帖子</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ sectionInfo.viewCount || 0 }}</span>
            <span class="stat-label">热度</span>
          </div>
        </div>
      </div>

      <TopicList ref="topicListRef" :default-query="query" :hide-categories="true" />
    </div>
  </MainLayout>
</template>

<style scoped>
.section-page {
  padding-bottom: 40px;
}

.section-header {
  margin-bottom: 24px;
  background: var(--el-bg-color-overlay);
  padding: 32px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  box-shadow: var(--el-box-shadow-lighter);
}

.header-main {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
}

.icon-box {
  width: 64px;
  height: 64px;
  background: var(--el-color-primary-light-9);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}

.section-name {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.section-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.section-stats {
  display: flex;
  align-items: center;
  background: var(--el-fill-color-lighter);
  padding: 12px 24px;
  border-radius: 12px;
  gap: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: var(--el-border-color-lighter);
}

@media (max-width: 768px) {
  .section-header {
    flex-direction: column;
    padding: 24px;
    align-items: flex-start;
  }
  
  .section-stats {
    width: 100%;
    justify-content: space-around;
  }
}
</style>
