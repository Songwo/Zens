<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import TopicList from '@/components/topic/TopicList.vue'
import { publicDataApi, type PublicSiteStats } from '@/api/publicData'
import { usePostComposerStore } from '@/store/postComposer'
import { useUserStore } from '@/store/user'
import {
  CheckCircle2,
  Flame,
  Hash,
  Heart,
  MessageCircle,
  PencilLine,
  Users,
} from 'lucide-vue-next'

const router = useRouter()
const composerStore = usePostComposerStore()
const userStore = useUserStore()
const siteStats = ref<PublicSiteStats>({
  totalPosts: 0,
  totalUsers: 0,
  totalComments: 0,
  todayPosts: 0,
})
const hotTopicCount = ref(8)
const unsolvedQaCount = ref(0)
const todaySolvedQaCount = ref(0)
const followedTagUpdateCount = ref(0)

const pulseCards = computed(() => [
  {
    label: '今日新帖',
    value: siteStats.value.todayPosts,
    desc: '快速扫过今天的新讨论',
    icon: MessageCircle,
    tone: 'green',
    path: '/',
  },
  {
    label: '待解决',
    value: unsolvedQaCount.value,
    desc: '答疑解惑里等待回答',
    icon: CheckCircle2,
    tone: 'blue',
    path: '/?sort=unsolved',
  },
  {
    label: '热门主题',
    value: hotTopicCount.value,
    desc: '本周被反复讨论的话题',
    icon: Hash,
    tone: 'purple',
    path: '/hot',
  },
  {
    label: '关注更新',
    value: userStore.isLoggedIn ? followedTagUpdateCount.value : '登录',
    desc: userStore.isLoggedIn ? '关注标签今日新帖' : '登录后同步兴趣',
    icon: Heart,
    tone: 'orange',
    path: userStore.isLoggedIn ? '/me' : '/auth',
  },
])

const communitySignals = computed(() => [
  { label: '活跃用户', value: siteStats.value.totalUsers, icon: Users },
  { label: '累计帖子', value: siteStats.value.totalPosts, icon: Flame },
  { label: '今日解决', value: todaySolvedQaCount.value, icon: CheckCircle2 },
])

onMounted(async () => {
  try {
    const res = await publicDataApi.getHomeBootstrapCached(12, 5, 'WEEK')
    if (res.code === 2000 && res.data?.siteStats) {
      siteStats.value = res.data.siteStats
    }
    if (res.code === 2000 && Array.isArray(res.data?.hotTags)) {
      hotTopicCount.value = res.data.hotTags.length
    }
    if (res.code === 2000 && res.data) {
      unsolvedQaCount.value = Number(res.data.unsolvedQaCount || 0)
      todaySolvedQaCount.value = Number(res.data.todaySolvedQaCount || 0)
      followedTagUpdateCount.value = Number(res.data.followedTagUpdateCount || 0)
    }
  } catch {
    // ignore bootstrap failure on hero
  }
})
</script>

<template>
  <MainLayout>
    <div class="page-content">
      <section class="pulse-hero" aria-label="社区脉搏">
        <div class="pulse-copy">
          <span class="pulse-kicker">社区脉搏</span>
          <h1 class="pulse-title">先看今天值得参与的讨论</h1>
          <p class="pulse-desc">
            Zens 会把新帖、热门主题、待解决问题和精华内容放到同一个入口里，让你少翻几页，多参与一次有价值的交流。
          </p>

          <div class="pulse-actions" aria-label="首页快捷操作">
            <button class="pulse-action primary" type="button" @click="composerStore.open()">
              <PencilLine class="action-icon" aria-hidden="true" />
              <span>发布帖子</span>
            </button>
            <button class="pulse-action secondary" type="button" @click="router.push('/hot')">
              <Flame class="action-icon" aria-hidden="true" />
              <span>看热榜</span>
            </button>
          </div>
        </div>

        <div class="pulse-board">
          <button
            v-for="item in pulseCards"
            :key="item.label"
            class="pulse-card"
            :class="`tone-${item.tone}`"
            type="button"
            @click="router.push(item.path)"
          >
            <span class="pulse-card-icon">
              <component :is="item.icon" aria-hidden="true" />
            </span>
            <span class="pulse-card-copy">
              <small>{{ item.label }}</small>
              <strong>{{ item.value }}</strong>
              <em>{{ item.desc }}</em>
            </span>
          </button>
        </div>

        <div class="signal-row" aria-label="社区数据概览">
          <div v-for="item in communitySignals" :key="item.label" class="signal-item">
            <component :is="item.icon" class="signal-icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
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

