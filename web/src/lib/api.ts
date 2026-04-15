import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { ResultCode } from '@/types'
import { getOrCreateDeviceId } from '@/utils/device'
import { useUserStore } from '@/store/user'

// ─────────────────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────────────────
type RetryConfig = InternalAxiosRequestConfig & {
  _retry?: boolean
  _networkRetryCount?: number
  _skipSign?: boolean
}
type RefreshFailReason = 'no_refresh' | 'invalid' | 'network'
type RefreshResult = { ok: true; token: string } | { ok: false; reason: RefreshFailReason }
type WarmupResult =
  | { ok: true; refreshed: boolean; reason: 'refreshed' | 'skipped' }
  | { ok: false; refreshed: false; reason: RefreshFailReason | 'no_session' }

// ─────────────────────────────────────────────────────────
// 常量
// ─────────────────────────────────────────────────────────
const AUTH_PUBLIC_PATHS = [
  '/auth/login', '/auth/register', '/auth/send-code', '/auth/verify-code',
  '/auth/check-email', '/auth/check-username', '/auth/refresh',
  '/auth/reset-password', '/auth/github/authorize-url', '/auth/github/login',
  '/auth/2fa/verify-login',
]
const REFRESH_AHEAD_MS    = 3 * 60 * 1000  // 过期前3分钟预刷新
const TOKEN_EXPIRE_SKEW_MS = 10 * 1000    // 10秒边界容差
const NETWORK_RETRY_DELAY_MS = 400
const MAX_NETWORK_RETRY_COUNT = 1
const TOAST_DEDUPE_MS = 3000
const RETRYABLE_STATUS = new Set([408, 425, 429, 500, 502, 503, 504])
const SAFE_RETRY_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

// ─────────────────────────────────────────────────────────
// Axios 实例
// ─────────────────────────────────────────────────────────
const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// 专用刷新客户端：不经过主拦截器，避免递归
const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 12000,
  headers: { 'Content-Type': 'application/json' },
})

// ─────────────────────────────────────────────────────────
// Toast 去重
// ─────────────────────────────────────────────────────────
const recentToastMap = new Map<string, number>()
function showErrorMessage(msg: string, key = msg) {
  const now = Date.now()
  if ((recentToastMap.get(key) || 0) + TOAST_DEDUPE_MS > now) return
  recentToastMap.set(key, now)
  ElMessage.error(msg)
}

// ─────────────────────────────────────────────────────────
// Token 工具
// ─────────────────────────────────────────────────────────
function decodeJwtPayload(token: string): Record<string, any> | null {
  try {
    const seg = token.split('.')
    if (seg.length < 2) return null
    const raw = seg[1].replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(seg[1].length / 4) * 4, '=')
    return JSON.parse(atob(raw))
  } catch { return null }
}

function getTokenExpireMs(token: string): number | null {
  const exp = Number(decodeJwtPayload(token)?.exp)
  return Number.isFinite(exp) ? exp * 1000 : null
}

function isTokenExpired(token: string): boolean {
  const exp = getTokenExpireMs(token)
  return exp !== null && exp <= Date.now() + TOKEN_EXPIRE_SKEW_MS
}

function shouldRefreshSoon(token: string): boolean {
  const exp = getTokenExpireMs(token)
  return exp !== null && exp - Date.now() <= REFRESH_AHEAD_MS
}

function getRememberMe(): boolean {
  return localStorage.getItem('remember_me') === 'true' || !!localStorage.getItem('access_token')
}

export function getAccessToken(): string | null {
  const t = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
  if (!t) return null
  if (isTokenExpired(t)) {
    localStorage.removeItem('access_token')
    sessionStorage.removeItem('access_token')
    return null
  }
  return t
}

export function getRefreshToken(): string | null {
  const t = localStorage.getItem('refresh_token') || sessionStorage.getItem('refresh_token')
  if (!t) return null
  if (isTokenExpired(t)) {
    localStorage.removeItem('refresh_token')
    sessionStorage.removeItem('refresh_token')
    return null
  }
  return t
}

function persistTokens(accessToken: string, refreshToken: string) {
  const rememberMe = getRememberMe()
  const keep = rememberMe ? localStorage : sessionStorage
  const drop = rememberMe ? sessionStorage : localStorage
  keep.setItem('access_token', accessToken)
  keep.setItem('refresh_token', refreshToken)
  if (rememberMe) keep.setItem('remember_me', 'true')
  drop.removeItem('access_token')
  drop.removeItem('refresh_token')
  try {
    const store = useUserStore()
    store.accessToken = accessToken
    store.refreshToken = refreshToken
  } catch { /* store 可能未初始化 */ }
}

export function clearAuthStorage() {
  ;['access_token', 'refresh_token', 'user_id', 'remember_me'].forEach(k => {
    localStorage.removeItem(k)
    sessionStorage.removeItem(k)
  })
  try { useUserStore().logout() } catch { /* ignore */ }
}

