import api from '@/lib/api'
import type { Result } from '@/types'

export interface SupporterVoucherInventory {
  quota: 30 | 50
  available: number
  assigned: number
  pendingGrants: number
}

export interface SupporterVoucherImportResult {
  quota: 30 | 50
  submitted: number
  imported: number
  duplicates: number
  pendingIssued: number
}

export const supporterVoucherAdminApi = {
  inventory() {
    return api.get<any, Result<SupporterVoucherInventory[]>>('/admin/supporter-vouchers/inventory')
  },

  importCodes(quota: 30 | 50, codes: string[]) {
    return api.post<any, Result<SupporterVoucherImportResult>>('/admin/supporter-vouchers/import', {
      quota,
      codes,
    })
  },
}
