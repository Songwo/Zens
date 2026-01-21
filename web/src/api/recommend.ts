import api from '@/lib/api'
import type { Result, RecommendPost } from '@/types'

export const recommendApi = {
    // 获取混合推荐列表 (个性化推荐)
    getHybridList(page = 1, pageSize = 10) {
        return api.get<any, Result<{ records: RecommendPost[]; total: number }>>('/recommend/list', {
            params: { page, pageSize }
        })
    },

    // Get recommended posts (legacy - redirect to hybrid list)
    getPosts(page = 1, pageSize = 20) {
        return this.getHybridList(page, pageSize)
    },

    // Get recommended tags (redirect to tag hot)
    getTags(limit = 10) {
        return api.get<any, Result<any[]>>('/tag/hot', { params: { limit } })
    },

    // Get similar posts (collaborative filtering)
    getSimilar(postId: string, limit = 6) {
        return api.get<any, Result<RecommendPost[]>>(`/recommend/post-detail/${postId}`, {
            params: { limit }
        })
    }
}
