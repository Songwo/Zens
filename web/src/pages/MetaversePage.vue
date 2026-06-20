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
  Orbit,
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

interface TopoNode {
  id: string
  label: string
  icon: string
  x: number
  y: number
  targetEntryId?: string
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
  topoNodes: TopoNode[]
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
    eyebrow: 'Zens Universe Portal',
    title: 'Zens 星港',
    subtitle: '连接社区里的每一个子宇宙',
    description: '从这里进入 CDK、抽奖、积分商城、媒体服务、后台工具与社区治理空间。',
    statsText: `${backendEntries.length} 个入口 · ${backendEntries.filter(item => item.status !== 'coming' && item.status !== 'maintenance').length} 个可访问 · ${backendEntries.filter(item => item.status === 'beta').length} 个内测项目 · ${backendEntries.filter(item => item.access === 'admin' || item.access === 'backoffice').length} 个治理入口`,
    primaryAction: { label: '进入主站', entryId: 'main-home' },
    secondaryAction: { label: '配置星港', url: '/admin/metaverse', adminOnly: true },
  },
  topoNodes: [
    { id: 'zens', label: 'Zens', icon: 'orbit', x: 50, y: 50, targetEntryId: 'main-home' },
    { id: 'cdk', label: 'CDK', icon: 'gift', x: 24, y: 28, targetEntryId: 'cdk-claim' },
    { id: 'lottery', label: 'Lottery', icon: 'trophy', x: 75, y: 26, targetEntryId: 'lottery-tool' },
    { id: 'shop', label: 'Shop', icon: 'store', x: 20, y: 72, targetEntryId: 'shop-home' },
    { id: 'media', label: 'Media', icon: 'video', x: 78, y: 70, targetEntryId: 'media-panel' },
    { id: 'admin', label: 'Admin', icon: 'shield', x: 50, y: 18, targetEntryId: 'admin-dashboard' },
    { id: 'docs', label: 'Guide', icon: 'book', x: 50, y: 84, targetEntryId: 'guide' },
  ],
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
  orbit: Orbit,
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

