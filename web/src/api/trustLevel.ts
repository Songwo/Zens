import api from '@/lib/api'
import type { Result } from '@/types'

/** 单个信任等级规格 */
export interface TrustLevelSpec {
    level: number
    label: string
    description: string
    privileges: string[]
}

/** TL 指标 */
export interface TrustMetrics {
    daysSinceRegister: number
    daysVisited: number
    daysVisitedRecent: number
    postsEnteredRecent: number
    postsReadRecent: number
    readTimeSec: number
    likesReceived: number
    likesGiven: number
    postsCreated: number
}

/** 信任等级详情 */
export interface TrustInfo {
    trustLevel: number
    levelLabel: string
    levels: TrustLevelSpec[]
    metrics: TrustMetrics
    silenced: boolean
    silencedUntil?: string
}

export const trustLevelApi = {
    /** 当前登录用户的信任等级详情 */
    info() {
        return api.get<any, Result<TrustInfo>>('/trust-level/info')
    },
    /** 任意用户的信任等级详情 */
    infoByUserId(userId: string) {
        return api.get<any, Result<TrustInfo>>(`/trust-level/info/${userId}`)
    },
    /** 各信任等级规格（公开） */
    thresholds() {
        return api.get<any, Result<TrustLevelSpec[]>>('/trust-level/thresholds')
    },
    /** 管理员手动设置信任等级 */
    promote(userId: string, newLevel: number, reason?: string) {
        return api.post<any, Result<void>>(`/trust-level/promote/${userId}`, null, {
            params: { newLevel, reason: reason || '' },
        })
    },
    /** 手动触发全量重算（管理员） */
    recalculate() {
        return api.post<any, Result<number>>('/trust-level/recalculate')
    },
    /** 查询信任等级变更日志（管理员） */
    events(page = 1, pageSize = 20) {
        return api.get<any, Result<TrustEvent[]>>('/trust-level/events', { params: { page, pageSize } })
    },
    /** 查询当前用户自己的信任等级变更历史 */
    myEvents(page = 1, pageSize = 20) {
        return api.get<any, Result<TrustEvent[]>>('/trust-level/my-events', { params: { page, pageSize } })
    },
}

/** 信任等级变更事件（sys_trust_event） */
export interface TrustEvent {
    id: number
    userId: string
    oldLevel: number
    newLevel: number
    reason: string
    metricsJson?: string
    createTime: string
}
