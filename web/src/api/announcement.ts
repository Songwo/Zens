import api from '@/lib/api'
import type { Result } from '@/types'

export interface Announcement {
    id: number
    title: string
    content: string
    type: string
    isActive: number
}

export const announcementApi = {
    getPendingPopup() {
        return api.get<any, Result<Announcement | null>>('/announcement/pending-popup')
    },
    markSeen(id: number) {
        return api.post<any, Result<void>>(`/announcement/mark-seen/${id}`)
    }
}
