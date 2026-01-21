import api from '@/lib/api'
import type { Post, PostSearchRequest, Result, CreatePostRequest } from '@/types'

export const postApi = {
    // Search Posts (Feed)
    searchList(data: PostSearchRequest) {
        return api.post<any, Result<{ records: Post[]; total: number; pages: number }>>('/post/search-lists', data)
    },

    // Get Detail
    getDetail(id: string) {
        return api.get<any, Result<Post>>(`/post/${id}`)
    },

    // Create Post
    create(data: CreatePostRequest) {
        return api.post<any, Result<void>>('/post/create-post', data)
    },

    // Like
    like(id: string) {
        return api.post<any, Result<void>>(`/post/${id}/like`)
    },

    // Collect
    collect(id: string) {
        return api.post<any, Result<void>>(`/post/${id}/collect`)
    },

    // Extract Tags (AI)
    extractTags(data: { content: string; title: string }) {
        return api.post<any, Result<{ tags: string; summary: string }>>('/post/extract-tags', data)
    }
}
