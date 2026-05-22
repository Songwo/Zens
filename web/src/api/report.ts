import api from '@/lib/api'
import type { Result } from '@/types'

/**
 * Song：举报接口
 * Song：说明
 */
export interface ReportManageItem {
    id: string
    targetType: string
    targetId: string
    targetTitle?: string
    targetPreview?: string
    sectionId?: number
    sectionName?: string
    reason: string
    details?: string
    reporterId: string
    status: number
    createTime: string
    updateTime: string
}

export const reportApi = {
    /* Song：说明 */
    create(data: { targetType: string; targetId: string; reason: string; details?: string }) {
        return api.post<any, Result<string>>('/report/create', data)
    },

    /* Song：说明 */
    getList(current = 1, size = 10, status?: number, sectionId?: number) {
        return api.get<any, Result<{ records: ReportManageItem[]; total: number }>>('/report/list', {
            params: { current, size, status, sectionId }
        })
    },

    /* Song：说明 */
    resolve(id: string, status: number) {
        return api.post<any, Result<void>>(`/report/resolve/${id}`, null, {
            params: { status }
        })
    },

    /* Song：打回帖子为草稿 */
    reject(id: string, reason: string) {
        return api.post<any, Result<void>>(`/report/reject/${id}`, { reason })
    }
}
