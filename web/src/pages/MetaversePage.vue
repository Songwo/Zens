<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key as ElementKey, Lock as ElementLock } from '@element-plus/icons-vue'
import {
  Activity,
  ArrowUpRight,
  BookOpen,
  Boxes,
  CircleDot,
  Coins,
  Compass,
  ExternalLink,
  FileText,
  Gift,
  Home,
  KeyRound,
  Layers3,
  Medal,
  Monitor,
  Network,
  Settings,
  Shield,
  ShieldCheck,
  Sparkles,
  Store,
  Ticket,
  Trophy,
  Users,
  Video,
  Wrench,
} from 'lucide-vue-next'
import MainLayout from '@/layouts/MainLayout.vue'
import { useUserStore } from '@/store/user'
import {
  metaverseAccessMeta,
  metaverseSpaces,
  metaverseStatusMeta,
  buildStationEntryHref,
  type MetaverseAccess,
  type MetaverseStatus,
} from '@/data/metaverseSpaces'
import { ensureCurrentUserProfile, hasAdminRole, hasBackofficeAccess } from '@/utils/sessionProfile'

type PortalStatusColor = 'success' | 'warning' | 'info' | 'danger' | 'default' | 'admin'

interface PortalAction {
  label: string
  entryId?: string
  url?: string
  adminOnly?: boolean
}

interface BannerConfig {
  eyebrow: string
  title: string
  subtitle: string
  description: string
  statsText: string
  primaryAction: PortalAction
  secondaryAction?: PortalAction
}

interface PortalEntry {
  id: string
  title: string
  description: string
  icon: string
  statusText: string
  statusColor: PortalStatusColor
  url: string
  groupId: string
  adminUrl?: string
  access?: MetaverseAccess
  status?: MetaverseStatus
  clientId?: string
  system?: string
  integration?: string
  requiredBadge?: string
  requiredTrustLevel?: number
}

interface PortalGroup {
  id: string
  title: string
  subtitle: string
  entryIds: string[]
}

