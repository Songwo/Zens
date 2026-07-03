<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import { publicDataApi, type PublicSiteStats } from '@/api/publicData'
import { usePostComposerStore } from '@/store/postComposer'
import {
  Code2,
  FileText,
  Flame,
  Hash,
  Heart,
  CheckCircle2,
  Compass,
  MessageCircle,
  PencilLine,
  Smile,
  Sparkles,
  Users,
} from 'lucide-vue-next'

const router = useRouter()
const composerStore = usePostComposerStore()
const siteStats = ref<PublicSiteStats>({
  totalPosts: 0,
  totalUsers: 0,
  totalComments: 0,
  todayPosts: 0,
})
const hotTopicCount = ref(8)

const heroStats = computed(() => [
  {
    label: '今日新帖',
    value: siteStats.value.todayPosts,
    icon: MessageCircle,
    tone: 'green',
  },
  {
    label: '活跃用户',
    value: siteStats.value.totalUsers,
    icon: Users,
    tone: 'orange',
  },
  {
    label: '累计互动',
    value: siteStats.value.totalComments,
    icon: Heart,
    tone: 'blue',
  },
  {
    label: '热门话题',
    value: hotTopicCount.value,
    icon: Hash,
    tone: 'purple',
  },
])

const discoveryLinks = [
  { label: '为你推荐', desc: '按兴趣和热度发现内容', path: '/?sort=recommend', icon: Compass },
  { label: '待解决问题', desc: '协同推进团队里的开发难题', path: '/?sort=unsolved', icon: MessageCircle },
  { label: '精华沉淀', desc: '沉淀可复用的经验与方案', path: '/featured', icon: CheckCircle2 },
]

onMounted(async () => {
  try {
    const res = await publicDataApi.getHomeBootstrapCached(8, 5, 'WEEK')
    if (res.code === 2000 && res.data?.siteStats) {
      siteStats.value = res.data.siteStats
    }
    if (res.code === 2000 && Array.isArray(res.data?.hotTags)) {
      hotTopicCount.value = res.data.hotTags.length
    }
  } catch {
    // ignore bootstrap failure on hero
  }
})
</script>

<template>
  <MainLayout>
    <div class="page-content">
      <section class="hero-panel">
        <div class="hero-copy-area">
          <div class="hero-copy">
            <span class="hero-kicker">企业级开发者社区与内容运营平台</span>
            <h1 class="hero-title">沉淀技术知识，驱动社区增长</h1>
            <p class="hero-desc">
              在 Zens 中管理内容流、交流工程实践、沉淀团队经验，让开发者协作与社区运营形成正循环。
            </p>
          </div>

          <div class="hero-actions" aria-label="首页快捷操作">
            <button class="hero-action primary" type="button" @click="composerStore.open()">
              <PencilLine class="action-icon" aria-hidden="true" />
              <span>发布帖子</span>
            </button>
            <button class="hero-action secondary" type="button" @click="router.push('/hot')">
              <Flame class="action-icon" aria-hidden="true" />
              <span>浏览热门</span>
            </button>
          </div>
        </div>

        <div class="hero-visual" aria-hidden="true">
          <div class="visual-stage">
            <div class="visual-orb orb-warm"></div>
            <div class="visual-orb orb-blue"></div>

            <div class="visual-card code-card">
              <div class="visual-card-head">
                <Code2 class="visual-card-icon" />
                <span>topic.ts</span>
              </div>
              <div class="code-lines">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>

            <div class="visual-card chat-card">
              <MessageCircle class="chat-icon" />
              <div class="bubble-lines">
                <span></span>
                <span></span>
              </div>
            </div>

            <div class="visual-card doc-card">
              <FileText class="doc-icon" />
              <div class="doc-lines">
                <span></span>
                <span></span>
                <span></span>
              </div>
            </div>

            <div class="smile-chip">
              <Smile />
            </div>
            <div class="spark-chip spark-one">
              <Sparkles />
            </div>
            <div class="spark-chip spark-two">
              <Sparkles />
            </div>
            <span class="float-dot dot-one"></span>
            <span class="float-dot dot-two"></span>
            <span class="float-dot dot-three"></span>
          </div>
        </div>

        <div class="hero-stats" aria-label="社区数据概览">
          <div
            v-for="item in heroStats"
            :key="item.label"
            class="stat-card"
            :class="`tone-${item.tone}`"
          >
            <div class="stat-icon-wrap">
              <component :is="item.icon" class="stat-icon" aria-hidden="true" />
            </div>
            <div class="stat-copy">
              <span class="stat-label">{{ item.label }}</span>
              <strong class="stat-value">{{ item.value }}</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="discovery-strip" aria-label="社区发现入口">
        <button
          v-for="item in discoveryLinks"
          :key="item.label"
          class="discovery-item"
          type="button"
          @click="router.push(item.path)"
        >
          <component :is="item.icon" class="discovery-icon" aria-hidden="true" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.desc }}</small>
          </span>
        </button>
      </section>

      <TopicList />
    </div>
  </MainLayout>
