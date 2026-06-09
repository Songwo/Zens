import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import { usePostComposerStore } from '@/store/postComposer'
import { ensureCurrentUserProfile, hasAdminRole, hasBackofficeAccess } from '@/utils/sessionProfile'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/pages/HomePage.vue'),
      meta: { keepAlive: true, title: '首页', description: '校园社区最新动态与推荐内容' }
    },
    {
      path: '/sections',
      name: 'sections-overview',
      component: () => import('@/pages/SectionsOverview.vue'),
      meta: { keepAlive: true, title: '板块全景', description: '浏览校园社区全部板块与话题方向' }
    },
    {
      path: '/s/:id',
      name: 'section',
      component: () => import('@/pages/SectionPage.vue'),
    },
    {
      path: '/t/:id',
      name: 'topic-detail',
      component: () => import('@/pages/PostDetailPage.vue'),
    },
    {
      path: '/r/:code',
      name: 'short-link-detail',
      component: () => import('@/pages/PostDetailPage.vue'),
    },
    {
      path: '/tag/:name',
      name: 'tag',
      component: () => import('@/pages/TagPage.vue'),
      meta: { keepAlive: true, title: '标签话题', description: '查看标签下的相关内容与讨论' }
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/pages/SearchPage.vue'),
      meta: { keepAlive: true, title: '搜索', description: '搜索帖子、标签与用户内容' }
    },
    {
      path: '/compose',
      name: 'compose',
      redirect: '/',
      // Song：说明
    },
    {
      path: '/me',
      name: 'me',
      component: () => import('@/pages/MePage.vue'),
      meta: { title: '个人中心', description: '查看个人资料、帖子、收藏与通知' }
    },
    {
      path: '/level',
      name: 'level',
      component: () => import('@/pages/LevelPage.vue'),
      meta: { requiresAuth: true, title: '我的等级', description: '查看等级进度、经验来源与等级特权' }
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/pages/SettingsPage.vue'),
    },
    {
      path: '/messages',
      name: 'messages',
      component: () => import('@/pages/MessagesPage.vue'),
      meta: { requiresAuth: true, title: '私信', description: '与社区成员进行私信沟通' },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/pages/NotificationsPage.vue'),
      meta: { requiresAuth: true, title: '消息通知', description: '查看评论、点赞、关注与系统通知' },
    },
    {
      path: '/guide',
      name: 'guide',
      component: () => import('@/pages/sidebar/GuidePage.vue'),
    },
    {
      path: '/hot',
      name: 'hot',
      component: () => import('@/pages/HotPage.vue'),
      meta: { keepAlive: true, title: '热门排行', description: '查看社区热度最高的话题和帖子' }
    },
    {
      path: '/connect',
      name: 'connect',
      component: () => import('@/pages/ConnectPage.vue'),
      meta: { requiresAuth: false, title: '等级中心', description: '查看等级成长、经验规则与权益' }
    },
    {
      path: '/invite',
      name: 'invite',
      component: () => import('@/pages/InvitePage.vue'),
      meta: { requiresAuth: true, title: '邀请好友', description: '邀请好友加入，获得经验奖励' }
    },
    {
      path: '/featured',
      name: 'featured',
      component: () => import('@/pages/FeaturedPage.vue'),
      meta: { title: '精华汇总', description: '精选优质内容与推荐专题' }
    },
    {
      path: '/feedback',
      name: 'feedback',
      component: () => import('@/pages/sidebar/FeedbackPage.vue'),
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('@/pages/sidebar/AboutPage.vue'),
      meta: { title: '关于我们', description: '了解 Zens 校园社区的理念与团队' }
    },
    {
      path: '/terms',
      name: 'terms',
      component: () => import('@/pages/sidebar/TermsPage.vue'),
      meta: { title: '用户协议', description: 'Zens 校园社区用户服务协议' }
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('@/pages/sidebar/PrivacyPage.vue'),
      meta: { title: '隐私政策', description: 'Zens 校园社区隐私保护政策' }
    },
    {
      path: '/contact',
      name: 'contact',
      component: () => import('@/pages/sidebar/ContactPage.vue'),
      meta: { title: '联系管理', description: '联系 Zens 校园社区管理员' }
    },
    {
      path: '/auth',
      name: 'auth',
      component: () => import('@/pages/auth/AuthPage.vue'),
      meta: { guest: true, title: '登录 / 注册', description: '登录或注册 Zens 校园社区账号' }
    },
    {
      path: '/sso/authorize',
      name: 'sso-authorize',
      component: () => import('@/pages/auth/SsoAuthorizePage.vue'),
      meta: { title: 'SSO 授权', description: '授权第三方应用使用社区账号登录' }
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/pages/admin/AdminPage.vue'),
      meta: { requiresAuth: true, requiresBackoffice: true },
      redirect: '/admin/posts',
      children: [
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/pages/admin/DashboardPage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'posts',
          name: 'admin-posts',
          component: () => import('@/pages/admin/PostsManagePage.vue'),
          meta: { requiresBackoffice: true },
        },
        {
          path: 'posts/trash',
          name: 'admin-posts-trash',
          component: () => import('@/pages/admin/PostsManagePage.vue'),
          meta: { requiresBackoffice: true },
        },
        {
          path: 'sections',
          name: 'admin-sections',
          component: () => import('@/pages/admin/SectionsManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/pages/admin/UsersManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'reports',
          name: 'admin-reports',
          component: () => import('@/pages/admin/ReportsManagePage.vue'),
          meta: { requiresBackoffice: true },
        },
        {
          path: 'changelog',
          name: 'admin-changelog',
          component: () => import('@/pages/admin/ChangelogManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'moderator-applications',
          name: 'admin-moderator-applications',
          component: () => import('@/pages/admin/ModeratorApplicationsManagePage.vue'),
        },
        {
          path: 'invite-codes',
          name: 'admin-invite-codes',
          component: () => import('@/pages/admin/InviteCodesPage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'cache',
          name: 'admin-cache',
          component: () => import('@/pages/admin/CacheManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/pages/admin/LogsManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'media',
          name: 'admin-media',
          component: () => import('@/pages/admin/MediaStatusPage.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'my-sections',
          name: 'admin-my-sections',
          component: () => import('@/pages/admin/MyModerationPage.vue'),
          meta: { requiresBackoffice: true },
        },
        {
          path: 'sso',
          name: 'admin-sso',
          component: () => import('@/pages/admin/SsoManagePage.vue'),
          meta: { requiresAdmin: true },
        },
        // Song：说明
        {
          path: 'content',
          redirect: '/admin/posts',
        },
      ]
    },
    // Song：说明
    { path: '/auth/login', redirect: '/auth?type=login' },
    { path: '/auth/register', redirect: '/auth?type=register' },
    { path: '/c/:id', redirect: to => `/s/${to.params.id}` },
    { path: '/categories', redirect: '/sections' },
    { path: '/category/:id', redirect: to => `/s/${to.params.id}` },
    { path: '/post/:id', redirect: to => `/t/${to.params.id}` },
    { path: '/p/:id', redirect: to => `/t/${to.params.id}` },
    { path: '/profile', redirect: '/me' },
    {
      path: '/user/:id',
      name: 'user-profile',
      component: () => import('@/pages/UserProfilePage.vue'),
      meta: { keepAlive: true, title: '用户主页', description: '查看该用户的主页与发帖' }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/pages/NotFoundPage.vue'),
      meta: { title: '页面不存在', description: '你访问的页面不存在或已被移除' }
    }
  ],
})

const TOKEN_EXPIRE_SKEW_MS = 5000

function decodeJwtExpireAtMs(token: string) {
  try {
    const segments = token.split('.')
    if (segments.length < 2) return null
    const payloadRaw = segments[1]
    if (!payloadRaw) return null
    const payload = payloadRaw
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(Math.ceil(payloadRaw.length / 4) * 4, '=')
    const json = atob(payload)
    const parsed = JSON.parse(json)
    const exp = Number(parsed?.exp)
    if (!Number.isFinite(exp)) return null
    return exp * 1000
  } catch {
    return null
  }
}

function isTokenExpired(token: string) {
  const expAt = decodeJwtExpireAtMs(token)
  if (!expAt) {
    return false
  }
  return expAt <= Date.now() + TOKEN_EXPIRE_SKEW_MS
}

function readToken(tokenKey: 'access_token' | 'refresh_token') {
  const token = localStorage.getItem(tokenKey) || sessionStorage.getItem(tokenKey)
  if (!token) {
    return null
  }
  if (isTokenExpired(token)) {
    localStorage.removeItem(tokenKey)
    sessionStorage.removeItem(tokenKey)
    return null
  }
  return token
}

function hasOauthCallbackParams(to: RouteLocationNormalized) {
  return Boolean(to.query.provider || to.query.code || to.query.state)
}

// Song：说明
router.beforeEach(async (to, _from, next) => {
  const composerStore = usePostComposerStore()
  const accessToken = readToken('access_token')
  const refreshToken = readToken('refresh_token')
  const hasSessionToken = !!(accessToken || refreshToken)
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth || record.path === '/me' || record.path === '/settings')
  const requiresBackoffice = to.matched.some(record => record.meta.requiresBackoffice)
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin)

  // Song：说明
  if (to.path === '/compose') {
    setTimeout(() => composerStore.open(), 100)
    return next({ path: '/' })
  }

  if (requiresAuth && !hasSessionToken) {
    next({ path: '/auth', query: { type: 'login', redirect: to.fullPath } })
  } else if ((requiresBackoffice || requiresAdmin) && hasSessionToken) {
    try {
      const profile = await ensureCurrentUserProfile({ force: requiresBackoffice || requiresAdmin })
      const isAdmin = hasAdminRole(profile)
      const canEnterBackoffice = hasBackofficeAccess(profile)

      if ((requiresAdmin && !isAdmin) || (requiresBackoffice && !canEnterBackoffice)) {
        next({ path: '/', replace: true })
      } else {
        next()
      }
    } catch (error) {
      console.error('Failed to check backoffice permission:', error)
      next({ path: '/auth', query: { type: 'login', redirect: to.fullPath } })
    }
  } else if (to.path === '/auth' && hasSessionToken && !to.query.redirect && !hasOauthCallbackParams(to)) {
    next({ path: '/' })
  } else {
    next()
  }
})

const ensureMetaTag = (name: string, content: string) => {
  let el = document.querySelector(`meta[name="${name}"]`) as HTMLMetaElement | null
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('name', name)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

const ensureCanonical = (path: string) => {
  let link = document.querySelector('link[rel="canonical"]') as HTMLLinkElement | null
  if (!link) {
    link = document.createElement('link')
    link.setAttribute('rel', 'canonical')
    document.head.appendChild(link)
  }
  const origin = window.location.origin
  link.setAttribute('href', `${origin}${path}`)
}

router.afterEach((to) => {
  const title = (to.meta.title as string) || '校园社区'
  const description = (to.meta.description as string) || 'Zens 校园社区，聚合校园技术交流与生活分享。'
  document.title = `${title} - Zens`
  ensureMetaTag('description', description)
  ensureCanonical(to.fullPath || '/')
})

export default router

