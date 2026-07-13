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
    trusted?: boolean
    createTime?: string
    updateTime?: string
}

export interface SsoClientPublicInfo {
    clientId: string
    clientName: string
    description?: string
    logoUrl?: string
    trusted?: boolean
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

    /** 一键创建或修复积分商城 SSO 应用 */
    upsertPointShopClient() {
        return api.post<any, Result<SsoClientItem>>('/sso/clients/presets/point-shop')
    },

    /** 一键创建或修复抽奖站 SSO 应用 */
    upsertLotteryClient() {
        return api.post<any, Result<SsoClientItem>>('/sso/clients/presets/lottery')
    },

    /** 一键创建或修复 CDK 空投站 SSO 应用 */
    upsertCdkAirdropClient() {
        return api.post<any, Result<SsoClientItem>>('/sso/clients/presets/cdk-airdrop')
    },

    /** 一键创建或修复公益 API 站 OAuth2 应用 */
    upsertPublicApiClient() {
        return api.post<any, Result<SsoClientItem>>('/sso/clients/presets/public-api')
    },

    /** 获取应用公开信息（授权页用） */
    getPublicClientInfo(clientId: string) {
        return api.get<any, Result<SsoClientPublicInfo>>(`/sso/clients/public/${clientId}`)
    },

    /** 生成 SSO Token（已登录用户调用） */
    authorize(data: { clientId: string; redirectUri: string }) {
        return api.post<any, Result<{ ssoToken: string }>>('/sso/authorize', data)
    },

    /** 生成标准 OAuth2 一次性授权码（路径名保留 oidc 兼容）。 */
    authorizeOidc(data: { clientId: string; redirectUri: string; state: string; scope?: string }) {
        return api.post<any, Result<{ redirectUrl: string }>>('/sso/oidc/authorize', data)
    },

    denyOidc(data: { clientId: string; redirectUri: string; state: string }) {
        return api.post<any, Result<{ redirectUrl: string }>>('/sso/oidc/deny', data)
    },
}
