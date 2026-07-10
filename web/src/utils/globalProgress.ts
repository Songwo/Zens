import { reactive } from 'vue'

const MIN_VISIBLE_MS = 220
const HIDE_DELAY_MS = 260
const SHOW_DELAY_MS = 120

let pendingCount = 0
let routeActive = false
let timer: number | null = null
let hideTimer: number | null = null
let showTimer: number | null = null
let startedAt = 0

export const globalProgressState = reactive({
  active: false,
  percent: 0,
  label: '',
})

function clearTimer() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function clearHideTimer() {
  if (hideTimer !== null) {
    window.clearTimeout(hideTimer)
    hideTimer = null
  }
}

function clearShowTimer() {
  if (showTimer !== null) {
    window.clearTimeout(showTimer)
    showTimer = null
  }
}

function activateProgress(label: string, initialPercent: number) {
  clearShowTimer()
  startedAt = Date.now()
  globalProgressState.active = true
  globalProgressState.percent = Math.max(globalProgressState.percent, initialPercent)
  globalProgressState.label = label
  clearTimer()
  timer = window.setInterval(tickProgress, 140)
}

function tickProgress() {
  if (!globalProgressState.active) return
  const ceiling = pendingCount > 1 ? 88 : 82
  const gap = Math.max(1, ceiling - globalProgressState.percent)
  const step = Math.max(0.6, gap * 0.08)
  globalProgressState.percent = Math.min(ceiling, globalProgressState.percent + step)
}

export function startGlobalProgress(label = '正在加载') {
  if (typeof window === 'undefined') return
  clearHideTimer()
  pendingCount += 1

  if (!globalProgressState.active) {
    globalProgressState.label = label
    if (showTimer === null) {
      showTimer = window.setTimeout(() => {
        showTimer = null
        if (pendingCount > 0 || routeActive) {
          activateProgress(globalProgressState.label || label, 8)
        }
      }, SHOW_DELAY_MS)
    }
    return
  }

  globalProgressState.label = label || globalProgressState.label
  globalProgressState.percent = Math.max(globalProgressState.percent, 18)
}

export function finishGlobalProgress() {
  if (typeof window === 'undefined') return
  pendingCount = Math.max(0, pendingCount - 1)
  if (pendingCount > 0 || routeActive) {
    if (globalProgressState.active) {
      globalProgressState.percent = Math.min(92, Math.max(globalProgressState.percent, 72))
    }
    return
  }

  if (!globalProgressState.active) {
    clearShowTimer()
    globalProgressState.percent = 0
    globalProgressState.label = ''
    return
  }

  const elapsed = Date.now() - startedAt
  const finish = () => {
    clearTimer()
    globalProgressState.percent = 100
    hideTimer = window.setTimeout(() => {
      globalProgressState.active = false
      globalProgressState.percent = 0
      globalProgressState.label = ''
    }, HIDE_DELAY_MS)
  }

  if (elapsed < MIN_VISIBLE_MS) {
    window.setTimeout(finish, MIN_VISIBLE_MS - elapsed)
    return
  }

  finish()
}

export function failGlobalProgress() {
  clearHideTimer()
  clearShowTimer()
  pendingCount = 0
  routeActive = false
  clearTimer()

  if (!globalProgressState.active) {
    globalProgressState.percent = 0
    globalProgressState.label = ''
    return
  }

  globalProgressState.percent = 100
  hideTimer = window.setTimeout(() => {
    globalProgressState.active = false
    globalProgressState.percent = 0
    globalProgressState.label = ''
  }, HIDE_DELAY_MS)
}

export function startRouteProgress(label = '正在打开页面') {
  if (typeof window === 'undefined') return
  clearHideTimer()
  clearShowTimer()
  routeActive = true

  if (!globalProgressState.active) {
    activateProgress(label, 10)
  } else {
    globalProgressState.percent = Math.max(globalProgressState.percent, 20)
    globalProgressState.label = label
  }
}

export function finishRouteProgress() {
  routeActive = false
  if (pendingCount > 0) {
    globalProgressState.percent = Math.min(92, Math.max(globalProgressState.percent, 76))
    globalProgressState.label = '正在同步数据'
    return
  }
  pendingCount += 1
  finishGlobalProgress()
}
