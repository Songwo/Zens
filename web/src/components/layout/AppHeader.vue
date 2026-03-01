<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus, Bell } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { usePostComposerStore } from '@/store/postComposer'

const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
const searchQuery = ref('')

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push({ path: '/search', query: { q: searchQuery.value } })
  }
}

const goToCompose = () => {
  composerStore.open()
}

const login = () => {
  router.push('/auth/login')
}

const handleDropdown = (command: string) => {
  if (command === 'profile') {
    router.push('/me')
  } else if (command === 'logout') {
    userStore.logout()
    router.push('/auth/login')
  }
}
</script>

<template>
  <div class="header-container">
    <div class="header-content">
      <!-- Left: Logo & Branding -->
      <div class="logo-section" @click="router.push('/')">
        <img src="/logo.png" alt="Zens" style="width:40px;height:40px;border-radius:8px;object-fit:contain;" />
        <span class="logo-text hidden-sm-and-down">Zens</span>
      </div>

      <!-- Middle: Search Bar -->
      <div class="search-section hidden-sm-and-down">
        <el-input
          v-model="searchQuery"
          placeholder="搜索话题、标签、用户..."
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearch"
          class="custom-search"
        />
      </div>

      <!-- Right: Actions & User -->
      <div class="actions-section">
        <template v-if="userStore.isLoggedIn">
          <el-button type="primary" :icon="Plus" round @click="goToCompose" class="hidden-xs-only">
            发帖
          </el-button>
          <el-button :icon="Plus" circle @click="goToCompose" class="hidden-sm-and-up" />

          <el-badge is-dot class="notice-badge">
            <el-button :icon="Bell" circle class="btn-icon borderless-btn" />
          </el-badge>

          <el-dropdown trigger="click" @command="handleDropdown">
            <el-avatar
              :size="32"
              :src="userStore.userInfo?.avatar || 'https://api.dicebear.com/7.x/notionists/svg?seed=' + userStore.userInfo?.username"
              class="user-avatar"
            />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button round @click="login">登录</el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.header-container {
  height: 100%;
  display: flex;
  justify-content: center;
  padding: 0 16px;
}
.header-content {
  width: 100%;
  max-width: 1320px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  flex-shrink: 0;
}
.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background-color: var(--el-color-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
}
.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.search-section {
  flex: 1;
  max-width: 500px;
  margin: 0 24px;
}
.custom-search :deep(.el-input__wrapper) {
  border-radius: 20px;
  background-color: #f4f4f5;
  box-shadow: none;
}
.custom-search :deep(.el-input__wrapper.is-focus) {
  background-color: var(--cp-bg-surface);
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.actions-section {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.notice-badge {
  line-height: 1;
}

.borderless-btn {
  border: none;
  background: transparent;
  font-size: 18px;
}
.borderless-btn:hover {
  background: #f4f4f5;
}

.user-avatar {
  cursor: pointer;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}
.user-avatar:hover {
  border-color: var(--el-color-primary-light-5);
}

@media (max-width: 768px) {
  .hidden-sm-and-down {
    display: none !important;
  }
}
@media (min-width: 769px) {
  .hidden-sm-and-up {
    display: none !important;
  }
}
@media (max-width: 480px) {
  .hidden-xs-only {
    display: none !important;
  }
}
</style>
