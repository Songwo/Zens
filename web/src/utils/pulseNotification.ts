import { reactive } from 'vue'

export interface PulseNotificationItem {
  id: string
  type: 'like' | 'comment' | 'post' | 'success' | 'warning' | 'error' | 'info'
  title: string
  message: string
  duration: number
  startTime: number
}

type PulseNotificationInput = Omit<PulseNotificationItem, 'id' | 'startTime' | 'duration'> & {
  duration?: number
}

export const notificationQueue = reactive<PulseNotificationItem[]>([])

export const pulseNotification = {
  show(item: PulseNotificationInput) {
    const id = Math.random().toString(36).substring(2, 9)
    const duration = item.duration ?? 4000
    const newItem: PulseNotificationItem = {
      ...item,
      id,
      duration,
      startTime: Date.now()
    }
    
    // Add to queue
    notificationQueue.push(newItem)

    // Automatically remove after duration
    setTimeout(() => {
      this.close(id)
    }, duration)
  },

  close(id: string) {
    const idx = notificationQueue.findIndex(n => n.id === id)
    if (idx !== -1) {
      notificationQueue.splice(idx, 1)
    }
  },

  like(message: string, title = '点赞成功') {
    this.show({ type: 'like', title, message })
  },

  comment(message: string, title = '评论成功') {
    this.show({ type: 'comment', title, message })
  },

  post(message: string, title = '发布成功') {
    this.show({ type: 'post', title, message })
  },

  success(message: string, title = '操作成功') {
    this.show({ type: 'success', title, message })
  },

  warning(message: string, title = '提示') {
    this.show({ type: 'warning', title, message })
  },

  error(message: string, title = '操作失败') {
    this.show({ type: 'error', title, message })
  },

  info(message: string, title = '通知') {
    this.show({ type: 'info', title, message })
  }
}
