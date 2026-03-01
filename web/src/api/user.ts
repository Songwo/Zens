import api from '@/lib/api'
import type { Result } from '@/types'

export interface UserProfile {
    id: string
    username: string
    nickname: string
    avatar: string
    email: string
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
}

export interface SupportContact {
    id: string
    username: string
    nickname: string
    avatar?: string
}

export const userApi = {
    getProfile: () => api.get<any, Result<UserProfile>>('/user/profile'),
    getSimpleProfile: () => api.get<any, Result<any>>('/user/simple-profile'),
    updateAvatar: (file: File) => {
        const formData = new FormData()
        formData.append('avatar', file)
        return api.put<any, Result<string>>('/user/avatar', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        })
    },
    updateUserDetails: (data: any) => api.post('/user/update-udetail', data),
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

    // Song：=================== 管理员接口 ===================

    /* Song：说明 */
    getAll: () => api.get<any, Result<any[]>>('/user/all'),

    /* Song：说明 */
    ban: (id: string) => api.post<any, Result<void>>(`/user/ban/${id}`),

    /* Song：说明 */
    unban: (id: string) => api.post<any, Result<void>>(`/user/unban/${id}`),

    /* Song：说明 */
    delete: (id: string) => api.delete<any, Result<void>>(`/user/${id}`)
}
