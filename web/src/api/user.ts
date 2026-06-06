import api from '@/lib/api'
import { uploadApi } from '@/api/upload'
import type { Result } from '@/types'

export interface UserProfile {
    id: string
    username: string
    nickname: string
    avatar: string
    email: string
    level?: number
    bio?: string
    school: string
    major: string
    role: number
    roles?: string[]
    grade?: string
    gender?: string
    enrollmentYear?: number
    interestTags?: string
    twoFactorEnabled?: number
    emailNotifyEnabled?: number
    githubBound?: boolean
    profileCardTheme?: string
    quickCardTheme?: string
    profileCardBgUrl?: string
    quickCardBgUrl?: string
    coverConfig?: string
    moderatedSectionIds?: number[]
}

export interface SupportContact {
    id: string
    username: string
    nickname: string
    avatar?: string
}

export interface UserPublicProfile {
    id: string
    username: string
    nickname: string
    avatar?: string
    bio?: string
    school?: string
    major?: string
    level?: number
    roles?: string[]
    enrollmentYear?: number
    interestTags?: string
    profileCardTheme?: string
    quickCardTheme?: string
    profileCardBgUrl?: string
    quickCardBgUrl?: string
    coverConfig?: string
    postCount: number
    followingCount: number
    followerCount: number
}

export const userApi = {
    getProfile: () => api.get<any, Result<UserProfile>>('/user/profile'),
    getSimpleProfile: () => api.get<any, Result<any>>('/user/simple-profile'),
    updateAvatar: async (file: File) => {
        const url = await uploadApi.uploadImage(file, 'avatar')
        return api.put<any, Result<string>>('/user/avatar', { avatarUrl: url })
    },
    updateUserDetails: (data: any) => api.post('/user/update-udetail', data),
    updateCover: (payload: { fit: 'cover' | 'contain'; x: number; y: number; height: number }) =>
        api.put<any, Result<string>>('/user/cover', payload),
    updatePwd: (data: any) => api.post('/user/update-pwd', data),

    /**
     * Song：说明
     */
    getProfileStats: () => api.get<any, Result<{ postCount: number; followingCount: number; followerCount: number }>>('/user/profile-stats'),

    getNotificationSettings: () => api.get<any, Result<{ emailNotifyEnabled: boolean }>>('/user/notification-settings'),

    updateNotificationSettings: (data: { emailNotifyEnabled: boolean }) =>
        api.post<any, Result<void>>('/user/notification-settings', data),

    /**
     * Song：说明
     */
    getSupportContact: () => api.get<any, Result<SupportContact>>('/user/support-contact'),

    getPublicProfile: (userId: string) => api.get<any, Result<UserPublicProfile>>(`/user/public/${userId}`),

    searchUsers: (keyword: string) =>
        api.get<any, Result<Array<{
            id: string
            username: string
            nickname: string
            avatar: string
            bio?: string
            school?: string
            postCount: number
            followerCount: number
        }>>>('/user/search', { params: { keyword } }),

    // Song：=================== 管理员接口 ===================

    /* Song：说明 */
    getAll: () => api.get<any, Result<any[]>>('/user/all'),

    /* Song：说明 */
    ban: (id: string) => api.post<any, Result<void>>(`/user/ban/${id}`),

    /* Song：说明 */
    unban: (id: string) => api.post<any, Result<void>>(`/user/unban/${id}`),

    /* Song：说明 */
    delete: (id: string) => api.delete<any, Result<void>>(`/user/${id}`),

    /* 设置用户角色 (管理员) */
    assignRole: (id: string, roleCode: string) =>
        api.post<any, Result<void>>(`/user/${id}/role`, null, { params: { roleCode } }),

    /* 设置用户负责的板块版主范围 (管理员) */
    updateModeratedSections: (id: string, sectionIds: number[]) =>
        api.put<any, Result<void>>(`/user/${id}/moderated-sections`, { sectionIds })
}
