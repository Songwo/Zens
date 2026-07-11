import api from '@/lib/api'
import type { Result } from '@/types'

export type OpsDraftStatus = 'CREATED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'PUBLISHED'

export interface OpsAutomationStatus {
  enabled: boolean
  circuitOpen: boolean
  circuitReason?: string | null
  circuitUpdatedAt?: string | null
  autoPublish: boolean
  approvalPolicy: 'ALL_MANUAL'
  firstApprovalCount: number
  todayPublishCount: number
  todayReplyCount: number
  dailyPublishLimit: number
  dailyReplyLimit: number
}

export interface OpsDraft {
  id: string
  title?: string
  content?: string
  markdown?: string
  markdownContent?: string
  body?: string
  type?: string
  draftType?: string
  contentType?: string
  status?: OpsDraftStatus
  plannedAt?: string | null
  scheduledAt?: string | null
  publishAt?: string | null
  plannedPublishAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  reviewNote?: string | null
  riskFlags?: unknown
  risks?: unknown
  flags?: unknown
  metadata?: unknown
  meta?: unknown
  metadataJson?: string | null
  sectionId?: string | number | null
  targetPostId?: string | null
  sourceService?: string | null
  failureReason?: string | null
  approvedAt?: string | null
  publishedAt?: string | null
  createTime?: string | null
  updateTime?: string | null
  [key: string]: unknown
}

export interface OpsDraftPage {
  records: OpsDraft[]
  total: number
  current: number
  size: number
  pages: number
}

export const opsAdminApi = {
  getStatus() {
    return api.get<any, Result<OpsAutomationStatus>>('/ops-admin/status')
  },

  getDrafts(params: { status?: string; page: number; size: number }) {
    return api.get<any, Result<OpsDraftPage>>('/ops-admin/drafts', { params })
  },

  approveDraft(id: string | number, note?: string) {
    return api.post<any, Result<OpsDraft>>(`/ops-admin/drafts/${encodeURIComponent(String(id))}/approve`, {
      note: note || '',
    })
  },

  rejectDraft(id: string | number, note: string) {
    return api.post<any, Result<OpsDraft>>(`/ops-admin/drafts/${encodeURIComponent(String(id))}/reject`, { note })
  },

  publishDraft(id: string | number, idempotencyKey: string) {
    const stableId = String(id)
    return api.post<any, Result<OpsDraft>>(`/ops-admin/drafts/${encodeURIComponent(stableId)}/publish`, {
      idempotencyKey,
    })
  },

  setCircuit(open: boolean, reason: string) {
    return api.post<any, Result<OpsAutomationStatus>>('/ops-admin/circuit', { open, reason })
  },
}
