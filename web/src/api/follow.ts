import api from '@/lib/api'
import type { Result } from '@/types'

export interface Follow {
    id: string
    followerId: string
    followingId: string
    createTime: string
}

export const followApi = {
    /* Song：说明 */
    follow(userId: string) {
        return api.post<any, Result<void>>(`/follow/${userId}`)
    },

    /* Song：说明 */
    unfollow(userId: string) {
        return api.delete<any, Result<void>>(`/follow/${userId}`)
    },

    /* Song：说明 */
    getFollowers(userId: string, page = 1, pageSize = 20) {
        return api.get<any, Result<{ records: any[]; total: number }>>(`/follow/followers/${userId}`, {
            params: { page, pageSize }
        })
    },

    /* Song：说明 */
    getFollowing(userId: string, page = 1, pageSize = 20) {
        return api.get<any, Result<{ records: any[]; total: number }>>(`/follow/following/${userId}`, {
            params: { page, pageSize }
        })
    },

    /* Song：说明 */
    isFollowing(userId: string) {
        return api.get<any, Result<boolean>>(`/follow/is-following/${userId}`)
    },

    /* Song：说明 */
    getMyFollowing() {
        return api.get<any, Result<any[]>>('/follow/following')
    },

    /* Song：说明 */
    getMyFollowers() {
        return api.get<any, Result<any[]>>('/follow/followers')
    },

    /* Song：说明 */
    getFollowStats() {
        return api.get<any, Result<{ followingCount: number; followerCount: number }>>('/follow/stats')
    },

    /* Song：说明 */
    getUserFollowStats(userId: string) {
        return api.get<any, Result<{ followingCount: number; followerCount: number }>>(`/follow/stats/${userId}`)
    }
}
