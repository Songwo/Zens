import api from '@/lib/api'
import type { Result } from '@/types'

export interface SearchHistory {
    id: number
    userId: string
    keyword: string
    resultCount: number
    clickedPostId: string
    searchFilters: any
    createdAt: string
}

export interface HotKeyword {
    keyword: string
    searchCount: number
}

export const searchApi = {
    // 获取搜索历史
    getHistory(limit: number = 20) {
        return api.get<any, Result<SearchHistory[]>>('/search/history', {
            params: { limit }
        })
    },

    // 清除搜索历史
    clearHistory() {
        return api.delete<any, Result<void>>('/search/history')
    },

    // 获取热门搜索词
    getHotKeywords(limit: number = 10) {
        return api.get<any, Result<HotKeyword[]>>('/search/hot-keywords', {
            params: { limit }
        })
    },

    // 获取搜索建议
    getSuggestions(keyword: string, limit: number = 10) {
        return api.get<any, Result<string[]>>('/search/suggestions', {
            params: { keyword, limit }
        })
    },

    // 记录搜索点击
    recordClick(keyword: string, postId: string) {
        return api.post<any, Result<void>>('/search/click', null, {
            params: { keyword, postId }
        })
    }
}

export interface UserBadge {
    id: number
    userId: string
    badgeType: string
    badgeCategory: string
    badgeName: string
    badgeDesc: string
    badgeIcon: string
    badgeColor: string
    earnedAt: string
    expiryAt: string
    status: number
    grantReason: string
    grantedBy: string
}

export const badgeApi = {
    // 获取用户徽章
    getUserBadges(userId: string) {
        return api.get<any, Result<UserBadge[]>>(`/badge/user/${userId}`)
    },

    // 授予徽章（管理员）
    grant(data: {
        userId: string
        badgeType: string
        badgeCategory?: string
        badgeName: string
        badgeDesc?: string
        grantReason?: string
    }) {
        return api.post<any, Result<void>>('/badge/grant', null, { params: data })
    },

    // 撤销徽章（管理员）
    revoke(badgeId: number) {
        return api.post<any, Result<void>>(`/badge/revoke/${badgeId}`)
    }
}

export interface SimilarPost {
    postId: string
    title: string
    summary: string
    hasAdoptedAnswer: number
    likeCount: number
    commentCount: number
    similarity: number
}

export interface QualityEvaluation {
    score: number
    level: string
    suggestions: string[]
}

export const aiAssistantApi = {
    // 查找相似问题
    findSimilarPosts(title: string, content: string) {
        return api.post<any, Result<SimilarPost[]>>('/ai-assistant/similar-posts', {
            title,
            content
        })
    },

    // 评估内容质量
    evaluateQuality(title: string, content: string) {
        return api.post<any, Result<QualityEvaluation>>('/ai-assistant/evaluate-quality', {
            title,
            content
        })
    },

    // 智能标签推荐
    suggestTags(title: string, content: string) {
        return api.post<any, Result<string[]>>('/ai-assistant/suggest-tags', {
            title,
            content
        })
    },

    // 评论摘要
    summarizeComments(postId: string) {
        return api.get<any, Result<{ summary: string }>>(`/ai-assistant/summarize-comments/${postId}`)
    },

    // 敏感内容检测
    detectSensitive(content: string) {
        return api.post<any, Result<{
            hasSensitive: boolean
            isSafe: boolean
            suggestion: string
            action: string
        }>>('/ai-assistant/detect-sensitive', { content })
    }
}
