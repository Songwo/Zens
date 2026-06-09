import type { AxiosRequestConfig } from 'axios'
import api from '@/lib/api'
import type { Result } from '@/types'

export interface TipRequest {
    targetType: string
    targetId: string
    amount: number
    message?: string
}

export interface TipRecord {
    id: number
    tipperId: string
    targetType: string
    targetId: string
    targetAuthorId: string
    amount: number
    message: string
    createdAt: string
}

export const tipApi = {
    // 打赏
    send(data: TipRequest) {
        return api.post<any, Result<void>>('/tip/send', data)
    },

    // 获取收到的打赏
    getReceived() {
        return api.get<any, Result<TipRecord[]>>('/tip/received')
    },

    // 获取发出的打赏
    getSent() {
        return api.get<any, Result<TipRecord[]>>('/tip/sent')
    },

    // 获取打赏统计
    getSum(targetType: string, targetId: string) {
        return api.get<any, Result<{ totalAmount: number }>>('/tip/sum', {
            params: { targetType, targetId }
        })
    }
}

export interface AnswerAdoption {
    id: number
    postId: string
    commentId: string
    adoptedBy: string
    adoptedAt: string
    reputationGranted: number
    expGranted: number
}

export const answerAdoptionApi = {
    // 采纳答案
    adopt(postId: string, commentId: string) {
        return api.post<any, Result<void>>('/answer-adoption/adopt', null, {
            params: { postId, commentId }
        })
    },

    // 取消采纳
    cancel(postId: string) {
        return api.post<any, Result<void>>('/answer-adoption/cancel', null, {
            params: { postId }
        })
    },

    // 获取采纳记录
    get(postId: string) {
        return api.get<any, Result<AnswerAdoption>>(`/answer-adoption/${postId}`)
    }
}

export interface PostSeries {
    id: number
    userId: string
    title: string
    description: string
    coverImage: string
    status: number
    postCount: number
    viewCount: number
    likeCount: number
    createdAt: string
    updatedAt: string
}

export const postSeriesApi = {
    // 创建系列
    create(data: { title: string; description?: string; coverImage?: string }) {
        return api.post<any, Result<{ seriesId: number }>>('/post-series/create', data)
    },

    // 添加帖子到系列
    addPost(seriesId: number, postId: string) {
        return api.post<any, Result<void>>(`/post-series/${seriesId}/add-post`, null, {
            params: { postId }
        })
    },

    // 从系列移除帖子
    removePost(seriesId: number, postId: string) {
        return api.post<any, Result<void>>(`/post-series/${seriesId}/remove-post`, null, {
            params: { postId }
        })
    },

    // 获取系列帖子列表
    getSeriesPosts(seriesId: number) {
        return api.get<any, Result<any[]>>(`/post-series/${seriesId}/posts`)
    },

    // 获取我的系列
    getMySeries() {
        return api.get<any, Result<PostSeries[]>>('/post-series/my')
    },

    // 获取用户系列
    getUserSeries(userId: string) {
        return api.get<any, Result<PostSeries[]>>(`/post-series/user/${userId}`)
    },

    // 更新排序
    reorder(seriesId: number, postId: string, orderIndex: number) {
        return api.post<any, Result<void>>(`/post-series/${seriesId}/reorder`, null, {
            params: { postId, orderIndex }
        })
    },

    // 获取系列详情
    getDetail(seriesId: number) {
        return api.get<any, Result<PostSeries>>(`/post-series/${seriesId}`)
    }
}
