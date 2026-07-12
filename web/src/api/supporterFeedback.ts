import api from '@/lib/api'
import type { Result } from '@/types'

export type SupporterFeedbackStatus = 'OPEN' | 'ANSWERED' | 'CLOSED'

export interface SupporterFeedback {
  id: number
  userId: string
  subject: string
  content: string
  status: SupporterFeedbackStatus
  adminReply?: string | null
  repliedBy?: string | null
  repliedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface SupporterFeedbackPage {
  records: SupporterFeedback[]
  total: number
}

export const supporterFeedbackApi = {
  mine: (page = 1, pageSize = 10) =>
    api.get<any, Result<SupporterFeedbackPage>>('/supporter/feedback', { params: { page, pageSize } }),
  create: (subject: string, content: string) =>
    api.post<any, Result<SupporterFeedback>>('/supporter/feedback', { subject, content }),
  admin: (params?: { page?: number; pageSize?: number; status?: SupporterFeedbackStatus; userId?: string }) =>
    api.get<any, Result<SupporterFeedbackPage>>('/admin/supporter-feedback', { params }),
  reply: (id: number, reply: string, status: 'ANSWERED' | 'CLOSED' = 'ANSWERED') =>
    api.patch<any, Result<SupporterFeedback>>(`/admin/supporter-feedback/${id}/reply`, { reply, status }),
}
