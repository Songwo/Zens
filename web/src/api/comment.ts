import api from '@/lib/api'
import type { Comment, CreateCommentRequest, Result } from '@/types'

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

    // Song：说明
    like(id: string) {
        return api.post<any, Result<void>>(`/comment/${id}/like`)
    }
}
