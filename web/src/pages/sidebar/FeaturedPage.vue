<script setup lang="ts">
import { Position, Star, Lightning } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = useRouter()
</script>

<template>
  <MainLayout>
    <div class="page-container">
      <div class="page-header">
        <div class="breadcrumb">
          <span class="back-link" @click="router.push('/')">首页</span>
          <span class="separator">/</span>
          <span class="current">精华主题</span>
        </div>
      </div>

      <div class="hero-section">
        <div class="glass-card">
          <div class="glow-effect"></div>
          <div class="icon-container">
            <el-icon class="pulse-icon"><Star /></el-icon>
            <el-icon class="rocket-icon"><Position /></el-icon>
          </div>
          <h1 class="gradient-text">精华主题板块酝酿中</h1>
          <p class="subtitle">很快我们就会为您带来精选的深度长文和干货合集，敬请期待！</p>
          <div class="action-buttons">
            <el-button @click="router.push('/')" type="primary" size="large" round class="explore-btn">先去广场逛逛</el-button>
          </div>
        </div>
        
        <!-- Connect 快捷入口 -->
        <div class="connect-card" @click="router.push('/connect')">
          <div class="connect-card-left">
            <div class="connect-icon"><el-icon><Lightning /></el-icon></div>
            <div>
              <h3>等级中心</h3>
              <p>查看您的等级、经验进度和等级特权</p>
            </div>
          </div>
          <span class="connect-arrow">→</span>
        </div>

        <!-- Background decorative elements -->
        <div class="bg-shape shape-1"></div>
        <div class="bg-shape shape-2"></div>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.page-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 16px;
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.breadcrumb {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 32px;
  display: flex;
  align-items: center;
}

.back-link {
  cursor: pointer;
  transition: color 0.2s;
}

.back-link:hover {
  color: var(--el-color-primary);
}

.separator {
  margin: 0 8px;
  color: var(--el-text-color-placeholder);
}

.current {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.hero-section {
  position: relative;
  min-height: 500px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 24px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.glass-card {
  position: relative;
  z-index: 10;
  background: rgba(var(--el-bg-color-rgb), 0.7);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 64px 40px;
  border-radius: 20px;
  text-align: center;
  border: 1px solid rgba(var(--el-border-color-lighter-rgb), 0.5);
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.05);
  max-width: 600px;
  width: 90%;
}

.glow-effect {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 80%;
  height: 4px;
  background: linear-gradient(90deg, transparent, var(--el-color-warning), transparent);
  border-radius: 4px 4px 0 0;
  opacity: 0.8;
}

.icon-container {
  position: relative;
  width: 100px;
  height: 100px;
  margin: 0 auto 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pulse-icon {
  font-size: 80px;
  color: var(--el-color-warning-light-5);
  position: absolute;
  animation: pulse 3s infinite ease-in-out;
}

.rocket-icon {
  font-size: 40px;
  color: var(--el-color-warning);
  position: absolute;
  z-index: 2;
  animation: float 4s infinite ease-in-out;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.2); opacity: 0.2; }
  100% { transform: scale(1); opacity: 0.5; }
}

@keyframes float {
  0% { transform: translateY(0px) rotate(0deg); }
  50% { transform: translateY(-10px) rotate(5deg); }
  100% { transform: translateY(0px) rotate(0deg); }
}

.gradient-text {
  margin: 0 0 16px 0;
  font-size: 32px;
  font-weight: 800;
  background: linear-gradient(135deg, var(--el-color-warning) 0%, var(--el-color-danger) 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1.2;
}

.subtitle {
  margin: 0 0 40px 0;
  color: var(--el-text-color-regular);
  font-size: 16px;
  line-height: 1.6;
}

.explore-btn {
  padding: 12px 40px;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 8px 20px var(--el-color-primary-light-5);
  transition: all 0.3s;
}

.explore-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px var(--el-color-primary-light-3);
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  z-index: 1;
  opacity: 0.4;
}

.shape-1 {
  width: 300px;
  height: 300px;
  background: var(--el-color-warning-light-5);
  top: -100px;
  right: -50px;
}

.shape-2 {
  width: 400px;
  height: 400px;
  background: var(--el-color-primary-light-5);
  bottom: -150px;
  left: -100px;
}

@media (max-width: 768px) {
  .hero-section {
    min-height: 400px;
  }
  
  .glass-card {
    padding: 40px 24px;
  }
  
  .gradient-text {
    font-size: 24px;
  }
  
  .subtitle {
    font-size: 15px;
  }
  
  .pulse-icon {
    font-size: 60px;
  }
  
  .rocket-icon {
    font-size: 32px;
  }
}

/* Song：说明 */
.connect-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-fill-color-blank) 100%);
  border: 1px solid var(--el-color-primary-light-7);
  cursor: pointer;
  transition: all 0.3s;
  margin-top: 24px;
  position: relative;
  z-index: 2;
}

.connect-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px var(--el-color-primary-light-8);
  border-color: var(--el-color-primary-light-5);
}

.connect-card-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.connect-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--el-color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.connect-card h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.connect-card p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.connect-arrow {
  font-size: 20px;
  color: var(--el-color-primary);
}
</style>
