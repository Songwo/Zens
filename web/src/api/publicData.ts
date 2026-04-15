import api from '@/lib/api'
import { tagApi } from '@/api/tag'
import { statsApi } from '@/api/stats'
import { cachedRequest } from '@/utils/requestCache'
import type { Result } from '@/types'

const FIVE_MINUTES = 5 * 60 * 1000
const ONE_MINUTE = 60 * 1000

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
}

export const publicDataApi = {
  getHomeBootstrapCached(hotTagLimit = 10, hotRankLimit = 5, timeRange: 'TODAY' | 'WEEK' | 'MONTH' | string = 'WEEK') {
    return cachedRequest(
      `public:home-bootstrap:${hotTagLimit}:${hotRankLimit}:${timeRange}`,
      ONE_MINUTE,
      () => api.get<any, Result<HomeBootstrapPayload>>('/public/home-bootstrap', {
        params: { hotTagLimit, hotRankLimit, timeRange },
      })
    )
  },

  getActiveSectionsCached() {
    return cachedRequest(
      'public:sections:active',
      FIVE_MINUTES,
      () => api.get<any, any>('/section/active')
    )
  },

  getHotTagsCached(limit = 10) {
    return cachedRequest(
      `public:tags:hot:${limit}`,
      FIVE_MINUTES,
      () => tagApi.getHotList(limit)
    )
  },

  getHotRankCached(limit = 5, timeRange: 'TODAY' | 'WEEK' | 'MONTH' | string = 'WEEK') {
    return cachedRequest(
      `public:hot-rank:${timeRange}:${limit}`,
      ONE_MINUTE,
      () => statsApi.getHotRank(timeRange, limit)
    )
  },
}