.pulse-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(360px, 1.05fr);
  grid-template-areas:
    "copy board"
    "signals board";
  gap: 14px 18px;
  padding: 18px;
  border: 1px solid rgba(234, 179, 74, 0.28);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(255, 248, 229, 0.98) 0%, rgba(255, 255, 255, 0.98) 56%),
    var(--el-bg-color-overlay);
  box-shadow: 0 14px 34px rgba(156, 105, 26, 0.08);
}

.pulse-copy {
  grid-area: copy;
  min-width: 0;
}

.pulse-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid rgba(242, 165, 41, 0.24);
  background: rgba(255, 255, 255, 0.68);
  color: #9a6211;
  font-size: 12px;
  font-weight: 800;
}

.pulse-title {
  max-width: 520px;
  margin: 10px 0 8px;
  color: #1f2937;
  font-size: 30px;
  line-height: 1.16;
  letter-spacing: 0;
}

.pulse-desc {
  max-width: 560px;
  margin: 0;
  color: #6b5a3f;
  font-size: 14px;
  line-height: 1.58;
}

.pulse-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 15px;
}

.pulse-action {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.22s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.pulse-action:active {
  transform: scale(0.98);
}

.pulse-action.primary {
  color: #fff;
  background: linear-gradient(135deg, #f6b800 0%, #f29b24 100%);
  box-shadow: 0 12px 24px rgba(242, 155, 36, 0.24);
}

.pulse-action.secondary {
  color: #8a5a00;
  background: rgba(255, 255, 255, 0.78);
  border-color: rgba(231, 174, 79, 0.42);
}

.pulse-action:hover {
  transform: translateY(-1px);
}

.action-icon {
  width: 17px;
  height: 17px;
  stroke-width: 2.4;
}

.pulse-board {
  grid-area: board;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.pulse-card {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  min-height: 118px;
  padding: 14px;
  border: 1px solid rgba(228, 189, 122, 0.22);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.76);
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(106, 74, 28, 0.055);
  transition: transform 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}

.pulse-card:hover {
  border-color: rgba(244, 180, 0, 0.42);
  background: #fff;
  transform: translateY(-1px);
}

.pulse-card-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
}

.pulse-card-icon svg {
  width: 19px;
  height: 19px;
  stroke-width: 2.4;
}

.pulse-card-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.pulse-card-copy small {
  color: #6b5a3f;
  font-size: 12px;
  font-weight: 800;
}

.pulse-card-copy strong {
  color: #1f2937;
  font-size: 25px;
  line-height: 1;
}

.pulse-card-copy em {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-style: normal;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.tone-green .pulse-card-icon {
  color: #26a269;
  background: #e8f8ef;
}

.tone-orange .pulse-card-icon {
  color: #e68a1f;
  background: #fff1d7;
}

.tone-blue .pulse-card-icon {
  color: #3f8ed8;
  background: #eaf4ff;
}

.tone-purple .pulse-card-icon {
  color: #8e63d9;
  background: #f2ecff;
}

.signal-row {
  grid-area: signals;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-self: end;
}

.signal-item {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 7px;
  border: 1px solid rgba(228, 189, 122, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  padding: 0 11px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
}

.signal-icon {
  width: 14px;
  height: 14px;
  color: #d18a00;
}

.signal-item strong {
  color: var(--el-text-color-primary);
}

@media (max-width: 900px) {
  .pulse-hero {
    grid-template-columns: 1fr;
    grid-template-areas:
      "copy"
      "board"
      "signals";
  }

  .pulse-title {
    font-size: 28px;
  }
}

@media (max-width: 560px) {
  .page-content {
    gap: 10px;
  }

  .pulse-hero {
    padding: 14px;
    border-radius: 16px;
    gap: 12px;
  }

  .pulse-title {
    margin: 8px 0 6px;
    font-size: 23px;
    line-height: 1.18;
  }

  .pulse-desc {
    font-size: 13px;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    overflow: hidden;
  }

  .pulse-actions {
    margin-top: 10px;
  }

  .pulse-action {
    flex: 1 1 0;
    min-width: 0;
    min-height: 34px;
    padding: 0 12px;
    font-size: 13px;
  }

  .pulse-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .pulse-card {
    grid-template-columns: 1fr;
    min-height: 112px;
    gap: 8px;
    padding: 10px;
    border-radius: 12px;
  }

  .pulse-card-icon {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }

  .pulse-card-icon svg {
    width: 16px;
    height: 16px;
  }

  .pulse-card-copy strong {
    font-size: 21px;
  }

  .pulse-card-copy em {
    -webkit-line-clamp: 1;
  }

  .signal-row {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .signal-item {
    justify-content: center;
    min-width: 0;
    padding: 0 8px;
  }

}
</style>
