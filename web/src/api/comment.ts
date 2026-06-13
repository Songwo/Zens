import api from '@/lib/api'
import type { Comment, CreateCommentRequest, Result } from '@/types'

export interface CommentShortLink {
    code: string
}

export interface ShortLinkResolveResult {
    targetType: 'comment' | 'post' | string
    postId?: string
    commentId?: string
}

export const commentApi = {
    // Song：说明
    getByPostId(postId: string, page = 1, size = 10) {
        return api.get<any, Result<{ records: Comment[]; total: number }>>(`/comment/post/${postId}`, {
            params: { page, size }
        })
    },

    // Song：说明
    add(data: CreateCommentRequest) {
        // Song：说明
        return api.post<any, Result<void>>('/comment/create', data)
    },

    // Song：说明
    delete(id: string) {
        return api.delete<any, Result<void>>(`/comment/${id}`)
    },

    // Song：编辑评论内容（仅作者/版主/管理员）
    edit(id: string, content: string) {
        return api.put<any, Result<void>>(`/comment/${id}`, { content })
    },

    // Song：恢复软删除评论
    restore(id: string) {
        return api.post<any, Result<void>>(`/comment/${id}/restore`)
    },

    // Song：说明
    like(id: string) {
        return api.post<any, Result<void>>(`/comment/${id}/like`)
    },

    collect(id: string) {
        return api.post<any, Result<{ isCollected: boolean }>>(`/comment/${id}/collect`)
    },

    createShortLink(postId: string, commentId: string) {
        return api.post<any, Result<CommentShortLink>>('/short-link/comment', { postId, commentId })
    },

    resolveShortLink(code: string) {
        return api.get<any, Result<ShortLinkResolveResult>>(`/short-link/${code}`)
    }
}
