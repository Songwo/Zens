import api from '@/lib/api'
import type { Result } from '@/types'

export interface UserProfileStats {
    posts: number
    likes: number
    reputation: number
    contribution: number
}

export interface UserProfileDetail {
    userId: string
    reputation: number
    contributionVal: number
    activeRegion: string
    preferredCategories: string
    totalPosts: number
    totalLikesReceived: number
    lastActiveTime: string
}

export const userProfileApi = {
    getMyProfile: () => api.get<any, Result<UserProfileDetail>>('/sys-user-profile'),
    getUserProfile: (userId: string) => api.get<any, Result<UserProfileDetail>>(`/sys-user-profile/${userId}`),
    updateMyProfile: (data: any) => api.put('/sys-user-profile', data),
    getMyStats: () => api.get<any, Result<UserProfileStats>>('/sys-user-profile/stats')
}
