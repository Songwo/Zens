<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { InfoFilled, Monitor, Setting, DataLine, Lightning } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { changelogApi, type ChangelogItem } from '@/api/changelog'

const router = useRouter()
const changelogs = ref<ChangelogItem[]>([])

const fallbackRoadmapItems: ChangelogItem[] = [
  {
    id: -1,
    version: 'v1.0.0',
    title: '基础社区功能',
    content: '已完成发帖、评论、标签分类、个人主页和后台管理。',
    stageNo: '01',
    stageLabel: '已上线',
    roadmapStatus: 'released',
    highlights: '发帖/评论/标签',
    timestamp: '已上线',
    status: 1,
    sortOrder: 300
  },
  {
    id: -2,
    version: 'v1.1.0',
    title: '用户体验优化',
    content: '正在优化错误提示、内容推荐、项目展示和消息通知。',
    stageNo: '02',
    stageLabel: '建设中',
    roadmapStatus: 'building',
    highlights: '推荐/通知/展示',
    timestamp: '建设中',
    status: 1,
    sortOrder: 200
  },
  {
    id: -3,
    version: 'v1.2.0',
    title: '内容生态增强',
    content: '计划加入积分等级、问答专区、创作者激励和专栏系统。',
    stageNo: '03',
    stageLabel: '下一阶段',
    roadmapStatus: 'planned',
    highlights: '积分/问答/专栏',
    timestamp: '下一阶段',
    status: 1,
    sortOrder: 100
  }
]

const resolveRoadmapItems = () => {
  if (!changelogs.value.length) return fallbackRoadmapItems
  const configuredItems = changelogs.value.filter(item => (
    item.stageNo || item.stageLabel || item.roadmapStatus || item.highlights
  ))
  return configuredItems.length ? configuredItems : changelogs.value
}

const roadmapItems = computed(() => (
  [...resolveRoadmapItems()].sort((a, b) => {
    const aOrder = a.sortOrder ?? 0
    const bOrder = b.sortOrder ?? 0
    if (aOrder !== bOrder) return bOrder - aOrder
    return getStageNo(a, 0).localeCompare(getStageNo(b, 0), 'zh-CN', { numeric: true })
  })
))

const roadmapCurrentItem = computed(() => (
  roadmapItems.value.find(item => getRoadmapStatus(item) === 'building')
  || roadmapItems.value.find(item => getRoadmapStatus(item) === 'planned')
  || roadmapItems.value[roadmapItems.value.length - 1]
))

const roadmapActionPath = computed(() => (
  roadmapItems.value.find(item => item.actionPath)?.actionPath || ''
))

const getStageNo = (item: ChangelogItem, index: number) => (
  item.stageNo || String(index + 1).padStart(2, '0')
)

const getRoadmapStatus = (item: ChangelogItem) => {
  if (item.roadmapStatus === 'building' || item.stageLabel === '建设中') return 'building'
  if (item.roadmapStatus === 'planned' || item.stageLabel === '下一阶段') return 'planned'
  return 'released'
}

const getStageLabel = (item: ChangelogItem, index: number) => {
  if (item.stageLabel) return item.stageLabel
  const status = getRoadmapStatus(item)
  if (status === 'building') return '建设中'
  if (status === 'planned') return '下一阶段'
  return index === 0 ? '已上线' : '已上线'
}

const getRoadmapStatusText = (item: ChangelogItem) => {
  const status = getRoadmapStatus(item)
  if (status === 'building') return '进行中'
  if (status === 'planned') return '规划中'
  return '已完成'
}

const handleRoadmapCta = () => {
  if (!roadmapActionPath.value) return
  if (/^https?:\/\//.test(roadmapActionPath.value)) {
    window.open(roadmapActionPath.value, '_blank', 'noopener,noreferrer')
    return
  }
  router.push(roadmapActionPath.value)
}

