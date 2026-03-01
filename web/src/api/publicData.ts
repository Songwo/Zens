import api from '@/lib/api'
import { tagApi } from '@/api/tag'
import { statsApi } from '@/api/stats'
import { cachedRequest } from '@/utils/requestCache'

const FIVE_MINUTES = 5 * 60 * 1000
const ONE_MINUTE = 60 * 1000

export const publicDataApi = {
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

  getHotRankCached(limit = 5) {
    return cachedRequest(
      `public:hot-rank:${limit}`,
      ONE_MINUTE,
      () => statsApi.getHotRank()
    )
  },
}
