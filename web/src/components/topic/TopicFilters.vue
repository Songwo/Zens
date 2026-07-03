<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { publicDataApi, type PublicSectionItem } from '@/api/publicData'

const props = defineProps<{
  hideCategories?: boolean
  modelValue?: NavType
}>()

type NavType = 'latest' | 'recommend' | 'hot' | 'essence' | 'unsolved' | 'solved' | 'pinned'

const activeNav = ref<NavType>('latest')
const activeCategory = ref('all')
const categories = ref<PublicSectionItem[]>([])
const categoryLoading = ref(false)
const navItems = computed(() => [
  { key: 'latest' as NavType, label: '最新' },
  { key: 'recommend' as NavType, label: '为你' },
  { key: 'hot' as NavType, label: '热门' },
  { key: 'unsolved' as NavType, label: '待解决' },
  { key: 'essence' as NavType, label: '精华' },
])

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

watch(() => props.modelValue, (value) => {
  if (value && activeNav.value !== value) {
    activeNav.value = value
  }
}, { immediate: true })

onMounted(() => {
  loadCategories()
})
</script>

<template>
  <div class="topic-filters" aria-label="帖子流筛选">
    <div class="filters-main">
      <button
        v-for="item in navItems"
        :key="item.key"
        class="tab-btn"
        :class="{ active: activeNav === item.key }"
        type="button"
        @click="setNav(item.key)"
      >
        {{ item.label }}
      </button>
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
    position: sticky;
    top: 54px;
    z-index: 80;
    padding: 8px;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    margin-bottom: 10px;
    border-radius: 14px;
    background: color-mix(in srgb, var(--el-bg-color-overlay) 94%, transparent);
    backdrop-filter: blur(14px) saturate(126%);
  }

  .filters-main {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 6px;
  }

  .tab-btn {
    min-width: 0;
    padding: 7px 4px;
    font-size: 11.5px;
    line-height: 1;
    white-space: nowrap;
  }

  .filters-side {
    justify-content: space-between;
    gap: 10px;
  }

  .filter-select {
    flex: 1;
    max-width: none;
  }

  .sort-label {
    flex: 0 0 auto;
  }
}
</style>
