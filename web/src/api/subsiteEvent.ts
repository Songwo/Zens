import api from '@/lib/api'
import type { Result } from '@/types'

export type SubsiteEventSeverity = 'info' | 'success' | 'warning' | 'danger' | 'error' | 'default'

export interface SubsiteEvent {
  id: number
  eventId: string
  source: string
  eventType: string
  userId?: string | null
  title: string
  content: string
  relatedId?: string | null
  severity: SubsiteEventSeverity
  status: string
  payloadJson?: string | null
  createdAt: string
}

export interface SubsiteEventPage {
  records: SubsiteEvent[]
  total: number
}

export interface SubsiteEventQuery {
  page?: number
  pageSize?: number
  source?: string
  eventType?: string
  userId?: string
  status?: string
}

export const subsiteEventApi = {
  my(params?: Pick<SubsiteEventQuery, 'page' | 'pageSize' | 'source'>) {
    return api.get<any, Result<SubsiteEventPage>>('/subsite-events/my', { params })
  },
  admin(params?: SubsiteEventQuery) {
    return api.get<any, Result<SubsiteEventPage>>('/admin/subsite-events', { params })
  },
}
