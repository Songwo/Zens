import api from '@/lib/api'
import type { Result } from '@/types'

/**
 * 帖子订阅（主题追踪）。
 * 订阅后：被追踪帖有新评论时收到即时站内通知；
 * 开了邮件通知的用户每天还会收到一封聚合摘要邮件。
 * 评论/发帖会自动订阅（后端处理），手动订阅/取消走这里。
 */
export const subscriptionApi = {
    /** 追踪帖子（幂等） */
    subscribe(postId: string) {
        return api.post<any, Result<void>>(`/subscription/${postId}`)
    },

    /** 取消追踪 */
    unsubscribe(postId: string) {
        return api.delete<any, Result<void>>(`/subscription/${postId}`)
    },

    /** 我是否已追踪该帖（未登录返回 subscribed=false，不报错） */
    getStatus(postId: string) {
        return api.get<any, Result<{ subscribed: boolean }>>(`/subscription/${postId}/status`)
    },
}
