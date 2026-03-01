const DEVICE_ID_KEY = 'device_id'

function fallbackUuid() {
  return 'dev-' + Math.random().toString(36).slice(2) + Date.now().toString(36)
}

export function getOrCreateDeviceId(): string {
  const local = localStorage.getItem(DEVICE_ID_KEY)
  const session = sessionStorage.getItem(DEVICE_ID_KEY)
  const exists = local || session
  if (exists) return exists

  const id = (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
    ? crypto.randomUUID()
    : fallbackUuid()

  // Song：说明
  localStorage.setItem(DEVICE_ID_KEY, id)
  sessionStorage.setItem(DEVICE_ID_KEY, id)
  return id
}
