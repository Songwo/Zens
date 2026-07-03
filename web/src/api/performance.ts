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

export interface WebVitalMetric {
  name: 'LCP' | 'CLS' | 'INP' | 'FCP' | 'TTFB' | string
  value: number
  rating?: 'good' | 'needs-improvement' | 'poor' | string
  route?: string
  navigationType?: string
  userAgent?: string
  timestamp?: number
}

export interface WebVitalEvent extends Required<WebVitalMetric> {
  time: string
}

export interface PerformanceSummary {
  hikari?: Record<string, unknown>
  cache?: Record<string, unknown>
  jvm?: Record<string, unknown>
  slowRequests?: SlowRequestEvent[]
  slowSql?: SlowSqlEvent[]
  webVitals?: {
    total?: number
    metrics?: Record<string, { count?: number; p75?: number; avg?: number; poor?: number }>
    recent?: WebVitalEvent[]
  }
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

  webVitals(params: { metric?: string; route?: string; limit?: number } = {}) {
    return api.get<any, Result<WebVitalEvent[]>>('/admin/performance/web-vitals', { params })
  },

  reportWebVital(data: WebVitalMetric) {
    return api.post<any, Result<void>>('/performance/web-vitals', data)
  },
}
