<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Document, Reading, Help, InfoFilled } from '@element-plus/icons-vue'
import { publicDataApi } from '@/api/publicData'

import { useRouter } from 'vue-router'
import { encodePostId } from '@/utils/shortId'
import { ElMessage } from 'element-plus'

const router = useRouter()

const handleAction = (name: string) => {
  const routeMap: Record<string, string> = {
    '阅读指南': '/guide',
    '精华主题': '/featured',
    '反馈帮助': '/feedback',
    '关于站点': '/about'
  }
  const path = routeMap[name]
  if (path) {
    router.push(path)
  } else {
    ElMessage.info(`${name}模块正在开发中，敬请期待！`)
  }
}

const popularItems = ref<any[]>([])
const loadingHot = ref(true)

onMounted(async () => {
  try {
    const res = await publicDataApi.getHomeBootstrapCached(10, 5, 'WEEK')
    if (res.code === 2000 && res.data) {
      popularItems.value = (res.data.hotRank || []).slice(0, 5)
    }
  } catch {
    popularItems.value = []
  } finally {
    loadingHot.value = false
  }

})
</script>

<template>
  <div class="right-rail">
    <!-- Welcome Action Card (2x2 Grid) -->
    <el-card shadow="never" class="rail-card welcome-card">
      <div class="welcome-header">
        <h3 class="card-title">欢迎来到开发者社区</h3>
        <p class="card-desc">探索知识，分享生活，结识同好。</p>
      </div>
      
      <div class="action-grid">
        <div class="action-btn" @click="handleAction('阅读指南')">
          <el-icon class="action-icon"><Reading /></el-icon>
          <span>阅读指南</span>
        </div>
        <div class="action-btn" @click="handleAction('精华主题')">
          <el-icon class="action-icon"><Document /></el-icon>
          <span>精华主题</span>
        </div>
        <div class="action-btn" @click="handleAction('反馈帮助')">
          <el-icon class="action-icon"><Help /></el-icon>
          <span>反馈帮助</span>
        </div>
        <div class="action-btn" @click="handleAction('关于站点')">
          <el-icon class="action-icon"><InfoFilled /></el-icon>
          <span>关于站点</span>
        </div>
      </div>
    </el-card>

    <!-- Trending Posts -->
    <el-card shadow="never" class="rail-card trending-card">
      <template #header>
        <div class="card-header">
          <div class="card-header-text">
            <span>本周行为热榜</span>
            <small>依据本周浏览与评论活跃度智能排序</small>
          </div>
        </div>
      </template>

      <!-- Loading skeleton -->
      <div v-if="loadingHot" class="trending-list">
        <el-skeleton :rows="1" animated v-for="i in 5" :key="i" style="margin-bottom:12px" />
      </div>

      <!-- Populated list -->
      <div v-else class="trending-list">
        <div
          v-for="(item, index) in popularItems"
          :key="item.postId"
          class="trending-item"
          @click="router.push(`/t/${encodePostId(item.postId)}`)"
        >
          <div class="item-rank" :class="{ 'is-top': index < 3 }">{{ index + 1 }}</div>
          <div class="item-content">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-meta">
              <span>{{ item.viewCount }} 阅读</span>
              <span>{{ item.commentCount || 0 }} 互动</span>
            </div>
          </div>
        </div>

        <!-- Empty fallback -->
        <div v-if="popularItems.length === 0" class="empty-hot-premium">
          <div class="empty-glow-box">
            <svg class="empty-icon-svg" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="glowGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#F59E0B" stop-opacity="0.8" />
                  <stop offset="100%" stop-color="#EF4444" stop-opacity="0.3" />
                </linearGradient>
              </defs>
              <circle cx="50" cy="50" r="32" fill="url(#glowGrad)" filter="blur(4px)" opacity="0.15" />
              <path d="M50 20C50 20 62 38 62 48C62 56.8366 56.6274 64 50 64C43.3726 64 38 56.8366 38 48C38 38 50 20 50 20Z" fill="url(#glowGrad)" />
              <path d="M50 35C50 35 56 46 56 52C56 56.4183 53.3137 60 50 60C46.6863 60 44 56.4183 44 52C44 46 50 35 50 35Z" fill="#FFFBEB" opacity="0.8" />
            </svg>
          </div>
          <div class="empty-title">热榜蓄势待发</div>
          <div class="empty-desc">发布优质帖子并吸引更多互动，即可在此引燃本周热门！</div>
        </div>
      </div>
    </el-card>

    <!-- Footer Links -->
    <div class="rail-footer">
      <div class="footer-links">
        <router-link to="/about">关于我们</router-link>
        <router-link to="/terms">用户协议</router-link>
        <router-link to="/privacy">隐私政策</router-link>
        <router-link to="/contact">联系管理</router-link>
      </div>
      <p class="copyright">© 2026 Zens</p>
    </div>
  </div>
