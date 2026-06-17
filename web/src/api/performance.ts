import api from '@/lib/api'
import type { Result } from '@/types'

export interface SlowRequestEvent {
  time: string
  method: string
  uri: string
  status: number
  costMs: number
}

export interface SlowSqlEvent {
  time: string
  mapperId: string
  costMs: number
  sql: string
}

export interface PerformanceSummary {
  hikari?: Record<string, unknown>
  cache?: Record<string, unknown>
  jvm?: Record<string, unknown>
  slowRequests?: SlowRequestEvent[]
  slowSql?: SlowSqlEvent[]
}

export const performanceApi = {
  summary() {
    return api.get<any, Result<PerformanceSummary>>('/admin/performance/summary')
  },

  slowRequests(params: { keyword?: string; minMs?: number; limit?: number } = {}) {
    return api.get<any, Result<SlowRequestEvent[]>>('/admin/performance/slow-requests', { params })
  },

  slowSql(params: { keyword?: string; minMs?: number; limit?: number } = {}) {
    return api.get<any, Result<SlowSqlEvent[]>>('/admin/performance/slow-sql', { params })
  },
}