export function hasAuthSession(): boolean {
  return Boolean(getAccessToken() || getRefreshToken())
}

// ─────────────────────────────────────────────────────────
// 单例刷新锁：全局唯一一个刷新 Promise，所有并发复用
// ─────────────────────────────────────────────────────────
let _refreshPromise: Promise<RefreshResult> | null = null

export async function refreshTokens(): Promise<RefreshResult> {
  if (_refreshPromise) return _refreshPromise
  _refreshPromise = _doRefresh().finally(() => { _refreshPromise = null })
  return _refreshPromise
}

async function _doRefresh(): Promise<RefreshResult> {
  const rt = getRefreshToken()
  if (!rt) return { ok: false, reason: 'no_refresh' }
  try {
    const resp = await refreshClient.post('/auth/refresh',
      { refreshToken: rt },
      { headers: { 'X-Device-Id': getOrCreateDeviceId() } }
    )
    const res = resp?.data
    if (res?.code === ResultCode.SUCCESS && res?.data?.accessToken && res?.data?.refreshToken) {
      persistTokens(res.data.accessToken, res.data.refreshToken)
      return { ok: true, token: res.data.accessToken }
    }
    return { ok: false, reason: 'invalid' }
  } catch (e: any) {
    return { ok: false, reason: e?.response ? 'invalid' : 'network' }
  }
}

export async function warmupSession(opts: { force?: boolean; silent?: boolean } = {}): Promise<WarmupResult> {
  const access = getAccessToken()
  const refresh = getRefreshToken()
  if (!access && !refresh) return { ok: false, refreshed: false, reason: 'no_session' }
  if (!opts.force && access && !shouldRefreshSoon(access)) return { ok: true, refreshed: false, reason: 'skipped' }
  const result = await refreshTokens()
  if (result.ok) return { ok: true, refreshed: true, reason: 'refreshed' }
  if (!opts.silent && result.reason !== 'network') showErrorMessage('登录状态已失效，请重新登录', 'auth-expired')
  return { ok: false, refreshed: false, reason: result.reason }
}
// ─────────────────────────────────────────────────────────
// 并发 401 队列
// ─────────────────────────────────────────────────────────
let _isRefreshing = false
let _refreshQueue: Array<{ resolve: (token: string) => void; reject: (e: unknown) => void }> = []

function _enqueueRefresh(): Promise<string> {
  return new Promise((resolve, reject) => _refreshQueue.push({ resolve, reject }))
}
function _flushQueue(token: string) { _refreshQueue.forEach(p => p.resolve(token)); _refreshQueue = [] }
function _rejectQueue(e: unknown) { _refreshQueue.forEach(p => p.reject(e)); _refreshQueue = [] }

// ─────────────────────────────────────────────────────────
// 辅助函数
// ─────────────────────────────────────────────────────────
function normalizePath(config: InternalAxiosRequestConfig): string {
  const raw = config.url || ''
  if (raw.startsWith('http://') || raw.startsWith('https://')) {
    try { return new URL(raw).pathname } catch { /* ignore */ }
  }
  return raw.startsWith('/api/') ? raw.slice(4) : raw
}

function isPublicAuthPath(path: string) {
  return AUTH_PUBLIC_PATHS.some(p => path.startsWith(p))
}

function needSign(config: InternalAxiosRequestConfig, token: string | null): boolean {
  if (!token) return false
  const method = String(config.method || 'get').toUpperCase()
  if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) return false
  return !isPublicAuthPath(normalizePath(config))
}

function applyAuthHeaders(config: RetryConfig) {
  const token = getAccessToken()
  const path = normalizePath(config)
  if (token && !isPublicAuthPath(path)) {
    config.headers['Authorization'] = `Bearer ${token}`
  } else {
    delete config.headers['Authorization']
  }
  config.headers['X-Device-Id'] = getOrCreateDeviceId()
}

function randomNonce() {
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    const b = new Uint8Array(16); crypto.getRandomValues(b)
    return Array.from(b).map(x => x.toString(16).padStart(2, '0')).join('')
  }
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

async function sha256Hex(payload: string): Promise<string> {
  if (typeof crypto === 'undefined' || !crypto.subtle) return payload
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(payload))
  return Array.from(new Uint8Array(buf)).map(b => b.toString(16).padStart(2, '0')).join('')
}

function sleep(ms: number) { return new Promise<void>(r => setTimeout(r, ms)) }

function redirectToLogin() {
  if (typeof window === 'undefined' || window.location.pathname.startsWith('/auth')) return
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const p = new URLSearchParams({ type: 'login' })
  if (redirect && redirect !== '/') p.set('redirect', redirect)
  window.location.href = `/auth?${p.toString()}`
}

