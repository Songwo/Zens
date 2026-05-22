/**
 * Song：说明
 */
export function timeAgo(dateStr: string | undefined | null): string {
    if (!dateStr) return ''

    const date = parseDate(dateStr)
    if (!date) return ''

    const now = new Date()
    const diff = now.getTime() - date.getTime()

    if (diff < 0) return '刚刚'

    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)

    if (seconds < 60) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    if (hours < 24) return `${hours}小时前`
    if (days === 1) {
        return `昨天 ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
    if (days < 7) return `${weekdayName(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}`

    // Song：说明
    if (date.getFullYear() === now.getFullYear()) {
        return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }

    // Song：说明
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function pad(n: number): string {
    return n < 10 ? `0${n}` : `${n}`
}

function parseDate(value: string): Date | null {
    const trimmed = value.trim()
    if (!trimmed) return null

    const normalized = trimmed.includes('T')
        ? trimmed
        : trimmed.replace(' ', 'T')
    const date = new Date(normalized)
    if (!Number.isNaN(date.getTime())) return date

    const fallback = new Date(trimmed)
    return Number.isNaN(fallback.getTime()) ? null : fallback
}

function weekdayName(date: Date): string {
    const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return names[date.getDay()] || ''
}
