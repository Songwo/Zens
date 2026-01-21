import api from '@/lib/api'
import type { Result } from '@/types'

export interface UserProfile {
    id: string
    username: string
    nickname: string
    avatar: string
    email: string
    school: string
    major: string
    role: number
    grade: string
    gender: string
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
    updatePwd: (data: any) => api.post('/user/update-pwd', data)
}
