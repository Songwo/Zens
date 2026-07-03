<script setup lang="ts">
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { Lock } from '@element-plus/icons-vue'

const router = useRouter()
</script>

<template>
  <MainLayout>
    <div class="page-container">
      <div class="breadcrumb">
        <span class="back-link" @click="router.push('/')">首页</span>
        <span class="separator">/</span>
        <span class="current">隐私政策</span>
      </div>
      <div class="title-area">
        <div class="icon-wrap icon-green">
          <el-icon><Lock /></el-icon>
        </div>
        <div class="title-text">
          <h1>隐私政策 <span>(Privacy Policy)</span></h1>
          <p class="subtitle">我们重视您的隐私，本文说明我们如何收集、使用和保护您的信息。</p>
        </div>
      </div>

      <el-card shadow="never" class="content-card">
        <div class="meta">最后更新：2026 年 3 月 28 日 &nbsp;·&nbsp; 版本 1.0</div>

        <section>
          <h2 class="h2-green">1. 我们收集哪些信息</h2>
          <p>当您使用本站时，我们可能收集以下信息：</p>
          <ul>
            <li><strong>账号信息：</strong>注册时填写的用户名、邮箱、昵称、头像等；</li>
            <li><strong>内容数据：</strong>您发布的帖子、评论、私信等；</li>
            <li><strong>行为数据：</strong>浏览记录、点赞、收藏等交互行为；</li>
            <li><strong>设备信息：</strong>IP 地址、设备类型、浏览器类型，用于安全风控；</li>
            <li><strong>第三方登录：</strong>使用 GitHub 登录时，我们获取您的 GitHub ID 和公开昵称。</li>
          </ul>
        </section>

        <section>
          <h2 class="h2-green">2. 我们如何使用您的信息</h2>
          <ul>
            <li>提供、运营和改进本站的功能与服务；</li>
            <li>进行内容推荐和个性化展示；</li>
            <li>发送服务通知（如回复提醒、私信提醒）；</li>
            <li>进行安全风控，防止账号滥用和恶意行为；</li>
            <li>统计分析社区趋势，优化产品体验。</li>
          </ul>
        </section>

        <section>
          <h2 class="h2-green">3. 信息存储与保护</h2>
          <ul>
            <li>您的密码经过 BCrypt 加密存储，我们无法读取明文密码；</li>
            <li>账号数据存储于国内服务器，受访问控制和加密保护；</li>
            <li>我们不会向第三方出售您的个人信息；</li>
            <li>匿名发帖功能仅对其他用户隐藏身份，管理员在必要时可查看。</li>
          </ul>
        </section>

        <section>
          <h2 class="h2-green">4. Cookie 与本地存储</h2>
          <p>本站使用 localStorage / sessionStorage 存储登录凭证（JWT Token），不依赖 Cookie 进行身份验证。我们不使用跨站追踪 Cookie。</p>
        </section>

        <section>
          <h2 class="h2-green">5. 您的权利</h2>
          <ul>
            <li><strong>访问：</strong>您可在「个人中心」查看您的账号信息；</li>
            <li><strong>修改：</strong>您可在「设置」页面随时修改昵称、头像、简介等信息；</li>
            <li><strong>删除：</strong>如需注销账号或删除数据，请联系管理员；</li>
            <li><strong>邮件通知：</strong>您可在设置中关闭邮件通知。</li>
          </ul>
        </section>

        <section>
          <h2 class="h2-green">6. 未成年人保护</h2>
          <p>本站主要面向具备独立判断能力的开发者与团队成员。如发现不适宜独立使用本平台的注册行为，请监护人及时联系我们处理。</p>
        </section>

        <section>
          <h2 class="h2-green">7. 政策更新</h2>
          <p>本政策如有重大变更，我们将通过站内通知或邮件告知您。继续使用本站即视为接受更新后的政策。</p>
        </section>

        <section>
          <h2 class="h2-green">8. 联系我们</h2>
          <p>如有隐私相关问题，请发送邮件至 <a href="mailto:zhaoqsnyah@gmail.com">zhaoqsnyah@gmail.com</a>，我们将在 3 个工作日内回复。</p>
        </section>
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
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
}
.icon-green {
  background: linear-gradient(135deg, var(--el-color-success-light-8), var(--el-color-success-light-9));
  color: var(--el-color-success);
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
.content-card { border-radius: 16px; }
.meta {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
section { margin-bottom: 28px; }
section h2 {
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  padding-left: 12px;
  border-left: 3px solid var(--el-color-primary);
}
.h2-green { border-left-color: var(--el-color-success) !important; }
section p, section li {
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.9;
  margin: 0 0 8px 0;
}
section ul { padding-left: 20px; }
section a { color: var(--el-color-primary); text-decoration: none; }
section a:hover { text-decoration: underline; }
@media (max-width: 768px) {
  .title-area { flex-direction: column; align-items: flex-start; }
}
</style>
