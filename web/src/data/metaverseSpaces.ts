import type { Component } from 'vue'
import {
  Aim,
  Box,
  Coin,
  Connection,
  DataLine,
  Document,
  Finished,
  House,
  Link,
  Lock,
  MagicStick,
  Medal,
  Monitor,
  Operation,
  Present,
  Promotion,
  Setting,
  Ticket,
  User,
  VideoCamera,
  Warning,
} from '@element-plus/icons-vue'
import { hasAdminRole, hasBackofficeAccess } from '@/utils/sessionProfile'
import { stationUrl, stationSsoCallback, type StationKey } from '@/config/stations'

export type MetaverseCategory = 'core' | 'welfare' | 'activity' | 'economy' | 'infra' | 'governance' | 'lab'
export type MetaverseStatus = 'open' | 'beta' | 'maintenance' | 'coming'
export type MetaverseAccess = 'public' | 'login' | 'backoffice' | 'admin' | 'badge' | 'trust'

export interface MetaverseSpace {
  id: string
  title: string
  subtitle: string
  description: string
  category: MetaverseCategory
  status: MetaverseStatus
  access: MetaverseAccess
  href: string
  localHref?: string
  adminHref?: string
  clientId?: string
  system: string
  accent: string
  icon: Component
  featured?: boolean
  pinned?: boolean
  /** 主入口卡片：true 时星港/启动器用"落地即登录"SSO 深链进站 */
  ssoEntry?: boolean
  requiredBadge?: string
  requiredTrustLevel?: number
  tags: string[]
  integration: string
}

export interface MetaverseCategoryMeta {
  id: MetaverseCategory | 'all'
  label: string
  description: string
}

export const metaverseCategories: MetaverseCategoryMeta[] = [
  { id: 'all', label: '全部空间', description: '查看所有可访问与即将开放的 Zens 子项目' },
  { id: 'core', label: '社区核心', description: '主社区、内容、搜索和个人中心' },
  { id: 'welfare', label: '福利权益', description: 'CDK、邀请码、空投和权益领取' },
  { id: 'activity', label: '活动工具', description: '抽奖、开奖、运营活动联动' },
  { id: 'economy', label: '积分经济', description: '积分商城、订单和兑换码池' },
  { id: 'infra', label: '基础设施', description: '媒体服务、SSO、缓存和系统观测' },
  { id: 'governance', label: '治理后台', description: '内容审核、举报、用户与信任等级' },
  { id: 'lab', label: '实验项目', description: '仍在设计和内测中的新入口' },
]

