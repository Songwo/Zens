import api from '@/lib/api'
import type { Result } from '@/types'

export interface SupporterPlan {
  code: string
  name: string
  description: string
  priceCents: number
  currency: string
  durationDays: number
  benefits: string[]
  paymentAvailable: boolean
}

export interface SupporterStatus {
  active: boolean
  planCode?: string
  planName?: string
  startsAt?: string
  expiresAt?: string
  remainingDays?: number
  benefits?: string[]
  capabilities?: string[]
}

export interface SupporterOrder {
  orderNo: string
  planCode: string
  planName: string
  amountCents: number
  currency: string
  provider: string
  status: string
  checkoutUrl?: string
  paidAt?: string
  expiresAt: string
  createdAt: string
}

export const supporterApi = {
  plans: () => api.get<any, Result<SupporterPlan[]>>('/supporter/plans'),
  me: () => api.get<any, Result<SupporterStatus>>('/supporter/me'),
  createOrder: (planCode: string, idempotencyKey: string) =>
    api.post<any, Result<SupporterOrder>>('/supporter/orders', { planCode, idempotencyKey }),
  order: (orderNo: string) => api.get<any, Result<SupporterOrder>>(`/supporter/orders/${orderNo}`),
}
