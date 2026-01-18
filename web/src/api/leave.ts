import api from '@/lib/api'
import type { Result } from '@/types'

export interface LeaveRequest {
    id?: number
    userId?: string
    type: number
    startTime: string
    endTime: string
    reason: string
    status: number
    approverId?: string
    approveTime?: string
    createTime?: string
}

export const leaveApi = {
    submit(data: LeaveRequest) {
        return api.post<any, Result<void>>('/leave/submit', data)
    },
    getMyList(page = 1, pageSize = 10) {
        return api.get<any, Result<{ records: LeaveRequest[]; total: number }>>('/leave/my-list', {
            params: { page, pageSize }
        })
    }
}