export const metaverseSpaces: MetaverseSpace[] = [
  {
    id: 'main-home',
    title: 'Zens 社区主站',
    subtitle: '开发者社区的信息流核心',
    description: '进入主社区首页，浏览最新帖子、热门讨论、精选内容和社区动态。',
    category: 'core',
    status: 'open',
    access: 'public',
    href: '/',
    system: 'Vue 主站 / Spring Boot',
    accent: '#f4b400',
    icon: House,
    featured: true,
    pinned: true,
    tags: ['主站', '信息流', '社区'],
    integration: '承载帖子、评论、标签、通知、私信、等级与信任体系，是所有子站身份的中心。',
  },
  {
    id: 'cdk-claim',
    title: 'CDK 空投领取',
    subtitle: '活动权益与兑换码发放',
    description: '面向社区成员的福利领取入口，用于内测资格、兑换码、节点和活动权益分发。',
    category: 'welfare',
    status: 'beta',
    access: 'login',
    href: stationUrl('cdk', '/local-claim'),
    localHref: stationUrl('cdk', '/claim/demo'),
    adminHref: stationUrl('cdk', '/admin/dashboard'),
    clientId: 'cdk-airdrop',
    system: 'cdk-airdrop-station',
    accent: '#d97757',
    icon: Present,
    featured: true,
    pinned: true,
    ssoEntry: true,
    tags: ['CDK', '空投', '福利', '风控'],
    integration: '通过主站 SSO 登录，CDK 子站维护项目、活动、库存、节点、领取记录与风险控制。',
  },
  {
    id: 'cdk-admin',
    title: 'CDK 运营后台',
    subtitle: '项目、库存、节点和领取记录',
    description: '管理员维护 CDK 项目、活动规则、库存导入、领取审计和风控策略。',
    category: 'welfare',
    status: 'beta',
    access: 'admin',
    href: stationUrl('cdk', '/admin/dashboard'),
    clientId: 'cdk-airdrop',
    system: 'cdk-airdrop-station',
    accent: '#9f7aea',
    icon: Operation,
    tags: ['运营后台', '库存', '节点'],
    integration: '真实后台包含项目、活动、CDK、领取记录、节点和风险管理。',
  },
  {
    id: 'lottery-tool',
    title: '原帖评论抽奖',
    subtitle: '透明、公平的社区开奖工具',
    description: '粘贴 Zens 原帖链接，同步评论楼层，按规则去重后抽取中奖用户。',
    category: 'activity',
    status: 'beta',
    access: 'login',
    href: stationUrl('lottery'),
    system: 'campus-lottery-station',
    accent: '#2cb1a6',
    icon: Aim,
    featured: true,
    ssoEntry: true,
    tags: ['抽奖', '评论', '活动'],
    integration: '通过 SSO 连接主站账号，可同步评论、执行抽奖，并把开奖结果回写原帖。',
  },
  {
    id: 'lottery-sso',
    title: '抽奖站 SSO',
    subtitle: '活动工具的登录通道',
    description: '直接发起抽奖站主站授权流程，适合测试子站 SSO 登录链路。',
    category: 'activity',
    status: 'beta',
    access: 'login',
    href: stationUrl('lottery', '/api/auth/sso/start'),
    clientId: 'campus-lottery-station',
    system: 'campus-lottery-station',
    accent: '#3b82f6',
    icon: Connection,
    tags: ['SSO', '登录', '联调'],
    integration: '依赖 COMMUNITY_SSO_AUTHORIZE_URL、COMMUNITY_SSO_TOKEN_URL 和 SSO_CLIENT_ID。',
  },
  {
    id: 'shop-home',
    title: 'Zens 积分商城',
    subtitle: '积分兑换与社区权益',
    description: '使用主站积分兑换商品、兑换码和社区权益，订单独立留痕。',
    category: 'economy',
    status: 'beta',
    access: 'login',
    href: stationUrl('shop'),
    adminHref: stationUrl('shop', '/admin'),
    clientId: 'zdc-shop',
    system: 'zdc-shop',
    accent: '#6a9bcc',
    icon: Coin,
    featured: true,
    ssoEntry: true,
    tags: ['积分', '商城', '兑换'],
    integration: '商城维护商品和订单，积分余额与扣减通过主站内部 HMAC 服务接口完成。',
  },
  {
    id: 'shop-orders',
    title: '商城兑换记录',
    subtitle: '订单、发货和兑换码查询',
    description: '查看个人兑换历史、订单状态、发货记录和兑换码内容。',
    category: 'economy',
    status: 'beta',
    access: 'login',
    href: stationUrl('shop', '/orders'),
    clientId: 'zdc-shop',
    system: 'zdc-shop',
    accent: '#788c5d',
    icon: Finished,
    tags: ['订单', '兑换记录', '积分'],
    integration: '订单与兑换码由 zdc-shop 独立维护，用户身份来自主站 SSO。',
  },
  {
    id: 'shop-admin',
    title: '商城管理台',
    subtitle: '商品、订单和兑换码池',
    description: '管理商品上架、订单处理、兑换码池、商品图片和退款返还积分。',
    category: 'economy',
    status: 'beta',
    access: 'admin',
    href: stationUrl('shop', '/admin'),
    clientId: 'zdc-shop',
    system: 'zdc-shop',
    accent: '#d97757',
    icon: Setting,
    tags: ['商城后台', '商品', '订单'],
    integration: '退款接口回调主站积分返还接口，保持商城订单和主站积分一致。',
  },
  {
    id: 'media-panel',
    title: '媒体服务面板',
    subtitle: '上传、分片、文件治理',
    description: '进入 Go 媒体服务原生面板，管理文件、运行配置、分片上传和审计。',
    category: 'infra',
    status: 'open',
    access: 'admin',
    href: stationUrl('media', '/panel/login'),
    system: 'go-media-service',
    accent: '#2cb1a6',
    icon: VideoCamera,
    tags: ['媒体', '上传', '文件'],
    integration: '主站通过 Upload JWT 与 Service Token 接入 Go 媒体服务，后台也提供 /admin/media。',
  },
  {
    id: 'media-health',
    title: '媒体健康检查',
    subtitle: '服务可用性探针',
    description: '快速查看媒体服务健康状态，排查上传链路和文件服务异常。',
    category: 'infra',
    status: 'open',
    access: 'admin',
    href: stationUrl('media', '/health'),
    system: 'go-media-service',
    accent: '#6a9bcc',
    icon: Monitor,
    tags: ['health', '监控', '运维'],
    integration: '服务同时暴露 /metrics、/debug/pprof 与 /panel，便于运维排障。',
  },
  {
    id: 'admin-dashboard',
    title: '主站管理后台',
    subtitle: '内容、用户、举报和运维',
    description: '进入主站后台工作台，处理内容审核、用户管理、举报、缓存、日志和性能观测。',
    category: 'governance',
    status: 'open',
    access: 'backoffice',
    href: '/admin/posts',
    adminHref: '/admin/dashboard',
    system: '主站管理后台',
    accent: '#f4b400',
    icon: DataLine,
    featured: true,
    tags: ['后台', '治理', '审核'],
    integration: '版主可进入板块治理范围，管理员可进入所有后台管理模块。',
  },
  {
    id: 'admin-sso',
    title: 'SSO 应用管理',
    subtitle: '子站接入与密钥治理',
    description: '管理 CDK、抽奖站、积分商城等子站的 clientId、回调地址、密钥和启用状态。',
    category: 'infra',
    status: 'open',
    access: 'admin',
    href: '/admin/sso',
    system: '主站 SSO',
    accent: '#9f7aea',
    icon: Lock,
    pinned: true,
    tags: ['SSO', '子站', '密钥'],
    integration: '所有需要主站账号登录的子站，都应在这里注册和维护授权配置。',
  },
  {
    id: 'invite-center',
    title: '邀请好友',
    subtitle: '社区通行证扩散入口',
    description: '生成邀请链接，邀请更多成员进入社区与子项目生态。',
    category: 'welfare',
    status: 'open',
    access: 'login',
    href: '/invite',
    adminHref: '/admin/invite-codes',
    system: '主站邀请系统',
    accent: '#788c5d',
    icon: Ticket,
    tags: ['邀请', '注册', '权益'],
    integration: '用户侧生成邀请链接，后台可管理邀请码、使用状态与启用状态。',
  },
  {
    id: 'trust-center',
    title: '信任等级中心',
    subtitle: '身份、权益和访问能力',
    description: '查看自己的信任等级、行为指标、社区特权和未来空间解锁条件。',
    category: 'core',
    status: 'open',
    access: 'login',
    href: '/trust',
    adminHref: '/admin/trust',
    system: '主站信任体系',
    accent: '#d97757',
    icon: Medal,
    tags: ['信任等级', '身份', '权限'],
    integration: '星港可按信任等级开放实验空间，后台可全量重算或单用户重算 TL。',
  },
  {
    id: 'guide',
    title: '阅读指南',
    subtitle: '新成员进入星港前的说明书',
    description: '理解社区使用方式、内容结构、发帖习惯和子站协作边界。',
    category: 'core',
    status: 'open',
    access: 'public',
    href: '/guide',
    system: '主站文档页',
    accent: '#6a9bcc',
    icon: Document,
    tags: ['指南', '规则', '新人'],
    integration: '适合作为所有子站登录页、帮助页和授权页的统一说明入口。',
  },
  {
    id: 'l-station',
    title: 'L站访问舱',
    subtitle: '私域资源与特别通行空间',
    description: '为持有指定徽章的成员预留的特别空间入口，可作为未来资源站、工具站或内测站。',
    category: 'lab',
    status: 'coming',
    access: 'badge',
    requiredBadge: '你可以访问L站',
    href: '/metaverse?space=l-station',
    system: 'Zens Lab',
    accent: '#141413',
    icon: MagicStick,
    featured: true,
    tags: ['L站', '徽章', '内测'],
    integration: '访问条件可绑定管理员授予的用户徽章，当前作为空间占位与权限演示。',
  },
  {
    id: 'project-docs',
    title: '项目文档入口',
    subtitle: '路线图、说明和协作资料',
    description: '汇总主站、CDK、抽奖站、积分商城和媒体服务的说明文档。',
    category: 'lab',
    status: 'open',
    access: 'public',
    href: '/about',
    system: 'Zens Docs',
    accent: '#6b7280',
    icon: Box,
    tags: ['文档', '路线图', '协作'],
    integration: '后续可以接入真实文档站、GitHub Issue 和 Roadmap。',
  },
]

