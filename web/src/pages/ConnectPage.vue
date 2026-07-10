<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import MainLayout from '@/layouts/MainLayout.vue'
import { levelApi, type LevelInfo, type LevelThreshold, type LevelExpRecord } from '@/api/level'
import { trustLevelApi, type TrustInfo } from '@/api/trustLevel'
import { TRUST_LEVELS, trustLevelColor, trustLevelLabel } from '@/utils/trustLevel'
import { levelTitle } from '@/utils/levelPrivileges'
import { userApi, type SupportContact } from '@/api/user'
import { useUserStore } from '@/store/user'
import { useRouter } from 'vue-router'
import { cachedRequest } from '@/utils/requestCache'
import {
  Trophy,
  Star,
  ChatLineRound,
  CollectionTag,
  User as UserIcon,
  View,
  TrendCharts,
  Lightning
} from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()
const LEVEL_THRESHOLDS_CACHE_TTL = 30 * 60 * 1000
const SUPPORT_CONTACT_CACHE_TTL = 5 * 60 * 1000

const levelInfo = ref<LevelInfo | null>(null)
const trustInfo = ref<TrustInfo | null>(null)
const thresholds = ref<LevelThreshold[]>([])
const loading = ref(false)
const recentRecordsLoading = ref(false)
const recentExpRecords = ref<LevelExpRecord[]>([])
const fullRecordsVisible = ref(false)
const fullRecordsLoading = ref(false)
const fullExpRecords = ref<LevelExpRecord[]>([])
const fullPagination = ref({
  current: 1,
  size: 12,
  total: 0,
  pages: 0
})
const supportContact = ref<SupportContact | null>(null)
const mailEntryUrl = (import.meta.env.VITE_ZENSMAIL_URL || '').trim()
const LV6_REQUIRED_EXP = 1500

const isLoggedIn = computed(() => !!userStore.accessToken)
const isLv6Unlocked = computed(() => (levelInfo.value?.level || 0) >= 6)
const levelThresholdMap = computed(() =>
  Object.fromEntries((thresholds.value || []).map(item => [item.level, item.experience]))
)
const lv6RemainingExp = computed(() => {
  if (!levelInfo.value) return LV6_REQUIRED_EXP
  return Math.max(0, LV6_REQUIRED_EXP - levelInfo.value.experience)
})
const level5RequiredExp = computed(() => levelThresholdMap.value[5] || 500)
const extraBenefits = computed(() => {
  const currentLevel = levelInfo.value?.level || 0
  const currentExp = levelInfo.value?.experience || 0
  const thresholdMap = levelThresholdMap.value
  const definitions = [
    {
      level: 3,
      title: '二维码工具',
      icon: 'QR',
      url: 'https://zens.cc.cd/',
      buttonText: '打开二维码服务',
      description: '提供便捷的二维码生成与分享入口，适合活动报名、链接分发和移动端扫码跳转。',
    },
    {
      level: 7,
      title: '图床系统',
      icon: '🖼️',
      url: 'https://zensimagebed.pages.dev',
      buttonText: '前往图床',
      description: '支持更轻量的图片上传与外链分发，适合发帖配图、封面图和素材暂存。',
    },
    {
      level: 8,
      title: '文件快传',
      icon: '⚡',
      url: 'https://znes.indevs.in',
      buttonText: '打开快传',
      description: '提供更高效的文件临时传输入口，适合跨设备同步资料和活动素材分享。',
    },
    {
      level: 9,
      title: '临时邮箱',
      icon: '📨',
      url: 'https://mail.songboke.us.kg',
      buttonText: '进入临时邮箱',
      description: '适合短期注册验证与测试场景，作为社区扩展工具使用更方便。',
    },
    {
      level: 10,
      title: 'Zens AI 公益站',
      icon: '🤖',
      url: 'https://zensapi.cc.cd',
      buttonText: '进入 AI 公益站',
      description: '开放给高等级活跃用户的 AI 工具入口，可用于学习、检索和创作辅助。',
    },
  ]

  return definitions.map(item => {
    const requiredExp = thresholdMap[item.level] || 0
    return {
      ...item,
      unlocked: currentLevel >= item.level,
      requiredExp,
      remainingExp: Math.max(0, requiredExp - currentExp),
    }
  })
})

