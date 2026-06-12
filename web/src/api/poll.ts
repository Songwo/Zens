import api from '@/lib/api'
import type { Result } from '@/types'

/**
 * 投票选项（含票数、占比、当前用户是否选了此项）
 */
export interface PollOption {
    id: number
    optionText: string
    optionOrder: number
    voteCount: number
    /** 占总票数的百分比（0-100，保留1位小数） */
    percent: number
    /** 当前用户是否选择了此项 */
    votedByMe: boolean
}

/**
 * 投票全貌 + 当前用户视角状态（后端已算好，前端直接渲染）
 */
export interface Poll {
    id: number
    postId: string
    /** 投票问题，空则前端用帖子标题兜底 */
    title?: string | null
    /** 0单选 1多选 */
    multiChoice: number
    /** 多选时最多可选项数；单选恒为1；0=不限 */
    maxChoices: number
    /** 截止时间，空=不限期 */
    deadline?: string | null
    /** 1进行中 0已关闭 */
    status: number
    /** 参与投票的去重人数 */
    voterCount: number
    /** 总票数（多选时各选项之和，可大于 voterCount） */
    totalVotes: number
    createdBy: string
    createdAt: string
    options: PollOption[]
    /** 是否已截止（deadline 已过 或 status=0） */
    closed: boolean
    /** 当前用户是否已投票 */
    votedByMe: boolean
    /** 当前用户能否投票（已登录 且 未截止 且 未投过） */
    canVote: boolean
    /** 是否应展示结果（已投票 或 已截止 或 是创建者） */
    showResult: boolean
}

/**
 * 发帖时附带的投票创建请求（嵌入 CreatePostRequest.poll，可选）
 */
export interface PollCreateRequest {
    /** 投票问题，可空（前端用帖子标题兜底） */
    title?: string
    /** 0单选 1多选 */
    multiChoice?: number
    /** 多选时最多可选项数；0=不限。单选忽略 */
    maxChoices?: number
    /** 截止时间，空=不限期 */
    deadline?: string
    /** 选项文本列表（2~10 项） */
    options: string[]
}

export const pollApi = {
    /** 按帖子ID查询投票（无投票返回 data=null）。访客也可查看 */
    getByPost(postId: string) {
        return api.get<any, Result<Poll | null>>(`/poll/by-post/${postId}`)
    },

    /** 投票：单选传1个 optionId，多选传多个 */
    vote(pollId: number, optionIds: number[]) {
        return api.post<any, Result<Poll>>('/poll/vote', { pollId, optionIds })
    },

    /** 提前关闭投票（作者/版主/管理员） */
    close(pollId: number) {
        return api.post<any, Result<Poll>>(`/poll/${pollId}/close`)
    },
}
