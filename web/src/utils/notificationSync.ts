export interface NotificationUnreadSyncDetail {
  unreadCount?: number
  delta?: number
  forceRefresh?: boolean
}

const NOTIFICATION_UNREAD_SYNC_EVENT = 'cp:notification-unread-sync'

export function emitNotificationUnreadSync(detail: NotificationUnreadSyncDetail) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(
    new CustomEvent<NotificationUnreadSyncDetail>(NOTIFICATION_UNREAD_SYNC_EVENT, { detail })
  )
}

export function onNotificationUnreadSync(
  handler: (detail: NotificationUnreadSyncDetail) => void
) {
  if (typeof window === 'undefined') {
    return () => {}
  }

  const listener = (event: Event) => {
    const customEvent = event as CustomEvent<NotificationUnreadSyncDetail>
    handler(customEvent.detail || {})
  }

  window.addEventListener(NOTIFICATION_UNREAD_SYNC_EVENT, listener as EventListener)
  return () => {
    window.removeEventListener(NOTIFICATION_UNREAD_SYNC_EVENT, listener as EventListener)
  }
}
