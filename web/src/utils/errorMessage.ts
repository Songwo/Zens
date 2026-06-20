const GENERIC_ERROR_PATTERNS = [
  /^network error$/i,
  /^request failed/i,
  /^timeout of \d+ms exceeded$/i,
  /^failed to fetch$/i,
  /^load failed$/i,
  /^internal server error$/i,
]

const isRecord = (value: unknown): value is Record<string, any> => {
  return !!value && typeof value === 'object'
}

const readMessage = (value: unknown): string => {
  if (typeof value === 'string') return value
  if (!isRecord(value)) return ''
  return String(value.message || value.msg || value.error || '').trim()
}

const sanitizeMessage = (message: string, fallback: string): string => {
  const text = message.trim()
  if (!text) return fallback
  if (GENERIC_ERROR_PATTERNS.some(pattern => pattern.test(text))) return fallback
  if (/(exception|stacktrace|java\.|org\.springframework|traceback)/i.test(text)) return fallback
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

export function getErrorMessage(error: unknown, fallback = '操作未完成，请稍后再试'): string {
  if (!isRecord(error)) return fallback

  const status = Number(error.response?.status)
  const responseData = error.response?.data
  const responseMessage = readMessage(responseData)

  if (responseMessage) {
    return sanitizeMessage(responseMessage, fallback)
  }

  // 业务错误：响应拦截器把 code≠2000 的响应(HTTP 200)转成了合成 Error，
  // 它带 _bizCode 且 message 是后端给的业务提示(如"密码错误")。此时没有 error.response，
  // 但绝不是网络问题——必须优先用这条业务 message，别落到下面的网络兜底。
  if (error._bizCode !== undefined) {
    return sanitizeMessage(readMessage(error), fallback)
  }

  if (error.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }

  // 真正的网络错误：只认 axios 的 ERR_NETWORK / ERR_* 网络码，不再因为"有 message"就误判。
  if (!error.response && (error.code === 'ERR_NETWORK' || error.code === 'ERR_CONNECTION_REFUSED')) {
    return '网络连接异常，请检查网络后重试'
  }

  if (status === 401) return '登录状态已失效，请重新登录'
  if (status === 403) return '没有权限执行此操作'
  if (status === 404) return '请求的内容不存在'
  if (status === 429) return '操作过于频繁，请稍后再试'
  if (status >= 500) return '服务器暂时不可用，请稍后再试'

  return sanitizeMessage(readMessage(error), fallback)
}