const handleTopoNode = (node: TopoNode) => {
  const entry = node.targetEntryId ? entryMap.value.get(node.targetEntryId) : null
  if (entry) openEntry(entry)
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
  <div class="zens-port min-h-[100dvh] bg-[#f7f7f5] text-gray-900">
    <header class="zens-port__topbar sticky top-0 z-30 border-b border-gray-100/80 bg-white/75 backdrop-blur-xl">
      <div class="zens-port__topbar-inner mx-auto flex min-h-16 items-center justify-between gap-4 px-5">
        <button
          type="button"
          class="zens-port__brand group inline-flex min-w-0 items-center gap-3 rounded-md px-1 py-2 text-left transition hover:bg-gray-50"
          aria-label="返回社区首页"
          @click="router.push('/')"
        >
          <img
            class="zens-port__logo h-[36px] w-[36px] shrink-0 rounded-xl bg-amber-50 object-cover ring-1 ring-amber-100 [content-visibility:visible]"
            src="/logo.png"
            alt=""
          />
          <span class="zens-port__brand-text truncate text-[19px] font-semibold tracking-normal text-gray-900">Zens 星港</span>
        </button>

        <div class="zens-port__actions flex items-center gap-2">
          <button
            type="button"
            class="zens-port__nav-button rounded-md px-3.5 py-2 text-sm font-medium text-gray-600 transition hover:bg-gray-50 hover:text-gray-900"
            @click="router.push('/')"
          >
            返回主站
          </button>
          <button
            v-if="isAdmin"
            type="button"
            class="zens-port__nav-button zens-port__nav-button--primary rounded-md bg-[#f6a800] px-3.5 py-2 text-sm font-medium text-white transition hover:bg-[#e99a00] active:scale-[0.98]"
            @click="router.push('/admin/metaverse')"
          >
            配置星港
          </button>
        </div>
      </div>
    </header>

    <div class="zens-port__layout mx-auto grid grid-cols-1 gap-10 px-5 py-8 xl:grid-cols-[180px_minmax(0,1fr)]">
      <aside class="zens-port__side hidden xl:block">
        <nav class="zens-port__side-nav sticky top-24 space-y-1 text-sm text-gray-500" aria-label="星港页面导航">
          <a class="zens-port__side-link block rounded-md px-3 py-2 transition hover:bg-gray-50 hover:text-gray-900" href="#quick">常用入口</a>
          <a
            v-for="group in portalConfig.groups"
            :key="group.id"
            class="zens-port__side-link block rounded-md px-3 py-2 transition hover:bg-gray-50 hover:text-gray-900"
            :href="`#${group.id}`"
          >
            {{ group.title }}
          </a>
          <p class="zens-port__side-pass px-3 pt-4 text-xs leading-5 text-gray-400">当前通行：{{ passLabel }}</p>
        </nav>
      </aside>

      <main class="zens-port__main min-w-0 space-y-12">
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

          <div class="zens-port__topology relative mt-9 h-[260px] md:mt-0" aria-label="Zens 子系统拓扑图">
            <svg class="zens-port__topology-lines absolute inset-0 h-full w-full" viewBox="0 0 100 100" aria-hidden="true" preserveAspectRatio="none">
              <line
                v-for="node in portalConfig.topoNodes.filter(item => item.id !== 'zens')"
                :key="node.id"
                x1="50"
                y1="50"
                :x2="node.x"
                :y2="node.y"
                stroke="rgba(156, 163, 175, 0.42)"
                stroke-width="0.35"
                vector-effect="non-scaling-stroke"
              />
            </svg>
            <button
              v-for="node in portalConfig.topoNodes"
              :key="node.id"
              type="button"
              class="zens-port__topology-node absolute inline-flex -translate-x-1/2 -translate-y-1/2 items-center gap-1.5 rounded-full bg-white/80 px-3 py-1.5 text-xs font-medium text-gray-700 ring-1 ring-gray-200/80 backdrop-blur transition hover:bg-gray-50 hover:text-gray-950"
              :class="node.id === 'zens' ? 'zens-port__topology-node--core px-4 py-2 text-sm font-semibold ring-amber-200' : ''"
              :style="{ left: `${node.x}%`, top: `${node.y}%` }"
              @click="handleTopoNode(node)"
            >
              <component :is="resolveIcon(node.icon)" class="zens-port__topology-icon h-3.5 w-3.5 text-amber-600" aria-hidden="true" />
              {{ node.label }}
            </button>
          </div>
        </section>

        <section id="quick" class="zens-port__section scroll-mt-24 space-y-4" aria-labelledby="quick-title">
          <div class="zens-port__section-head flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 id="quick-title" class="zens-port__section-title m-0 text-2xl font-semibold tracking-normal text-gray-950">常用入口</h2>
              <p class="zens-port__section-desc mb-0 mt-2 text-sm text-gray-500">最常进入的社区空间与活动工具。</p>
            </div>
            <span class="zens-port__pass rounded-md bg-gray-100 px-2.5 py-1.5 text-xs font-medium text-gray-500">
              当前通行：{{ passLabel }}
            </span>
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
      </main>
    </div>

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
</template>

<style scoped>
.zens-port {
  min-height: 100dvh;
  background:
    radial-gradient(820px 340px at 86% -120px, rgba(246, 168, 0, 0.12), transparent 60%),
    linear-gradient(180deg, #fbfbfa 0%, #f7f7f5 44%, #f5f6f7 100%);
  color: #111827;
}

.zens-port,
.zens-port * {
  box-sizing: border-box;
}

.zens-port button {
  font: inherit;
}

.zens-port__topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid rgba(243, 244, 246, 0.9);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(18px);
}

.zens-port__topbar-inner,
.zens-port__layout {
  width: min(calc(100% - 48px), 1680px);
  margin: 0 auto;
  padding-right: 0;
  padding-left: 0;
}

.zens-port__topbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  gap: 16px;
}

