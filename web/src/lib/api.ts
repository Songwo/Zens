import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { ResultCode } from '@/types'
import { getOrCreateDeviceId } from '@/utils/device'
import { useUserStore } from '@/store/user'

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean }
type RefreshFailReason = 'no_refresh' | 'invalid' | 'network'
type RefreshResult = { ok: true } | { ok: false; reason: RefreshFailReason }

const api = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const AUTH_PUBLIC_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/send-code',
  '/auth/verify-code',
  '/auth/check-email',
  '/auth/check-username',
  '/auth/refresh',
  '/auth/reset-password',
  '/auth/github/authorize-url',
  '/auth/github/login',
  '/auth/2fa/verify-login',
]
const REFRESH_AHEAD_MS = 2 * 60 * 1000
const TOKEN_EXPIRE_SKEW_MS = 5 * 1000

function clearTokenValue(tokenKey: 'access_token' | 'refresh_token') {
  localStorage.removeItem(tokenKey)
  sessionStorage.removeItem(tokenKey)
}

function getAccessToken() {
  const token = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
  if (!token) {
    return null
  }
  if (isTokenExpired(token)) {
    clearTokenValue('access_token')
    return null
  }
  return token
}

function getRefreshToken() {
  const token = localStorage.getItem('refresh_token') || sessionStorage.getItem('refresh_token')
  if (!token) {
    return null
  }
  if (isTokenExpired(token)) {
    clearTokenValue('refresh_token')
    return null
  }
  return token
}

function persistTokens(accessToken: string, refreshToken: string) {
  const rememberMe = localStorage.getItem('remember_me') === 'true'
  if (rememberMe) {
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refreshToken)
    sessionStorage.removeItem('access_token')
    sessionStorage.removeItem('refresh_token')
  } else {
    sessionStorage.setItem('access_token', accessToken)
    sessionStorage.setItem('refresh_token', refreshToken)
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  try {
    const userStore = useUserStore()
    userStore.accessToken = accessToken
    userStore.refreshToken = refreshToken
  } catch {
    // Song：说明
  }
}

function clearAuthStorage() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user_id')
  localStorage.removeItem('remember_me')
  sessionStorage.removeItem('access_token')
  sessionStorage.removeItem('refresh_token')
  sessionStorage.removeItem('user_id')
  try {
    const userStore = useUserStore()
    userStore.logout()
  } catch {
    // Song：说明
  }
}

function randomNonce() {
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('')
  }
  return `${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`
}

async function sha256Hex(payload: string) {
  if (typeof crypto === 'undefined' || !crypto.subtle) {
    return payload
  }
  const data = new TextEncoder().encode(payload)
  const hashBuffer = await crypto.subtle.digest('SHA-256', data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}

function normalizePath(config: InternalAxiosRequestConfig) {
  const raw = config.url || ''
  if (!raw) return '/'
  try {
    if (raw.startsWith('http://') || raw.startsWith('https://')) {
      const url = new URL(raw)
      return url.pathname || '/'
    }
  } catch {
    // Song：说明
  }
  return raw.startsWith('/api/') ? raw.slice(4) : raw
}

function isPublicAuthPath(path: string) {
  return AUTH_PUBLIC_PATHS.some((item) => path.startsWith(item))
}

function needSign(config: InternalAxiosRequestConfig, token: string | null) {
  if (!token) return false
  const method = String(config.method || 'get').toUpperCase()
  const isMutating = method === 'POST' || method === 'PUT' || method === 'PATCH' || method === 'DELETE'
  if (!isMutating) return false
  const path = normalizePath(config)
  return !isPublicAuthPath(path)
}

function decodeJwtExpireAtMs(token: string) {
  try {
    const segments = token.split('.')
    if (segments.length < 2) return null
    const payloadRaw = segments[1]
    if (!payloadRaw) return null
    const payload = payloadRaw
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(Math.ceil(payloadRaw.length / 4) * 4, '=')
    const json = atob(payload)
    const parsed = JSON.parse(json)
    const exp = Number(parsed?.exp)
    if (!Number.isFinite(exp)) return null
    return exp * 1000
  } catch {
    return null
  }
}

function shouldRefreshSoon(accessToken: string) {
  const expAt = decodeJwtExpireAtMs(accessToken)
  if (!expAt) return false
  return expAt - Date.now() <= REFRESH_AHEAD_MS
}

function isTokenExpired(token: string) {
  const expAt = decodeJwtExpireAtMs(token)
  if (!expAt) {
    return false
  }
  return expAt <= Date.now() + TOKEN_EXPIRE_SKEW_MS
}

let refreshingPromise: Promise<RefreshResult> | null = null

async function refreshTokens() {
  if (refreshingPromise) {
    return refreshingPromise
  }
  refreshingPromise = (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      return { ok: false, reason: 'no_refresh' } as const
    }
    try {
      const deviceId = getOrCreateDeviceId()
      const resp = await refreshClient.post('/auth/refresh', { refreshToken }, {
        headers: {
          'X-Device-Id': deviceId,
        },
      })
      const res = resp?.data
      if (res?.code === ResultCode.SUCCESS && res?.data?.accessToken && res?.data?.refreshToken) {
        persistTokens(res.data.accessToken, res.data.refreshToken)
        return { ok: true } as const
      }
      return { ok: false, reason: 'invalid' } as const
    } catch (e: any) {
      if (e?.response) {
        return { ok: false, reason: 'invalid' } as const
      }
      return { ok: false, reason: 'network' } as const
    } finally {
      refreshingPromise = null
    }
  })()
  return refreshingPromise
}

