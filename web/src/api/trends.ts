import api from '@/lib/api'
import type { Result } from '@/types'

export const trendsApi = {
    // Song：说明
    getKeywordCloud() {
        return api.get<any, Result<any>>('/trend-stat/keyword-cloud')
    },

    // Song：说明
    getPostTrend(days = 7) {
        return api.get<any, Result<any>>('/trend-stat/post-trend', { params: { days } })
    },

    // Song：说明
    getSectionPie() {
        return api.get<any, Result<any>>('/trend-stat/section-pie')
    },

    // Song：说明
    getHeatRank() {
        return api.get<any, Result<any[]>>('/trend-stat/heat-rank')
    },

    // Song：说明
    getPrediction() {
        return api.get<any, Result<any[]>>('/trend-stat/prediction')
    },

    getDashboard() {
        return api.get<any, Result<{ todayPosts: number; totalPosts: number; totalUsers: number; todayUsers: number }>>('/trend-stat/dashboard')
    }
}
