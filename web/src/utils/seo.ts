import type { RouteLocationNormalizedLoaded } from 'vue-router'

const SITE_NAME = 'Zens 开发者社区'
const DEFAULT_DESCRIPTION = 'Zens 开发者社区，连接技术讨论、内容沉淀、团队协作与社区增长。'

function ensureMeta(selector: string, createAttrs: Record<string, string>) {
  let element = document.head.querySelector<HTMLMetaElement>(selector)
  if (!element) {
    element = document.createElement('meta')
    Object.entries(createAttrs).forEach(([key, value]) => element?.setAttribute(key, value))
    document.head.appendChild(element)
  }
  return element
}

export function setPageMeta(options: { title?: string; description?: string; image?: string; url?: string }) {
  if (typeof document === 'undefined') return

  const title = options.title ? `${options.title} - ${SITE_NAME}` : SITE_NAME
  const description = options.description || DEFAULT_DESCRIPTION
  const url = options.url || window.location.href
  const image = options.image || `${window.location.origin}/logo-horizontal.png`

  document.title = title

  const descriptionMeta = ensureMeta('meta[name="description"]', { name: 'description' })
  descriptionMeta.setAttribute('content', description)

  const ogTitle = ensureMeta('meta[property="og:title"]', { property: 'og:title' })
  ogTitle.setAttribute('content', title)

  const ogDescription = ensureMeta('meta[property="og:description"]', { property: 'og:description' })
  ogDescription.setAttribute('content', description)

  const ogType = ensureMeta('meta[property="og:type"]', { property: 'og:type' })
  ogType.setAttribute('content', 'website')

  const ogUrl = ensureMeta('meta[property="og:url"]', { property: 'og:url' })
  ogUrl.setAttribute('content', url)

  const ogImage = ensureMeta('meta[property="og:image"]', { property: 'og:image' })
  ogImage.setAttribute('content', image)
}

export function setRouteMeta(route: RouteLocationNormalizedLoaded) {
  const title = typeof route.meta.title === 'string' ? route.meta.title : ''
  const description = typeof route.meta.description === 'string' ? route.meta.description : ''
  setPageMeta({ title, description })
}
