<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Compass, Connection, DataLine, Medal, Menu as IconMenu, Plus, Close, ArrowRight, Present } from '@element-plus/icons-vue'
import { publicDataApi } from '@/api/publicData'
import { tagApi } from '@/api/tag'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { formatDiscoveryTagName, pickCuratedDiscoveryTags } from '@/utils/communityDiscovery'
import { formatSectionIcon, formatSectionName } from '@/utils/communitySections'
import { invalidateCommunityContentCaches } from '@/utils/communityCache'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const categories = ref<any[]>([])
const topTags = ref<string[]>([])
const myTags = ref<any[]>([])
const myTagsLoading = ref(false)
const addTagVisible = ref(false)
const addTagKeyword = ref('')
const searchResults = ref<any[]>([])
const searching = ref(false)
const showAllMyTags = ref(false)
const showAllTopTags = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null
const TAG_DISPLAY_LIMIT = 8

const activeMenu = computed(() => {
  if (route.path.startsWith('/hot')) return '/hot'
  if (route.path.startsWith('/featured')) return '/featured'
  if (route.path.startsWith('/benefits')) return '/benefits'
  if (route.path.startsWith('/metaverse')) return '/metaverse'
  return '/'
})

const activeSectionId = computed(() => (route.name === 'section' ? String(route.params.id || '') : ''))
const activeTag = computed(() => (route.name === 'tag' ? decodeURIComponent(String(route.params.name || '')) : ''))
const visibleMyTags = computed(() => showAllMyTags.value ? myTags.value : myTags.value.slice(0, TAG_DISPLAY_LIMIT))
const visibleTopTags = computed(() => showAllTopTags.value ? topTags.value : topTags.value.slice(0, TAG_DISPLAY_LIMIT))
const hasMoreMyTags = computed(() => myTags.value.length > TAG_DISPLAY_LIMIT)
const hasMoreTopTags = computed(() => topTags.value.length > TAG_DISPLAY_LIMIT)

const go = (path: string) => router.push(path)
const isSectionActive = (id: number | string) => activeSectionId.value === String(id)
const isTagActive = (tag: string) => activeTag.value === tag
const getSectionLabel = (section: { name?: string }) => formatSectionName(section.name || '')
const getSectionIcon = (section: { icon?: string; name?: string }) => formatSectionIcon(section.icon, section.name || '')

const loadMyTags = async () => {
  if (!userStore.isLoggedIn) return
  myTagsLoading.value = true
  try {
    const res = await tagApi.getMyFollowing()
    if (res.code === 2000 && Array.isArray(res.data)) {
      myTags.value = res.data
    }
  } catch { /* ignore */ } finally {
    myTagsLoading.value = false
  }
}

const onSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = addTagKeyword.value.trim()
  if (!kw) { searchResults.value = []; return }
  searchTimer = setTimeout(async () => {
    searching.value = true
    try {
      const res = await tagApi.search(kw)
      if (res.code === 2000 && Array.isArray(res.data)) {
        // 过滤掉已关注的
        const followedIds = new Set(myTags.value.map((t: any) => t.id))
        searchResults.value = res.data.filter((t: any) => !followedIds.has(t.id))
      }
    } catch { /* ignore */ } finally {
      searching.value = false
    }
  }, 300)
}

const followTag = async (tag: any) => {
  try {
    await tagApi.follow(tag.id)
    myTags.value.push(tag)
    invalidateCommunityContentCaches()
    searchResults.value = searchResults.value.filter(t => t.id !== tag.id)
    addTagKeyword.value = ''
    searchResults.value = []
    addTagVisible.value = false
    ElMessage.success(`已添加「${tag.name}」到我的标签`)
  } catch (e: any) {
    ElMessage.error(e?.message || '添加失败')
  }
}

const unfollowTag = async (tag: any, e: Event) => {
  e.stopPropagation()
  try {
    await tagApi.unfollow(tag.id)
    myTags.value = myTags.value.filter((t: any) => t.id !== tag.id)
    invalidateCommunityContentCaches()
  } catch (e: any) {
    ElMessage.error(e?.message || '移除失败')
  }
}

const closeAddTag = () => {
  addTagVisible.value = false
  addTagKeyword.value = ''
  searchResults.value = []
}

