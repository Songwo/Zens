import api from '@/lib/api'
import type { LoginRequest, LoginResponse, RegisterRequest, Result } from '@/types'

export const authApi = {
    // Get Captcha Image (Blob)
    getCaptcha(uuid: string) {
        return api.get(`/auth/captcha?uuid=${uuid}`, {
            responseType: 'blob'
        })
    },

    // Login
    login(data: LoginRequest) {
        return api.post<any, Result<LoginResponse>>('/auth/login', data)
    },

    // Register
    register(data: RegisterRequest) {
        return api.post<any, Result<void>>('/auth/register', data)
    },

    // Send Email Code
    sendCode(email: string) {
        return api.post<any, Result<void>>('/auth/send-code', { email })
    }
}