interface PortalConfig {
  banner: BannerConfig
  quickEntryIds: string[]
  groups: PortalGroup[]
  entries: PortalEntry[]
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const selectedEntry = ref<PortalEntry | null>(null)
const detailVisible = ref(false)

const profile = computed(() => userStore.userInfo as any)
const isLoggedIn = computed(() => userStore.isLoggedIn)
const isAdmin = computed(() => hasAdminRole(profile.value))
const canEnterBackoffice = computed(() => hasBackofficeAccess(profile.value))

const groupIdByEntryId: Record<string, string> = {
  'main-home': 'community',
  guide: 'community',
  'trust-center': 'community',
  'invite-center': 'community',
  'cdk-claim': 'reward',
  'lottery-tool': 'reward',
  'shop-home': 'reward',
  'shop-orders': 'reward',
  'media-panel': 'content',
  'media-health': 'content',
  'project-docs': 'content',
  'lottery-sso': 'account',
  'admin-sso': 'account',
  'l-station': 'account',
  'admin-dashboard': 'governance',
  'cdk-admin': 'governance',
  'shop-admin': 'governance',
}

const iconNameByEntryId: Record<string, string> = {
  'main-home': 'home',
  guide: 'book',
  'trust-center': 'medal',
  'invite-center': 'ticket',
  'cdk-claim': 'gift',
  'cdk-admin': 'wrench',
  'lottery-tool': 'trophy',
  'lottery-sso': 'key',
  'shop-home': 'store',
  'shop-orders': 'coins',
  'shop-admin': 'settings',
  'media-panel': 'video',
  'media-health': 'activity',
  'admin-dashboard': 'shield',
  'admin-sso': 'network',
  'l-station': 'sparkles',
  'project-docs': 'file',
}

const statusColorByMetaverseStatus: Record<MetaverseStatus, PortalStatusColor> = {
  open: 'success',
  beta: 'warning',
  maintenance: 'danger',
  coming: 'info',
}

const backendEntries: PortalEntry[] = metaverseSpaces.map(space => ({
  id: space.id,
  title: space.title,
  description: space.description,
  icon: iconNameByEntryId[space.id] || 'circle',
  statusText: metaverseStatusMeta[space.status].label,
  statusColor: space.access === 'admin' || space.access === 'backoffice' ? 'admin' : statusColorByMetaverseStatus[space.status],
  url: buildStationEntryHref(space),
  groupId: groupIdByEntryId[space.id] || 'community',
  adminUrl: space.adminHref,
  access: space.access,
  status: space.status,
  clientId: space.clientId,
  system: space.system,
  integration: space.integration,
  requiredBadge: space.requiredBadge,
  requiredTrustLevel: space.requiredTrustLevel,
}))

const mockBackendPortalConfig: PortalConfig = {
  banner: {
    eyebrow: '社区生态工作台',
    title: 'Zens 星港',
    subtitle: '把福利、活动、积分和治理入口收进同一个社区语境',
    description: '星港不再像一个独立站点，而是 Zens 社区的工具抽屉：常用入口优先，受限空间按登录态、信任等级和后台身份自然展示。',
    statsText: `${backendEntries.length} 个入口 · ${backendEntries.filter(item => item.status !== 'coming' && item.status !== 'maintenance').length} 个在线入口 · ${backendEntries.filter(item => item.status === 'beta').length} 个内测项目 · ${backendEntries.filter(item => item.access === 'admin' || item.access === 'backoffice').length} 个治理入口`,
    primaryAction: { label: '回到社区首页', entryId: 'main-home' },
    secondaryAction: { label: '星港配置', url: '/admin/metaverse', adminOnly: true },
  },
  quickEntryIds: ['main-home', 'cdk-claim', 'lottery-tool', 'shop-home'],
  groups: [
    {
      id: 'community',
      title: '社区空间',
      subtitle: '主站、指南、信任等级和邀请入口',
      entryIds: ['main-home', 'guide', 'trust-center', 'invite-center'],
    },
    {
      id: 'reward',
      title: '活动与奖励',
      subtitle: '福利领取、评论抽奖、积分兑换和订单记录',
      entryIds: ['cdk-claim', 'lottery-tool', 'shop-home', 'shop-orders'],
    },
    {
      id: 'content',
      title: '内容与媒体',
      subtitle: '媒体服务、健康检查与项目说明',
      entryIds: ['media-panel', 'media-health', 'project-docs'],
    },
    {
      id: 'account',
      title: '账号连接',
      subtitle: 'SSO 链路、授权应用与特别访问空间',
      entryIds: ['lottery-sso', 'admin-sso', 'l-station'],
    },
    {
      id: 'governance',
      title: '管理与治理',
      subtitle: '社区后台、运营后台和治理工具',
      entryIds: ['admin-dashboard', 'cdk-admin', 'shop-admin'],
    },
  ],
  entries: backendEntries,
}

const portalConfig = ref<PortalConfig>(mockBackendPortalConfig)

const iconComponents: Record<string, Component> = {
  activity: Activity,
  book: BookOpen,
  boxes: Boxes,
  circle: CircleDot,
  coins: Coins,
  compass: Compass,
  file: FileText,
  gift: Gift,
  home: Home,
  key: KeyRound,
  layers: Layers3,
  medal: Medal,
  monitor: Monitor,
  network: Network,
  settings: Settings,
  shield: Shield,
  sparkles: Sparkles,
  store: Store,
  ticket: Ticket,
  trophy: Trophy,
  users: Users,
  video: Video,
  wrench: Wrench,
}

const statusClassMap: Record<PortalStatusColor, string> = {
  success: 'status-success',
  warning: 'status-warning',
  info: 'status-info',
  danger: 'status-danger',
  admin: 'status-admin',
  default: 'status-default',
}

const entryMap = computed(() => new Map(portalConfig.value.entries.map(entry => [entry.id, entry])))

const quickEntries = computed(() =>
  portalConfig.value.quickEntryIds
    .map(id => entryMap.value.get(id))
    .filter(Boolean) as PortalEntry[]
)

const visibleGroups = computed(() =>
  portalConfig.value.groups.map(group => ({
    ...group,
    entries: group.entryIds.map(id => entryMap.value.get(id)).filter(Boolean) as PortalEntry[],
  }))
)

const portalPulseCards = computed(() => [
  {
    label: '可访问入口',
    value: portalConfig.value.entries.filter(entry => canAccessEntry(entry)).length,
    desc: '当前身份可直接进入',
    icon: Compass,
    tone: 'green',
    targetId: 'quick',
  },
  {
    label: '活动福利',
    value: portalConfig.value.entries.filter(entry => entry.groupId === 'reward').length,
    desc: 'CDK、抽奖、积分商城',
    icon: Gift,
    tone: 'orange',
    targetId: 'reward',
  },
  {
    label: '治理入口',
    value: portalConfig.value.entries.filter(entry => entry.access === 'admin' || entry.access === 'backoffice').length,
    desc: canEnterBackoffice.value ? '按权限展示后台' : '登录后按权限展示',
    icon: Shield,
    tone: 'purple',
    targetId: 'governance',
  },
  {
    label: '统一登录',
    value: 'SSO',
    desc: '子站沿用社区身份',
    icon: KeyRound,
    tone: 'blue',
    targetId: 'account',
  },
])

const passLabel = computed(() => {
  if (!isLoggedIn.value) return '访客'
  if (isAdmin.value) return '管理员'
  if (canEnterBackoffice.value) return '版务'
  return `TL${profile.value?.trustLevel ?? 0}`
})

const isIconUrl = (icon: string) => /^https?:\/\//i.test(icon) || icon.startsWith('/')
const resolveIcon = (icon: string) => iconComponents[icon] || CircleDot

const canAccessEntry = (entry: PortalEntry) => {
  if (entry.status === 'coming' || entry.status === 'maintenance') return false
  if (!entry.access || entry.access === 'public') return true
  if (!isLoggedIn.value) return false
  if (entry.access === 'login') return true
  if (entry.access === 'backoffice') return canEnterBackoffice.value
  if (entry.access === 'admin') return isAdmin.value
  if (entry.access === 'badge') return Boolean(entry.requiredBadge && profile.value?.badgeText === entry.requiredBadge)
  if (entry.access === 'trust') return Number(profile.value?.trustLevel ?? 0) >= Number(entry.requiredTrustLevel ?? 0)
  return false
}

const canManageEntry = (entry: PortalEntry) => {
  if (!entry.adminUrl) return false
  return entry.access === 'backoffice' ? canEnterBackoffice.value : isAdmin.value
}

const blockedReason = (entry: PortalEntry) => {
  if (entry.status === 'coming') return '即将开放'
  if (entry.status === 'maintenance') return '维护中'
  if (!entry.access || entry.access === 'public') return ''
  if (!isLoggedIn.value) return '需要登录'
  if (entry.access === 'backoffice') return '版务可进'
  if (entry.access === 'admin') return '管理员'
  if (entry.access === 'badge') return '需要徽章'
  if (entry.access === 'trust') return `需要 TL${entry.requiredTrustLevel ?? 0}`
  return ''
}

const runtimeStatusText = (entry: PortalEntry) => {
  if (!canAccessEntry(entry)) return blockedReason(entry) || '不可进入'
  if (entry.access && entry.access !== 'public' && entry.status === 'open') {
    return metaverseAccessMeta[entry.access].label
  }
  return entry.statusText
}

const runtimeStatusColor = (entry: PortalEntry): PortalStatusColor => {
  if (!canAccessEntry(entry)) return 'default'
  return entry.statusColor
}

const openHref = (href: string) => {
  if (/^https?:\/\//i.test(href)) {
    window.open(href, '_blank', 'noopener,noreferrer')
    return
  }
  router.push(href)
}

const openEntry = (entry: PortalEntry) => {
  if (!canAccessEntry(entry)) {
    if (!isLoggedIn.value && entry.access !== 'public') {
      ElMessage.info('先登录社区账号，再进入这个空间')
      router.push({ path: '/auth', query: { type: 'login', redirect: route.fullPath } })
      return
    }
    ElMessage.warning(blockedReason(entry) || '暂不可进入')
    return
  }
  openHref(entry.url)
}

const openAction = (action: PortalAction) => {
  if (action.adminOnly && !isAdmin.value) {
    ElMessage.warning('需要管理员权限才能配置星港')
    return
  }
  if (action.entryId) {
    const entry = entryMap.value.get(action.entryId)
    if (entry) openEntry(entry)
    return
  }
  if (action.url) openHref(action.url)
}

const showDetail = (entry: PortalEntry) => {
  selectedEntry.value = entry
  detailVisible.value = true
}

const openAdmin = (entry: PortalEntry) => {
  if (!entry.adminUrl) return
  if (!canManageEntry(entry)) {
    ElMessage.warning('需要管理员权限才能进入管理入口')
    return
  }
  openHref(entry.adminUrl)
}

const jumpToSection = (id: string) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const syncSelectedFromQuery = () => {
  const id = String(route.query.space || '')
  if (!id) return
  const found = entryMap.value.get(id)
  if (found) showDetail(found)
}

onMounted(async () => {
  syncSelectedFromQuery()
  if (userStore.accessToken && !userStore.userInfo) {
    try {
      await ensureCurrentUserProfile()
    } catch {
      // 星港公开索引不阻断未完成的会话刷新。
    }
  }
})

watch(() => route.query.space, syncSelectedFromQuery)
</script>

<template>
  <MainLayout>
    <div class="zens-port">
        <section
          class="zens-port__hero overflow-hidden rounded-[28px] border border-amber-100/70 bg-[radial-gradient(circle_at_78%_18%,rgba(246,168,0,0.18),transparent_34%),linear-gradient(135deg,#fffaf0,#ffffff)] px-6 py-8 md:grid md:min-h-[260px] md:grid-cols-[minmax(0,1fr)_360px] md:items-center md:gap-8 md:px-9"
          aria-labelledby="metaverse-title"
        >
          <div class="zens-port__hero-copy max-w-2xl">
            <p class="zens-port__eyebrow m-0 text-xs font-medium uppercase tracking-[0.18em] text-amber-700/80">
              {{ portalConfig.banner.eyebrow }}
            </p>
            <h1 id="metaverse-title" class="zens-port__title mb-0 mt-5 text-4xl font-semibold tracking-normal text-gray-950 md:text-5xl">
              {{ portalConfig.banner.title }}
            </h1>
            <p class="zens-port__subtitle mb-0 mt-4 text-xl font-medium leading-snug text-gray-900 md:text-2xl">
              {{ portalConfig.banner.subtitle }}
            </p>
            <p class="zens-port__desc mb-0 mt-4 max-w-[58ch] text-[15px] leading-7 text-gray-500">
              {{ portalConfig.banner.description }}
            </p>