onMounted(async () => {
  try {
    const [sectionRes, hotTagRes] = await Promise.all([
      publicDataApi.getActiveSectionsCached(),
      publicDataApi.getHotTagsCached(30),
    ])
    if ((sectionRes.code === 2000 || sectionRes.code === 200) && Array.isArray(sectionRes.data)) {
      categories.value = sectionRes.data || []
    }
    if (hotTagRes.code === 2000 && Array.isArray(hotTagRes.data)) {
      const extractedTags = pickCuratedDiscoveryTags(hotTagRes.data, { limit: 10 })
        .map((item: any) => item.name)
        .filter(Boolean)
      topTags.value = Array.from(new Set(extractedTags)).slice(0, 10)
    }
  } catch { /* ignore */ }

  loadMyTags()
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

      <el-menu-item index="/benefits" @click="go('/benefits')">
        <el-icon><Present /></el-icon>
        <span>福利中心</span>
      </el-menu-item>

      <el-menu-item index="/metaverse" @click="go('/metaverse')">
        <el-icon><Connection /></el-icon>
        <span>星港</span>
      </el-menu-item>
    </el-menu>

    <!-- 板块分类 -->
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
            <span class="cat-icon">{{ getSectionIcon(cat) }}</span>
            <span class="cat-name">{{ getSectionLabel(cat) }}</span>
          </div>
          <span v-if="isSectionActive(cat.id)" class="active-mark">当前</span>
        </li>
        <li v-if="categories.length === 0" class="category-item cat-empty">
          <span class="cat-name">加载中...</span>
        </li>
      </ul>
    </div>

    <!-- 我的标签（已登录显示） -->
    <div v-if="userStore.isLoggedIn" class="nav-group">
      <div class="menu-group-title flex-between">
        <span>我的标签</span>
        <el-button link size="small" @click="addTagVisible = !addTagVisible" title="添加标签">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>

      <!-- 搜索添加框 -->
      <div v-if="addTagVisible" class="add-tag-box">
        <el-input
          v-model="addTagKeyword"
          placeholder="搜索标签名称..."
          size="small"
          clearable
          @input="onSearchInput"
          @keydown.esc="closeAddTag"
        />
        <div v-if="searching" class="tag-search-hint">搜索中...</div>
        <div v-else-if="addTagKeyword && searchResults.length === 0" class="tag-search-hint">没有找到匹配的标签</div>
        <ul v-else-if="searchResults.length > 0" class="tag-search-results">
          <li
            v-for="tag in searchResults"
            :key="tag.id"
            class="tag-search-item"
            @click="followTag(tag)"
          >
            <span class="tag-search-name"># {{ tag.name }}</span>
            <span class="tag-search-count">{{ tag.postCount || 0 }} 帖</span>
          </li>
        </ul>
      </div>

      <!-- 已关注标签列表 -->
      <div v-if="myTagsLoading" class="tag-loading">加载中...</div>
      <div v-else-if="myTags.length === 0 && !addTagVisible" class="tag-empty-hint">
        点击 <el-icon style="vertical-align: middle"><Plus /></el-icon> 添加你常看的标签
      </div>
      <div v-else class="tags-container">
        <div
          v-for="tag in visibleMyTags"
          :key="tag.id"
          class="my-tag-item"
          :class="{ active: isTagActive(tag.name) }"
          @click="go(`/tag/${encodeURIComponent(tag.name)}`)"
        >
          <span class="my-tag-name"># {{ tag.name }}</span>
          <el-icon class="my-tag-remove" @click="unfollowTag(tag, $event)"><Close /></el-icon>
        </div>
      </div>
      <button
        v-if="hasMoreMyTags"
        class="tag-expand-button"
        type="button"
        @click="showAllMyTags = !showAllMyTags"
      >
        {{ showAllMyTags ? '收起标签' : `展开 ${myTags.length - TAG_DISPLAY_LIMIT} 个` }}
      </button>
    </div>

    <!-- 热门标签 -->
    <div class="nav-group" v-if="topTags.length > 0">
      <div class="menu-group-title">推荐主题</div>
      <div class="tags-container">
        <el-tag
          v-for="tag in visibleTopTags"
          :key="tag"
          size="small"
          class="nav-tag"
          :class="{ active: isTagActive(tag) }"
          @click="go(`/tag/${encodeURIComponent(tag)}`)"
        >
          # {{ formatDiscoveryTagName(tag) }}
        </el-tag>
      </div>
      <button
        v-if="hasMoreTopTags"
        class="tag-expand-button"
        type="button"
        @click="showAllTopTags = !showAllTopTags"
      >
        {{ showAllTopTags ? '收起主题' : `展开 ${topTags.length - TAG_DISPLAY_LIMIT} 个` }}
      </button>
    </div>
  <!-- 邀请好友入口（已登录显示） -->
    <div v-if="userStore.isLoggedIn" class="nav-group invite-entry" @click="go('/invite')">
      <div class="invite-banner">
        <el-icon class="invite-icon"><Plus /></el-icon>
        <div class="invite-text">
          <span class="invite-title">邀请好友</span>
          <span class="invite-sub">Lv5+ 可生成邀请链接</span>
        </div>
        <el-icon class="invite-arrow"><ArrowRight /></el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped>
.left-nav {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100vh - var(--header-height) - 54px);
}

.nav-menu {
  border-right: none;
  background-color: transparent;
}

.nav-menu .el-menu-item {
  height: 38px;
  line-height: 38px;
  border-radius: 10px;
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

.invite-entry {
  cursor: pointer;
  margin-top: auto;
  padding-top: 12px;
}

.invite-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--cp-primary) 10%, var(--el-bg-color));
  border: 1px solid color-mix(in srgb, var(--cp-primary) 34%, var(--el-border-color-light));
  transition: all 0.2s ease;
}

