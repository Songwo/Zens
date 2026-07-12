import api from '@/lib/api'
import type { Result } from '@/types'

export interface SupporterCreatorInsightSummary {
  publishedPosts: number
  totalViews: number
  totalLikes: number
  totalCollects: number
  totalComments: number
  avgDwellSec: number
}

export interface SupporterCreatorInsightDaily {
  date: string
  views: number
  likes: number
  collects: number
  comments: number
  avgDwellSec: number
}

export interface SupporterCreatorInsights {
  days: number
  fromDate: string
  toDate: string
  generatedAt: string
  summary: SupporterCreatorInsightSummary
  trend: SupporterCreatorInsightDaily[]
}

export const supporterInsightsApi = {
  get(days = 30) {
    return api.get<any, Result<SupporterCreatorInsights>>('/supporter/creator-insights', {
      params: { days },
    })
  },
}
