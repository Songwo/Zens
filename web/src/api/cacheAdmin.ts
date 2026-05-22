import api from '@/lib/api'
import type { Result } from '@/types'

export interface CacheOverview {
  tagHot: number
  postFeed: number
  postFeedVersion: number
  postDetail: number
  postDetailVersion: number
  postHeatRank: number
  postHeatRankVersion: number
  userRecommend: number
  tokenTotal: number
  captcha: number
  lock: number
  legacyAccess: number
  legacyRefresh: number
  authAccess: number
  authRefresh: number
  authDevice: number
  requestNonce: number
  mediaToken: number
  mediaUpload: number
  mediaChunk: number
  mediaAdmin: number
  mediaHealth: number
  mediaError: number
  mediaMetric: number
  mediaTotal: number
  total: number
}

export const cacheAdminApi = {
  getOverview() {
    return api.get<any, Result<CacheOverview>>('/admin/cache/overview')
  },

  countByPattern(pattern: string) {
    return api.get<any, Result<number>>('/admin/cache/count', {
      params: { pattern },
    })
  },

  clearTagCache() {
    return api.delete<any, Result<string>>('/admin/cache/tag/clear')
  },

  clearTokenCache() {
    return api.delete<any, Result<string>>('/admin/cache/token/clear')
  },

  clearMediaCache() {
    return api.delete<any, Result<string>>('/admin/cache/media/clear')
  },

  clearByPattern(pattern: string) {
    return api.delete<any, Result<string>>('/admin/cache/clear', {
      params: { pattern },
    })
  },
}