const expNeeded = computed(() => {
  if (!levelInfo.value) return 0
  if (levelInfo.value.level >= 10) return 0
  return levelInfo.value.nextLevelExp - levelInfo.value.experience
})

const levelColor = computed(() => {
  if (!levelInfo.value) return '#909399'
  const lv = levelInfo.value.level
  if (lv >= 9) return '#E6A23C'
  if (lv >= 7) return '#F56C6C'
  if (lv >= 5) return '#409EFF'
  if (lv >= 3) return '#67C23A'
  return '#909399'
})

const expRules = [
  { action: '发帖', exp: '+5', icon: TrendCharts, desc: '发布一篇帖子' },
  { action: '被点赞', exp: '+2', icon: Star, desc: '你的帖子被他人点赞' },
  { action: '被评论', exp: '+3', icon: ChatLineRound, desc: '你的帖子收到评论' },
  { action: '被收藏', exp: '+3', icon: CollectionTag, desc: '你的帖子被他人收藏' },
  { action: '每日登录', exp: '+1', icon: UserIcon, desc: '每天首次登录' },
  { action: '每日浏览', exp: '+1 (上限5)', icon: View, desc: '浏览帖子，每天最多5次' },
]

const fetchLevelInfo = async () => {
  if (!isLoggedIn.value) return
  try {
    const res = await levelApi.getInfo()
    levelInfo.value = res.data
  } catch {
    // Song：说明
  }
}

const fetchTrustInfo = async () => {
  if (!isLoggedIn.value) return
  try {
    const res = await trustLevelApi.info()
    trustInfo.value = res.data
  } catch {
    // Song：信任等级获取失败不阻塞页面
  }
}

const currentTrustLevel = computed(() => trustInfo.value?.trustLevel ?? userStore.userInfo?.trustLevel ?? 0)
const expTitle = computed(() => levelInfo.value ? levelTitle(levelInfo.value.level) : '')

const fetchThresholds = async () => {
  try {
    const res = await cachedRequest(
      'level:thresholds',
      LEVEL_THRESHOLDS_CACHE_TTL,
      () => levelApi.getThresholds()
    )
    thresholds.value = res.data || []
  } catch {
    // Song：说明
  }
}

const fetchRecentExpRecords = async () => {
  if (!isLoggedIn.value) return
  recentRecordsLoading.value = true
  try {
    const res = await levelApi.getExpRecords({ days: 7, page: 1, pageSize: 10 })
    recentExpRecords.value = res.data?.records || []
  } catch {
    recentExpRecords.value = []
  } finally {
    recentRecordsLoading.value = false
  }
}

const fetchFullExpRecords = async (page = 1) => {
  if (!isLoggedIn.value) return
  fullRecordsLoading.value = true
  try {
    const res = await levelApi.getExpRecords({ days: 0, page, pageSize: fullPagination.value.size })
    fullExpRecords.value = res.data?.records || []
    fullPagination.value.current = Number(res.data?.current || page)
    fullPagination.value.total = Number(res.data?.total || 0)
    fullPagination.value.pages = Number(res.data?.pages || 0)
  } catch {
    fullExpRecords.value = []
    fullPagination.value.total = 0
    fullPagination.value.pages = 0
  } finally {
    fullRecordsLoading.value = false
  }
}

const openFullRecords = async () => {
  fullRecordsVisible.value = true
  fullPagination.value.current = 1
  await fetchFullExpRecords(1)
}

const handleFullRecordPageChange = async (page: number) => {
  await fetchFullExpRecords(page)
}

const formatDateTime = (value: string) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const yyyy = date.getFullYear()
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

const fetchSupportContact = async () => {
  if (!isLoggedIn.value) return
  try {
    const res = await cachedRequest(
      'level:support-contact',
      SUPPORT_CONTACT_CACHE_TTL,
      () => userApi.getSupportContact()
    )
    supportContact.value = res.data || null
  } catch {
    supportContact.value = null
  }
}

const ensureSupportContactLoaded = async () => {
  if (supportContact.value || !isLoggedIn.value) return
  await fetchSupportContact()
}

