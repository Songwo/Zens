import { hasAuthSession, warmupSession } from '@/lib/api'
import { shouldReduceBackgroundWork } from '@/utils/network'
import { emitNotificationUnreadSync } from '@/utils/notificationSync'
import { wsClient } from '@/utils/websocket'

const IDLE_RESUME_THRESHOLD_MS = 5 * 60 * 1000
const HIDDEN_RESUME_THRESHOLD_MS = 45 * 1000
const WARMUP_THROTTLE_MS = 45 * 1000
const PERIODIC_WARMUP_MS = 4 * 60 * 1000

let initialized = false
let cleanupHandler: (() => void) | null = null

export function initSessionResilience() {
  if (typeof window === 'undefined') {
    return () => {}
  }

  if (initialized && cleanupHandler) {
    return cleanupHandler
  }

  let lastActivityAt = Date.now()
  let lastHiddenAt = 0
  let lastWarmupAt = 0
  let warmupPromise: Promise<void> | null = null

  const markActivity = () => {
    lastActivityAt = Date.now()
  }

  const runWarmup = (force = false) => {
    if (!hasAuthSession()) {
      return Promise.resolve()
    }
    if (!force && shouldReduceBackgroundWork()) {
      return Promise.resolve()
    }

    const now = Date.now()
    if (!force && now - lastWarmupAt < WARMUP_THROTTLE_MS) {
      return warmupPromise || Promise.resolve()
    }
    if (warmupPromise) {
      return warmupPromise
    }

    lastWarmupAt = now
    warmupPromise = (async () => {
      const result = await warmupSession({ force, silent: true })
      if (result.ok) {
        emitNotificationUnreadSync({ forceRefresh: true })
        wsClient.resumeConnection(force)
      }
    })()
      .catch(() => {
        // ignore recovery failures and let request interceptor handle the next explicit request
      })
      .finally(() => {
        warmupPromise = null
      })

    return warmupPromise
  }

  const maybeWarmupOnResume = (force = false) => {
    const now = Date.now()
    const idleDuration = now - lastActivityAt
    const hiddenDuration = lastHiddenAt ? now - lastHiddenAt : 0

    if (force || idleDuration >= IDLE_RESUME_THRESHOLD_MS || hiddenDuration >= HIDDEN_RESUME_THRESHOLD_MS) {
      void runWarmup(true)
    }
  }

  const handleVisibilityChange = () => {
    if (document.hidden) {
      lastHiddenAt = Date.now()
      return
    }
    maybeWarmupOnResume(true)
  }

  const handleFocus = () => {
    maybeWarmupOnResume(false)
  }

  const handleOnline = () => {
    void runWarmup(true)
  }

  const handlePageShow = (event: PageTransitionEvent) => {
    if (event.persisted) {
      void runWarmup(true)
    }
  }

  const handlePointerResume = () => {
    const idleDuration = Date.now() - lastActivityAt
    markActivity()
    if (idleDuration >= IDLE_RESUME_THRESHOLD_MS) {
      void runWarmup(false)
    }
  }

  const activityEvents: Array<[keyof WindowEventMap, EventListenerOrEventListenerObject, AddEventListenerOptions?]> = [
    ['pointerdown', handlePointerResume, { passive: true }],
    ['keydown', markActivity],
    ['focus', handleFocus],
    ['online', handleOnline],
    ['pageshow', handlePageShow as EventListener],
  ]

  activityEvents.forEach(([eventName, handler, options]) => {
    window.addEventListener(eventName, handler, options)
  })
  document.addEventListener('visibilitychange', handleVisibilityChange)

  const timer = window.setInterval(() => {
    if (!document.hidden && !shouldReduceBackgroundWork()) {
      void runWarmup(false)
    }
  }, PERIODIC_WARMUP_MS)

  cleanupHandler = () => {
    window.clearInterval(timer)
    activityEvents.forEach(([eventName, handler]) => {
      window.removeEventListener(eventName, handler)
    })
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    initialized = false
    cleanupHandler = null
  }

  initialized = true
  return cleanupHandler
}