            <div class="zens-port__hero-actions mt-7 flex flex-wrap gap-2.5">
              <button
                type="button"
                class="zens-port__hero-button zens-port__hero-button--primary inline-flex items-center gap-2 rounded-md bg-gray-950 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-gray-800 active:scale-[0.98]"
                @click="openAction(portalConfig.banner.primaryAction)"
              >
                <Home class="zens-port__button-icon h-4 w-4" aria-hidden="true" />
                {{ portalConfig.banner.primaryAction.label }}
              </button>
              <button
                v-if="portalConfig.banner.secondaryAction && (!portalConfig.banner.secondaryAction.adminOnly || isAdmin)"
                type="button"
                class="zens-port__hero-button zens-port__hero-button--secondary inline-flex items-center gap-2 rounded-md bg-white/75 px-4 py-2.5 text-sm font-medium text-gray-700 ring-1 ring-amber-100 transition hover:bg-white hover:text-gray-950 active:scale-[0.98]"
                @click="openAction(portalConfig.banner.secondaryAction)"
              >
                <Settings class="zens-port__button-icon h-4 w-4" aria-hidden="true" />
                {{ portalConfig.banner.secondaryAction.label }}
              </button>
            </div>

            <p class="zens-port__stats mb-0 mt-7 text-xs leading-5 text-gray-500">{{ portalConfig.banner.statsText }}</p>
          </div>