.invite-banner:hover {
  background: color-mix(in srgb, var(--cp-primary) 15%, var(--el-bg-color));
  transform: translateY(-1px);
}

.invite-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.invite-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.invite-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.invite-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.invite-arrow {
  font-size: 14px;
  color: var(--el-text-color-placeholder);
}

.nav-menu .el-menu-item.is-active {
  background-color: #fff3d4;
  color: #7a5700;
  font-weight: 700;
  border-left: 3px solid var(--cp-primary);
}

.menu-group-title {
  font-size: 12px;
  font-weight: 800;
  color: var(--cp-text-muted);
  margin-bottom: 8px;
  padding: 0 8px;
  letter-spacing: 0;
}

.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.nav-group {
  padding: 10px 6px 0;
  border-top: 1px solid color-mix(in srgb, var(--el-border-color-lighter) 80%, transparent);
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
  border-radius: 10px;
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
  background: #fff0be;
  border-radius: 999px;
  padding: 2px 6px;
  font-weight: 700;
}

.cat-empty {
  opacity: 0.7;
  cursor: default;
}

/* 我的标签 */
.add-tag-box {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tag-search-hint {
  font-size: 12px;
  color: var(--cp-text-muted);
  padding: 4px 4px;
}

.tag-search-results {
  list-style: none;
  padding: 0;
  margin: 0;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  max-height: 180px;
  overflow-y: auto;
}

.tag-search-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 10px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 13px;
}

.tag-search-item:hover {
  background: var(--cp-hover);
}

.tag-search-name {
  color: var(--cp-text);
  font-weight: 500;
}

.tag-search-count {
  font-size: 11px;
  color: var(--cp-text-muted);
}

.tag-loading, .tag-empty-hint {
  font-size: 12px;
  color: var(--cp-text-muted);
  padding: 4px 8px;
}

.my-tag-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px 3px 8px;
  border-radius: 9px;
  border: 1px solid var(--cp-border);
  background: var(--cp-bg-surface);
  cursor: pointer;
  font-size: 12px;
  transition: all 0.18s;
  margin: 0 4px 6px 0;
}

.my-tag-item:hover {
  background: var(--cp-hover);
  border-color: var(--cp-primary-light);
}

.my-tag-item.active {
  background: #fff3d4;
  border-color: #f4b400;
  color: #7a5700;
  font-weight: 700;
}

.my-tag-name {
  color: inherit;
}

.my-tag-remove {
  font-size: 10px;
  color: var(--cp-text-muted);
  opacity: 0;
  transition: opacity 0.15s;
}

.my-tag-item:hover .my-tag-remove {
  opacity: 1;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  padding: 0 2px;
}

.nav-tag {
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--cp-border);
  background-color: var(--cp-bg-surface);
  color: var(--cp-text);
  border-radius: 9px;
  margin: 0 4px 6px 0;
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

.tag-expand-button {
  width: calc(100% - 8px);
  min-height: 28px;
  margin: 2px 4px 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--cp-text-muted);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.tag-expand-button:hover {
  background: var(--cp-hover);
  color: var(--cp-primary-dark);
}
</style>
