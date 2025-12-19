import api from '@/lib/api'
import type { Result, Post } from '@/types'

export const recommendApi = {
    // Get recommended posts
    getPosts(page = 1, pageSize = 20) {
        return api.get<any, Result<{ records: Post[]; total: number }>>('/recommend/posts', {
            params: { page, pageSize }
        })
    },

    // Get recommended tags
    getTags(limit = 10) {
        return api.get<any, Result<any[]>>('/recommend/tags', { params: { limit } })
    },

    // Get similar posts (collaborative filtering)
    getSimilar(postId: string, limit = 6) {
        return api.get<any, Result<Post[]>>(`/recommend/similar/${postId}`, {
            params: { limit }
        })
    }
}