          <div class="zens-port__pulse-board" aria-label="星港数据概览">
            <button
              v-for="item in portalPulseCards"
              :key="item.label"
              type="button"
              class="zens-port__pulse-card"
              :class="`tone-${item.tone}`"
              @click="jumpToSection(item.targetId)"
            >
              <span class="zens-port__pulse-icon">
                <component :is="item.icon" aria-hidden="true" />
              </span>
              <span class="zens-port__pulse-copy">
                <small>{{ item.label }}</small>
                <strong>{{ item.value }}</strong>
                <em>{{ item.desc }}</em>
              </span>
            </button>
          </div>
        </section>

        <nav class="zens-port__channel-strip" aria-label="星港内容分组">
          <a href="#quick">常用入口</a>
          <a v-for="group in portalConfig.groups" :key="group.id" :href="`#${group.id}`">
            {{ group.title }}
          </a>
          <span>当前通行：{{ passLabel }}</span>
        </nav>

        <section id="quick" class="zens-port__section scroll-mt-24 space-y-4" aria-labelledby="quick-title">
          <div class="zens-port__section-head flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 id="quick-title" class="zens-port__section-title m-0 text-2xl font-semibold tracking-normal text-gray-950">常用入口</h2>
              <p class="zens-port__section-desc mb-0 mt-2 text-sm text-gray-500">最常进入的社区空间与活动工具。</p>
            </div>
          </div>