onMounted(async () => {
  try {
    const res = await changelogApi.getList()
    if (res.data) changelogs.value = res.data
  } catch {
    changelogs.value = []
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

      <!-- Roadmap -->
      <div class="section-container roadmap-section">
        <div class="roadmap-panel">
          <div class="roadmap-header">
            <div>
              <p class="roadmap-kicker">社区路线图</p>
              <h3>Zens 正在成长</h3>
              <p v-if="roadmapCurrentItem" class="roadmap-current">
                当前焦点：{{ roadmapCurrentItem.version }} · {{ roadmapCurrentItem.title }}
              </p>
            </div>
            <button class="roadmap-link" :class="{ 'is-static': !roadmapActionPath }" type="button" @click="handleRoadmapCta">
              查看完整路线图 <span>→</span>
            </button>
          </div>

          <div class="roadmap-timeline" :style="{ '--roadmap-count': String(Math.max(roadmapItems.length, 1)) }">
            <article
              v-for="(item, index) in roadmapItems"
              :key="item.id"
              class="roadmap-node"
              :class="[`roadmap-${getRoadmapStatus(item)}`, { 'is-focus': item.id === roadmapCurrentItem?.id }]"
            >
              <div class="roadmap-marker-wrap" aria-hidden="true">
                <span class="roadmap-marker"></span>
              </div>
              <div class="roadmap-card">
                <div class="roadmap-stage">
                  <span class="roadmap-index">{{ getStageNo(item, index) }}</span>
                  <span class="roadmap-status">{{ getStageLabel(item, index) }}</span>
                  <span class="roadmap-state">{{ getRoadmapStatusText(item) }}</span>
                </div>
                <div class="roadmap-version">{{ item.version }}</div>
                <h4>{{ item.title }}</h4>
                <p>{{ item.content }}</p>
                <div v-if="item.highlights" class="roadmap-highlights">{{ item.highlights }}</div>
                <a
                  v-if="item.upgradeEnabled === 1 && item.upgradeUrl"
                  class="roadmap-upgrade"
                  :href="item.upgradeUrl"
                  target="_blank"
                  rel="noreferrer"
                >
                  在线升级
                </a>
              </div>
            </article>
          </div>
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

    </div>
  </MainLayout>
</template>

<style scoped>
.page-container {
  width: 100%;
  max-width: 1200px;
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

.roadmap-section {
  margin-bottom: 48px;
}

.roadmap-panel {
  border-radius: 20px;
  padding: 28px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 16px 40px rgba(31, 41, 55, 0.05);
}

.roadmap-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.roadmap-kicker {
  margin: 0 0 6px 0;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.roadmap-header h3 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.roadmap-current {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.5;
}

.roadmap-link {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 999px;
  padding: 0 14px;
  background: var(--el-fill-color-blank);
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.roadmap-link:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  transform: translateY(-1px);
}

.roadmap-link.is-static {
  cursor: default;
}

.roadmap-link.is-static:hover {
  transform: none;
}

.roadmap-timeline {
  --roadmap-count: 3;
  display: grid;
  grid-template-columns: repeat(var(--roadmap-count), minmax(220px, 1fr));
  gap: 0;
  overflow-x: auto;
  padding: 4px 2px 10px;
  scrollbar-width: thin;
}

.roadmap-node {
  position: relative;
  min-width: 220px;
  padding: 46px 9px 0;
}

.roadmap-node::before {
  content: '';
  position: absolute;
  top: 18px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--el-border-color);
}

.roadmap-node:first-child::before {
  left: 50%;
}

.roadmap-node:last-child::before {
  right: 50%;
}

.roadmap-marker-wrap {
  position: absolute;
  top: 7px;
  left: 50%;
  z-index: 2;
  transform: translateX(-50%);
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--el-bg-color);
  display: flex;
  align-items: center;
  justify-content: center;
}

.roadmap-marker {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--el-color-success);
  box-shadow: 0 0 0 5px var(--el-color-success-light-9);
}

.roadmap-building .roadmap-marker {
  background: var(--el-color-warning);
  box-shadow: 0 0 0 5px var(--el-color-warning-light-9);
}

.roadmap-planned .roadmap-marker {
  background: var(--el-bg-color);
  border: 2px dashed var(--el-color-info);
  box-shadow: 0 0 0 5px var(--el-fill-color-light);
}

.roadmap-card {
  min-height: 250px;
  height: 100%;
  border-radius: 12px;
  padding: 18px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 10px 24px rgba(31, 41, 55, 0.04);
  display: flex;
  flex-direction: column;
}

.roadmap-node.is-focus .roadmap-card {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 14px 30px rgba(64, 158, 255, 0.12);
  transform: translateY(-4px);
}

.roadmap-stage {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--el-color-success);
  font-weight: 800;
  flex-wrap: wrap;
}

.roadmap-building .roadmap-stage {
  color: var(--el-color-warning);
}

.roadmap-planned .roadmap-stage {
  color: var(--el-color-info);
}

.roadmap-index {
  font-size: 20px;
  line-height: 1;
}

.roadmap-status {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--el-color-success-light-9);
  font-size: 12px;
  line-height: 1;
}

.roadmap-building .roadmap-status {
  background: var(--el-color-warning-light-9);
}

.roadmap-planned .roadmap-status {
  background: var(--el-fill-color-light);
}

.roadmap-state {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.roadmap-version {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 700;
}

.roadmap-card h4 {
  margin: 0 0 10px 0;
  color: var(--el-text-color-primary);
  font-size: 17px;
  line-height: 1.35;
  font-weight: 800;
}

.roadmap-card p {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.75;
}

.roadmap-highlights {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.roadmap-upgrade {
  display: inline-flex;
  margin-top: 12px;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 960px) {
  .roadmap-panel {
    padding: 24px;
  }

  .roadmap-header {
    flex-direction: column;
    gap: 14px;
  }

  .roadmap-timeline {
    display: flex;
    flex-direction: column-reverse;
    overflow-x: visible;
    padding: 0 0 0 34px;
  }

  .roadmap-node {
    min-width: 0;
    padding: 0 0 18px 22px;
  }

  .roadmap-node::before {
    top: 0;
    bottom: 0;
    left: -1px;
    right: auto;
    width: 2px;
    height: auto;
  }

  .roadmap-node:first-child::before,
  .roadmap-node:last-child::before {
    left: -1px;
    right: auto;
  }

  .roadmap-marker-wrap {
    top: 2px;
    left: 0;
  }

  .roadmap-card {
    min-height: 0;
  }
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

  .roadmap-panel {
    border-radius: 18px;
    padding: 20px;
  }

  .roadmap-header h3 {
    font-size: 21px;
  }

  .roadmap-link {
    width: 100%;
    justify-content: center;
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
