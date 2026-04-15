import api from '@/lib/api'
import type { Result } from '@/types'

export interface SiteStats {
    totalPosts: number
    totalUsers: number
    totalComments: number
    todayPosts: number
}

export interface HotPost {
    postId: string
    title: string
    heatScore: number
    viewCount: number
    likeCount: number
    commentCount: number
}

export const statsApi = {
    /**
     * Song：说明
     */
    getSiteStats() {
        return api.get<any, Result<SiteStats>>('/stats/site')
    },

    /**
     * Song：说明
     */
    getPostTrend() {
        return api.get<any, Result<{ date: string, count: number }[]>>('/trend-stat/post-trend')
    },

    /**
     * Song：说明
     */
    getUserTrend() {
        return api.get<any, Result<{ date: string, count: number }[]>>('/trend-stat/user-trend')
    },

    /**
     * Song：说明
     */
    getHotRank(timeRange?: 'TODAY' | 'WEEK' | 'MONTH' | string, limit = 10) {
        return api.get<any, Result<HotPost[]>>('/heat-rank/top', {
            params: {
                timeRange,
                limit,
            }
        })
    }
}
