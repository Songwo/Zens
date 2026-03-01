import api from '@/lib/api'
import type { Result } from '@/types'

export interface Notification {
    id: string
    userId: string
    type: string
    title: string
    content: string
    relatedId?: string | number
    isRead: number
    createTime?: string
    createdAt?: string
}

export const notificationApi = {
    getList(page: number, pageSize: number) {
        return api.get<any, Result<{ records: Notification[]; total: number; unreadCount: number }>>('/notification/list', {
            params: { page, pageSize }
        })
    },

    markAsRead(id: string) {
        return api.put<any, Result<void>>(`/notification/${id}/read`)
    },

    markAllAsRead() {
        return api.put<any, Result<void>>('/notification/read-all')
    },

    getUnreadCount() {
        return api.get<any, Result<number>>('/notification/unread-count')
    },

    delete(id: string) {
        return api.delete<any, Result<void>>(`/notification/${id}`)
    }
}

