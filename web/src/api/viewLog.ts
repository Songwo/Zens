import api from '@/lib/api'
import type { Result } from '@/types'

export interface ViewLog {
    postId: string
    title: string
    viewTime: string
}

export interface ViewHistoryPage {
    records: ViewLog[]
    total: number
    current: number
    size: number
    pages: number
}

export const viewLogApi = {
    /* Song：说明 */
    recordView: (postId: string) => api.post('/view-log/record', null, { params: { postId } }),

    /* Song：阅读时长心跳 —— 前端定时上报停留毫秒数，累加到用户阅读时长与帖子平均阅读时长 */
    heartbeat: (postId: string, durationMs: number) =>
        api.post('/view-log/heartbeat', null, { params: { postId, durationMs } }),

    /* Song：说明 */
    getUserHistory: (userId: string, limit = 20) => api.get<any, Result<ViewLog[]>>(`/view-log/user-history/${userId}`, { params: { limit } }),

    /* Song：说明 */
    getUserHistoryPaged: (userId: string, page = 1, pageSize = 20) =>
        api.get<any, Result<ViewHistoryPage>>(`/view-log/user-history/${userId}/page`, { params: { page, pageSize } }),

    /* Song：说明 */
    getViewCount: (postId: string, startTime?: string, endTime?: string) =>
        api.get<any, Result<number>>('/view-log/count', { params: { postId, startTime, endTime } }),

    /* Song：说明 */
    getTotalViewCount: (postId: string) => api.get<any, Result<number>>(`/view-log/total/${postId}`),

    /* Song：说明 */
    getHotPosts: (limit = 10, startTime?: string) =>
        api.get<any, Result<any[]>>('/view-log/hot-posts', { params: { limit, startTime } }),

    /* Song：说明 */
    getDailyStats: (startDate: string, endDate: string) =>
        api.get<any, Result<any[]>>('/view-log/daily-stats', { params: { startDate, endDate } }),

    /* Song：说明 */
    getDeviceDistribution: () => api.get<any, Result<Record<string, number>>>('/view-log/device-distribution'),

    /* Song：说明 */
    cleanOldLogs: (daysToKeep = 90) => api.delete<any, Result<number>>('/view-log/clean', { params: { daysToKeep } })
}
