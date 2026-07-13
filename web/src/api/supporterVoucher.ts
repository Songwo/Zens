import api from '@/lib/api'
import type { Result } from '@/types'

export type SupporterVoucherStatus = 'PENDING' | 'ISSUED'

export interface SupporterVoucherGrant {
  id: number
  sourceOrderNo: string
  planCode: string
  quota: number
  status: SupporterVoucherStatus
  code?: string | null
  redemptionUrl: string
  grantedAt: string
  issuedAt?: string | null
}

export const supporterVoucherApi = {
  mine: () => api.get<any, Result<SupporterVoucherGrant[]>>('/supporter/vouchers'),
}
