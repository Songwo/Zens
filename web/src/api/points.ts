import api from '@/lib/api'
import type { Result } from '@/types'

/** 积分流水一行,对应主站 sys_point_txn。 */
export interface PointTxn {
  id: number
  /** 积分变动,正=入账 负=扣减 */
  delta: number
  /** 本笔完成后的余额 */
  balanceAfter: number
  /** 来源: main-site / zdc-shop / campus-lottery-station / cdk-airdrop */
  source: string
  /** 业务类型: checkin / shop.order / shop.refund / lottery.join / lottery.refund */
  bizType: string
  orderId?: string
  reason: string
  createdAt: string
}

export interface PointTxnPage {
  records: PointTxn[]
  total: number
}

export interface PointSummary {
  points: number
  monthEarned: number
  monthSpent: number
}

export const pointsApi = {
  /** 积分概览:当前余额 + 本月收支合计 */
  getSummary() {
    return api.get<any, Result<PointSummary>>('/user/points/summary')
  },
  /** 积分明细分页 */
  getTransactions(params: { page: number; pageSize: number }) {
    return api.get<any, Result<PointTxnPage>>('/user/points/transactions', { params })
  },
}
