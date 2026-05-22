import api from '@/lib/api'
import type { Result } from '@/types'

export interface SsoClientItem {
    id: string
    clientId: string
    clientName: string
    clientSecret?: string
    clientSecretMasked?: string
    redirectUri: string
    description?: string
    logoUrl?: string
    enabled: boolean
    createTime?: string
    updateTime?: string
}

export interface SsoClientPublicInfo {
    clientId: string
    clientName: string
    description?: string
    logoUrl?: string
}

export const ssoApi = {
    /** 查询所有 SSO 应用 */
    listClients() {
        return api.get<any, Result<SsoClientItem[]>>('/sso/clients')
    },

    /** 创建 SSO 应用 */
    createClient(data: {
        clientId: string
        clientName: string
        redirectUri: string
        description?: string
        logoUrl?: string
    }) {
        return api.post<any, Result<SsoClientItem>>('/sso/clients', data)
    },

    /** 更新 SSO 应用 */
    updateClient(id: string, data: {
        clientName: string
        redirectUri: string
        description?: string
        logoUrl?: string
    }) {
        return api.put<any, Result<string>>(`/sso/clients/${id}`, data)
    },

    /** 删除 SSO 应用 */
    deleteClient(id: string) {
        return api.delete<any, Result<string>>(`/sso/clients/${id}`)
    },

    /** 启用/禁用 */
    toggleClient(id: string, enabled: boolean) {
        return api.post<any, Result<string>>(`/sso/clients/${id}/toggle`, { enabled })
    },

    /** 重置密钥 */
    resetSecret(id: string) {
        return api.post<any, Result<{ clientSecret: string }>>(`/sso/clients/${id}/reset-secret`)
    },

    /** 获取应用公开信息（授权页用） */
    getPublicClientInfo(clientId: string) {
        return api.get<any, Result<SsoClientPublicInfo>>(`/sso/clients/public/${clientId}`)
    },

    /** 生成 SSO Token（已登录用户调用） */
    authorize(data: { clientId: string; redirectUri: string }) {
        return api.post<any, Result<{ ssoToken: string }>>('/sso/authorize', data)
    },
}
