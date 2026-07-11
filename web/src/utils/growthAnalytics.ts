import type { Router } from 'vue-router'

type GrowthEvent = 'page_view' | 'post_read' | 'signup_completed' | 'topics_selected' |
  'post_collected' | 'user_followed' | 'comment_created' | 'post_created' | 'subscription_created'

const uuid = () => crypto.randomUUID().replace(/-/g, '')
const getId = (storage: Storage, key: string) => {
  let value = storage.getItem(key)
  if (!value) { value = uuid(); storage.setItem(key, value) }
  return value
}

const source = () => {
  const campaign = new URLSearchParams(location.search).get('utm_source')
  if (campaign) return campaign.slice(0, 40)
  if (!document.referrer) return 'direct'
  try {
    const host = new URL(document.referrer).hostname
    return host === location.hostname ? 'internal' : host.replace(/^www\./, '').slice(0, 40)
  } catch { return 'direct' }
}

export function trackGrowthEvent(eventName: GrowthEvent, properties: Record<string, string | number | boolean> = {}) {
  if (navigator.doNotTrack === '1') return
  const body = JSON.stringify({
    eventName,
    anonymousId: getId(localStorage, 'zens_analytics_id'),
    sessionId: getId(sessionStorage, 'zens_analytics_session'),
    route: location.pathname,
    source: source(),
    properties,
  })
  const endpoint = `${(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')}/api/growth/events`
  const token = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
  if (!token && navigator.sendBeacon) navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
  else fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body,
    keepalive: true,
  }).catch(() => {})
}

export function installGrowthAnalytics(router: Router) {
  router.afterEach((to) => {
    trackGrowthEvent('page_view')
    if (to.name === 'topic-detail') {
      window.setTimeout(() => trackGrowthEvent('post_read', { postId: String(to.params.id), readSeconds: 20 }), 20_000)
    }
  })
  window.addEventListener('zens:growth', ((event: CustomEvent<{ name: GrowthEvent; properties?: Record<string, string | number | boolean> }>) => {
    if (event.detail?.name) trackGrowthEvent(event.detail.name, event.detail.properties)
  }) as EventListener)
}
