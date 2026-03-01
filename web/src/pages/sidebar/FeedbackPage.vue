<script setup lang="ts">
import { Help, Message, ChatLineSquare, ArrowRight, Lightning } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MainLayout from '@/layouts/MainLayout.vue'
import ModeratorApplyDialog from '@/components/ModeratorApplyDialog.vue'

const router = useRouter()
const activeNames = ref(['1'])
const showModDialog = ref(false)

const contactEmail = 'zhaoqsnyah@gmail.com'

const copyEmail = () => {
  navigator.clipboard.writeText(contactEmail).then(() => {
    ElMessage.success('邮箱已复制到剪贴板')
  })
}
</script>

<template>
  <MainLayout>
    <div class="page-container">
      <div class="page-header">
        <div class="breadcrumb">
          <span class="back-link" @click="router.push('/')">首页</span>
          <span class="separator">/</span>
          <span class="current">反馈帮助</span>
        </div>
        <div class="title-area">
          <div class="icon-bulb">
            <el-icon><Help /></el-icon>
          </div>
          <div class="title-text">
            <h1>反馈帮助 <span>(Feedback & Help)</span></h1>
            <p class="subtitle">遇到任何使用问题，或有新功能建议，请随时与我们联系。</p>
          </div>
        </div>
      </div>

      <el-row :gutter="32" class="content-row">
        <!-- FAQ Section -->
        <el-col :xs="24" :md="14">
          <div class="section-title">
            <el-icon><ChatLineSquare /></el-icon>
            <h3>常见问题 (FAQ)</h3>
          </div>
          
          <el-collapse v-model="activeNames" class="custom-collapse" accordion>
            <el-collapse-item name="1">
              <template #title>
                <span class="collapse-title">忘记密码怎么办？</span>
              </template>
              <div class="collapse-content">
                请在登录页面选择“忘记密码”，目前我们支持通过已绑定的安全邮箱获取验证码进行重置。如果无法收到邮件，请检查垃圾邮件箱或联系管理员。
              </div>
            </el-collapse-item>
            
            <el-collapse-item name="2">
              <template #title>
                <span class="collapse-title">我遇到了页面报错，该向谁反馈？</span>
              </template>
              <div class="collapse-content">
                感谢反馈！您可以去“杂谈反馈”板块发帖，或者直接通过下方的邮件联系开发人员。发帖时请尽量包含报错截图、您的操作步骤以及浏览器版本，这会极大加快我们排查问题的速度。
              </div>
            </el-collapse-item>
            
            <el-collapse-item name="3">
              <template #title>
                <span class="collapse-title">账号能否注销？</span>
              </template>
              <div class="collapse-content">
                为了保障社区主题与评论数据的完整性，我们暂时不提供前端自动注销入口。如有特殊需求，请发送邮件至下方服务邮箱联系站长手工处理，我们会在3个工作日内响应。
              </div>
            </el-collapse-item>
            
            <el-collapse-item name="4">
              <template #title>
                <span class="collapse-title">如何申请成为版主？</span>
              </template>
              <div class="collapse-content">
                当您的账号等级达到 Lv5，且在某一特定技术板块表现活跃、内容优质，您可以申请成为对应板块的版主。版主享有删帖、置顶等社区管理权限，同时也会获得专属标识。
                <el-button type="primary" size="small" round style="margin-top: 12px" @click="showModDialog = true">👑 立即申请版主</el-button>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-col>

        <!-- Contact Section -->
        <el-col :xs="24" :md="10">
          <div class="contact-card-wrapper">
            <div class="section-title">
              <el-icon><Message /></el-icon>
              <h3>联系与支持</h3>
            </div>
            
            <el-card shadow="hover" class="contact-card border-glow">
              <div class="contact-header">
                <div class="avatar-bg">
                  <el-icon :size="32" color="#fff"><Message /></el-icon>
                </div>
                <div>
                  <h4>开发者邮箱</h4>
                  <p>7x24小时接收反馈</p>
                </div>
              </div>
              
              <div class="email-display">
                <div class="email-text">{{ contactEmail }}</div>
              </div>
              
              <div class="contact-actions">
                <el-button type="primary" class="action-btn" @click="copyEmail" plain>
                  复制邮箱
                </el-button>
                <el-button type="primary" class="action-btn" tag="a" :href="`mailto:${contactEmail}`">
                  发送邮件 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
                </el-button>
              </div>
              
              <div class="contact-note">
                <p>💡 提示：我们非常重视大家的声音！紧急事宜、商业合作或重大Bug反馈，请直接发送邮件，我们会及时回复。</p>
              </div>
            </el-card>
          </div>
        </el-col>
      </el-row>

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

      <!-- 版主申请弹窗 -->
      <ModeratorApplyDialog v-model:visible="showModDialog" />
    </div>
  </MainLayout>
</template>

<style scoped>
.page-container {
  max-width: 1000px;
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
  margin-bottom: 48px;
}

.icon-bulb {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-success-light-8) 0%, var(--el-color-success-light-9) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--el-color-success);
  box-shadow: 0 8px 16px var(--el-color-success-light-9);
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
.custom-collapse {
  border-top: none;
  border-bottom: none;
}

.custom-collapse :deep(.el-collapse-item__header) {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 12px 16px;
  background-color: transparent;
  border-radius: 8px;
  transition: all 0.2s;
}

.custom-collapse :deep(.el-collapse-item__header:hover) {
  background-color: var(--el-fill-color-light);
}

.custom-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
  background-color: transparent;
}

.custom-collapse :deep(.el-collapse-item__content) {
  padding: 16px 24px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
  font-size: 15px;
}

.collapse-title {
  position: relative;
}

/* Song：说明 */
.contact-card-wrapper {
  position: sticky;
  top: 100px;
}

.contact-card {
  border-radius: 20px;
  border: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(145deg, var(--el-bg-color) 0%, var(--el-fill-color-light) 100%);
}

.border-glow {
  position: relative;
  overflow: hidden;
}

.border-glow::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, var(--el-color-primary), var(--el-color-success));
}

.contact-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.avatar-bg {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 16px var(--el-color-primary-light-5);
}

.contact-header h4 {
  margin: 0 0 4px 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.contact-header p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.email-display {
  background: var(--el-fill-color);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  margin-bottom: 24px;
  border: 1px dashed var(--el-border-color);
}

.email-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
  letter-spacing: 0.5px;
}

.contact-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.action-btn {
  flex: 1;
  border-radius: 12px;
  padding: 12px 0;
  font-weight: 600;
}

.contact-note {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  padding: 12px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
  border-left: 3px solid var(--el-color-primary);
}

.contact-note p {
  margin: 0;
}

@media (max-width: 991px) {
  .contact-card-wrapper {
    margin-top: 40px;
    position: static;
  }
}

@media (max-width: 768px) {
  .title-area {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
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
  margin-top: 32px;
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