</template>

<style scoped>
.page-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.hero-panel {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.22fr) minmax(230px, 0.58fr);
  grid-template-areas:
    "copy visual"
    "stats visual";
  gap: 12px 28px;
  min-height: 0;
  padding: 16px 24px;
  border-radius: 18px;
  border: 1px solid rgba(245, 190, 92, 0.34);
  background:
    radial-gradient(circle at 86% 22%, rgba(255, 204, 111, 0.28), transparent 34%),
    linear-gradient(135deg, #fff2bf 0%, #fff8e6 42%, #ffffff 100%);
  box-shadow: 0 14px 34px rgba(156, 105, 26, 0.08), 0 1px 0 rgba(255, 255, 255, 0.8) inset;
}

.hero-panel::before {
  position: absolute;
  inset: 1px;
  z-index: -1;
  content: '';
  border-radius: 17px;
  background:
    radial-gradient(circle at 18% 12%, rgba(255, 255, 255, 0.78), transparent 26%),
    radial-gradient(circle at 60% 100%, rgba(255, 214, 135, 0.2), transparent 34%);
  pointer-events: none;
}

.hero-copy-area {
  grid-area: copy;
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: flex-start;
}

.hero-copy {
  min-width: 0;
}

.hero-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 5px 11px;
  border-radius: 999px;
  border: 1px solid rgba(242, 165, 41, 0.22);
  background: rgba(255, 255, 255, 0.56);
  color: #9a6211;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  box-shadow: 0 8px 20px rgba(242, 165, 41, 0.08);
}

.hero-title {
  max-width: 620px;
  margin: 10px 0 8px;
  font-size: clamp(24px, 2.6vw, 32px);
  line-height: 1.13;
  letter-spacing: 0;
  color: #1f2937;
}

