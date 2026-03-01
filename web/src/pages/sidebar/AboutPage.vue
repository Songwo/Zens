<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { InfoFilled, Monitor, Setting, DataLine, Lightning } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { changelogApi, type ChangelogItem } from '@/api/changelog'

const router = useRouter()
const changelogs = ref<ChangelogItem[]>([])

onMounted(async () => {
  try {
    const res = await changelogApi.getList()
    if (res.data) changelogs.value = res.data
  } catch {
    // Song：说明
  }
})
</script>

<template>
  <MainLayout>
    <div class="page-container">
      <div class="page-header">
        <div class="breadcrumb">
          <span class="back-link" @click="router.push('/')">首页</span>
          <span class="separator">/</span>
          <span class="current">关于站点</span>
        </div>
        <div class="title-area">
          <div class="icon-bulb">
            <el-icon><InfoFilled /></el-icon>
          </div>
          <div class="title-text">
            <h1>关于站点 <span>(About Us)</span></h1>
            <p class="subtitle">Zens 致力于连接每一位开发者与创作者。</p>
          </div>
        </div>
      </div>

      <!-- Vision Hero -->
      <div class="vision-hero">
        <div class="vision-content">
          <h3>我们的愿景</h3>
          <p class="vision-text">我们希望打造一个纯净、高效、充满活力的技术分享空间。在这里，你可以讨论最新的框架趋势，也可以记录自己踩坑的日常，或者结交志同道合的技术伙伴。</p>
        </div>
        <div class="vision-bg-pattern"></div>
      </div>

      <!-- Tech Stack -->
      <div class="section-container">
        <div class="section-title">
          <el-icon><Monitor /></el-icon>
          <h3>强劲驱动的技术栈</h3>
        </div>
        
        <el-row :gutter="20" class="tech-grid">
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="tech-card tech-frontend">
              <div class="tech-icon"><el-icon><Monitor /></el-icon></div>
              <h4>前端架构</h4>
              <p>Vue 3 + Vite<br/>TypeScript, Element Plus</p>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="tech-card tech-backend">
              <div class="tech-icon"><el-icon><Setting /></el-icon></div>
              <h4>后端服务</h4>
              <p>Spring Boot 3<br/>MyBatis-Plus, JWT Auth</p>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="tech-card tech-data">
              <div class="tech-icon"><el-icon><DataLine /></el-icon></div>
              <h4>数据与缓存</h4>
              <p>MySQL 8.0<br/>Redis 高性能缓存</p>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- Timeline -->
      <div class="section-container timeline-section">
        <div class="section-title">
          <el-icon><DataLine /></el-icon>
          <h3>发展历程</h3>
        </div>
        
        <el-card shadow="never" class="timeline-card">
          <el-timeline v-if="changelogs.length">
            <el-timeline-item
              v-for="item in changelogs"
              :key="item.id"
              :timestamp="item.timestamp"
              placement="top"
              :type="item.sortOrder >= 100 ? 'primary' : undefined"
              :size="item.sortOrder >= 100 ? 'large' : 'normal'"
              :color="item.sortOrder < 100 ? '#a0cfff' : undefined"
            >
              <el-card shadow="hover" class="timeline-node-card" :class="{ 'future-node': item.sortOrder < 100 }">
                <h4>{{ item.title }}</h4>
                <p>{{ item.content }}</p>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="timeline-empty">
            <p>暂无发展日志</p>
          </div>
        </el-card>
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
  margin-bottom: 24px;
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

.title-area {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 40px;
}

.icon-bulb {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-info-light-8) 0%, var(--el-color-info-light-9) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--el-color-info);
  box-shadow: 0 8px 16px var(--el-color-info-light-9);
}

.title-text h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.title-text h1 span {
  font-size: 18px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.subtitle {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 15px;
}

/* Song：说明 */
.vision-hero {
  position: relative;
  border-radius: 24px;
  background: linear-gradient(135deg, var(--el-bg-color) 0%, var(--el-fill-color-light) 100%);
  border: 1px solid var(--el-border-color-lighter);
  padding: 48px;
  margin-bottom: 48px;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(0,0,0,0.02);
}

.vision-content {
  position: relative;
  z-index: 2;
  max-width: 600px;
}

.vision-content h3 {
  margin: 0 0 16px 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-color-primary);
}

.vision-text {
  margin: 0;
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.vision-bg-pattern {
  position: absolute;
  top: 0;
  right: 0;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle at top right, var(--el-color-primary-light-7) 0%, transparent 70%);
  opacity: 0.3;
  z-index: 1;
}

/* Song：说明 */
.section-container {
  margin-bottom: 48px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  font-size: 22px;
  color: var(--el-color-primary);
}

.section-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

/* Song：说明 */
.tech-grid {
  row-gap: 20px;
}

.tech-card {
  height: 100%;
  border-radius: 16px;
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: center;
  padding: 16px 0;
}

.tech-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.05);
}

.tech-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  transition: transform 0.3s;
}

.tech-card:hover .tech-icon {
  transform: scale(1.1) rotate(5deg);
}

.tech-frontend .tech-icon {
  background-color: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.tech-frontend:hover { border-color: var(--el-color-success-light-5); }

.tech-backend .tech-icon {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.tech-backend:hover { border-color: var(--el-color-primary-light-5); }

.tech-data .tech-icon {
  background-color: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}
.tech-data:hover { border-color: var(--el-color-warning-light-5); }

.tech-card h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.tech-card p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

/* Song：说明 */
.timeline-card {
  border-radius: 20px;
  padding: 16px 16px 0 16px;
  background-color: var(--el-fill-color-blank);
}

.timeline-node-card {
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
}

.timeline-node-card h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.timeline-node-card p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.future-node {
  background-color: var(--el-fill-color-light);
  border-style: dashed;
}

.future-node h4 {
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .title-area {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  
  .vision-hero {
    padding: 32px 24px;
  }
}

.timeline-empty {
  text-align: center;
  padding: 32px;
  color: var(--el-text-color-placeholder);
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
