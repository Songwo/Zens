const DEVICE_ID_KEY = 'device_id'

function isMobileDevice(): boolean {
  if (typeof navigator === 'undefined') return false
  return /Android|iPhone|iPad|iPod|Mobile|Tablet/i.test(navigator.userAgent)
}

function generateDeviceId(): string {
  const prefix = isMobileDevice() ? 'mob-' : 'pc-'
  const uid = (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
    ? crypto.randomUUID()
    : 'fallback-' + Math.random().toString(36).slice(2) + Date.now().toString(36)
  return prefix + uid
}

export function getOrCreateDeviceId(): string {
  const existing = localStorage.getItem(DEVICE_ID_KEY) || sessionStorage.getItem(DEVICE_ID_KEY)
  if (existing) return existing

  const id = generateDeviceId()
  localStorage.setItem(DEVICE_ID_KEY, id)
  sessionStorage.setItem(DEVICE_ID_KEY, id)
  return id
}