export const metaverseStatusMeta: Record<MetaverseStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  open: { label: '开放中', type: 'success' },
  beta: { label: '内测中', type: 'warning' },
  maintenance: { label: '维护中', type: 'danger' },
  coming: { label: '即将开放', type: 'info' },
}

export const metaverseAccessMeta: Record<MetaverseAccess, { label: string; description: string }> = {
  public: { label: '公开', description: '所有访客可进入' },
  login: { label: '登录', description: '登录社区账号后可进入' },
  backoffice: { label: '版务', description: '版主或管理员可进入' },
  admin: { label: '管理员', description: '仅管理员可进入' },
  badge: { label: '徽章', description: '需要指定用户徽章' },
  trust: { label: '信任等级', description: '需要达到指定信任等级' },
}

export function canAccessMetaverseSpace(space: MetaverseSpace, profile: any, isLoggedIn: boolean) {
  if (space.status === 'coming' || space.status === 'maintenance') {
    return false
  }
  if (space.access === 'public') return true
  if (!isLoggedIn) return false
  if (space.access === 'login') return true
  if (space.access === 'backoffice') return hasBackofficeAccess(profile)
  if (space.access === 'admin') return hasAdminRole(profile)
  if (space.access === 'badge') return Boolean(space.requiredBadge && profile?.badgeText === space.requiredBadge)
  if (space.access === 'trust') return Number(profile?.trustLevel ?? 0) >= Number(space.requiredTrustLevel ?? 0)
  return false
}

