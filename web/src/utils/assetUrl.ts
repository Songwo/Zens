const DEFAULT_PUBLIC_ORIGIN = 'https://www.allinsong.top'
const PUBLIC_ORIGIN = (import.meta.env.VITE_WEB_BASE_URL || DEFAULT_PUBLIC_ORIGIN).replace(/\/$/, '')

const RELATIVE_PUBLIC_PATH = /^(\/?(?:uploads|static|icons|assets)\/|\/?logo(?:-horizontal)?\.png)/i

export function resolvePublicAssetUrl(value?: string | null) {
  const raw = String(value || '').trim()
  if (!raw || raw === 'null' || raw === 'undefined') return ''

  if (RELATIVE_PUBLIC_PATH.test(raw)) {
    const normalizedPath = raw.startsWith('/') ? raw : `/${raw}`
    return `${PUBLIC_ORIGIN}${normalizedPath}`
  }

  try {
    const url = new URL(raw)
    if (url.hostname === 'allinsong.top' && RELATIVE_PUBLIC_PATH.test(url.pathname)) {
      url.hostname = 'www.allinsong.top'
      return url.toString()
    }
    return url.toString()
  } catch {
    return ''
  }
}

export function publicAssetBackground(value?: string | null) {
  const url = resolvePublicAssetUrl(value)
  return url ? `url("${url}")` : ''
}
