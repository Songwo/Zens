const TURNSTILE_SCRIPT_ID = 'cf-turnstile-script'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
const TURNSTILE_MAX_READY_CHECKS = 40
const TURNSTILE_READY_CHECK_DELAY_MS = 50

let turnstileLoadPromise: Promise<TurnstileApi> | null = null

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
    turnstileLoadPromise = null
    reject(new Error('Turnstile API was not available after script load'))
    return
  }

  window.setTimeout(() => {
    waitForTurnstileApi(resolve, reject, attempts + 1)
  }, TURNSTILE_READY_CHECK_DELAY_MS)
}

export const loadTurnstileApi = (): Promise<TurnstileApi> => {
  if (window.turnstile?.render) {
    return Promise.resolve(window.turnstile)
  }

  if (turnstileLoadPromise) {
    return turnstileLoadPromise
  }

  turnstileLoadPromise = new Promise<TurnstileApi>((resolve, reject) => {
    const handleLoad = () => waitForTurnstileApi(resolve, reject)
    const handleError = () => {
      turnstileLoadPromise = null
      reject(new Error('Failed to load Turnstile script'))
    }

    const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID) as HTMLScriptElement | null
    if (existingScript) {
      existingScript.addEventListener('load', handleLoad, { once: true })
      existingScript.addEventListener('error', handleError, { once: true })
      handleLoad()
      return
    }

    const script = document.createElement('script')
    script.id = TURNSTILE_SCRIPT_ID
    script.src = TURNSTILE_SCRIPT_SRC
    script.async = true
    script.defer = true
    script.addEventListener('load', handleLoad, { once: true })
    script.addEventListener('error', handleError, { once: true })
    document.head.appendChild(script)
  })

  return turnstileLoadPromise
}