export function resolveMetaverseHref(space: MetaverseSpace) {
  return space.localHref || space.href
}

/** clientId → 子站地址键，用于构造一站式登录深链。 */
const CLIENT_TO_STATION: Record<string, StationKey> = {
  'zdc-shop': 'shop',
  'cdk-airdrop': 'cdk',
  'campus-lottery-station': 'lottery',
}

/**
 * 主入口卡片的"落地即登录"深链：对标记了 ssoEntry 的第一方站，返回主站
 * `/sso/authorize?client_id=&redirect_uri=` 深链——配合 trusted 自动授权，
 * 已登录用户点一下即免同意登录进站。其余卡片走原 href（依赖已建立的子站会话）。
 */
export function buildStationEntryHref(space: MetaverseSpace): string {
  if (space.ssoEntry && space.clientId) {
    const stationKey = CLIENT_TO_STATION[space.clientId]
    const callback = stationKey ? stationSsoCallback(stationKey) : ''
    if (callback) {
      return `/sso/authorize?client_id=${encodeURIComponent(space.clientId)}&redirect_uri=${encodeURIComponent(callback)}`
    }
  }
  return resolveMetaverseHref(space)
}

/** 深链是否为主站内部路由（/sso/authorize 用 router.push，外链用 window.open）。 */
export function isInternalHref(href: string): boolean {
  return href.startsWith('/')
}

export function getMetaverseCategoryMeta(category: MetaverseCategory) {
  return metaverseCategories.find(item => item.id === category)
}

export const metaverseFlows = [
  {
    id: 'sso',
    title: '主站 SSO 连接所有子站',
    from: 'Zens 主站',
    to: 'CDK / 抽奖 / 积分商城',
    icon: Connection,
    description: '主站维护 SSO 应用和授权流程，子站用 clientId 与回调地址接入统一身份。',
    touchpoints: ['/sso/authorize', '/admin/sso', 'zdc-shop', 'cdk-airdrop', 'lottery-station'],
  },
  {
    id: 'points',
    title: '积分商城强一致扣减主站积分',
    from: 'zdc-shop',
    to: 'Spring Boot 主站',
    icon: Coin,
    description: '商城维护订单和兑换码，积分余额由主站提供，下单和退款通过内部服务接口同步。',
    touchpoints: ['/api/orders', '/points/consume', '/points/credit', 'sys_user.points'],
  },
  {
    id: 'lottery',
    title: '抽奖工具读取并回写主站原帖',
    from: '抽奖站',
    to: '帖子与评论',
    icon: Aim,
    description: '抽奖站同步原帖评论、执行去重开奖，并可由机器人把结果发布回原帖。',
    touchpoints: ['/api/lottery/comments/sync', '/api/lottery/draw', '开奖结果回帖'],
  },
  {
    id: 'media',
    title: '媒体服务从主站拆出独立治理',
    from: 'Vue / Spring Boot',
    to: 'Go Media Service',
    icon: VideoCamera,
    description: '前端上传经主站代理，后台和 Go 面板双入口管理文件、上传和运行状态。',
    touchpoints: ['/admin/media', ':8090/panel', '/health', '/metrics'],
  },
  {
    id: 'lab',
    title: '徽章与信任等级决定实验空间',
    from: '用户身份',
    to: 'L站访问舱 / 未来项目',
    icon: Promotion,
    description: '管理员授予徽章或用户达到信任等级后，星港可解锁更私域的空间入口。',
    touchpoints: ['badgeText', 'trustLevel', '/admin/users', '/admin/trust'],
  },
]

export const metaverseAdminWarnings = [
  {
    title: '子站地址已支持环境变量切换',
    description: 'CDK、抽奖站、积分商城和媒体服务地址改由 VITE_STATION_* 环境变量驱动（见 .env.development / .env.production），dev 自动回落 localhost，上线只需配置生产域名。',
    icon: Warning,
  },
  {
    title: '星港 V1 使用前端配置驱动',
    description: '当前不新增数据库表。若需要后台动态 CRUD，可把 metaverseSpaces 替换为 /admin/metaverse/spaces 接口。',
    icon: Link,
  },
  {
    title: '权限显示已接入用户身份',
    description: '页面会基于登录态、管理员、版务、徽章和信任等级展示可访问状态；真正的子站仍需自身服务端校验。',
    icon: User,
  },
]