          <div class="zens-port__list divide-y divide-gray-100">
            <button
              v-for="entry in quickEntries"
              :key="entry.id"
              type="button"
              class="zens-port__entry group grid w-full grid-cols-[36px_minmax(0,1fr)] items-center gap-4 rounded-md px-2 py-4 text-left transition hover:bg-gray-50 sm:grid-cols-[36px_minmax(0,1fr)_auto]"
              @click="openEntry(entry)"
              @contextmenu.prevent="showDetail(entry)"
            >
              <span class="zens-port__entry-icon zens-port__entry-icon--warm grid h-9 w-9 place-items-center rounded-md bg-amber-50 text-amber-700">
                <img v-if="isIconUrl(entry.icon)" class="zens-port__entry-img h-[20px] w-[20px] object-contain [content-visibility:visible]" :src="entry.icon" alt="" />
                <component v-else :is="resolveIcon(entry.icon)" class="zens-port__entry-svg h-5 w-5" aria-hidden="true" />
              </span>

              <span class="zens-port__entry-copy min-w-0">
                <span class="zens-port__entry-title block truncate text-[15px] font-medium text-gray-950">{{ entry.title }}</span>
                <span class="zens-port__entry-desc mt-1 block truncate text-sm text-gray-500">{{ entry.description }}</span>
              </span>

              <span class="zens-port__entry-meta col-span-2 flex items-center justify-between gap-3 sm:col-span-1">
                <span
                  class="zens-port__status rounded-full px-2.5 py-1 text-xs font-medium"
                  :class="statusClassMap[runtimeStatusColor(entry)]"
                >
                  {{ runtimeStatusText(entry) }}
                </span>
                <ArrowUpRight class="zens-port__entry-arrow h-4 w-4 text-gray-300 transition group-hover:text-gray-600" aria-hidden="true" />
              </span>
            </button>
          </div>
        </section>

        <section class="zens-port__all space-y-10" aria-labelledby="all-title">
          <div>
            <h2 id="all-title" class="zens-port__section-title m-0 text-2xl font-semibold tracking-normal text-gray-950">全部空间</h2>
            <p class="zens-port__section-desc mb-0 mt-2 text-sm text-gray-500">按用途整理所有子系统入口，后台可通过同一份 JSON 配置动态调整。</p>
          </div>

          <section
            v-for="group in visibleGroups"
            :id="group.id"
            :key="group.id"
            class="zens-port__group scroll-mt-24"
            :aria-labelledby="`${group.id}-title`"
          >
            <div class="zens-port__group-head mb-3 flex flex-wrap items-end justify-between gap-3">
              <div>
                <h3 :id="`${group.id}-title`" class="zens-port__group-title m-0 text-lg font-semibold tracking-normal text-gray-950">
                  {{ group.title }}
                </h3>
                <p class="zens-port__group-desc mb-0 mt-1 text-sm text-gray-500">{{ group.subtitle }}</p>
              </div>
              <span class="zens-port__group-count text-xs font-medium text-gray-400">{{ group.entries.length }} 个入口</span>
            </div>

            <div class="zens-port__list divide-y divide-gray-100">
              <button
                v-for="entry in group.entries"
                :key="entry.id"
                type="button"
                class="zens-port__entry group grid w-full grid-cols-[36px_minmax(0,1fr)] items-center gap-4 rounded-md px-2 py-4 text-left transition hover:bg-gray-50 sm:grid-cols-[36px_minmax(0,1fr)_auto]"
                @click="openEntry(entry)"
                @contextmenu.prevent="showDetail(entry)"
              >
                <span class="zens-port__entry-icon grid h-9 w-9 place-items-center rounded-md bg-gray-100 text-gray-600 transition group-hover:bg-amber-50 group-hover:text-amber-700">
                  <img v-if="isIconUrl(entry.icon)" class="zens-port__entry-img h-[20px] w-[20px] object-contain [content-visibility:visible]" :src="entry.icon" alt="" />
                  <component v-else :is="resolveIcon(entry.icon)" class="zens-port__entry-svg h-5 w-5" aria-hidden="true" />
                </span>

