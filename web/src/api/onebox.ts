import api from '@/lib/api'
import type { Result } from '@/types'

export interface OneboxPreview {
    url: string
    title?: string
    description?: string
    image?: string
    siteName?: string
    /** provider: github/youtube/bilibili/twitter/generic */
    provider: string
    embedId?: string
    embeddable: boolean
    cached?: boolean
}

export const oneboxApi = {
    /** 预览某个 URL 的 OG 元数据（公开接口，缓存 1 小时） */
    preview(url: string) {
        return api.get<any, Result<OneboxPreview>>('/onebox/preview', { params: { url } })
    },
}
