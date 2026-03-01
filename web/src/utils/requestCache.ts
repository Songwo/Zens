type CacheValue<T> = {
  expiresAt: number
  data: T
}

type CacheOptions = {
  persist?: boolean
}

const memoryCache = new Map<string, CacheValue<unknown>>()
const inflight = new Map<string, Promise<unknown>>()
const SESSION_PREFIX = 'cp:req:cache:'

function readSessionCache<T>(key: string): CacheValue<T> | null {
  try {
    const raw = sessionStorage.getItem(SESSION_PREFIX + key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as CacheValue<T>
    if (!parsed || typeof parsed.expiresAt !== 'number') return null
    if (Date.now() >= parsed.expiresAt) {
      sessionStorage.removeItem(SESSION_PREFIX + key)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function writeSessionCache<T>(key: string, value: CacheValue<T>) {
  try {
    sessionStorage.setItem(SESSION_PREFIX + key, JSON.stringify(value))
  } catch {
    // Song：说明
  }
}

export async function cachedRequest<T>(
  key: string,
  ttlMs: number,
  fetcher: () => Promise<T>,
  options: CacheOptions = { persist: true }
): Promise<T> {
  const now = Date.now()
  const memoryHit = memoryCache.get(key) as CacheValue<T> | undefined
  if (memoryHit && now < memoryHit.expiresAt) {
    return memoryHit.data
  }

  if (options.persist) {
    const sessionHit = readSessionCache<T>(key)
    if (sessionHit) {
      memoryCache.set(key, sessionHit as CacheValue<unknown>)
      return sessionHit.data
    }
  }

  const ongoing = inflight.get(key) as Promise<T> | undefined
  if (ongoing) {
    return ongoing
  }

  const task = fetcher()
    .then((data) => {
      const value: CacheValue<T> = {
        expiresAt: Date.now() + Math.max(ttlMs, 1000),
        data,
      }
      memoryCache.set(key, value as CacheValue<unknown>)
      if (options.persist) {
        writeSessionCache(key, value)
      }
      return data
    })
    .finally(() => {
      inflight.delete(key)
    })

  inflight.set(key, task as Promise<unknown>)
  return task
}

export function clearRequestCache(keyPrefix?: string) {
  if (!keyPrefix) {
    memoryCache.clear()
    inflight.clear()
    try {
      const keys = Object.keys(sessionStorage)
      keys.forEach((key) => {
        if (key.startsWith(SESSION_PREFIX)) {
          sessionStorage.removeItem(key)
        }
      })
    } catch {
      // Song：说明
    }
    return
  }

  Array.from(memoryCache.keys()).forEach((key) => {
    if (key.startsWith(keyPrefix)) {
      memoryCache.delete(key)
    }
  })
  Array.from(inflight.keys()).forEach((key) => {
    if (key.startsWith(keyPrefix)) {
      inflight.delete(key)
    }
  })
  try {
    const keys = Object.keys(sessionStorage)
    keys.forEach((rawKey) => {
      if (rawKey.startsWith(SESSION_PREFIX + keyPrefix)) {
        sessionStorage.removeItem(rawKey)
      }
    })
  } catch {
    // Song：说明
  }
}
