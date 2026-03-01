import api from '@/lib/api'
import type { Result } from '@/types'

export interface Tag {
    id: number
    name: string
    postCount: number
    heatScore?: number
}

export const tagApi = {
    // Song：说明
    getHotTags(limit = 10) {
        return api.get<any, Result<Tag[]>>('/tag/hot', { params: { limit } })
    },

    // Song：说明
    getHotList(limit = 10) {
        return api.get<any, Result<Tag[]>>('/tag/hot', { params: { limit } })
    },

    // Song：说明
    search(keyword: string) {
        return api.get<any, Result<Tag[]>>('/tag/search', { params: { keyword } })
    },

    // Song：说明
    toggleFollow(tagId: number | string, score?: number) {
        return api.post<any, Result<{ isFollowing: boolean }>>(`/tag/${tagId}/toggle`, null, { params: { score } })
    },

    // Song：说明
    follow(tagId: number | string, score?: number) {
        return api.post<any, Result<{ success: boolean }>>(`/tag/${tagId}/follow`, null, { params: { score } })
    },

    // Song：说明
    unfollow(tagId: number | string) {
        return api.delete<any, Result<{ success: boolean }>>(`/tag/${tagId}/unfollow`)
    },

    // Song：说明
    getStatus(tagId: number | string) {
        return api.get<any, Result<{ isFollowing: boolean }>>(`/tag/${tagId}/status`)
    },

    // Song：说明
    getMyFollowing() {
        return api.get<any, Result<Tag[]>>('/tag/my-following')
    },

    // Song：说明
    updateScore(tagId: number | string, score: number) {
        return api.put<any, Result<{ success: boolean }>>(`/tag/${tagId}/score`, null, { params: { score } })
    }
}
