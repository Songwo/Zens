import api from '@/lib/api'
import type { Result } from '@/types'

/**
 * Song：举报接口
 * Song：说明
 */
export interface SysReport {
    id: string
    targetType: string
    targetId: string
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
    getList(current = 1, size = 10, status?: number) {
        return api.get<any, Result<{ records: SysReport[]; total: number }>>('/report/list', {
            params: { current, size, status }
        })
    },

    /* Song：说明 */
    resolve(id: string, status: number) {
        return api.post<any, Result<void>>(`/report/resolve/${id}`, null, {
            params: { status }
        })
    }
}
