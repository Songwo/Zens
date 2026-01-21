import api from '@/lib/api'
import type { Result } from '@/types'

export interface ViewLog {
    id: string
    userId: string
    postId: string
    viewTime: string
    ip: string
    device: string
}

export const viewLogApi = {
    recordView: (postId: string) => api.post('/view-log/record', null, { params: { postId } }),
    getUserHistory: (userId: string, limit = 20) => api.get<any, Result<ViewLog[]>>(`/view-log/user-history/${userId}`, { params: { limit } })
}
