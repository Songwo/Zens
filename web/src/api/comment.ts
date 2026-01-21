import api from '@/lib/api'
import type { Comment, CreateCommentRequest, Result } from '@/types'

export const commentApi = {
    // Get Comments by Post ID
    getByPostId(postId: string, page = 1, size = 10) {
        return api.get<any, Result<{ records: Comment[]; total: number }>>(`/comment/post/${postId}`, {
            params: { page, size }
        })
    },

    // Add Comment (Authenticated or Anonymous)
    add(data: CreateCommentRequest) {
        // Determine endpoint based on auth status or just use 'create' which handles both
        return api.post<any, Result<void>>('/comment/create', data)
    },

    // Delete Comment
    delete(id: string) {
        return api.delete<any, Result<void>>(`/comment/${id}`)
    },

    // Like Comment
    like(id: string) {
        return api.post<any, Result<void>>(`/comment/${id}/like`)
    }
}
