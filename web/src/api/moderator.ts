import api from '@/lib/api'
import type { Result } from '@/types'

export interface ModeratorApplicationItem {
    id: number
    userId: string
    sectionId: number
    reason: string
    status: number // Song：0=待处理, 1=已通过, 2=已拒绝
    reviewNote: string | null
    createdAt: string
    reviewedAt: string | null
}

export const moderatorApi = {
    /**
     * Song：申请版主
     */
    apply(sectionId: number, reason: string) {
        return api.post<any, Result<string>>('/moderator/apply', { sectionId, reason })
    },

    /**
     * Song：获取我的申请列表
     */
    getMyApplications() {
        return api.get<any, Result<ModeratorApplicationItem[]>>('/moderator/my-applications')
    },

    /**
     * Song：获取所有申请 (管理员)
     */
    getAllApplications() {
        return api.get<any, Result<ModeratorApplicationItem[]>>('/moderator/applications')
    },

    /**
     * Song：批准申请 (管理员)
     */
    approve(id: number, reviewNote?: string) {
        return api.post<any, Result<string>>(`/moderator/approve/${id}`, null, { params: { reviewNote } })
    },

    /**
     * Song：拒绝申请 (管理员)
     */
    reject(id: number, reviewNote: string) {
        return api.post<any, Result<string>>(`/moderator/reject/${id}`, null, { params: { reviewNote } })
    }
}