// ─────────────────────────────────────────────────────────
// 请求拦截器
// ─────────────────────────────────────────────────────────
api.interceptors.request.use(async (config) => {
  const path = normalizePath(config)
  const isPublic = isPublicAuthPath(path)
  let token = getAccessToken()

  if (!isPublic) {
    if (_refreshPromise) {
      // 有正在进行的刷新，等它完成再用新 token
      await _refreshPromise
      token = getAccessToken()
    } else if (!token && getRefreshToken()) {
      // access token 过期，立即刷新（阻塞此请求）
      const result = await refreshTokens()
      if (result.ok) token = getAccessToken()
    } else if (token && shouldRefreshSoon(token)) {
      // 快过期，后台静默预刷新（不阻塞当前请求）
      refreshTokens().catch(() => {})
    }
  }

  config.headers['X-Device-Id'] = getOrCreateDeviceId()
  if (token && !isPublic) {
    config.headers['Authorization'] = `Bearer ${token}`
  } else {
    delete config.headers['Authorization']
  }

  // 请求签名（跳过重试请求）
  if (needSign(config, token) && !(config as RetryConfig)._skipSign) {
    const method = String(config.method || 'get').toUpperCase()
    const timestamp = String(Date.now())
    const nonce = randomNonce()
    const deviceId = getOrCreateDeviceId()
    const sig = await sha256Hex(`${method}\n${path}\n${timestamp}\n${nonce}\n${deviceId}\n${token}`)
    config.headers['X-Request-Timestamp'] = timestamp
    config.headers['X-Request-Nonce'] = nonce
    config.headers['X-Request-Signature'] = sig
  }
  return config
}, (error) => Promise.reject(error))

// ─────────────────────────────────────────────────────────
// 响应拦截器
// ─────────────────────────────────────────────────────────
api.interceptors.response.use(
  (response) => {
    const res = response.data
    if (response.config.responseType === 'blob' || res instanceof Blob) return res
    if (res?.code && res.code !== ResultCode.SUCCESS) {
      const err = new Error(res.message || '请求失败') as any
      err._bizCode = res.code
      err._bizData = res.data
      return Promise.reject(err)
    }
    return res
  },
  async (error) => {
    if (error?.code === 'ERR_CANCELED') return Promise.reject(error)

    const originalRequest = error?.config as RetryConfig | undefined
    const status = error?.response?.status

    // ── 401 无感刷新 ──────────────────────────────────────
    if (status === 401 && originalRequest && !originalRequest._retry) {
      const isPublic = isPublicAuthPath(normalizePath(originalRequest))
      if (!isPublic) {
        // 标记已重试，防止无限循环
        originalRequest._retry = true
        originalRequest._skipSign = true  // 重试时跳过重签名

        if (_isRefreshing) {
          // 刷新进行中：挂入队列，等待新 token 后重放
          try {
            await _enqueueRefresh()
            applyAuthHeaders(originalRequest)
            return api(originalRequest)
          } catch {
            return Promise.reject(error)
          }
        }

        _isRefreshing = true
        try {
          const result = await refreshTokens()
          if (result.ok) {
            // 刷新成功：唤醒队列 + 重放当前请求
            _flushQueue(result.token)
            applyAuthHeaders(originalRequest)
            return api(originalRequest)
          }
          // 刷新失败：拒绝队列中所有挂起请求
          _rejectQueue(new Error('Token refresh failed'))
          if (result.reason === 'network') {
            showErrorMessage('网络异常，登录校验失败，请稍后重试', 'auth-refresh-network')
            return Promise.reject(error)
          }
          // token 真正失效 → 清除 session 并跳转
          showErrorMessage('登录已过期，请重新登录', 'auth-expired')
          clearAuthStorage()
          redirectToLogin()
          return Promise.reject(error)
        } finally {
          _isRefreshing = false
        }
      }
    }

    // ── 安全方法网络重试 ──────────────────────────────────
    if (originalRequest && !originalRequest._retry) {
      const retryCount = originalRequest._networkRetryCount || 0
      const method = String(originalRequest.method || 'get').toUpperCase()
      const isRetryable = SAFE_RETRY_METHODS.has(method)
        && retryCount < MAX_NETWORK_RETRY_COUNT
        && (!error?.response || RETRYABLE_STATUS.has(error.response.status))

      if (isRetryable) {
        originalRequest._networkRetryCount = retryCount + 1
        if (hasAuthSession()) await warmupSession({ force: !getAccessToken(), silent: true })
        applyAuthHeaders(originalRequest)
        await sleep(NETWORK_RETRY_DELAY_MS * (retryCount + 1))
        try { return await api(originalRequest) } catch (retryErr) { error = retryErr }
      }
    }

    // ── 错误提示 ──────────────────────────────────────────
    const errMsg = error?.response?.data?.message
      || (error?.code === 'ECONNABORTED' ? '请求超时，请重试' : null)
      || (!error?.response ? '网络连接异常，请检查网络' : null)
    if (errMsg) showErrorMessage(errMsg, error?.code || errMsg)

    return Promise.reject(error)
  }
)

export default api
