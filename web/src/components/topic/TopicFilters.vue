<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { publicDataApi, type PublicSectionItem } from '@/api/publicData'

const props = defineProps<{
  hideCategories?: boolean
}>()

type NavType = 'latest' | 'hot' | 'essence'

const activeNav = ref<NavType>('latest')
const activeCategory = ref('all')
const categories = ref<PublicSectionItem[]>([])
const categoryLoading = ref(false)

const emit = defineEmits<{
  (e: 'filter-change', payload: { navType: NavType; category: string }): void
}>()

const emitFilterChange = () => {
  emit('filter-change', {
    navType: activeNav.value,
    category: props.hideCategories ? 'all' : activeCategory.value,
  })
}

const setNav = (navType: NavType) => {
  if (activeNav.value === navType) return
  activeNav.value = navType
  emitFilterChange()
}

const handleCategoryChange = () => {
  emitFilterChange()
}

const loadCategories = async () => {
  if (props.hideCategories || categoryLoading.value || categories.value.length > 0) return
  categoryLoading.value = true
  try {
    const res = await publicDataApi.getActiveSectionsCached()
    categories.value = Array.isArray(res.data) ? res.data : []
  } catch {
    categories.value = []
  } finally {
    categoryLoading.value = false
  }
}

watch(() => props.hideCategories, (hideCategories) => {
  if (hideCategories) {
    activeCategory.value = 'all'
    emitFilterChange()
    return
  }
  loadCategories()
})

onMounted(() => {
  loadCategories()
})
</script>

<template>
  <div class="topic-filters">
    <div class="filters-main">
      <button class="tab-btn" :class="{ active: activeNav === 'latest' }" @click="setNav('latest')">最新发布</button>
      <button class="tab-btn" :class="{ active: activeNav === 'hot' }" @click="setNav('hot')">热门排行</button>
      <button class="tab-btn" :class="{ active: activeNav === 'essence' }" @click="setNav('essence')">精华文档</button>
    </div>

    <div v-if="!hideCategories" class="filters-side">
      <span class="sort-label">分类</span>
      <el-select
        v-model="activeCategory"
        class="filter-select"
        :loading="categoryLoading"
        @change="handleCategoryChange"
      >
        <el-option label="全部分类" value="all" />
        <el-option
          v-for="category in categories"
          :key="category.id"
          :label="category.name"
          :value="String(category.id)"
        />
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
  width: 160px;
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
    max-width: 220px;
  }
}
</style>