</template>

<style scoped>
.right-rail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rail-card {
  border-radius: 12px;
  border-color: var(--el-border-color-lighter);
  background: var(--el-bg-color-overlay);
}

.rail-card :deep(.el-card__body) {
  padding: 20px;
}

/* Song：说明 */
.welcome-card {
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-bg-color-overlay) 100%);
}

.welcome-header {
  margin-bottom: 16px;
}

.welcome-header .card-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.3;
}

.welcome-header .card-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px 8px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.action-btn:hover {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-7);
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
}

.action-icon {
  font-size: 22px;
  margin-bottom: 6px;
  color: var(--el-color-primary);
  transition: transform 0.2s;
}

.action-btn:hover .action-icon {
  transform: scale(1.1);
}

/* Song：说明 */
.trending-card {
  max-height: 480px;
  display: flex;
  flex-direction: column;
}

.trending-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.trending-card :deep(.el-card__body) {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.trending-card :deep(.el-card__body)::-webkit-scrollbar {
  width: 6px;
}

.trending-card :deep(.el-card__body)::-webkit-scrollbar-track {
  background: transparent;
}

.trending-card :deep(.el-card__body)::-webkit-scrollbar-thumb {
  background: var(--el-border-color-light);
  border-radius: 3px;
}

.trending-card :deep(.el-card__body)::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color);
}

.card-header {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-header-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-header-text small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.trending-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.trending-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.2s;
}

.trending-item:hover {
  background-color: var(--el-fill-color-light);
}

.item-rank {
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color);
  border-radius: 6px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  line-height: 1;
}

.item-rank.is-top {
  color: #fff;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.3);
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s;
}

.trending-item:hover .item-title {
  color: var(--el-color-primary);
}

.trending-item {
  transition: background-color 0.18s ease, transform 0.18s ease;
  border-radius: 8px;
}

.trending-item:hover {
  background-color: var(--el-fill-color-light);
  transform: translateX(2px);
}

.item-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.empty-hot-premium {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 30px 16px;
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.15) 0%, rgba(255, 255, 255, 0) 100%);
  border-radius: 12px;
  border: 1px dashed rgba(245, 158, 11, 0.2);
  transition: all 0.3s ease;
}

.empty-hot-premium:hover {
  border-color: rgba(245, 158, 11, 0.35);
  background: linear-gradient(180deg, rgba(254, 243, 199, 0.28) 0%, rgba(255, 255, 255, 0) 100%);
}

.empty-glow-box {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  position: relative;
}

.empty-icon-svg {
  width: 100%;
  height: 100%;
  animation: pulseFlame 2.5s ease-in-out infinite alternate;
}

@keyframes pulseFlame {
  0% {
    transform: scale(0.96);
    filter: drop-shadow(0 2px 4px rgba(245, 158, 11, 0.1));
  }
  100% {
    transform: scale(1.04);
    filter: drop-shadow(0 5px 10px rgba(239, 68, 68, 0.25));
  }
}

.empty-title {
  font-size: 13px;
  font-weight: 700;
  color: #B45309;
  margin-bottom: 6px;
}

.empty-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
  max-width: 180px;
}

/* Song：说明 */
.rail-footer {
  padding: 16px;
  margin-top: 8px;
}

.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  margin-bottom: 12px;
}

.footer-links a {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-decoration: none;
  transition: color 0.2s;
}

.footer-links a:hover {
  color: var(--el-color-primary);
}

.copyright {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  text-align: center;
}
</style>
