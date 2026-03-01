import api from '@/lib/api'
import type { Post, PostSearchRequest, Result, CreatePostRequest } from '@/types'

export const postApi = {
    // Song：说明
    searchList(data: PostSearchRequest) {
        return api.post<any, Result<{ records: Post[]; total: number; pages: number }>>('/post/search-lists', data)
    },

    // Song：说明
    getDetail(id: string) {
        return api.get<any, Result<Post>>(`/post/${id}`)
    },

    // Song：说明
    create(data: CreatePostRequest) {
        return api.post<any, Result<void>>('/post/create-post', data)
    },

    // Song：说明
    update(data: { postId: string; title?: string; content?: string; tags?: string; sectionId?: number; coverImage?: string }) {
        return api.post<any, Result<void>>('/post/update-post', data)
    },

    // Song：说明
    like(id: string) {
        return api.post<any, Result<void>>(`/post/${id}/like`)
    },

    // Song：说明
    collect(id: string) {
        return api.post<any, Result<void>>(`/post/${id}/collect`)
    },

    // Song：置顶 (管理员) - 旧接口，保持兼容
    pin(id: string) {
        return api.post<any, Result<void>>(`/post/${id}/pin`)
    },

    // Song：全局置顶/取消
    setGlobalPin(id: string, pinOrder?: number, expireAt?: string) {
        return api.post<any, Result<void>>(`/post/${id}/global-pin`, { pinOrder, expireAt })
    },

    // Song：板块置顶/取消
    setCategoryPin(id: string, pinOrder?: number, expireAt?: string) {
        return api.post<any, Result<void>>(`/post/${id}/category-pin`, { pinOrder, expireAt })
    },

    // Song：获取置顶列表
    getPinnedPosts(sectionId?: number) {
        return api.get<any, Result<Post[]>>('/post/pinned', { params: { sectionId } })
    },

    // Song：说明
    feature(id: string) {
        return api.post<any, Result<void>>(`/post/${id}/feature`)
    },

    // Song：提取标签 (智能)
    extractTags(data: { content: string; title: string }) {
        return api.post<any, Result<{ tags: string; summary: string }>>('/post/extract-tags', data)
    },

    // Song：说明
    regenerateSummary(id: string) {
        return api.post<any, Result<string>>(`/post/${id}/summary/regenerate`)
    },

    // Song：说明
    delete(id: string) {
        return api.delete<any, Result<void>>(`/post/${id}`)
    },

    // Song：说明
    report(data: { targetType: string; targetId: string; reason: string; details?: string }) {
        return api.post<any, Result<void>>('/report/create', data)
    }
}
