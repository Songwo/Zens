<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const goHome = () => router.push('/')
</script>

<template>
  <div class="app-logo cursor-pointer" @click="goHome">
    <img src="/logo.png" alt="Zens" class="logo-img" />
    <span class="logo-text">Zens</span>
  </div>
</template>

<style scoped>
.app-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: contain;
}

.logo-text {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: var(--el-text-color-primary);
}
</style>
