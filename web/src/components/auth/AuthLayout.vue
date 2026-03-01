<script setup lang="ts">
import BrandPanel from './BrandPanel.vue'
</script>

<template>
  <div class="auth-layout">
    <!-- Left Side: Brand Panel (approx 40%) -->
    <div class="auth-left">
      <BrandPanel />
    </div>

    <!-- Right Side: Form Wizard wrapper (approx 60%) -->
    <div class="auth-right">
      <div class="auth-form-container">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-layout {
  display: flex;
  min-height: 100vh;
  width: 100vw;
  background-color: var(--el-bg-color-page);
  overflow-x: hidden;
}

.auth-left {
  flex: 0 0 40%;
  max-width: 480px;
}

.auth-right {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 24px;
  position: relative;
}

.auth-form-container {
  width: 100%;
  max-width: 440px; /* Song：右侧表单宽度控制在 420~480 */
  background: var(--el-bg-color-overlay);
  border-radius: var(--el-border-radius-large);
  padding: 40px;
  box-shadow: var(--el-box-shadow-light);
  border: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 992px) {
  .auth-layout {
    flex-direction: column;
  }
  
  .auth-left {
    flex: 0 0 auto;
    max-width: none;
    width: 100%;
  }

  .auth-form-container {
    padding: 32px 24px;
    box-shadow: none;
    border: none;
    background: transparent;
  }
}
</style>