.hero-desc {
  margin: 0;
  max-width: 560px;
  font-size: 14px;
  line-height: 1.55;
  color: #6b5a3f;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.hero-action {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 999px;
  padding: 0 18px;
  border: 1px solid transparent;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.22s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.hero-action:active {
  transform: scale(0.98);
}

.hero-action.primary {
  color: #fff;
  background: linear-gradient(135deg, #f6b800 0%, #f29b24 100%);
  box-shadow: 0 12px 24px rgba(242, 155, 36, 0.26);
}

.hero-action.primary:hover {
  box-shadow: 0 16px 28px rgba(242, 155, 36, 0.32);
  transform: translateY(-1px);
}

.hero-action.secondary {
  color: #8a5a00;
  background: rgba(255, 255, 255, 0.74);
  border-color: rgba(231, 174, 79, 0.4);
  box-shadow: 0 10px 22px rgba(82, 58, 22, 0.06);
}

.hero-action.secondary:hover {
  border-color: rgba(231, 174, 79, 0.72);
  background: #fff;
  transform: translateY(-1px);
}

.action-icon {
  width: 17px;
  height: 17px;
  stroke-width: 2.4;
}

.hero-visual {
  grid-area: visual;
  position: relative;
  display: flex;
  min-height: 0;
  align-items: center;
}

.visual-stage {
  position: relative;
  width: min(100%, 300px);
  height: 140px;
  margin: 0 0 0 auto;
  transform: translate3d(0, 2px, 0);
}

.visual-orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(10px);
  opacity: 0.7;
}

.orb-warm {
  right: 28px;
  bottom: 10px;
  width: 150px;
  height: 54px;
  background: rgba(247, 180, 58, 0.24);
}

.orb-blue {
  top: 28px;
  right: 16px;
  width: 78px;
  height: 78px;
  background: rgba(106, 155, 204, 0.18);
}

.visual-card,
.smile-chip,
.spark-chip {
  position: absolute;
  border: 1px solid rgba(231, 174, 79, 0.24);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 34px rgba(124, 84, 24, 0.13);
  backdrop-filter: blur(14px);
}

.visual-card {
  border-radius: 20px;
}

.code-card {
  top: 8px;
  right: 38px;
  width: 168px;
  min-height: 78px;
  padding: 12px;
  transform: rotate(-4deg);
}

.visual-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #72511d;
  font-size: 11px;
  font-weight: 800;
}

.visual-card-icon {
  width: 18px;
  height: 18px;
  color: #f29b24;
}

.code-lines,
.doc-lines,
.bubble-lines {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 9px;
}

.code-lines span,
.doc-lines span,
.bubble-lines span {
  display: block;
  height: 7px;
  border-radius: 999px;
  background: #f2d592;
}

.code-lines span:nth-child(1) {
  width: 84%;
}

.code-lines span:nth-child(2) {
  width: 58%;
  background: #b8d5f0;
}

.code-lines span:nth-child(3) {
  width: 72%;
  background: #f7c86a;
}

.chat-card {
  top: 70px;
  left: 18px;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 9px;
  width: 144px;
  min-height: 62px;
  padding: 10px;
  transform: rotate(5deg);
}

.chat-icon {
  width: 32px;
  height: 32px;
  padding: 7px;
  border-radius: 12px;
  color: #35a66f;
  background: #e9f8ef;
}

.bubble-lines {
  margin-top: 2px;
}

.bubble-lines span:nth-child(1) {
  width: 86px;
  background: #cdeed9;
}

.bubble-lines span:nth-child(2) {
  width: 62px;
  background: #f3d080;
}

.doc-card {
  right: 18px;
  bottom: 0;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 9px;
  width: 148px;
  min-height: 66px;
  padding: 10px;
  transform: rotate(3deg);
}

.doc-icon {
  width: 32px;
  height: 32px;
  padding: 7px;
  border-radius: 12px;
  color: #4b8edb;
  background: #eaf3ff;
}

.doc-lines {
  gap: 7px;
  margin-top: 1px;
}

.doc-lines span:nth-child(1) {
  width: 74px;
  background: #b9d7f2;
}

.doc-lines span:nth-child(2) {
  width: 54px;
  background: #f5cf79;
}

.doc-lines span:nth-child(3) {
  width: 66px;
  background: #e4e8ef;
}

.smile-chip {
  top: 0;
  left: 64px;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 15px;
  color: #f29b24;
  transform: rotate(8deg);
}

.smile-chip svg {
  width: 22px;
  height: 22px;
}

.spark-chip {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 11px;
  color: #a56de2;
  background: rgba(255, 255, 255, 0.9);
}

.spark-one {
  top: 18px;
  right: 4px;
  animation: soft-float 4s ease-in-out infinite;
}

.spark-two {
  bottom: 20px;
  left: 0;
  color: #f2a51f;
  animation: soft-float 4.8s ease-in-out infinite reverse;
}

.spark-chip svg {
  width: 16px;
  height: 16px;
}

.float-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #f6c453;
  box-shadow: 0 8px 18px rgba(246, 196, 83, 0.3);
}

.dot-one {
  top: 66px;
  left: 40px;
}

