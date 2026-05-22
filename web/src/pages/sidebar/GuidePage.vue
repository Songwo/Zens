<script setup lang="ts">
import { Reading, ChatLineRound, Trophy, Warning, CollectionTag, Lightning } from '@element-plus/icons-vue'
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
          <span class="current">阅读指南</span>
        </div>
        <div class="title-area">
          <div class="icon-bulb">
            <el-icon><Reading /></el-icon>
          </div>
          <div class="title-text">
            <h1>阅读指南 <span>(Reading Guide)</span></h1>
            <p class="subtitle">欢迎来到 Zens 开发者社区，这里是一份简单的上手指南。</p>
          </div>
        </div>
      </div>

      <div class="rules-grid">
        <el-row :gutter="24">
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="rule-card">
              <div class="rule-icon-wrapper rule-friendly">
                <el-icon><ChatLineRound /></el-icon>
              </div>
              <h3>友善交流</h3>
              <p>尊重每一位创作者，禁止人身攻击与引战。在这里我们鼓励真诚、包容的讨论氛围。</p>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="rule-card">
              <div class="rule-icon-wrapper rule-quality">
                <el-icon><Reading /></el-icon>
              </div>
              <h3>质量优先</h3>
              <p>提问前请先搜索是否有类似问题，分享内容尽量详细、排版清晰，让知识更有价值。</p>
            </el-card>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-card shadow="hover" class="rule-card">
              <div class="rule-icon-wrapper rule-tags">
                <el-icon><CollectionTag /></el-icon>
              </div>
              <h3>标签规范</h3>
              <p>发布帖子时，请选择准确的板块并打上合适的标签，以便他人快速检索到你的内容。</p>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-card shadow="never" class="info-card level-info">
        <div class="info-content">
          <div class="info-icon"><el-icon><Trophy /></el-icon></div>
          <div class="info-text">
            <h3>如何获取等级与积分？</h3>
            <p>积极发帖、回复、获得有价值的点赞，都可以提升您的社区影响力。当等级提高后，将解锁更多站点隐藏功能与权限。
              <el-button link type="primary" class="go-level-btn" @click="router.push('/connect')">查看等级系统 &rarr;</el-button>
            </p>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="info-card warning-info">
        <div class="info-content">
          <div class="info-icon"><el-icon><Warning /></el-icon></div>
          <div class="info-text">
            <h3>举报与违规处理</h3>
            <p>遇到不良信息，请使用帖子右下角的“举报”功能，管理员核实后会第一时间处理并对您的有效反馈给予积分奖励。</p>
          </div>
        </div>
      </el-card>

      <!-- Connect 快捷入口 -->
      <div class="connect-card" @click="router.push('/connect')">
        <div class="connect-card-left">
          <div class="connect-icon"><el-icon><Lightning /></el-icon></div>
          <div>
            <h3>等级中心</h3>
            <p>查看您的等级、经验进度和等级特权</p>
          </div>
        </div>
        <el-icon class="connect-arrow">&rarr;</el-icon>
      </div>

      <div class="footer-note">
        <el-button type="primary" size="large" class="start-btn" @click="router.push('/')">立即开始探索吧</el-button>
      </div>
    </div>
  </MainLayout>
</template>

<style scoped>
.page-container {
  width: 100%;
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
  color: var(--cp-primary);
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
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-8) 0%, var(--el-color-primary-light-9) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--el-color-primary);
  box-shadow: 0 8px 16px var(--el-color-primary-light-9);
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

.rules-grid {
  margin-bottom: 24px;
}

.rule-card {
  height: 100%;
  border-radius: 16px;
  border: 1px solid var(--el-border-color-lighter);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.rule-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.05);
  border-color: var(--el-color-primary-light-7);
}

.rule-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 16px;
  transition: transform 0.3s;
}

.rule-card:hover .rule-icon-wrapper {
  transform: scale(1.1);
}

.rule-friendly {
  background-color: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.rule-quality {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.rule-tags {
  background-color: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.rule-card h3 {
  margin: 0 0 12px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rule-card p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.info-card {
  border-radius: 16px;
  border: none;
  margin-bottom: 20px;
}

.level-info {
  background: linear-gradient(to right, var(--el-color-primary-light-9), var(--el-fill-color-blank));
  border-left: 4px solid var(--el-color-primary);
}

.warning-info {
  background: linear-gradient(to right, var(--el-color-danger-light-9), var(--el-fill-color-blank));
  border-left: 4px solid var(--el-color-danger);
}

.info-content {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  padding: 8px 0;
}

.info-icon {
  font-size: 28px;
  padding-top: 4px;
}

.level-info .info-icon {
  color: var(--el-color-primary);
}

.warning-info .info-icon {
  color: var(--el-color-danger);
}

.info-text h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.info-text p {
  margin: 0;
  font-size: 15px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.go-level-btn {
  margin-left: 8px;
  font-weight: 600;
}

.footer-note {
  margin-top: 48px;
  text-align: center;
}

.start-btn {
  border-radius: 20px;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 4px 12px var(--el-color-primary-light-5);
  transition: transform 0.2s, box-shadow 0.2s;
}

.start-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px var(--el-color-primary-light-3);
}

@media (max-width: 768px) {
  .title-area {
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 16px;
  }
  
  .info-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .info-icon {
    padding-top: 0;
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
  margin-bottom: 24px;
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