.zens-port__brand,
.zens-port__actions,
.zens-port__hero-actions,
.zens-port__hero-button,
.zens-port__topology-node,
.zens-port__entry-meta,
.zens-port__footer {
  display: inline-flex;
  align-items: center;
}

.zens-port__brand {
  min-width: 0;
  gap: 12px;
  padding: 8px 4px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.18s ease;
}

.zens-port__brand:hover {
  background: rgba(249, 250, 251, 0.9);
}

.zens-port__logo {
  flex: 0 0 36px;
  width: 36px !important;
  height: 36px !important;
  max-width: 36px !important;
  max-height: 36px !important;
  border-radius: 12px;
  background: #fffbeb;
  object-fit: cover;
  content-visibility: visible !important;
  contain-intrinsic-size: 36px 36px;
  box-shadow: 0 0 0 1px #fde68a;
}

.zens-port__brand-text {
  overflow: hidden;
  color: #111827;
  font-size: 19px;
  font-weight: 650;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zens-port__actions {
  flex: 0 0 auto;
  gap: 8px;
}

.zens-port__nav-button {
  min-height: 36px;
  padding: 8px 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 14px;
  font-weight: 550;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.zens-port__nav-button:hover {
  background: #f9fafb;
  color: #111827;
}

.zens-port__nav-button--primary {
  background: #f6a800;
  color: #fff;
}

.zens-port__nav-button--primary:hover {
  background: #e99a00;
  color: #fff;
}

.zens-port__nav-button:active,
.zens-port__hero-button:active {
  transform: scale(0.98);
}

.zens-port__layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 40px;
  padding-top: 32px;
  padding-bottom: 64px;
}

.zens-port__side {
  display: none;
}

.zens-port__side-nav {
  position: sticky;
  top: 96px;
  display: grid;
  gap: 4px;
  color: #6b7280;
  font-size: 14px;
}

.zens-port__side-link {
  display: block;
  padding: 8px 12px;
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.zens-port__side-link:hover {
  background: #f9fafb;
  color: #111827;
}

.zens-port__side-pass {
  margin: 10px 0 0;
  padding: 12px;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.65;
}

.zens-port__main {
  min-width: 0;
  display: grid;
  gap: 48px;
}

.zens-port__hero {
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
  min-height: 260px;
  padding: 32px 24px;
  border: 1px solid rgba(253, 230, 138, 0.72);
  border-radius: 28px;
  background:
    radial-gradient(circle at 78% 18%, rgba(246, 168, 0, 0.18), transparent 34%),
    linear-gradient(135deg, #fffaf0, #ffffff);
}

.zens-port__hero-copy {
  max-width: 672px;
}

.zens-port__eyebrow {
  margin: 0;
  color: rgba(180, 83, 9, 0.86);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.zens-port__title {
  margin: 20px 0 0;
  color: #030712;
  font-size: clamp(40px, 6vw, 52px);
  font-weight: 650;
  line-height: 1.04;
  letter-spacing: 0;
}

.zens-port__subtitle {
  margin: 16px 0 0;
  color: #111827;
  font-size: clamp(20px, 3vw, 24px);
  font-weight: 600;
  line-height: 1.3;
}

.zens-port__desc {
  max-width: 58ch;
  margin: 16px 0 0;
  color: #6b7280;
  font-size: 15px;
  line-height: 1.85;
}

.zens-port__hero-actions {
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}

.zens-port__hero-button {
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 10px 16px;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.zens-port__hero-button--primary {
  background: #111827;
  color: #fff;
}

.zens-port__hero-button--primary:hover {
  background: #1f2937;
}

.zens-port__hero-button--secondary {
  background: rgba(255, 255, 255, 0.76);
  color: #374151;
  box-shadow: 0 0 0 1px rgba(253, 230, 138, 0.95);
}

.zens-port__hero-button--secondary:hover {
  background: #fff;
  color: #030712;
}

.zens-port__button-icon,
.zens-port__entry-arrow,
.zens-port__footer-icon {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
}

.zens-port__stats {
  margin: 28px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.65;
}

.zens-port__topology {
  position: relative;
  min-height: 230px;
}

.zens-port__topology-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.zens-port__topology-node {
  position: absolute;
  transform: translate(-50%, -50%);
  gap: 6px;
  padding: 6px 12px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: #374151;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  backdrop-filter: blur(12px);
  box-shadow: 0 0 0 1px rgba(229, 231, 235, 0.92);
  transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.zens-port__topology-node:hover {
  background: #f9fafb;
  color: #030712;
}

.zens-port__topology-node--core {
  padding: 9px 16px;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 0 0 1px rgba(251, 191, 36, 0.5);
}

.zens-port__topology-icon {
  width: 14px;
  height: 14px;
  flex: 0 0 14px;
  color: #d97706;
}

.zens-port__section,
.zens-port__all {
  display: grid;
  gap: 16px;
  scroll-margin-top: 96px;
}

.zens-port__all {
  gap: 40px;
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
  color: #030712;
  font-size: 24px;
  font-weight: 650;
  line-height: 1.25;
  letter-spacing: 0;
}

.zens-port__section-desc,
.zens-port__group-desc {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.65;
}

.zens-port__pass {
  flex: 0 0 auto;
  padding: 6px 10px;
  border-radius: 8px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.zens-port__list {
  display: grid;
  border-top: 1px solid #f3f4f6;
}

.zens-port__entry {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  width: 100%;
  gap: 16px;
  padding: 16px 8px;
  border: 0;
  border-bottom: 1px solid #f3f4f6;
  border-radius: 8px;
  background: transparent;
  color: #111827;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.18s ease;
}

.zens-port__entry:hover {
  background: #f9fafb;
}

.zens-port__entry-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f3f4f6;
  color: #4b5563;
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
  color: #030712;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.35;
}

.zens-port__entry-desc {
  display: block;
  margin-top: 4px;
  color: #6b7280;
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
  gap: 12px;
  scroll-margin-top: 96px;
}

.zens-port__group-title {
  margin: 0;
  color: #030712;
  font-size: 18px;
  font-weight: 650;
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
  padding-top: 20px;
  border-top: 1px solid #f3f4f6;
  color: #6b7280;
  font-size: 14px;
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
    grid-template-columns: minmax(0, 1fr) minmax(360px, 0.42fr);
    align-items: center;
    padding: 36px;
  }
}

@media (min-width: 1280px) {
  .zens-port__layout {
    grid-template-columns: clamp(180px, 12vw, 220px) minmax(0, 1fr);
  }

  .zens-port__side {
    display: block;
  }
}

@media (min-width: 1440px) {
  .zens-port__layout {
    gap: clamp(48px, 4vw, 72px);
  }

  .zens-port__hero {
    grid-template-columns: minmax(0, 1fr) minmax(420px, 0.46fr);
    padding: 44px;
  }

  .zens-port__hero-copy {
    max-width: 760px;
  }

  .zens-port__topology {
    min-height: 300px;
  }
}

@media (min-width: 1800px) {
  .zens-port__topbar-inner,
  .zens-port__layout {
    width: min(calc(100% - 72px), 1760px);
  }

  .zens-port__hero {
    grid-template-columns: minmax(0, 1fr) minmax(480px, 0.5fr);
  }
}

@media (max-width: 700px) {
  .zens-port__topbar-inner,
  .zens-port__layout {
    width: min(calc(100% - 24px), 1680px);
  }

  .zens-port__topbar-inner {
    min-height: 0;
    align-items: flex-start;
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .zens-port__actions {
    flex-direction: column;
    align-items: flex-end;
    gap: 6px;
  }

  .zens-port__nav-button {
    min-height: 32px;
    padding: 6px 10px;
    font-size: 12px;
  }

  .zens-port__layout {
    gap: 32px;
    padding: 20px 0 46px;
  }

  .zens-port__hero {
    padding: 26px 18px;
    border-radius: 22px;
  }

  .zens-port__title {
    font-size: 40px;
  }

  .zens-port__hero-button {
    width: 100%;
  }

  .zens-port__topology {
    min-height: 210px;
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
