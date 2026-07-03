import { performanceApi, type WebVitalMetric } from '@/api/performance'

type MetricName = 'LCP' | 'CLS' | 'INP' | 'FCP' | 'TTFB'

type ObserverWithBuffered = PerformanceObserverInit & {
  buffered?: boolean
}

const sentKeys = new Set<string>()
const pending: WebVitalMetric[] = []
let flushTimer: number | null = null
let installed = false

const thresholds: Record<MetricName, [number, number]> = {
  LCP: [2500, 4000],
  CLS: [0.1, 0.25],
  INP: [200, 500],
  FCP: [1800, 3000],
  TTFB: [800, 1800],
}

const getRating = (name: MetricName, value: number) => {
  const [good, poor] = thresholds[name]
  if (value <= good) return 'good'
  if (value <= poor) return 'needs-improvement'
  return 'poor'
}

const getRoute = () => `${window.location.pathname}${window.location.search}`

const enqueueMetric = (metric: WebVitalMetric) => {
  const value = Number(metric.value)
  if (!Number.isFinite(value) || value < 0) return

  const payload: WebVitalMetric = {
    ...metric,
    value: Math.round(value * 100) / 100,
    route: metric.route || getRoute(),
    userAgent: navigator.userAgent,
    timestamp: Date.now(),
  }
  const key = `${payload.name}:${payload.route}:${Math.round(payload.value * 100)}`
  if (sentKeys.has(key)) return
  sentKeys.add(key)
  pending.push(payload)

  if (flushTimer !== null) return
  flushTimer = window.setTimeout(flushMetrics, 800)
}

const flushMetrics = () => {
  flushTimer = null
  if (!pending.length) return

  const batch = pending.splice(0, pending.length)
  batch.forEach((metric) => {
    void performanceApi.reportWebVital(metric).catch(() => {
      // Experience metrics are best-effort and must never interrupt the app.
    })
  })
}

const observe = (type: string, callback: PerformanceObserverCallback) => {
  if (typeof PerformanceObserver === 'undefined') return
  try {
    const observer = new PerformanceObserver(callback)
    observer.observe({ type, buffered: true } as ObserverWithBuffered)
    return observer
  } catch {
    return undefined
  }
}

const reportNavigationMetrics = () => {
  const nav = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
  if (!nav) return

  const navigationType = nav.type || 'navigate'
  enqueueMetric({
    name: 'TTFB',
    value: nav.responseStart,
    rating: getRating('TTFB', nav.responseStart),
    navigationType,
  })
}

export function installWebVitals() {
  if (installed || typeof window === 'undefined' || typeof performance === 'undefined') return
  installed = true

  let clsValue = 0
  let lcpValue = 0
  let inpValue = 0

  window.addEventListener('load', () => {
    window.setTimeout(reportNavigationMetrics, 0)
  }, { once: true })

  observe('paint', (list) => {
    list.getEntries().forEach((entry) => {
      if (entry.name === 'first-contentful-paint') {
        enqueueMetric({
          name: 'FCP',
          value: entry.startTime,
          rating: getRating('FCP', entry.startTime),
        })
      }
    })
  })

  observe('largest-contentful-paint', (list) => {
    const entries = list.getEntries()
    const last = entries[entries.length - 1]
    if (last) {
      lcpValue = last.startTime
    }
  })

  observe('layout-shift', (list) => {
    list.getEntries().forEach((entry: any) => {
      if (!entry.hadRecentInput) {
        clsValue += Number(entry.value) || 0
      }
    })
  })

  observe('event', (list) => {
    list.getEntries().forEach((entry: any) => {
      const duration = Number(entry.duration) || 0
      const delay = Number(entry.processingStart || 0) - Number(entry.startTime || 0)
      inpValue = Math.max(inpValue, duration || delay)
    })
  })

  const reportFinalMetrics = () => {
    if (lcpValue > 0) {
      enqueueMetric({ name: 'LCP', value: lcpValue, rating: getRating('LCP', lcpValue) })
    }
    if (clsValue >= 0) {
      enqueueMetric({ name: 'CLS', value: clsValue, rating: getRating('CLS', clsValue) })
    }
    if (inpValue > 0) {
      enqueueMetric({ name: 'INP', value: inpValue, rating: getRating('INP', inpValue) })
    }
    flushMetrics()
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      reportFinalMetrics()
    }
  })
  window.addEventListener('pagehide', reportFinalMetrics)
}