                <span class="zens-port__entry-copy min-w-0">
                  <span class="zens-port__entry-title block text-[15px] font-medium text-gray-950">{{ entry.title }}</span>
                  <span class="zens-port__entry-desc mt-1 block text-sm leading-6 text-gray-500">{{ entry.description }}</span>
                </span>

                <span class="zens-port__entry-meta col-span-2 flex items-center justify-between gap-3 sm:col-span-1">
                  <span
                    class="zens-port__status rounded-full px-2.5 py-1 text-xs font-medium"
                    :class="statusClassMap[runtimeStatusColor(entry)]"
                  >
                    {{ runtimeStatusText(entry) }}
                  </span>
                  <ExternalLink class="zens-port__entry-arrow h-4 w-4 text-gray-300 transition group-hover:text-gray-600" aria-hidden="true" />
                </span>
              </button>
            </div>
          </section>
        </section>

        <footer class="zens-port__footer flex items-start gap-2 border-t border-gray-100 pt-5 text-sm leading-6 text-gray-500">
          <ShieldCheck class="zens-port__footer-icon mt-0.5 h-4 w-4 shrink-0 text-gray-400" aria-hidden="true" />
          <span>受限空间会按登录态、信任等级、徽章和后台身份判断；右键入口可查看空间详情。</span>
        </footer>

    <el-drawer v-model="detailVisible" size="400px" :title="selectedEntry?.title || '空间详情'">
      <template v-if="selectedEntry">
        <div class="space-y-2 text-sm">
          <div class="flex justify-between gap-4 border-b border-gray-100 py-2">
            <span class="text-gray-500">状态</span>
            <strong class="text-right font-medium text-gray-900">{{ selectedEntry.status ? metaverseStatusMeta[selectedEntry.status].label : selectedEntry.statusText }}</strong>
          </div>
          <div class="flex justify-between gap-4 border-b border-gray-100 py-2">
            <span class="text-gray-500">访问</span>
            <strong class="text-right font-medium text-gray-900">{{ selectedEntry.access ? metaverseAccessMeta[selectedEntry.access].label : '公开' }}</strong>
          </div>
          <div class="flex justify-between gap-4 border-b border-gray-100 py-2">
            <span class="text-gray-500">系统</span>
            <strong class="text-right font-medium text-gray-900">{{ selectedEntry.system || '-' }}</strong>
          </div>
          <div v-if="selectedEntry.clientId" class="flex justify-between gap-4 border-b border-gray-100 py-2">
            <span class="text-gray-500">Client</span>
            <strong class="text-right font-medium text-gray-900">{{ selectedEntry.clientId }}</strong>
          </div>
        </div>

        <p class="mb-0 mt-5 text-sm leading-7 text-gray-600">{{ selectedEntry.description }}</p>
        <p class="mb-0 mt-4 border-l-2 border-amber-200 pl-3 text-sm leading-7 text-gray-500">
          {{ selectedEntry.integration }}
        </p>

        <div v-if="!canAccessEntry(selectedEntry)" class="mt-5 flex items-center gap-2 rounded-md bg-amber-50 px-3 py-2 text-sm font-medium text-amber-800">
          <el-icon><ElementLock /></el-icon>
          {{ blockedReason(selectedEntry) }}
        </div>

        <div class="mt-6 flex gap-2">
          <el-button type="primary" :disabled="!canAccessEntry(selectedEntry)" @click="openEntry(selectedEntry)">
            进入
          </el-button>
          <el-button v-if="selectedEntry.adminUrl" :disabled="!canManageEntry(selectedEntry)" @click="openAdmin(selectedEntry)">
            管理
          </el-button>
        </div>

        <p class="mt-5 flex items-center gap-1.5 text-xs text-gray-400">
          <el-icon><ElementKey /></el-icon>
          右键入口可查看详情。
        </p>
      </template>
    </el-drawer>
    </div>
  </MainLayout>
</template>

<style scoped>
.zens-port {
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  color: var(--el-text-color-primary);
}

.zens-port,
.zens-port * {
  box-sizing: border-box;
}

.zens-port button {
  font: inherit;
}

.zens-port__hero-actions,
.zens-port__hero-button,
.zens-port__entry-meta,
.zens-port__footer {
  display: inline-flex;
  align-items: center;
}

.zens-port__hero-button:active {
  transform: scale(0.98);
}

.zens-port__hero {
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(360px, 1.05fr);
  gap: 18px;
  padding: 18px;
  border: 1px solid rgba(234, 179, 74, 0.28);
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(255, 248, 229, 0.98) 0%, rgba(255, 255, 255, 0.98) 56%),
    var(--el-bg-color-overlay);
  box-shadow: 0 14px 34px rgba(156, 105, 26, 0.08);
}

