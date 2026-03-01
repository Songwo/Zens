import api from '@/lib/api'
import type { Result } from '@/types'

export interface LevelInfo {
    level: number
    experience: number
    currentLevelExp: number
    nextLevelExp: number
    progress: number
    lastUpgrade: string | null
}

export interface LevelThreshold {
    level: number
    experience: number
}

export interface LevelExpRecord {
    id: number
    expDelta: number
    reason: string
    createTime: string
}

export interface LevelExpRecordPage {
    records: LevelExpRecord[]
    total: number
    current: number
    size: number
    pages: number
}

export const levelApi = {
    getInfo() {
        return api.get<any, Result<LevelInfo>>('/level/info')
    },
    getThresholds() {
        return api.get<any, Result<LevelThreshold[]>>('/level/thresholds')
    },
    getExpRecords(params?: { days?: number; page?: number; pageSize?: number }) {
        return api.get<any, Result<LevelExpRecordPage>>('/level/exp-records', { params })
    }
}
