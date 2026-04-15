<script setup lang="ts">
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = useRouter()

const goHome = () => {
  router.replace('/')
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.replace('/')
}
</script>

<template>
  <MainLayout>
    <div class="not-found-page">
      <section class="not-found-card">
        <p class="error-code">404</p>
        <h1 class="title">页面走丢了</h1>
        <p class="desc">你访问的链接可能已失效，或者页面已迁移。</p>
        <div class="actions">
          <el-button type="primary" size="large" @click="goHome">返回首页</el-button>
          <el-button size="large" @click="goBack">返回上一步</el-button>
        </div>
      </section>
    </div>
  </MainLayout>
</template>

<style scoped>
.not-found-page {
  min-height: calc(100vh - var(--header-height) - 120px);
  display: grid;
  place-items: center;
  padding: 32px 0;
}

.not-found-card {
  width: min(560px, 100%);
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  padding: 36px 28px;
  text-align: center;
  box-shadow: var(--el-box-shadow-light);
}

.error-code {
  margin: 0;
  font-size: clamp(52px, 8vw, 76px);
  line-height: 1;
  font-weight: 800;
  color: var(--el-color-primary);
}

.title {
  margin: 14px 0 8px;
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.desc {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.actions {
  margin-top: 26px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
</style>
