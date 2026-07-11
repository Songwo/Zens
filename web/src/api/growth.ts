import api from '@/lib/api'
import type { Result } from '@/types'

export interface GrowthDashboard {
  days: number
  overview: Record<'visitors' | 'registrations' | 'activated' | 'contributors' | 'supporters', number>
  daily: Array<{ date: string; activeUsers: number; pageViews: number; engagedUsers: number }>
  sources: Array<{ source: string; visitors: number }>
  retention: Array<{ cohortDate: string; cohortSize: number; d1: number; d7: number; d30: number }>
}

export const growthApi = {
  dashboard(days = 30) {
    return api.get<any, Result<GrowthDashboard>>('/admin/growth/dashboard', { params: { days } })
  },
}