const goSupportDm = async () => {
  await ensureSupportContactLoaded()
  const mailClaimDraft = '你好，我已达到 Lv6，申请领取社区独立域名邮箱。'
  if (supportContact.value?.id) {
    router.push({
      path: '/messages',
      query: {
        peerId: supportContact.value.id,
        peerName: supportContact.value.nickname || supportContact.value.username || '管理员',
        draft: mailClaimDraft,
      },
    })
    return
  }
  router.push({
    path: '/messages',
    query: {
      draft: mailClaimDraft,
    },
  })
}

const goLogin = () => {
  router.push('/auth?type=login')
}

onMounted(async () => {
  loading.value = true
  await Promise.all([fetchThresholds(), fetchLevelInfo(), fetchRecentExpRecords(), fetchTrustInfo()])
  if (isLv6Unlocked.value) {
    await fetchSupportContact()
  }
  loading.value = false
})

watch(isLoggedIn, async (val) => {
  if (!val) {
    levelInfo.value = null
    recentExpRecords.value = []
    supportContact.value = null
    return
  }
  await Promise.all([fetchLevelInfo(), fetchRecentExpRecords()])
  if (isLv6Unlocked.value) {
    await fetchSupportContact()
  }
})

watch(isLv6Unlocked, async (unlocked) => {
  if (!unlocked || supportContact.value || !isLoggedIn.value) return
  await fetchSupportContact()
})
</script>

