<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Compass, DataLine, Medal, Menu as IconMenu } from '@element-plus/icons-vue'
import { publicDataApi } from '@/api/publicData'

const router = useRouter()
const route = useRoute()

const categories = ref<any[]>([])
const topTags = ref<string[]>([])

const activeMenu = computed(() => {
  if (route.path.startsWith('/hot')) return '/hot'
  if (route.path.startsWith('/featured')) return '/featured'
  return '/'
})

const activeSectionId = computed(() => (route.name === 'section' ? String(route.params.id || '') : ''))
const activeTag = computed(() => (route.name === 'tag' ? decodeURIComponent(String(route.params.name || '')) : ''))

const go = (path: string) => {
  router.push(path)
}

const isSectionActive = (id: number | string) => activeSectionId.value === String(id)
const isTagActive = (tag: string) => activeTag.value === tag

onMounted(async () => {
  try {
    const res = await publicDataApi.getActiveSectionsCached()
    if ((res.code === 2000 || res.code === 200) && res.data) {
      categories.value = res.data
    }
  } catch {
    // Song：说明
  }

  try {
    const res = await publicDataApi.getHotTagsCached(10)
    if (res.code === 2000 && res.data) {
      const extractedTags = Array.isArray(res.data)
        ? res.data.map((item: any) => (typeof item === 'string' ? item : item.name)).filter(Boolean)
        : []
      const splitTags = extractedTags.flatMap((item: string) =>
        item
          .split(',')
          .map((piece) => piece.trim())
          .filter(Boolean)
      )
      topTags.value = Array.from(new Set(splitTags)).slice(0, 10)
    }
  } catch {
    // Song：说明
  }
})
</script>

<template>
  <div class="left-nav">
    <el-menu :default-active="activeMenu" class="nav-menu" :router="false">
      <el-menu-item index="/" @click="go('/')">
        <el-icon><Compass /></el-icon>
        <span>最新发布</span>
      </el-menu-item>

      <el-menu-item index="/hot" @click="go('/hot')">
        <el-icon><DataLine /></el-icon>
        <span>热门排行</span>
      </el-menu-item>

      <el-menu-item index="/featured" @click="go('/featured')">
        <el-icon><Medal /></el-icon>
        <span>精华汇总</span>
      </el-menu-item>
    </el-menu>

    <div class="nav-group">
      <div class="menu-group-title flex-between">
        <span>板块分类</span>
        <el-button link size="small" @click="go('/sections')">
          <el-icon><IconMenu /></el-icon>
        </el-button>
      </div>

      <ul class="category-list">
        <li
          v-for="cat in categories"
          :key="cat.id"
          class="category-item"
          :class="{ active: isSectionActive(cat.id) }"
          @click="go(`/s/${cat.id}`)"
        >
          <div class="cat-left">
            <span class="cat-icon">{{ cat.icon || '#' }}</span>
            <span class="cat-name">{{ cat.name }}</span>
          </div>
          <span v-if="isSectionActive(cat.id)" class="active-mark">当前</span>
        </li>

        <li v-if="categories.length === 0" class="category-item cat-empty">
          <span class="cat-name">加载中...</span>
        </li>
      </ul>
    </div>

    <div class="nav-group" v-if="topTags.length > 0">
      <div class="menu-group-title">热门标签</div>
      <div class="tags-container">
        <el-tag
          v-for="tag in topTags"
          :key="tag"
          size="small"
          class="nav-tag"
          :class="{ active: isTagActive(tag) }"
          @click="go(`/tag/${encodeURIComponent(tag)}`)"
        >
          # {{ tag }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.left-nav {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.nav-menu {
  border-right: none;
  background-color: transparent;
}

.nav-menu .el-menu-item {
  height: 40px;
  line-height: 40px;
  border-radius: 8px;
  margin-bottom: 4px;
  font-weight: 600;
  color: var(--cp-text);
  border-left: 3px solid transparent;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.nav-menu .el-menu-item:hover {
  background-color: var(--cp-hover);
  transform: translateX(2px);
}

.nav-menu .el-menu-item.is-active {
  background-color: #fff3d4;
  color: #7a5700;
  font-weight: 700;
  border-left: 3px solid var(--cp-primary);
}

.menu-group-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--cp-text-muted);
  margin-bottom: 10px;
  padding: 0 8px;
  letter-spacing: 0.5px;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.nav-group {
  padding: 0 6px;
}

.category-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.category-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
  margin-bottom: 4px;
  border-left: 3px solid transparent;
}

.category-item:hover {
  background-color: var(--cp-hover);
  transform: translateX(2px);
}

.category-item.active {
  background: #fff9e8;
  border-left-color: var(--cp-primary);
}

.cat-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.cat-icon {
  font-size: 14px;
  width: 18px;
  text-align: center;
}

.cat-name {
  font-size: 14px;
  color: var(--cp-text);
  font-weight: 500;
}

.active-mark {
  font-size: 11px;
  color: #7a5700;
  background: #ffe7a6;
  border-radius: 999px;
  padding: 2px 6px;
  font-weight: 700;
}

.cat-empty {
  opacity: 0.7;
  cursor: default;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 4px;
}

.nav-tag {
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--cp-border);
  background-color: var(--cp-bg-surface);
  color: var(--cp-text);
  border-radius: 999px;
}

.nav-tag:hover {
  background-color: var(--cp-hover);
  color: var(--cp-primary-dark);
  border-color: var(--cp-primary-light);
}

.nav-tag.active {
  background: #fff3d4;
  border-color: #f4b400;
  color: #7a5700;
  font-weight: 700;
}
</style>
