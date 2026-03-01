<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  hideCategories?: boolean
}>()

const activeTab = ref('latest')
const selectedSort = ref('default')

const emit = defineEmits<{
  (e: 'filter-change', payload: { tab: string; sort: string }): void
}>()

const setTab = (tab: string) => {
  activeTab.value = tab
  emit('filter-change', { tab: activeTab.value, sort: selectedSort.value })
}

const handleSortChange = () => {
  emit('filter-change', { tab: activeTab.value, sort: selectedSort.value })
}
</script>

<template>
  <div class="topic-filters">
    <div class="filters-main">
      <button class="tab-btn" :class="{ active: activeTab === 'latest' }" @click="setTab('latest')">最新发布</button>
      <button class="tab-btn" :class="{ active: activeTab === 'top' }" @click="setTab('top')">热门排行</button>
      <button
        v-if="!hideCategories"
        class="tab-btn"
        :class="{ active: activeTab === 'categories' }"
        @click="setTab('categories')"
      >
        板块分类
      </button>
      <button class="tab-btn" :class="{ active: activeTab === 'docs' }" @click="setTab('docs')">精华文档</button>
    </div>

    <div class="filters-side">
      <span class="sort-label">排序</span>
      <el-select v-model="selectedSort" class="filter-select" @change="handleSortChange">
        <el-option label="默认" value="default" />
        <el-option label="评论最多" value="comments" />
        <el-option label="近期活跃" value="active" />
      </el-select>
    </div>
  </div>
</template>

<style scoped>
.topic-filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color-overlay);
  margin-bottom: 14px;
}

.filters-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tab-btn {
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  border-radius: 999px;
  padding: 7px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.tab-btn:hover {
  border-color: var(--cp-primary-light);
  color: var(--cp-primary-dark);
  transform: translateY(-1px);
}

.tab-btn.active {
  color: #7a5700;
  background: #fff3d4;
  border-color: #f4b400;
  box-shadow: inset 0 0 0 1px rgba(244, 180, 0, 0.2);
}

.filters-side {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.sort-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.filter-select {
  width: 120px;
}

@media (max-width: 768px) {
  .topic-filters {
    padding: 10px;
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .filters-side {
    justify-content: space-between;
  }

  .filter-select {
    flex: 1;
    max-width: 160px;
  }
}
</style>
