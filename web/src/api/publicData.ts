import api from '@/lib/api'
import { tagApi } from '@/api/tag'
import { statsApi } from '@/api/stats'
import { useUserStore } from '@/store/user'
import { cachedRequest } from '@/utils/requestCache'
import type { Result } from '@/types'

const FIVE_MINUTES = 5 * 60 * 1000
const ONE_MINUTE = 60 * 1000
const QUIET_PUBLIC_REQUEST = {
  skipGlobalProgress: true,
  silentError: true,
}

export interface PublicSectionItem {
  id: number
  name: string
  icon?: string
  description?: string
  postCount?: number
}

export interface PublicTagItem {
  id: number
  name: string
  heat?: number
  postCount?: number
}

export interface PublicHotRankItem {
  postId: string
  title: string
  heatScore: number
  viewCount: number
  likeCount: number
  commentCount: number
}

export interface PublicSiteStats {
  totalPosts: number
  totalUsers: number
  totalComments: number
  todayPosts: number
}

export interface HomeBootstrapPayload {
  activeSections: PublicSectionItem[]
  hotTags: PublicTagItem[]
  hotRank: PublicHotRankItem[]
  siteStats: PublicSiteStats
  unsolvedQaCount?: number
  todaySolvedQaCount?: number
  followedTagUpdateCount?: number
}

export const publicDataApi = {
  getHomeBootstrapCached(hotTagLimit = 10, hotRankLimit = 5, timeRange: 'TODAY' | 'WEEK' | 'MONTH' | string = 'WEEK') {
    const userStore = useUserStore()
    const userScope = userStore.isLoggedIn ? `user:${userStore.userId || 'session'}` : 'guest'
    return cachedRequest(
      `public:home-bootstrap:${userScope}:${hotTagLimit}:${hotRankLimit}:${timeRange}`,
      ONE_MINUTE,
      () => api.get<any, Result<HomeBootstrapPayload>>('/public/home-bootstrap', {
        ...QUIET_PUBLIC_REQUEST,
        params: { hotTagLimit, hotRankLimit, timeRange },
      })
    )
  },

  getActiveSectionsCached() {
    return cachedRequest(
      'public:sections:active',
      FIVE_MINUTES,
      () => api.get<any, any>('/section/active', QUIET_PUBLIC_REQUEST)
    )
  },

  getHotTagsCached(limit = 10) {
    return cachedRequest(
      `public:tags:hot:${limit}`,
      FIVE_MINUTES,
      () => tagApi.getHotList(limit, QUIET_PUBLIC_REQUEST)
    )
  },

  getHotRankCached(limit = 5, timeRange: 'TODAY' | 'WEEK' | 'MONTH' | string = 'WEEK') {
    return cachedRequest(
      `public:hot-rank:${timeRange}:${limit}`,
      ONE_MINUTE,
      () => statsApi.getHotRank(timeRange, limit, QUIET_PUBLIC_REQUEST)
    )
  },
}
