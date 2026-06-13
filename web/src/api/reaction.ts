import api from '@/lib/api'
import type { Result } from '@/types'

export type ReactionType = 'love' | 'haha' | 'wow' | 'celebrate'

export type ReactionTarget = 'post' | 'comment'

export interface ReactionResp {
    /** 各表情数量: { love: 3, haha: 1 } */
    counts: Record<string, number>
    /** 当前用户所选表情，未反应为 null */
    mine: ReactionType | null
}

export const reactionApi = {
    /** 查询单个目标的表情聚合 */
    get(targetType: ReactionTarget, targetId: string) {
        return api.get<any, Result<ReactionResp>>('/reaction', { params: { targetType, targetId } })
    },
    /** 切换/设置表情（需登录） */
    react(targetType: ReactionTarget, targetId: string, type: ReactionType) {
        return api.post<any, Result<ReactionResp>>('/reaction', null, {
            params: { targetType, targetId, type }
        })
    },
    /** 批量查询（评论列表用，body 为目标ID数组） */
    batch(targetType: ReactionTarget, ids: string[]) {
        return api.post<any, Result<Record<string, ReactionResp>>>('/reaction/batch', ids, {
            params: { targetType }
        })
    }
}
