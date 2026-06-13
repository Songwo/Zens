const BASE62_CHARS = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'

/**
 * 将 POST_ 开头的雪花 ID 转换为 Base62 简短格式。
 * 例如：POST_1772600781115578881 -> p3qWbZ92Xw
 */
export function encodePostId(postId: string | undefined | null): string {
  if (!postId) return ''
  const idStr = String(postId)
  if (!idStr.startsWith('POST_')) {
    return idStr
  }
  const numericStr = idStr.replace('POST_', '')
  try {
    let num = BigInt(numericStr)
    if (num === 0n) return 'p0'
    let result = ''
    while (num > 0n) {
      const rem = num % 62n
      result = BASE62_CHARS[Number(rem)] + result
      num = num / 62n
    }
    return 'p' + result
  } catch (e) {
    return idStr
  }
}

/**
 * 将 Base62 简短格式还原为正常的 POST_ 雪花 ID。
 * 例如：p3qWbZ92Xw -> POST_1772600781115578881
 */
export function decodePostId(shortId: string | undefined | null): string {
  if (!shortId) return ''
  const shortIdStr = String(shortId)
  if (!shortIdStr.startsWith('p')) {
    return shortIdStr
  }
  const base62Str = shortIdStr.substring(1)
  try {
    let num = 0n
    for (let i = 0; i < base62Str.length; i++) {
      const char = base62Str.charAt(i)
      const value = BASE62_CHARS.indexOf(char)
      if (value === -1) {
        return shortIdStr
      }
      num = num * 62n + BigInt(value)
    }
    return 'POST_' + num.toString()
  } catch (e) {
    return shortIdStr
  }
}

/**
 * 将纯数字的用户雪花 ID 转换为 Base62 简短格式。
 * 例如：1772600781115578881 -> u3qWbZ92Xw
 */
export function encodeUserId(userId: string | number | undefined | null): string {
  if (!userId) return ''
  const idStr = String(userId).trim()
  if (!/^\d+$/.test(idStr)) {
    return idStr
  }
  try {
    let num = BigInt(idStr)
    if (num === 0n) return 'u0'
    let result = ''
    while (num > 0n) {
      const rem = num % 62n
      result = BASE62_CHARS[Number(rem)] + result
      num = num / 62n
    }
    return 'u' + result
  } catch (e) {
    return idStr
  }
}

/**
 * 将 Base62 简短格式还原为正常的纯数字用户雪花 ID。
 * 例如：u3qWbZ92Xw -> 1772600781115578881
 */
export function decodeUserId(shortId: string | undefined | null): string {
  if (!shortId) return ''
  const shortIdStr = String(shortId).trim()
  if (!shortIdStr.startsWith('u')) {
    return shortIdStr
  }
  const base62Str = shortIdStr.substring(1)
  try {
    let num = 0n
    for (let i = 0; i < base62Str.length; i++) {
      const char = base62Str.charAt(i)
      const value = BASE62_CHARS.indexOf(char)
      if (value === -1) {
        return shortIdStr
      }
      num = num * 62n + BigInt(value)
    }
    return num.toString()
  } catch (e) {
    return shortIdStr
  }
}

/**
 * 将评论 ID 转换为短码，分享链接只暴露 c 开头的短参数。
 * 数字雪花 ID 使用 Base62；非数字兜底为 base64url 文本编码，避免原文出现在 URL。
 */
export function encodeCommentId(commentId: string | number | undefined | null): string {
  if (commentId === undefined || commentId === null) return ''
  const idStr = String(commentId).trim()
  if (!idStr) return ''
  if (/^\d+$/.test(idStr)) {
    try {
      let num = BigInt(idStr)
      if (num === 0n) return 'c0'
      let result = ''
      while (num > 0n) {
        const rem = num % 62n
        result = BASE62_CHARS[Number(rem)] + result
        num = num / 62n
      }
      return 'c' + result
    } catch {
      return 'cx' + encodeBase64UrlText(idStr)
    }
  }
  return 'cx' + encodeBase64UrlText(idStr)
}

/**
 * 将评论短码还原为内部评论 ID。兼容旧链接：非 c/cx 开头时按原值返回。
 */
export function decodeCommentId(shortId: string | undefined | null): string {
  if (!shortId) return ''
  const shortIdStr = String(shortId).trim()
  if (shortIdStr.startsWith('cx')) {
    return decodeBase64UrlText(shortIdStr.substring(2)) || shortIdStr
  }
  if (!shortIdStr.startsWith('c')) {
    return shortIdStr
  }
  const base62Str = shortIdStr.substring(1)
  try {
    let num = 0n
    for (let i = 0; i < base62Str.length; i++) {
      const char = base62Str.charAt(i)
      const value = BASE62_CHARS.indexOf(char)
      if (value === -1) {
        return shortIdStr
      }
      num = num * 62n + BigInt(value)
    }
    return num.toString()
  } catch {
    return shortIdStr
  }
}

function encodeBase64UrlText(value: string): string {
  try {
    const bytes = new TextEncoder().encode(value)
    let binary = ''
    bytes.forEach(byte => {
      binary += String.fromCharCode(byte)
    })
    return btoa(binary)
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/g, '')
  } catch {
    return value
  }
}

function decodeBase64UrlText(value: string): string {
  try {
    const padded = value.replace(/-/g, '+').replace(/_/g, '/')
      .padEnd(Math.ceil(value.length / 4) * 4, '=')
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, char => char.charCodeAt(0))
    return new TextDecoder().decode(bytes)
  } catch {
    return ''
  }
}
