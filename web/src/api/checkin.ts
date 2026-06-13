import api from '@/lib/api'
import type { Result } from '@/types'

export interface CheckInStatus {
    /** 今日是否已签到 */
    checkedToday: boolean
    /** 当前连续签到天数 */
    continuousDays: number
    /** 累计签到天数 */
    totalDays: number
    /** 本月已签到日期（yyyy-MM-dd），用于日历点亮 */
    monthDates: string[]
    /** 本次签到获得积分（仅 checkIn 返回，>0） */
    rewardPoints: number
    /** 本次签到获得经验（仅 checkIn 返回，>0） */
    rewardExp: number
    /** 用户当前总积分 */
    totalPoints: number
}

export const checkInApi = {
    /** 查询签到状态 */
    getStatus() {
        return api.get<any, Result<CheckInStatus>>('/check-in/status')
    },
    /** 执行签到 */
    checkIn() {
        return api.post<any, Result<CheckInStatus>>('/check-in')
    }
}
