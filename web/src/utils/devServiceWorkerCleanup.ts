export function cleanupDevServiceWorker() {
  if (!import.meta.env.DEV || typeof window === 'undefined') return
  if (!('serviceWorker' in navigator)) return

  navigator.serviceWorker.getRegistrations()
    .then((registrations) => Promise.all(registrations.map(registration => registration.unregister())))
    .then(() => {
      if (!('caches' in window)) return
      return caches.keys().then(keys => Promise.all(keys.map(key => caches.delete(key))))
    })
    .catch(() => {
      // 开发环境缓存清理失败不影响应用运行。
    })
}
