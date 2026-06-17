import api from '@/lib/api'
import type { Result } from '@/types'

export interface ChangelogItem {
    id: number
    version: string
    title: string
    content: string
    stageNo?: string
    stageLabel?: string
    roadmapStatus?: 'released' | 'building' | 'planned' | string
    highlights?: string
    actionPath?: string
    upgradeEnabled?: number
    upgradeUrl?: string
    timestamp: string
    status: number
    sortOrder: number
    createdAt?: string
    updatedAt?: string
}

export const changelogApi = {
    /**
     * Song：获取已发布的发展历程列表
     */
    getList() {
        return api.get<any, Result<ChangelogItem[]>>('/changelog/list')
    },

    /**
     * Song：获取后台发展历程配置列表 (管理员)
     */
    getAdminList() {
        return api.get<any, Result<ChangelogItem[]>>('/admin/changelog/list')
    },

    /**
     * Song：新增发展历程 (管理员)
     */
    create(data: Partial<ChangelogItem>) {
        return api.post<any, Result<ChangelogItem>>('/changelog', data)
    },

    /**
     * Song：更新发展历程 (管理员)
     */
    update(id: number, data: Partial<ChangelogItem>) {
        return api.put<any, Result<ChangelogItem>>(`/changelog/${id}`, data)
    },

    /**
     * Song：删除发展历程 (管理员)
     */
    delete(id: number) {
        return api.delete<any, Result<void>>(`/changelog/${id}`)
    }
}
