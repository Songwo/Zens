<script setup lang="ts">
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { Document } from '@element-plus/icons-vue'

const router = useRouter()
</script>

<template>
  <MainLayout>
    <div class="page-container">
      <div class="page-header">
        <div class="breadcrumb">
          <span class="back-link" @click="router.push('/')">首页</span>
          <span class="separator">/</span>
          <span class="current">用户协议</span>
        </div>
        <div class="title-area">
          <div class="icon-wrap">
            <el-icon><Document /></el-icon>
          </div>
          <div class="title-text">
            <h1>用户协议 <span>(Terms of Service)</span></h1>
            <p class="subtitle">请在使用 Zens 开发者社区前仔细阅读以下条款。</p>
          </div>
        </div>
      </div>

      <el-card shadow="never" class="content-card">
        <div class="meta">最后更新：2026 年 3 月 28 日 &nbsp;·&nbsp; 版本 1.0</div>

        <section>
          <h2>1. 接受条款</h2>
          <p>欢迎使用 Zens 开发者社区（以下简称「本站」）。当您注册账号或以任何方式使用本站时，即视为您已阅读、理解并同意本协议的所有条款。若您不同意任何条款，请立即停止使用本站。</p>
        </section>

        <section>
          <h2>2. 账号注册与安全</h2>
          <ul>
            <li>您需提供真实、准确的注册信息，并妥善保管账号密码；</li>
            <li>每个邮箱仅限注册一个账号；</li>
            <li>您对账号下的全部行为负责；如发现账号被盗用，请立即联系管理员；</li>
            <li>本站有权在不事先通知的情况下暂停或终止违规账号。</li>
          </ul>
        </section>

        <section>
          <h2>3. 内容规范</h2>
          <p>在本站发布内容时，您不得：</p>
          <ul>
            <li>发布违反中华人民共和国法律法规的内容；</li>
            <li>发布侮辱、诽谤、骚扰他人的内容；</li>
            <li>传播垃圾广告、虚假信息或诈骗内容；</li>
            <li>侵犯他人知识产权、肖像权或隐私权；</li>
            <li>发布色情、暴力或其他不良内容。</li>
          </ul>
          <p>违反上述规范的内容将被删除，情节严重者将被封禁账号。</p>
        </section>

        <section>
          <h2>4. 知识产权</h2>
          <p>您在本站发布的内容，版权归您本人所有。您授予本站在全球范围内免费、非独占地展示、传播您所发布内容的权利，用于运营和推广本站社区。</p>
          <p>本站的 Logo、界面设计、源代码等知识产权归 Zens 团队所有，未经授权不得复制或商业使用。</p>
        </section>

        <section>
          <h2>5. 免责声明</h2>
          <ul>
            <li>本站以「现状」提供服务，不对服务的持续可用性、准确性作出保证；</li>
            <li>用户发布内容的真实性由发布者负责，本站不承担连带责任；</li>
            <li>因不可抗力（服务器故障、网络中断等）导致的数据丢失，本站不承担赔偿责任。</li>
          </ul>
        </section>

        <section>
          <h2>6. 协议变更</h2>
          <p>本站有权随时修订本协议，修订后的协议将在本页面公告。继续使用本站即视为接受修订后的条款。</p>
        </section>

        <section>
          <h2>7. 联系我们</h2>
          <p>如对本协议有任何疑问，请通过 <a href="mailto:zhaoqsnyah@gmail.com">zhaoqsnyah@gmail.com</a> 与我们联系。</p>
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
  background: linear-gradient(135deg, var(--el-color-primary-light-8), var(--el-color-primary-light-9));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--el-color-primary);
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
.content-card {
  border-radius: 16px;
  padding: 8px 8px;
}
.meta {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
section {
  margin-bottom: 32px;
}
section h2 {
  font-size: 17px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  padding-left: 12px;
  border-left: 3px solid var(--el-color-primary);
}
section p, section li {
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.9;
  margin: 0 0 8px 0;
}
section ul {
  padding-left: 20px;
}
section a {
  color: var(--el-color-primary);
  text-decoration: none;
}
section a:hover { text-decoration: underline; }
@media (max-width: 768px) {
  .title-area { flex-direction: column; align-items: flex-start; }
}
</style>