.zens-port__hero-copy {
  min-width: 0;
  max-width: 640px;
}

.zens-port__eyebrow {
  display: inline-flex;
  width: fit-content;
  margin: 0;
  padding: 5px 10px;
  border: 1px solid rgba(242, 165, 41, 0.24);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.68);
  color: #9a6211;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.zens-port__title {
  max-width: 520px;
  margin: 10px 0 8px;
  color: #1f2937;
  font-size: 30px;
  font-weight: 800;
  line-height: 1.16;
  letter-spacing: 0;
}

.zens-port__subtitle {
  margin: 0;
  color: #6b5a3f;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
}

.zens-port__desc {
  max-width: 620px;
  margin: 8px 0 0;
  color: #6b5a3f;
  font-size: 14px;
  line-height: 1.58;
}

.zens-port__hero-actions {
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 15px;
}

.zens-port__hero-button {
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 800;
  transition: transform 0.18s ease, box-shadow 0.22s ease, border-color 0.2s ease, background-color 0.2s ease;
}

.zens-port__hero-button--primary {
  background: linear-gradient(135deg, #f6b800 0%, #f29b24 100%);
  color: #fff;
  box-shadow: 0 12px 24px rgba(242, 155, 36, 0.24);
}

.zens-port__hero-button--primary:hover {
  transform: translateY(-1px);
}

.zens-port__hero-button--secondary {
  border-color: rgba(231, 174, 79, 0.42);
  background: rgba(255, 255, 255, 0.78);
  color: #8a5a00;
  box-shadow: none;
}

.zens-port__hero-button--secondary:hover {
  transform: translateY(-1px);
}

.zens-port__button-icon,
.zens-port__entry-arrow,
.zens-port__footer-icon {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
}

.zens-port__stats {
  margin: 16px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.zens-port__pulse-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.zens-port__pulse-card {
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

.zens-port__pulse-card:hover {
  border-color: rgba(244, 180, 0, 0.42);
  background: #fff;
  transform: translateY(-1px);
}

.zens-port__pulse-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
}

.zens-port__pulse-icon svg {
  width: 19px;
  height: 19px;
  stroke-width: 2.4;
}

.zens-port__pulse-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.zens-port__pulse-copy small {
  color: #6b5a3f;
  font-size: 12px;
  font-weight: 800;
}

.zens-port__pulse-copy strong {
  color: #1f2937;
  font-size: 25px;
  line-height: 1;
}

.zens-port__pulse-copy em {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-style: normal;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.tone-green .zens-port__pulse-icon {
  color: #26a269;
  background: #e8f8ef;
}

.tone-orange .zens-port__pulse-icon {
  color: #e68a1f;
  background: #fff1d7;
}

.tone-blue .zens-port__pulse-icon {
  color: #3f8ed8;
  background: #eaf4ff;
}

.tone-purple .zens-port__pulse-icon {
  color: #8e63d9;
  background: #f2ecff;
}

.zens-port__channel-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: color-mix(in srgb, var(--el-bg-color-overlay) 82%, transparent);
}

.zens-port__channel-strip a,
.zens-port__channel-strip span {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  border-radius: 999px;
  padding: 0 11px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.zens-port__channel-strip a {
  border: 1px solid transparent;
  background: transparent;
}

.zens-port__channel-strip a:hover {
  border-color: rgba(244, 180, 0, 0.28);
  background: #fff8e5;
  color: #8a5a00;
}

.zens-port__channel-strip span {
  margin-left: auto;
  border: 1px solid rgba(228, 189, 122, 0.22);
  background: rgba(255, 255, 255, 0.72);
  color: #8a5a00;
}

.zens-port__section,
.zens-port__all {
  display: grid;
  gap: 14px;
  scroll-margin-top: 96px;
}

.zens-port__all {
  gap: 18px;
}

.zens-port__section-head,
.zens-port__group-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.zens-port__section-title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 0;
}

.zens-port__section-desc,
.zens-port__group-desc {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.65;
}

.zens-port__list {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
}

.zens-port__entry {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  width: 100%;
  gap: 16px;
  padding: 14px;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: 0;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.18s ease;
}

.zens-port__entry:hover {
  background: var(--el-fill-color-extra-light);
}

.zens-port__entry:last-child {
  border-bottom: 0;
}

.zens-port__entry-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  transition: background-color 0.18s ease, color 0.18s ease;
}

.zens-port__entry-icon--warm,
.zens-port__entry:hover .zens-port__entry-icon {
  background: #fffbeb;
  color: #b45309;
}

.zens-port__entry-img,
.zens-port__entry-svg {
  width: 20px !important;
  height: 20px !important;
  max-width: 20px !important;
  max-height: 20px !important;
  content-visibility: visible !important;
  contain-intrinsic-size: 20px 20px;
}

.zens-port__entry-copy {
  min-width: 0;
}

.zens-port__entry-title {
  display: block;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 800;
  line-height: 1.35;
}

.zens-port__entry-desc {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.65;
}

.zens-port__entry-meta {
  grid-column: 1 / -1;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.zens-port__status {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.35;
  white-space: nowrap;
}

.status-success {
  background: #ecfdf5;
  color: #047857;
  box-shadow: 0 0 0 1px #d1fae5;
}

.status-warning {
  background: #fffbeb;
  color: #b45309;
  box-shadow: 0 0 0 1px #fde68a;
}

.status-info {
  background: #f0f9ff;
  color: #0369a1;
  box-shadow: 0 0 0 1px #bae6fd;
}

.status-danger {
  background: #fff1f2;
  color: #be123c;
  box-shadow: 0 0 0 1px #ffe4e6;
}

.status-admin {
  background: #f5f3ff;
  color: #6d28d9;
  box-shadow: 0 0 0 1px #ede9fe;
}

.status-default {
  background: #f3f4f6;
  color: #6b7280;
  box-shadow: 0 0 0 1px #e5e7eb;
}

.zens-port__entry-arrow {
  color: #d1d5db;
  transition: color 0.18s ease;
}

.zens-port__entry:hover .zens-port__entry-arrow {
  color: #4b5563;
}

.zens-port__group {
  display: grid;
  gap: 10px;
  scroll-margin-top: 96px;
}

.zens-port__group-title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: 0;
}

.zens-port__group-count {
  color: #9ca3af;
  font-size: 12px;
  font-weight: 600;
}

.zens-port__footer {
  gap: 8px;
  padding: 14px 4px 4px;
  border-top: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.zens-port__footer-icon {
  margin-top: 3px;
  color: #9ca3af;
}

.zens-port :deep(.el-drawer__body) {
  color: #374151;
}

@media (min-width: 640px) {
  .zens-port__entry {
    grid-template-columns: 36px minmax(0, 1fr) auto;
  }

  .zens-port__entry-meta {
    grid-column: auto;
  }
}

@media (min-width: 768px) {
  .zens-port__hero {
    grid-template-columns: minmax(0, 0.95fr) minmax(360px, 1.05fr);
    align-items: center;
  }
}

@media (max-width: 700px) {
  .zens-port {
    gap: 10px;
  }

  .zens-port__hero {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 14px;
    border-radius: 16px;
  }

  .zens-port__title {
    margin: 8px 0 6px;
    font-size: 23px;
    line-height: 1.18;
  }

  .zens-port__subtitle {
    font-size: 13px;
  }

  .zens-port__desc {
    overflow: hidden;
    font-size: 13px;
    line-height: 1.45;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    line-clamp: 3;
  }

  .zens-port__hero-actions {
    margin-top: 10px;
  }

  .zens-port__hero-button {
    flex: 1 1 0;
    min-width: 0;
    min-height: 34px;
    padding: 0 12px;
    font-size: 13px;
  }

  .zens-port__pulse-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .zens-port__pulse-card {
    grid-template-columns: 1fr;
    min-height: 112px;
    gap: 8px;
    padding: 10px;
    border-radius: 12px;
  }

  .zens-port__pulse-icon {
    width: 30px;
    height: 30px;
    border-radius: 10px;
  }

  .zens-port__pulse-icon svg {
    width: 16px;
    height: 16px;
  }

  .zens-port__pulse-copy strong {
    font-size: 21px;
  }

  .zens-port__channel-strip span {
    width: 100%;
    margin-left: 0;
    justify-content: center;
  }

  .zens-port__section-head,
  .zens-port__group-head {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .zens-port *,
  .zens-port *::before,
  .zens-port *::after {
    transition-duration: 0.01ms !important;
  }
}
</style>
