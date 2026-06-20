const TURNSTILE_SCRIPT_ID = 'cf-turnstile-script'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
const TURNSTILE_MAX_READY_CHECKS = 80
const TURNSTILE_READY_CHECK_DELAY_MS = 50

let turnstileLoadPromise: Promise<TurnstileApi> | null = null

type LoadTurnstileOptions = {
  force?: boolean
}

const waitForTurnstileApi = (
  resolve: (api: TurnstileApi) => void,
  reject: (reason?: unknown) => void,
  attempts = 0
) => {
  if (window.turnstile?.render) {
    resolve(window.turnstile)
    return
  }

  if (attempts >= TURNSTILE_MAX_READY_CHECKS) {
    resetTurnstileLoader()
    reject(new Error('Turnstile API was not available after script load'))
    return
  }

  window.setTimeout(() => {
    waitForTurnstileApi(resolve, reject, attempts + 1)
  }, TURNSTILE_READY_CHECK_DELAY_MS)
}

export const resetTurnstileLoader = () => {
  turnstileLoadPromise = null
  const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID)
  existingScript?.remove()
}

export const loadTurnstileApi = (options: LoadTurnstileOptions = {}): Promise<TurnstileApi> => {
  if (options.force) {
    resetTurnstileLoader()
  }

  if (window.turnstile?.render) {
    return Promise.resolve(window.turnstile)
  }

  if (turnstileLoadPromise) {
    return turnstileLoadPromise
  }

  turnstileLoadPromise = new Promise<TurnstileApi>((resolve, reject) => {
    const handleLoad = () => {
      const script = document.getElementById(TURNSTILE_SCRIPT_ID) as HTMLScriptElement | null
      if (script) script.dataset.loaded = 'true'
      waitForTurnstileApi(resolve, reject)
    }
    const handleError = () => {
      resetTurnstileLoader()
      reject(new Error('Failed to load Turnstile script'))
    }

    const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID) as HTMLScriptElement | null
    if (existingScript) {
      if (existingScript.dataset.failed === 'true') {
        resetTurnstileLoader()
        loadTurnstileApi().then(resolve).catch(reject)
        return
      }
      if (existingScript.dataset.loaded === 'true') {
        handleLoad()
        return
      }
      existingScript.addEventListener('load', handleLoad, { once: true })
      existingScript.addEventListener('error', () => {
        existingScript.dataset.failed = 'true'
        handleError()
      }, { once: true })
      handleLoad()
      return
    }

    const script = document.createElement('script')
    script.id = TURNSTILE_SCRIPT_ID
    script.src = TURNSTILE_SCRIPT_SRC
    script.async = true
    script.defer = true
    script.addEventListener('load', handleLoad, { once: true })
    script.addEventListener('error', () => {
      script.dataset.failed = 'true'
      handleError()
    }, { once: true })
    document.head.appendChild(script)
  })

  return turnstileLoadPromise
}
