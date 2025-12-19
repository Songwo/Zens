import api from '@/lib/api'
import type { Result } from '@/types'

export interface Tag {
    id: number
    name: string
    postCount: number
    heatScore?: number
}

export const tagApi = {
    // Get hot tags
    getHotTags(limit = 10) {
        return api.get<any, Result<Tag[]>>('/tag/hot', { params: { limit } })
    },

    // Search tags
    search(keyword: string) {
        return api.get<any, Result<Tag[]>>('/tag/search', { params: { keyword } })
    },

    // Toggle follow tag
    toggleFollow(tagId: number | string, score?: number) {
        return api.post<any, Result<{ isFollowing: boolean }>>(`/tag/${tagId}/toggle`, null, { params: { score } })
    },

    // Follow tag
    follow(tagId: number | string, score?: number) {
        return api.post<any, Result<{ success: boolean }>>(`/tag/${tagId}/follow`, null, { params: { score } })
    },

    // Unfollow tag
    unfollow(tagId: number | string) {
        return api.delete<any, Result<{ success: boolean }>>(`/tag/${tagId}/unfollow`)
    },

    // Check follow status
    getStatus(tagId: number | string) {
        return api.get<any, Result<{ isFollowing: boolean }>>(`/tag/${tagId}/status`)
    },

    // Get my following tags
    getMyFollowing() {
        return api.get<any, Result<Tag[]>>('/tag/my-following')
    },

    // Update tag interest score
    updateScore(tagId: number | string, score: number) {
        return api.put<any, Result<{ success: boolean }>>(`/tag/${tagId}/score`, null, { params: { score } })
    }
}
