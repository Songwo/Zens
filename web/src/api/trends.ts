import api from '@/lib/api'
import type { Result } from '@/types'

export const trendsApi = {
    // Keyword Cloud
    getKeywordCloud() {
        return api.get<any, Result<any>>('/trend-stat/keyword-cloud')
    },

    // Post Trend (Line Chart)
    getPostTrend() {
        return api.get<any, Result<any>>('/trend-stat/post-trend')
    },

    // Category Pie
    getCategoryPie() {
        return api.get<any, Result<any>>('/trend-stat/category-pie')
    },

    // Heat Rank
    getHeatRank() {
        return api.get<any, Result<any[]>>('/trend-stat/heat-rank')
    },

    // Prediction
    getPrediction() {
        return api.get<any, Result<any[]>>('/trend-stat/prediction')
    }
}
