import { ref, onUnmounted } from 'vue'
import { viewLogApi } from '@/api/viewLog'

/**
 * Song：阅读时长追踪 composable（借鉴 Discourse PostTiming）
 *
 * 工作方式：
 * - start(postId) 后，每 10 秒检查一次页面可见性，可见则累加 dwellMs
 * - 每累计满 30 秒上报一次心跳，重置计数
 * - 切到后台 / 页面卸载时 flush 一次，避免丢失
 * - 单例：同一时刻只追踪一个帖子
 */
const HEARTBEAT_INTERVAL_MS = 10_000 // 每 10 秒检查一次可见性
const FLUSH_THRESHOLD_MS = 30_000 // 累计满 30 秒上报一次

export function useDwellTime() {
    let timer: number | undefined
    let lastTick = 0
    let activePostId: string | null = null
    const dwellMs = ref(0)

    const flush = (postId: string | null = activePostId) => {
        if (!postId || dwellMs.value <= 0) return
        const ms = dwellMs.value
        dwellMs.value = 0
        // Song：fire-and-forget，失败静默，不打扰阅读体验
        viewLogApi.heartbeat(postId, ms).catch(() => {})
    }

    const tick = () => {
        if (!activePostId) return
        if (typeof document !== 'undefined' && document.visibilityState !== 'visible') {
            // Song：页面不可见时不累加，但也不 flush（用户可能切回）
            lastTick = Date.now()
            return
        }
        const now = Date.now()
        const delta = now - lastTick
        lastTick = now
        if (delta > 0 && delta < HEARTBEAT_INTERVAL_MS * 3) {
            // Song：防止设备休眠后恢复产生异常大 delta（限制单 tick ≤ 30s）
            dwellMs.value += delta
        }
        if (dwellMs.value >= FLUSH_THRESHOLD_MS) {
            flush()
        }
    }

    const onVisibilityChange = () => {
        // Song：切到后台立即 flush 一次，保留已累计时长
        if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
            flush()
        } else {
            lastTick = Date.now()
        }
    }

    const start = (postId: string) => {
        if (activePostId === postId && timer) return
        // Song：切换帖子，先 flush 旧的
        if (activePostId && activePostId !== postId) {
            flush()
        }
        activePostId = postId
        dwellMs.value = 0
        lastTick = Date.now()
        if (timer) {
            window.clearInterval(timer)
        }
        timer = window.setInterval(tick, HEARTBEAT_INTERVAL_MS)
        if (typeof document !== 'undefined') {
            document.addEventListener('visibilitychange', onVisibilityChange)
        }
    }

    const stop = () => {
        if (timer) {
            window.clearInterval(timer)
            timer = undefined
        }
        if (typeof document !== 'undefined') {
            document.removeEventListener('visibilitychange', onVisibilityChange)
        }
        flush()
        activePostId = null
    }

    onUnmounted(() => {
        stop()
    })

    return { start, stop, flush }
}
