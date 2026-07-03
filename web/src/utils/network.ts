export type NetworkConnectionInfo = {
  saveData?: boolean
  effectiveType?: string
}

export const readNetworkConnection = (): NetworkConnectionInfo => {
  if (typeof navigator === 'undefined') {
    return {}
  }

  const connection = (navigator as Navigator & {
    connection?: NetworkConnectionInfo
  }).connection

  return {
    saveData: Boolean(connection?.saveData),
    effectiveType: connection?.effectiveType || '',
  }
}

export const shouldReduceBackgroundWork = () => {
  const connection = readNetworkConnection()
  return Boolean(connection.saveData || /(^slow-2g$|^2g$)/i.test(connection.effectiveType || ''))
}

export const isOffline = () => {
  return typeof navigator !== 'undefined' && !navigator.onLine
}

