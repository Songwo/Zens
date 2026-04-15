import api from '@/lib/api'
import type { Result } from '@/types'

export const authApi = {
    /**
     * Song：发送邮箱验证码（注册/登录共用）
     * Song：说明
     */
    sendOtp(email: string, _type?: 'email' | 'phone') {
        return api.post<any, Result<string>>('/auth/send-code', { email }, { timeout: 30000 })
    },

    /**
     * Song：说明
     * Song：说明
     */
    verifyOtp(email: string, code: string) {
        return api.post<any, Result<string>>('/auth/verify-code', { email, code })
    },

    /**
     * Song：检查邮箱是否已注册
     * Song：说明
     */
    checkEmail(email: string) {
        return api.post<any, Result<{ exists: boolean; username?: string }>>('/auth/check-email', { email })
    },

    /**
     * Song：检查用户名是否可用
     * Song：说明
     */
    checkUsername(username: string) {
        return api.get<any, Result<{ available: boolean; message?: string }>>('/auth/check-username', { params: { username } })
    },

    /**
     * Song：登录（支持两种方式）
     * Song：说明
     * Song：说明
     */
    login(data: {
        loginType: 'password' | 'otp'
        account?: string
        password?: string
        email?: string
        code?: string
        rememberMe?: boolean
        twoFactorCode?: string
        'cf-turnstile-response': string
    }) {
        return api.post<any, Result<{
            accessToken?: string
            refreshToken?: string
            twoFactorRequired?: boolean
            twoFactorTicket?: string
        }>>('/auth/login', data)
    },

    /**
     * Song：刷新令牌
     */
    refresh(refreshToken: string) {
        return api.post<any, Result<{ accessToken: string; refreshToken: string }>>('/auth/refresh', { refreshToken })
    },

    getGithubAuthorizeUrl() {
        return api.get<any, Result<{ url: string }>>('/auth/github/authorize-url')
    },

    githubLogin(data: { code: string; state: string; rememberMe?: boolean; twoFactorCode?: string }) {
        return api.post<any, Result<{
            accessToken?: string
            refreshToken?: string
            twoFactorRequired?: boolean
            twoFactorTicket?: string
        }>>('/auth/github/login', data)
    },

    verifyTwoFactorLogin(data: { ticket: string; code: string }) {
        return api.post<any, Result<{ accessToken: string; refreshToken: string }>>('/auth/2fa/verify-login', data)
    },

    getTwoFactorSetup() {
        return api.post<any, Result<{
            secret: string
            otpauthUri: string
            qrCodeUrl: string
            expireSeconds: number
        }>>('/auth/2fa/setup')
    },

    enableTwoFactor(data: { code: string }) {
        return api.post<any, Result<string>>('/auth/2fa/enable', data)
    },

    disableTwoFactor(data: { code: string }) {
        return api.post<any, Result<string>>('/auth/2fa/disable', data)
    },

    /**
     * Song：注册
     * Song：说明
     */
    register(data: {
        username: string
        password: string
        email: string
        code: string
        nickname?: string
        school?: string
        major?: string
        grade?: number
        gender?: number
    }) {
        return api.post<any, Result<string>>('/auth/register', data)
    },

    /**
     * Song：别名: 发送验证码
     */
    sendVerificationCode(email: string) {
        return this.sendOtp(email)
    },

    /**
     * 邀请码校验
     */
    validateInviteCode(code: string) {
        return api.get<any, Result<string>>('/invite/validate', { params: { code } })
    },

    /**
     * Song：登出
     */
    logout() {
        return api.post<any, Result<void>>('/auth/logout')
    },

    /**
     * Song：忘记密码 — 重置密码
     * Song：说明
     */
    resetPassword(data: { email: string; code: string; newPassword: string }) {
        return api.post<any, Result<string>>('/auth/reset-password', data)
    },
}
