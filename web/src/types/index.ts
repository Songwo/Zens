export interface Result<T = any> {
    code: number
    message: string
    data: T
}

export interface LoginRequest {
    loginType: 'password' | 'otp'
    account?: string
    password?: string
    email?: string
    code?: string
    rememberMe?: boolean
    twoFactorCode?: string
}

export interface RegisterRequest {
    username: string // Song：学号
    password: string
    email: string
    code: string // Song：邮箱验证码
    nickname?: string
    avatar?: string
    major?: string
    enrollmentYear?: number
    school?: string
    gender?: number // Song：说明
}

export interface CreatePostRequest {
    title: string
    content: string
    sectionId: number
    coverImage?: string
    tags: string
    status?: number
}

export interface SaveDraftRequest {
    postId?: string
    title?: string
    content?: string
    sectionId?: number
    coverImage?: string
    tags?: string
    images?: string
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
    enrollmentYear?: number
    interestTags?: string
    profileCardTheme?: string
    quickCardTheme?: string
    profileCardBgUrl?: string
    quickCardBgUrl?: string
}

export const ResultCode = {
    SUCCESS: 2000,
    FAILED: 2001
} as const

export interface LoginResponse {
    accessToken?: string
    refreshToken?: string
    twoFactorRequired?: boolean
    twoFactorTicket?: string
}


export interface PostSearchRequest {
    page?: number
    pageSize?: number
    needTotal?: boolean
    keyword?: string
    sectionId?: number
    sectionIds?: number[]
    status?: number
    orderBy?: 'new' | 'hot' | string
    navType?: 'latest' | 'hot' | 'essence' | string
    category?: string | number
    timeRange?: 'TODAY' | 'WEEK' | 'MONTH' | string
    isFeatured?: boolean
    tag?: string
    userId?: string
    likedBy?: string
    collectedBy?: string
    pinnedOnly?: boolean
    cursor?: string
    cursorId?: string
    auditStatus?: string
}

export interface Post {
    id: string
    userId: string
    sectionId: number
    title: string
    content: string
    coverImage?: string
    images: string[]
    tags: string
    isAnonymous: number
    locationName?: string
    status: number
    auditStatus?: string
    rejectReason?: string

    viewCount: number
    likeCount: number
    collectCount: number
    commentCount: number

    createTime: string
    updateTime: string
    lastReplyAt?: string
    lastActivityAt?: string

    isLiked: boolean
    isCollected: boolean
    isPinned?: number // Song：已废弃
    globalPin?: number
    categoryPin?: number
    pinOrder?: number
    pinExpireAt?: string
    isFeatured?: number
    heatScore?: number

    authorName: string
    authorAvatar?: string
    authorRoles?: string[]
    authorBadgeText?: string
    authorBadgeColor?: string
    authorBadgeStyle?: string

    sentimentScore?: number
    sentimentLabel?: string
    sectionName?: string
    trendLevel?: string
    summary?: string
}

export interface Comment {
    id: string
    parentId: string
    content: string
    userId: string
    nickname: string
    roles?: string[]
    userAvatar?: string
    userBadgeText?: string
    userBadgeColor?: string
    userBadgeStyle?: string
    likeCount: number
    collectCount?: number
    createTime: string
    editTime?: string | null
    isLiked?: boolean
    isCollected?: boolean
    children?: Comment[]
    replyUserId?: string
    replyUserNickname?: string
    isAnonymous?: number
    auditStatus?: string
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