<template>
  <MainLayout>
    <div class="page-container connect-page" v-loading="loading">
      
      <div class="page-header">
        <div class="breadcrumb">
          <span class="back-link" @click="router.push('/')">首页</span>
          <span class="separator">/</span>
          <span class="current">等级中心</span>
        </div>
        <div class="title-area">
          <div class="icon-bulb">
            <el-icon><Lightning /></el-icon>
          </div>
          <div class="title-text">
            <h1>等级中心 <span>(Account Level)</span></h1>
            <p class="subtitle">双轴等级体系：信任等级（TL）控制功能权限，资历等级（Lv）记录社区贡献。</p>
          </div>
        </div>
      </div>

      <!-- Song：双轴主横幅 —— TL 主轴（管权限）+ Lv 副轴（管资历），点击进对应详情页 -->
      <div v-if="isLoggedIn" class="dual-axis-banner">
        <div class="axis-card axis-trust" :style="{ '--axis-color': trustLevelColor(currentTrustLevel) }" @click="router.push('/trust')">
          <div class="axis-head">
            <span class="axis-tag">信任等级</span>
            <span class="axis-level" :style="{ background: trustLevelColor(currentTrustLevel) }">
              TL{{ currentTrustLevel }} · {{ trustLevelLabel(currentTrustLevel) }}
            </span>
          </div>
          <p class="axis-desc">{{ TRUST_LEVELS[currentTrustLevel]?.description }}</p>
          <div class="axis-privs">
            <span v-for="p in (TRUST_LEVELS[currentTrustLevel]?.privileges || [])" :key="p" class="axis-priv-tag">{{ p }}</span>
          </div>
          <span class="axis-link">查看信任详情 →</span>
        </div>
        <div class="axis-card axis-exp" @click="router.push('/level')">
          <div class="axis-head">
            <span class="axis-tag axis-tag-secondary">资历等级</span>
            <span class="axis-level axis-level-secondary" v-if="levelInfo">Lv.{{ levelInfo.level }} · {{ expTitle }}</span>
          </div>
          <p class="axis-desc">由发帖/签到攒经验驱动，记录你在社区的资历与贡献。</p>
          <div class="axis-privs">
            <span class="axis-priv-tag">{{ levelInfo?.experience ?? 0 }} 经验值</span>
            <span class="axis-priv-tag" v-if="levelInfo && levelInfo.level < 10">距 Lv.{{ levelInfo.level + 1 }} 还差 {{ expNeeded }}</span>
          </div>
          <span class="axis-link">查看资历详情 →</span>
        </div>
      </div>

      <!-- Level Card (logged in) -->
      <div v-if="isLoggedIn && levelInfo" class="vip-card-wrapper">
        <div class="vip-card" :style="{ '--user-level-color': levelColor }">
          <div class="vip-bg-pattern"></div>
          
          <div class="vip-content">
            <div class="vip-top">
              <div class="user-info">
                <el-avatar :size="72" :src="userStore.userInfo?.avatar" class="vip-avatar">
                  {{ userStore.userInfo?.nickname?.charAt(0) || 'U' }}
                </el-avatar>
                <div class="user-meta">
                  <h2 class="user-name">{{ userStore.userInfo?.nickname || '用户' }}</h2>
                  <div class="level-badge">
                    <el-icon><Trophy /></el-icon>
                    Lv{{ levelInfo.level }} 用户
                  </div>
                </div>
              </div>
              <div class="exp-display">
                <span class="exp-number">{{ levelInfo.experience }}</span>
                <span class="exp-label">当前经验值</span>
              </div>
            </div>

            <div class="vip-progress">
              <div class="progress-labels">
                <span class="label-left">Lv{{ levelInfo.level }}</span>
                <span class="label-right" v-if="levelInfo.level < 10">Lv{{ levelInfo.level + 1 }}</span>
                <span class="label-right" v-else>MAX</span>
              </div>
              <el-progress
                :percentage="levelInfo.progress"
                :stroke-width="14"
                :color="'#fff'"
                :show-text="false"
                class="light-progress"
              />
              <div class="progress-hint">
                <template v-if="levelInfo.level < 10">
                  距离下一等级还需 <strong>{{ expNeeded }}</strong> 经验
                </template>
                <template v-else>
                  已达到最高等级
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Lv5 邀请码权益 -->
      <el-card v-if="isLoggedIn && levelInfo" class="premium-card level5-invite-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><Star /></el-icon>
            <span>Lv5 解锁：邀请好友特权</span>
          </div>
        </template>
        <div class="invite-claim-content">
          <p v-if="(levelInfo.level || 0) >= 5">
            🎉 你已达到 Lv5，可以生成专属邀请链接，邀请好友加入社区！每成功邀请一位新用户注册，你将获得 <strong>+30 经验值</strong>奖励。
          </p>
          <p v-else>
            达到 <strong>Lv5</strong> 后可生成专属邀请码并邀请好友加入。每成功邀请一人可获得 <strong>+30 经验值</strong>。
            当前 <strong>Lv{{ levelInfo.level }}</strong>，还差
            <strong>{{ Math.max(0, level5RequiredExp - levelInfo.experience) }}</strong>
            经验解锁。
          </p>
          <ul class="invite-perks">
            <li>📨 每次可生成 1 个邀请链接（未使用上限 5 个）</li>
            <li>🎁 被邀请人成功注册后自动发放经验</li>
            <li>📋 可在「邀请中心」查看邀请记录和奖励状态</li>
            <li>⏱️ 邀请链接有效期 30 天</li>
          </ul>
          <el-button
            type="primary"
            round
            :disabled="(levelInfo.level || 0) < 5"
            @click="$router.push('/invite')"
          >
            {{ (levelInfo.level || 0) >= 5 ? '前往邀请中心' : 'Lv5 后解锁' }}
          </el-button>
        </div>
      </el-card>

      <el-card v-if="isLoggedIn && levelInfo" class="premium-card level6-mail-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><Trophy /></el-icon>
            <span>Lv6 专属：独立域名邮箱系统</span>
          </div>
        </template>
        <div class="mail-claim-content">
          <p v-if="isLv6Unlocked">
            你已达到六级，可申请社区独立域名邮箱（用于身份展示与活动优先通知）。
            领取方式：私信管理员提交申请，审核通过后开通。
          </p>
          <p v-else>
            该权益在 <strong>Lv6</strong> 解锁（需 1500 经验）。你当前为
            <strong>Lv{{ levelInfo.level }}</strong>，还差
            <strong>{{ lv6RemainingExp }}</strong> 经验即可申请。
          </p>
          <div class="mail-contact">
            <span v-if="supportContact">联系管理员：{{ supportContact.nickname || supportContact.username }}</span>
            <span v-else>联系管理员：社区管理员</span>
            <el-tag
              v-if="mailEntryUrl"
              class="mail-entry-tag"
              type="primary"
              effect="plain"
              round
              tag="a"
              :href="mailEntryUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              邮箱直达
            </el-tag>
          </div>
          <el-button
            type="primary"
            round
            :disabled="!isLv6Unlocked"
            @click="goSupportDm"
          >
            {{ isLv6Unlocked ? '私信管理员领取' : '达到 Lv6 后可领取' }}
          </el-button>
          <div v-if="!isLv6Unlocked" class="mail-locked-hint">
            入口已为你显示，达到 Lv6 后按钮会自动可用。
          </div>
        </div>
      </el-card>

      <div v-if="isLoggedIn && levelInfo" class="benefit-grid">
        <el-card
          v-for="benefit in extraBenefits"
          :key="benefit.level"
          class="premium-card benefit-card"
          shadow="never"
        >
          <template #header>
            <div class="card-title benefit-title">
              <span class="benefit-emoji">{{ benefit.icon }}</span>
              <span>Lv{{ benefit.level }} 解锁：{{ benefit.title }}</span>
            </div>
          </template>
          <div class="benefit-content">
            <p v-if="benefit.unlocked">
              你已达到 <strong>Lv{{ benefit.level }}</strong>，当前可以直接使用「{{ benefit.title }}」。
              {{ benefit.description }}
            </p>
            <p v-else>
              该权益将在 <strong>Lv{{ benefit.level }}</strong> 解锁。
              当前 <strong>Lv{{ levelInfo.level }}</strong>，还差
              <strong>{{ benefit.remainingExp }}</strong> 经验即可开启。
            </p>
            <div class="benefit-link-row">
              <el-tag
                class="benefit-link-tag"
                type="success"
                effect="plain"
                round
                tag="a"
                :href="benefit.url"
                target="_blank"
                rel="noopener noreferrer"
              >
                {{ benefit.url.replace('https://', '') }}
              </el-tag>
            </div>
            <el-button
              type="primary"
              round
              tag="a"
              :href="benefit.unlocked ? benefit.url : undefined"
              target="_blank"
              rel="noopener noreferrer"
              :disabled="!benefit.unlocked"
            >
              {{ benefit.unlocked ? benefit.buttonText : `Lv${benefit.level} 后解锁` }}
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- Not logged in -->
      <el-card v-if="!isLoggedIn" class="login-guide-card" shadow="never">
        <div class="guide-content">
          <div class="guide-icon-wrapper">
            <el-icon :size="48"><Trophy /></el-icon>
          </div>
          <h2>登录以开启等级特权</h2>
          <p>查看您的等级和经验值进度，参与社区互动获取经验以点亮更高等级徽章。</p>
          <el-button type="primary" size="large" @click="goLogin" round class="login-btn">立即登录 / 注册</el-button>
        </div>
      </el-card>

      <el-row :gutter="24">
        <el-col :xs="24" :md="12">
          <!-- Experience Rules -->
          <el-card class="premium-card rules-card" shadow="never">
            <template #header>
              <div class="card-title">
                <el-icon><Star /></el-icon>
                <span>如何获取经验？</span>
              </div>
            </template>
            <div class="rules-list">
              <div v-for="(row, index) in expRules" :key="index" class="rule-item">
                <div class="rule-icon-box">
                  <el-icon><component :is="row.icon" /></el-icon>
                </div>
                <div class="rule-info">
                  <h4>{{ row.action }}</h4>
                  <p>{{ row.desc }}</p>
                </div>
                <div class="rule-exp">
                  {{ row.exp }}
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="12">
          <!-- Level Thresholds -->
          <el-card v-if="thresholds.length" class="premium-card thresholds-card" shadow="never">
            <template #header>
              <div class="card-title">
                <el-icon><TrendCharts /></el-icon>
                <span>等级阈值表</span>
              </div>
            </template>
            <div class="thresholds-grid">
              <div
                v-for="t in thresholds"
                :key="t.level"
                class="threshold-item"
                :class="{ active: levelInfo && levelInfo.level === t.level }"
              >
                <div class="threshold-level">
                  <el-icon v-if="levelInfo && levelInfo.level === t.level" class="active-icon"><Star /></el-icon>
                  Lv{{ t.level }}
                </div>
                <div class="threshold-exp">{{ t.experience }} EXP</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="isLoggedIn" class="premium-card records-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><Trophy /></el-icon>
            <span>经验加分记录（近7天）</span>
            <el-button link type="primary" class="view-all-btn" @click.stop="openFullRecords">查看完整记录</el-button>
          </div>
        </template>

        <div v-loading="recentRecordsLoading">
          <el-empty
            v-if="recentExpRecords.length === 0"
            description="近7天暂无加分记录（历史累计经验请点“查看完整记录”）"
          />
          <div v-else class="records-list">
            <div v-for="item in recentExpRecords" :key="item.id" class="record-item">
              <div class="record-main">
                <div class="record-reason">{{ item.reason || '经验变更' }}</div>
                <div class="record-time">{{ formatDateTime(item.createTime) }}</div>
              </div>
              <div class="record-exp">+{{ item.expDelta }}</div>
            </div>
          </div>
        </div>
      </el-card>

    </div>

    <el-dialog
      v-model="fullRecordsVisible"
      title="经验加分完整记录"
      width="760px"
      append-to-body
      align-center
      destroy-on-close
    >
      <div v-loading="fullRecordsLoading">
        <el-empty v-if="fullExpRecords.length === 0" description="暂无经验记录" />
        <el-table v-else :data="fullExpRecords" size="small">
          <el-table-column prop="createTime" label="时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="来源" min-width="220" />
          <el-table-column prop="expDelta" label="加分" width="120" align="right">
            <template #default="{ row }">
              <span class="table-exp">+{{ row.expDelta }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="fullPagination.total > fullPagination.size" class="records-pagination">
        <el-pagination
          layout="prev, pager, next, total"
          :current-page="fullPagination.current"
          :page-size="fullPagination.size"
          :total="fullPagination.total"
          @current-change="handleFullRecordPageChange"
        />
      </div>
    </el-dialog>
  </MainLayout>
</template>

<style scoped>
.page-container.connect-page {
  max-width: min(100%, var(--cp-profile-page-width, 1080px));
  margin: 0 auto;
  padding: 32px 16px;
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.page-header {
  margin-bottom: 40px;
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
}

.icon-bulb {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
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

/* Song：双轴主横幅 —— TL（信任/权限）+ Lv（资历/经验）并列 */
.dual-axis-banner {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 28px;
}
.axis-card {
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 18px 20px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
}
.axis-card:hover {
  border-color: var(--axis-color, var(--el-color-primary));
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}
.axis-trust {
  border-left: 4px solid var(--axis-color);
}
.axis-exp {
  border-left: 4px solid var(--el-text-color-secondary);
}
.axis-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.axis-tag {
  font-size: 12px;
  font-weight: 700;
  color: var(--axis-color, var(--el-color-primary));
  letter-spacing: 0.5px;
}
.axis-tag-secondary {
  color: var(--el-text-color-secondary);
}
.axis-level {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}
.axis-level-secondary {
  background: var(--el-text-color-secondary);
}
.axis-desc {
  margin: 6px 0 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.axis-privs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.axis-priv-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.axis-link {
  font-size: 12px;
  color: var(--el-color-primary);
  font-weight: 600;
}
@media (max-width: 768px) {
  .dual-axis-banner {
    grid-template-columns: 1fr;
  }
}

/* Song：说明 */
.vip-card-wrapper {
  margin-bottom: 32px;
}

.level5-invite-card {
  border-left: 3px solid var(--el-color-primary);
}

.invite-claim-content p {
  margin-bottom: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.invite-perks {
  list-style: none;
  padding: 0;
  margin: 0 0 16px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.invite-perks li {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 4px 0;
}

.level6-mail-card {
  margin-bottom: 24px;
}

.mail-claim-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
}

.mail-contact {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.mail-entry-tag {
  cursor: pointer;
}

.mail-locked-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.benefit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  margin-bottom: 24px;
}

.benefit-card {
  margin-bottom: 0;
}

.benefit-title {
  line-height: 1.4;
}

.benefit-emoji {
  font-size: 20px;
  line-height: 1;
}

.benefit-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
}

.benefit-content p {
  margin: 0;
}

.benefit-link-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.benefit-link-tag {
  max-width: 100%;
  cursor: pointer;
}

.benefit-link-tag :deep(*) {
  word-break: break-all;
}

.vip-card {
  position: relative;
  overflow: hidden;
  border-radius: 24px;
  background: linear-gradient(135deg, var(--user-level-color) 0%, #2b2b2b 100%);
  color: #fff;
  padding: 40px;
  box-shadow: 0 20px 40px rgba(0,0,0,0.15);
  transition: transform 0.3s;
}

.vip-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 24px 48px rgba(0,0,0,0.2);
}

.vip-bg-pattern {
  position: absolute;
  top: -50%;
  right: -10%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}

.vip-content {
  position: relative;
  z-index: 2;
}

.vip-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 40px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.vip-avatar {
  border: 3px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 8px 16px rgba(0,0,0,0.2);
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-name {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  text-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

.level-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  font-size: 14px;
  font-weight: 600;
  width: fit-content;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.exp-display {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.exp-number {
  font-size: 40px;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 4px 8px rgba(0,0,0,0.3);
}

.exp-label {
  font-size: 14px;
  opacity: 0.8;
  margin-top: 8px;
  font-weight: 500;
}

.vip-progress {
  background: rgba(0, 0, 0, 0.2);
  padding: 20px 24px;
  border-radius: 16px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 12px;
}

.light-progress :deep(.el-progress-bar__outer) {
  background-color: rgba(255, 255, 255, 0.2) !important;
}
.light-progress :deep(.el-progress-bar__inner) {
  background-color: #fff !important;
  box-shadow: 0 0 10px rgba(255,255,255,0.5);
}

.progress-hint {
  text-align: center;
  font-size: 14px;
  margin-top: 16px;
  opacity: 0.9;
}

.progress-hint strong {
  color: #fff;
  font-size: 16px;
}

/* Song：说明 */
.premium-card {
  border-radius: 20px;
  border: 1px solid var(--el-border-color-lighter);
  height: 100%;
  margin-bottom: 24px;
}

.premium-card :deep(.el-card__header) {
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  padding: 16px 24px;
  border-radius: 20px 20px 0 0;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.card-title .el-icon {
  color: var(--el-color-primary);
}

/* Song：说明 */
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px;
  border-radius: 12px;
  transition: background-color 0.2s;
}

.rule-item:hover {
  background-color: var(--el-fill-color-light);
}

.rule-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.rule-info {
  flex: 1;
}

.rule-info h4 {
  margin: 0 0 4px 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rule-info p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.rule-exp {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  padding: 4px 12px;
  border-radius: 20px;
}

/* Song：说明 */
.thresholds-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.threshold-item {
  text-align: center;
  padding: 20px 12px;
  border-radius: 16px;
  background-color: var(--el-fill-color-light);
  border: 1px solid transparent;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.threshold-item:hover {
  transform: translateY(-2px);
  background-color: var(--el-fill-color);
  box-shadow: 0 8px 16px rgba(0,0,0,0.05);
}

.threshold-item.active {
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-fill-color-blank) 100%);
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 8px 20px var(--el-color-primary-light-8);
}

.threshold-level {
  font-size: 18px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.active-icon {
  color: var(--el-color-primary);
}

.threshold-exp {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

/* Song：说明 */
.login-guide-card {
  border-radius: 24px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-fill-color-blank) 100%);
  border: 1px dashed var(--el-color-primary-light-5);
  margin-bottom: 32px;
}

.guide-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  text-align: center;
}

.guide-icon-wrapper {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  box-shadow: 0 8px 24px var(--el-color-primary-light-7);
}

.guide-content h2 {
  margin: 0 0 16px 0;
  font-size: 24px;
  font-weight: 800;
}

.guide-content p {
  margin: 0 0 32px 0;
  color: var(--el-text-color-secondary);
  font-size: 16px;
  max-width: 480px;
  line-height: 1.6;
}

.login-btn {
  padding: 12px 48px;
  font-size: 16px;
  box-shadow: 0 8px 20px var(--el-color-primary-light-5);
}

.records-card {
  margin-top: 8px;
}

.view-all-btn {
  margin-left: auto;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
}

.record-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.record-reason {
  font-size: 14px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.record-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.record-exp {
  color: var(--el-color-success);
  font-weight: 700;
}

.records-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.table-exp {
  color: var(--el-color-success);
  font-weight: 700;
}

@media (max-width: 768px) {
  .title-area {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  
  .vip-top {
    flex-direction: column;
    gap: 24px;
    align-items: flex-start;
    margin-bottom: 32px;
  }
  
  .exp-display {
    align-items: flex-start;
  }
  
  .thresholds-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .benefit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
