<script setup lang="ts">
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { ChatDotRound, User, Message, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

function copyEmail() {
  navigator.clipboard.writeText('zhaoqsnyah@gmail.com').then(() => {
    ElMessage.success('邮箱已复制到剪贴板')
  })
}
</script>

<template>
  <MainLayout>
    <div class="page-container">
      <div class="breadcrumb">
        <span class="back-link" @click="router.push('/')">首页</span>
        <span class="separator">/</span>
        <span class="current">联系管理</span>
      </div>
      <div class="title-area">
        <div class="icon-wrap">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <div class="title-text">
          <h1>联系管理 <span>(Contact Us)</span></h1>
          <p class="subtitle">遇到问题或有任何建议，欢迎随时联系我们。</p>
        </div>
      </div>

      <!-- 联系方式卡片 -->
      <el-row :gutter="20" class="contact-grid">
        <el-col :xs="24" :sm="12">
          <el-card shadow="hover" class="contact-card">
            <div class="contact-icon-wrap icon-blue">
              <el-icon><Message /></el-icon>
            </div>
            <h3>邮件联系</h3>
            <p>发送邮件至管理员邮箱，我们将在 1-3 个工作日内回复。</p>
            <div class="email-row">
              <span class="email-text">zhaoqsnyah@gmail.com</span>
              <el-button size="small" type="primary" plain @click="copyEmail">复制</el-button>
            </div>
            <el-button
              type="primary"
              class="contact-btn"
              @click="() => window.open('mailto:zhaoqsnyah@gmail.com', '_blank')"
            >
              发送邮件
            </el-button>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="12">
          <el-card shadow="hover" class="contact-card">
            <div class="contact-icon-wrap icon-orange">
              <el-icon><Warning /></el-icon>
            </div>
            <h3>站内举报</h3>
            <p>发现违规内容？使用帖子或评论旁的举报功能，管理员会及时处理。</p>
            <el-button
              type="warning"
              plain
              class="contact-btn"
              @click="router.push('/')"
            >
              前往社区
            </el-button>
          </el-card>
        </el-col>
      </el-row>

      <!-- 管理员信息 -->
      <el-card shadow="never" class="admin-card">
        <div class="admin-header">
          <el-icon><User /></el-icon>
          <h3>管理员</h3>
        </div>
        <div class="admin-info">
          <el-avatar :size="56" src="" class="admin-avatar">
            <el-icon><User /></el-icon>
          </el-avatar>
          <div class="admin-detail">
            <div class="admin-name">Song</div>
            <div class="admin-role">超级管理员 · Zens 校园社区</div>
            <div class="admin-email">
              <el-icon><Message /></el-icon>
              <span>zhaoqsnyah@gmail.com</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 常见问题 -->
      <el-card shadow="never" class="faq-card">
        <h3 class="faq-title">常见问题</h3>
        <el-collapse accordion>
          <el-collapse-item title="如何申请成为版主？" name="1">
            <p>在任意板块页面点击「申请版主」按钮，填写申请理由后提交，管理员审核通过后即可获得版主权限。</p>
          </el-collapse-item>
          <el-collapse-item title="账号被封禁如何申诉？" name="2">
            <p>请发送邮件至 zhaoqsnyah@gmail.com，标题注明「账号申诉」并提供您的用户名，我们会核实情况后尽快处理。</p>
          </el-collapse-item>
          <el-collapse-item title="如何找回密码？" name="3">
            <p>在登录页面点击「忘记密码」，通过注册邮箱验证码完成密码重置。</p>
          </el-collapse-item>
          <el-collapse-item title="发帖审核需要多久？" name="4">
            <p>正常情况下帖子会在提交后自动进入审核队列，管理员或版主会在 24 小时内完成审核。</p>
          </el-collapse-item>
          <el-collapse-item title="如何删除自己的账号？" name="5">
            <p>目前尚不支持自助注销，请联系管理员邮箱申请账号注销，我们会在 3 个工作日内处理。</p>
          </el-collapse-item>
        </el-collapse>
      </el-card>
    </div>
  </MainLayout>
</template>

<style scoped>
.page-container {
  max-width: 860px;
  margin: 0 auto;
  padding: 32px 16px;
  animation: fadeIn 0.4s ease-out;
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
.back-link { cursor: pointer; transition: color 0.2s; }
.back-link:hover { color: var(--el-color-primary); }
.separator { margin: 0 8px; color: var(--el-text-color-placeholder); }
.current { color: var(--el-text-color-primary); font-weight: 500; }
.title-area {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 32px;
}
.icon-wrap {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-warning-light-8), var(--el-color-warning-light-9));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--el-color-warning);
}
.title-text h1 {
  margin: 0 0 8px 0;
  font-size: 26px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.title-text h1 span {
  font-size: 16px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.subtitle { margin: 0; color: var(--el-text-color-regular); font-size: 14px; }
.contact-grid { margin-bottom: 24px; row-gap: 20px; }
.contact-card {
  border-radius: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
  transition: all 0.3s;
}
.contact-card:hover { transform: translateY(-3px); }
.contact-icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  margin-bottom: 16px;
}
.icon-blue { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.icon-orange { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.contact-card h3 {
  margin: 0 0 8px 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.contact-card p {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
  flex: 1;
}
.email-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.email-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-family: monospace;
  flex: 1;
  word-break: break-all;
}
.contact-btn { width: 100%; }
.admin-card {
  border-radius: 16px;
  margin-bottom: 24px;
}
.admin-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  font-size: 18px;
  color: var(--el-color-primary);
}
.admin-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.admin-info {
  display: flex;
  align-items: center;
  gap: 20px;
}
.admin-avatar { background: var(--el-color-primary-light-7); }
.admin-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}
.admin-role {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.admin-email {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-color-primary);
}
.faq-card {
  border-radius: 16px;
}
.faq-title {
  margin: 0 0 20px 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}
.faq-card p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}
@media (max-width: 768px) {
  .title-area { flex-direction: column; align-items: flex-start; }
  .admin-info { flex-direction: column; align-items: flex-start; }
}
</style>
