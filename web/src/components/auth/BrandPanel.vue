<script setup lang="ts">
import { Connection, Opportunity, Guide, Medal, User } from '@element-plus/icons-vue'
</script>

<template>
  <div class="brand-panel">
    <div class="brand-content">
      <!-- Logo & Title -->
      <div class="brand-header">
        <img src="/logo.png" alt="Zens" class="brand-logo-img" />
        <h1 class="brand-title">Zens</h1>
      </div>

      <!-- Slogans (Others: "连接年轻开发者的脉搏，构建真实的校园工程师网络", "用代码重塑校园，极客精神从这里向外生长") -->
      <p class="brand-slogan">
        汇聚纯粹的创造力，在技术洪流中保持独立思考。
      </p>

      <div class="brand-advocacy">
        <h3>我们倡导</h3>
        <ul>
          <li>
            <el-icon><Opportunity /></el-icon>
            <span><strong>深度思考：</strong>拒绝低质灌水，鼓励高质量技术长文</span>
          </li>
          <li>
            <el-icon><Connection /></el-icon>
            <span><strong>开放互助：</strong>开源精神，无私分享踩坑与最佳实践</span>
          </li>
          <li>
            <el-icon><Guide /></el-icon>
            <span><strong>就事论事：</strong>保持理性客观，尊重每一种技术选择</span>
          </li>
        </ul>
      </div>

      <div class="brand-benefits">
        <h3>你将获得</h3>
        <div class="benefit-cards">
          <div class="benefit-card">
            <el-icon class="b-icon"><Medal /></el-icon>
            <div class="b-text">真实的工程级经验沉淀</div>
          </div>
          <div class="benefit-card">
            <el-icon class="b-icon"><User /></el-icon>
            <div class="b-text">高质量的同辈学习圈子</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Security Tip at the bottom -->
    <div class="brand-footer">
      <p class="security-tip">🔒 隐私与安全：为了构建可信赖的社区环境，我们需要验证您的身份。我们会严格保护您的数据，绝不用于营销。</p>
    </div>
  </div>
</template>

<style scoped>
.brand-panel {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, #FAFAFA 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 64px;
  box-sizing: border-box;
  color: var(--el-text-color-primary);
  border-right: 1px solid var(--el-border-color-lighter);
}

.brand-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.brand-logo-img {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  object-fit: contain;
  box-shadow: var(--el-box-shadow-light);
}

.brand-title {
  font-size: 32px;
  font-weight: 900;
  margin: 0;
  letter-spacing: -0.02em;
}

.brand-slogan {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  margin-bottom: 48px;
}

.brand-advocacy h3,
.brand-benefits h3 {
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--el-text-color-secondary);
  font-weight: 700;
  margin-bottom: 16px;
}

.brand-advocacy ul {
  list-style: none;
  padding: 0;
  margin: 0 0 40px 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-advocacy li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.brand-advocacy li .el-icon {
  font-size: 18px;
  color: var(--el-color-primary);
  margin-top: 2px;
}

.benefit-cards {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.benefit-card {
  background: var(--cp-bg-card);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: var(--el-box-shadow-lighter);
  flex: 1;
  min-width: 200px;
}

.benefit-card .b-icon {
  font-size: 24px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  padding: 8px;
  border-radius: 50%;
}

.benefit-card .b-text {
  font-weight: 600;
  font-size: 13px;
}

.brand-footer {
  margin-top: 40px;
}

.security-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  background: rgba(0,0,0,0.02);
  padding: 12px 16px;
  border-radius: 6px;
  margin: 0;
}

@media (max-width: 992px) {
  .brand-panel {
    padding: 32px 24px;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  
  .brand-slogan {
    margin-bottom: 24px;
  }
  
  .brand-advocacy, .brand-benefits, .brand-footer {
    display: none; /* Song：小屏折叠复杂信息，保留横幅 */
  }
}
</style>
