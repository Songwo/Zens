import api from '@/lib/api'
import type { Result } from '@/types'

export interface Notification {
    id: string
    userId: string
    senderId: string
    senderName: string
    senderAvatar?: string
    title: string
    content: string
    type: number // 1: System, 2: Reply, 3: Like, 4: Collect
    relatedId: string
    status: number // 0: Unread, 1: Read
    createTime: string
}

export const notificationApi = {
    getUnreadCount() {
        return api.get<any, Result<number>>('/notification/unread-count')
    },
    getList(page = 1, pageSize = 10) {
        return api.get<any, Result<{ records: Notification[]; total: number }>>('/notification/list', {
            params: { page, pageSize }
        })
    },
    markAsRead(id: string) {
        return api.post<any, Result<void>>(`/notification/${id}/read`)
    },
    markAllAsRead() {
        return api.post<any, Result<void>>('/notification/read-all')
    }
}
