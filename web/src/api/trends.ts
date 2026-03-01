import api from '@/lib/api'
import type { Result } from '@/types'

export const trendsApi = {
    // Song：说明
    getKeywordCloud() {
        return api.get<any, Result<any>>('/trend-stat/keyword-cloud')
    },

    // Song：说明
    getPostTrend() {
        return api.get<any, Result<any>>('/trend-stat/post-trend')
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
    }
}
