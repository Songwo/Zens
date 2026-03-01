import api from '@/lib/api'
import type { Result, RecommendPost } from '@/types'

export const recommendApi = {
    // Song：获取混合推荐列表 (个性化推荐)
    getHybridList(page = 1, pageSize = 10) {
        return api.get<any, Result<{ records: RecommendPost[]; total: number }>>('/recommend/list', {
            params: { page, pageSize }
        })
    },

    // Song：说明
    getPosts(page = 1, pageSize = 20) {
        return this.getHybridList(page, pageSize)
    },

    // Song：说明
    getTags(limit = 10) {
        return api.get<any, Result<any[]>>('/tag/hot', { params: { limit } })
    },

    // Song：说明
    getSimilar(postId: string, limit = 6) {
        return api.get<any, Result<RecommendPost[]>>(`/recommend/post-detail/${postId}`, {
            params: { limit }
        })
    }
}
