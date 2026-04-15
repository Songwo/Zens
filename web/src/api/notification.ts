import api from '@/lib/api'
import type { Result } from '@/types'

export interface Notification {
    id: string | number
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

    markBatchAsRead(ids: Array<string | number>) {
        const payload = normalizeIds(ids)
        return api.put<any, Result<void>>('/notification/read-batch', { ids: payload })
    },

    getUnreadCount() {
        return api.get<any, Result<number>>('/notification/unread-count')
    },

    delete(id: string) {
        return api.delete<any, Result<void>>(`/notification/${id}`)
    },

    deleteBatch(ids: Array<string | number>) {
        const payload = normalizeIds(ids)
        return api.delete<any, Result<void>>('/notification/batch', { data: { ids: payload } })
    }
}

function normalizeIds(ids: Array<string | number>) {
    return Array.from(
        new Set(
            ids
                .map(item => Number(item))
                .filter(item => Number.isFinite(item) && item > 0)
        )
    )
}
