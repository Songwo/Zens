import api from '@/lib/api'
import type { Result } from '@/types'

// Song：说明
export interface TopicAuthor {
    id: string
    name: string
    avatar: string
}

export interface TopicStats {
    views: number
    likes: number
    replies: number
}

export interface TopicDetail {
    id: string
    title: string
    content: string
    createdAt: string
    author: TopicAuthor
    stats: TopicStats
    tags: string[]
    category: { id: string; name: string; color: string }
}

export interface Reply {
    id: string
    content: string
    floor: number
    createdAt: string
    likes: number
    author: TopicAuthor
}

export const topicApi = {
    /**
     * Song：说明
     */
    getDetail(id: string) {
        return api.get<any, Result<TopicDetail>>(`/post/${id}`)
    },

    /**
     * Song：说明
     */
    getReplies(id: string, page = 1, size = 20) {
        return api.get<any, Result<{ records: Reply[]; total: number }>>(`/comment/post/${id}`, {
            params: { page, size }
        })
    },

    /**
     * Song：说明
     */
    reply(id: string, content: string) {
        return api.post<any, Result<void>>('/comment/create', { postId: id, content })
    }
}
