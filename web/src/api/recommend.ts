import api from '@/lib/api'
import type { Result, RecommendPost } from '@/types'

export const recommendApi = {
    // 获取混合推荐列表 (个性化推荐)
    getHybridList(page = 1, pageSize = 10) {
        return api.get<any, Result<{ records: RecommendPost[]; total: number }>>('/recommend/list', {
            params: { page, pageSize }
        })
    },

    // Get recommended posts (legacy/tag-based)
    getPosts(page = 1, pageSize = 20) {
        return api.get<any, Result<{ records: any[]; total: number }>>('/recommend/posts', {
            params: { page, pageSize }
        })
    },

    // Get recommended tags
    getTags(limit = 10) {
        return api.get<any, Result<any[]>>('/recommend/tags', { params: { limit } })
    },

    // Get similar posts (collaborative filtering)
    getSimilar(postId: string, limit = 6) {
        return api.get<any, Result<RecommendPost[]>>(`/recommend/post-detail/${postId}`, {
            params: { limit }
        })
    }
}