api.interceptors.request.use(
  async (config) => {
    let token = getAccessToken()
    const deviceId = getOrCreateDeviceId()
    const path = normalizePath(config)
    const isPublicAuthRequest = isPublicAuthPath(path)

    if (!isPublicAuthRequest) {
      if (token && shouldRefreshSoon(token)) {
        const refreshResult = await refreshTokens()
        if (refreshResult.ok) {
          token = getAccessToken()
        }
      } else if (!token && getRefreshToken()) {
        const refreshResult = await refreshTokens()
        if (refreshResult.ok) {
          token = getAccessToken()
        }
      }
    }

    config.headers['X-Device-Id'] = deviceId
    if (token && !isPublicAuthRequest) {
      config.headers.Authorization = `Bearer ${token}`
    } else if (config.headers.Authorization) {
      delete config.headers.Authorization
    }

    if (needSign(config, token)) {
      const method = String(config.method || 'get').toUpperCase()
      const path = normalizePath(config)
      const timestamp = String(Date.now())
      const nonce = randomNonce()
      const signaturePayload = `${method}\n${path}\n${timestamp}\n${nonce}\n${deviceId}\n${token}`
      const signature = await sha256Hex(signaturePayload)
      config.headers['X-Request-Timestamp'] = timestamp
      config.headers['X-Request-Nonce'] = nonce
      config.headers['X-Request-Signature'] = signature
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => {
    const res = response.data

    if (response.config.responseType === 'blob' || res instanceof Blob) {
      return res
    }

    if (res.code && res.code !== ResultCode.SUCCESS) {
      const errorMsg = res.message || '请求失败'
      return Promise.reject(new Error(errorMsg))
    }

    return res
  },
  async (error) => {
    const response = error?.response
    const originalRequest = error?.config as RetryConfig | undefined

    if (response && response.status === 401 && originalRequest) {
      const isPublicAuthRequest = isPublicAuthPath(normalizePath(originalRequest))

      if (!isPublicAuthRequest && !originalRequest._retry) {
        originalRequest._retry = true
        const refreshResult = await refreshTokens()
        if (refreshResult.ok) {
          const latest = getAccessToken()
          originalRequest.headers = originalRequest.headers || {}
          if (latest) {
            originalRequest.headers.Authorization = `Bearer ${latest}`
          }
          originalRequest.headers['X-Device-Id'] = getOrCreateDeviceId()
          return api(originalRequest)
        }
        if (refreshResult.reason === 'network') {
          ElMessage.error('网络异常，登录状态校验失败，请稍后重试')
          return Promise.reject(error)
        }
      }

      if (!window.location.pathname.startsWith('/auth')) {
        const msg = '登录已过期，请重新登录'
        ElMessage.error(msg)
        clearAuthStorage()
        window.location.href = '/auth?type=login'
      }
      return Promise.reject(error)
    }

    if (response) {
      ElMessage.error(response.data?.message || '请求失败，请稍后重试')
    } else {
      ElMessage.error('网络连接异常')
    }
    return Promise.reject(error)
  }
)

export default api