.dot-two {
  right: 2px;
  bottom: 66px;
  width: 12px;
  height: 12px;
  background: #9fd0ff;
}

.dot-three {
  left: 164px;
  bottom: 12px;
  width: 8px;
  height: 8px;
  background: #82d8ad;
}

.hero-stats {
  grid-area: stats;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.stat-card {
  display: flex;
  min-width: 0;
  min-height: 48px;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(228, 189, 122, 0.22);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 22px rgba(106, 74, 28, 0.06);
  backdrop-filter: blur(12px);
}

.stat-icon-wrap {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 12px;
}

.stat-icon {
  width: 17px;
  height: 17px;
  stroke-width: 2.4;
}

.stat-copy {
  min-width: 0;
}

.stat-label {
  display: block;
  font-size: 12px;
  font-weight: 700;
  color: #6b5a3f;
}

.stat-value {
  display: block;
  margin-top: 2px;
  font-size: 20px;
  line-height: 1;
  color: #1f2937;
}

.tone-green .stat-icon-wrap {
  color: #26a269;
  background: #e8f8ef;
}

.tone-orange .stat-icon-wrap {
  color: #e68a1f;
  background: #fff1d7;
}

.tone-blue .stat-icon-wrap {
  color: #3f8ed8;
  background: #eaf4ff;
}

.tone-purple .stat-icon-wrap {
  color: #8e63d9;
  background: #f2ecff;
}

.discovery-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.discovery-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 58px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color-overlay);
  cursor: pointer;
  padding: 10px 12px;
  text-align: left;
  transition: background-color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.discovery-item:hover {
  border-color: rgba(244, 180, 0, 0.42);
  background: var(--el-fill-color-extra-light);
  transform: translateY(-1px);
}

.discovery-icon {
  width: 20px;
  height: 20px;
  color: #d18a00;
}

.discovery-item span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.discovery-item strong {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.discovery-item small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes soft-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }

  50% {
    transform: translate3d(0, -8px, 0);
  }
}

@media (max-width: 900px) {
  .hero-panel {
    grid-template-columns: 1fr;
    grid-template-areas:
      "copy"
      "visual"
      "stats";
    padding: 24px;
  }

  .hero-title {
    font-size: 30px;
  }

  .visual-stage {
    margin: 0 auto;
  }

  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .discovery-strip {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .page-content {
    gap: 10px;
  }

  .discovery-strip {
    gap: 8px;
  }

  .discovery-item {
    min-height: 50px;
    border-radius: 12px;
  }

  .hero-panel {
    min-height: 0;
    padding: 14px 16px 12px;
    border-radius: 18px;
    gap: 10px;
  }

  .hero-panel::before {
    border-radius: 17px;
  }

  .hero-kicker {
    padding: 4px 9px;
    font-size: 11px;
  }

  .hero-title {
    margin: 8px 0 6px;
    font-size: 23px;
    line-height: 1.18;
  }

  .hero-desc {
    font-size: 13px;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    overflow: hidden;
  }

  .hero-actions {
    margin-top: 10px;
  }

  .hero-action.primary {
    display: none;
  }

  .hero-action {
    flex: 1 1 100%;
    min-height: 34px;
    padding: 0 14px;
    font-size: 13px;
  }

  .hero-visual {
    display: none;
  }

  .hero-stats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 6px;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
  }

  .hero-stats::-webkit-scrollbar {
    display: none;
  }

  .stat-card {
    min-width: 76px;
    min-height: 48px;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 5px;
    padding: 8px;
    border-radius: 12px;
  }

  .stat-icon-wrap {
    width: 24px;
    height: 24px;
    border-radius: 9px;
  }

  .stat-icon {
    width: 14px;
    height: 14px;
  }

  .stat-label {
    font-size: 10px;
    line-height: 1;
    white-space: nowrap;
  }

  .stat-value {
    margin-top: 0;
    font-size: 17px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .spark-one,
  .spark-two {
    animation: none;
  }
}
</style>
