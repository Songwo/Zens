const DEFAULT_PUBLIC_ORIGIN = 'https://www.allinsong.top'
const PUBLIC_ORIGIN = (import.meta.env.VITE_WEB_BASE_URL || DEFAULT_PUBLIC_ORIGIN).replace(/\/$/, '')
const PUBLIC_HOSTNAME = new URL(PUBLIC_ORIGIN).hostname

const RELATIVE_PUBLIC_PATH = /^(\/?(?:uploads|static|official|icons|assets)\/|\/?logo(?:-horizontal)?\.png)/i

// 稳定业务字段仍可保存语义化路径；前端映射为内容版本 URL 后即可安全使用
// immutable 缓存。替换文件时只需同步更新这里的内容摘要。
const PUBLIC_ASSET_REVISIONS: Record<string, string> = {
  '/official/zens-ops-avatar.webp': 'f2d13162c146',
}

function applyPublicAssetRevision(url: URL) {
  const revision = url.hostname === PUBLIC_HOSTNAME
    ? PUBLIC_ASSET_REVISIONS[url.pathname]
    : undefined
  if (revision && !url.searchParams.has('v')) {
    url.searchParams.set('v', revision)
  }
  return url.toString()
}

export function resolvePublicAssetUrl(value?: string | null) {
  const raw = String(value || '').trim()
  if (!raw || raw === 'null' || raw === 'undefined') return ''

  if (RELATIVE_PUBLIC_PATH.test(raw)) {
    const normalizedPath = raw.startsWith('/') ? raw : `/${raw}`
    return applyPublicAssetRevision(new URL(normalizedPath, PUBLIC_ORIGIN))
  }

  try {
    const url = new URL(raw)
    if (url.hostname === 'allinsong.top' && RELATIVE_PUBLIC_PATH.test(url.pathname)) {
      url.hostname = 'www.allinsong.top'
    }
    return applyPublicAssetRevision(url)
  } catch {
    return ''
  }
}

export function publicAssetBackground(value?: string | null) {
  const url = resolvePublicAssetUrl(value)
  return url ? `url("${url}")` : ''
}
