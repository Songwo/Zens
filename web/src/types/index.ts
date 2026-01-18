export interface Result<T = any> {
    code: number
    message: string
    data: T
}

export interface LoginRequest {
    username: string
    password: string
    uuid: string
    code: string
}

export interface RegisterRequest {
    username: string // student ID
    password: string
    email: string
    code: string // email verification code
    nickname?: string
    avatar?: string
    major?: string
    grade?: number
    school?: string
    gender?: number // 1: male, 2: female
}

export interface CreatePostRequest {
    categoryID: string
    title: string
    content: string
    images?: string
    tags: string
    isAnonymous?: number
    locationName?: string
}

export interface UserInfo {
    id: string
    username: string
    nickname: string
    avatar: string
    email: string
    role: number
    school?: string
    major?: string
}

export const ResultCode = {
    SUCCESS: 2000,
    FAILED: 2001
} as const

export interface LoginResponse {
    accessToken: string
    refreshToken: string
}


export interface Post {
    id: string
    userId: string
    categoryId: string
    title: string
    content: string
    images: string[]
    tags: string
    isAnonymous: number
    locationName?: string
    status: number

    viewCount: number
    likeCount: number
    collectCount: number
    commentCount: number

    createTime: string
    updateTime: string

    isLiked: boolean
    isCollected: boolean

    authorName: string
    authorAvatar?: string

    sentimentScore?: number
    sentimentLabel?: string
    categoryName?: string
    trendLevel?: string
    summary?: string
}

export interface Comment {
    id: string
    parentId: string
    content: string
    userId: string
    nickname: string
    userAvatar?: string
    likeCount: number
    createTime: string
    isLiked?: boolean
    children?: Comment[]
    replyUserId?: string
    replyUserNickname?: string
    isAnonymous?: number
}

export interface CreateCommentRequest {
    postId: string
    parentId?: string
    content: string
    isAnonymous?: number
}

export interface RecommendPost extends Post {
    recommendReason: string
}

export interface PostSearchRequest {
    categoryID?: string
    userID?: string
    keyword?: string
    status?: number
    page: number
    pageSize: number
    orderBy?: 'new' | 'hot'
    tag?: string
    likedBy?: string
    collectedBy?: string
}
