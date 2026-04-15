import SockJS from 'sockjs-client'
import { Stomp, CompatClient } from '@stomp/stompjs'

export interface PostEvent {
  type: 'POST_CREATED' | 'POST_REPLIED' | 'POST_VIEWED' | 'POST_LIKED' | 'POST_COLLECTED' | 'PIN_UPDATED' | 'CATEGORY_PIN_UPDATED' | 'POST_DELETED' | 'POST_UPDATED'
  postId: string
  sectionId?: number
  title?: string
  authorName?: string
  authorAvatar?: string
  data?: {
    viewCount?: number
    likeCount?: number
    collectCount?: number
    commentCount?: number
    lastReplyAt?: string
    lastActivityAt?: string
    globalPin?: number
    categoryPin?: number
    pinOrder?: number
  }
  timestamp: string
}

export interface NotificationEvent {
  id: number
  userId: string
  type: string
  title: string
  content: string
  relatedId?: string | number
  relatedUserId?: string
  relatedUserNickname?: string
  relatedUserAvatar?: string
  isRead: number
  createdAt: string
}

export type PostEventHandler = (event: PostEvent) => void
export type NotificationEventHandler = (event: NotificationEvent) => void

class WebSocketClient {
  private client: CompatClient | null = null
  private connected = false
  private reconnectAttempts = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private connectingPromise: Promise<void> | null = null
  private shouldReconnect = true
  private readonly reconnectBaseDelay = 1500
  private readonly reconnectMaxDelay = 30000
  private lastHiddenAt = 0
  private handlers: Map<string, Set<PostEventHandler | NotificationEventHandler>> = new Map()
  private subscriptions: Map<string, any> = new Map()
  private currentUserId: string | null = null
  private baseUrl: string

  constructor(baseUrl: string = '/api/ws') {
    this.baseUrl = baseUrl
    this.bindLifecycleListeners()
  }

  private readonly handleOnline = () => {
    this.resumeConnection(true)
  }

  private readonly handleOffline = () => {
    if (this.connected) {
      console.warn('[WebSocket] 网络离线，等待网络恢复后重连')
    }
  }

  private readonly handleVisibilityChange = () => {
    if (typeof document === 'undefined') {
      return
    }

    if (document.hidden) {
      this.lastHiddenAt = Date.now()
      return
    }

    const hiddenDuration = this.lastHiddenAt ? Date.now() - this.lastHiddenAt : 0
    if (hiddenDuration >= 45000) {
      this.resumeConnection(true)
      return
    }
    this.resumeConnection(false)
  }

  private bindLifecycleListeners() {
    if (typeof window === 'undefined') {
      return
    }
    window.addEventListener('online', this.handleOnline)
    window.addEventListener('offline', this.handleOffline)
    document.addEventListener('visibilitychange', this.handleVisibilityChange)
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  private nextReconnectDelay() {
    const exponential = this.reconnectBaseDelay * Math.pow(2, Math.min(this.reconnectAttempts, 6))
    const jitter = Math.floor(Math.random() * 1000)
    return Math.min(this.reconnectMaxDelay, exponential + jitter)
  }

  private resetTransportConnection() {
    const currentClient = this.client
    this.client = null
    this.connected = false
    this.subscriptions.clear()

    if (currentClient) {
      try {
        currentClient.disconnect(() => {
          // noop
        })
      } catch {
        // ignore transport teardown failures
      }
    }
  }

  private scheduleReconnect(immediate = false) {
    if (!this.shouldReconnect || this.connected || this.connectingPromise || this.reconnectTimer || this.handlers.size === 0) {
      return
    }
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      console.warn('[WebSocket] 当前离线，等待网络恢复后重连')
      return
    }

    const delay = immediate ? 0 : this.nextReconnectDelay()
    console.log(`[WebSocket] ${delay === 0 ? '立即' : `${delay}ms 后`}尝试重连...`)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.shouldReconnect || this.connected || this.connectingPromise) {
        return
      }
      this.reconnectAttempts++
      this.connect().catch(() => {
        // Song：连接失败后将由 connect 内部继续调度
      })
    }, delay)
  }

  connect(): Promise<void> {
    if (this.connected) {
      return Promise.resolve()
    }
    if (this.connectingPromise) {
      return this.connectingPromise as Promise<void>
    }

    this.shouldReconnect = true
    this.clearReconnectTimer()
    this.connectingPromise = new Promise<void>((resolve, reject) => {
      try {
        this.client = Stomp.over(() => new SockJS(this.baseUrl))
        const connectHeaders: Record<string, string> = {}
        if (this.currentUserId) {
          connectHeaders['X-User-Id'] = this.currentUserId
        }
        // 发送 JWT token，让后端能识别用户 Principal
        const token = localStorage.getItem('access_token') || sessionStorage.getItem('access_token')
        if (token) {
          connectHeaders['Authorization'] = `Bearer ${token}`
        }

        // Song：禁用调试日志（生产环境）
        this.client.debug = () => {}
        this.client.heartbeat.outgoing = 20000
        this.client.heartbeat.incoming = 20000
        const compatClient = this.client as CompatClient & {
          onWebSocketClose?: (event?: CloseEvent) => void
          onStompError?: (frame: any) => void
        }
        compatClient.onWebSocketClose = () => {
          const wasConnected = this.connected
          this.connected = false
          this.subscriptions.clear()
          if (wasConnected) {
            console.warn('[WebSocket] 连接中断，准备重连')
          }
          this.scheduleReconnect()
        }
        compatClient.onStompError = (frame) => {
          console.error('[WebSocket] STOMP 错误:', frame)
        }

        this.client.connect(
          connectHeaders,
          () => {
            this.connected = true
            this.reconnectAttempts = 0
            this.clearReconnectTimer()
            console.log('[WebSocket] 连接成功')

            // Song：等待下一个事件循环再重新订阅，确保连接完全就绪
            setTimeout(() => {
              this.resubscribeAll()
            }, 0)

            resolve()
          },
          (error: any) => {
            console.error('[WebSocket] 连接失败:', error)
            this.connected = false
            this.scheduleReconnect()
            reject(error)
          }
        )
      } catch (error) {
        console.error('[WebSocket] 初始化失败:', error)
        this.scheduleReconnect()
        reject(error)
      }
    }).finally(() => {
      this.connectingPromise = null
    })

    return this.connectingPromise as Promise<void>
  }

  disconnect() {
    this.shouldReconnect = false
    this.clearReconnectTimer()
    this.reconnectAttempts = 0

    if (this.client) {
      this.client.disconnect(() => {
        console.log('[WebSocket] 已断开连接')
      })
    }

    this.client = null
    this.connected = false
    this.connectingPromise = null
    this.subscriptions.clear()
  }

  resumeConnection(force = false) {
    if (!this.shouldReconnect || this.handlers.size === 0) {
      return
    }

    if (force && this.connected) {
      this.resetTransportConnection()
    }

    if (this.connected) {
      this.resubscribeAll()
      return
    }

    if (this.connectingPromise) {
      return
    }

    this.scheduleReconnect(true)
  }

  private resubscribeAll() {
    const topics = Array.from(this.handlers.keys())
    topics.forEach(topic => {
      this.subscribeToTopic(topic)
    })
  }

  private subscribeToTopic(topic: string) {
    if (!this.client || !this.connected) {
      console.warn('[WebSocket] 未连接，无法订阅:', topic)
      return
    }

    // Song：如果已订阅，先取消
    if (this.subscriptions.has(topic)) {
      this.subscriptions.get(topic).unsubscribe()
    }

    const subscription = this.client.subscribe(topic, (message) => {
      try {
        const event: PostEvent | NotificationEvent = JSON.parse(message.body)
        this.notifyHandlers(topic, event)
      } catch (error) {
        console.error('[WebSocket] 解析消息失败:', error)
      }
    })

    this.subscriptions.set(topic, subscription)
    console.log('[WebSocket] 已订阅:', topic)
  }

  private notifyHandlers(topic: string, event: PostEvent | NotificationEvent) {
    const handlers = this.handlers.get(topic)
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(event as any)
        } catch (error) {
          console.error('[WebSocket] 处理事件失败:', error)
        }
      })
    }
  }

  /**
   * Song：订阅全局帖子事件
   */
  subscribeGlobal(handler: PostEventHandler): () => void {
    return this.subscribe('/topic/posts', handler)
  }

  /**
   * Song：订阅指定板块的帖子事件
   */
  subscribeSection(sectionId: number, handler: PostEventHandler): () => void {
    return this.subscribe(`/topic/section/${sectionId}`, handler)
  }

  /**
   * Song：订阅用户通知
   */
  subscribeNotifications(userId: string, handler: NotificationEventHandler): () => void {
    this.currentUserId = userId
    return this.subscribe(`/user/${userId}/queue/notifications`, handler as any)
  }

  /**
   * 订阅强制下线事件（单设备策略踢下线）
   */
  subscribeForceLogout(userId: string, handler: (reason: string) => void): () => void {
    return this.subscribe(`/user/${userId}/queue/force-logout`, handler as any)
  }

  /**
   * Song：说明
   */
  setUserId(userId: string | null) {
    this.currentUserId = userId
  }

  /**
   * Song：通用订阅方法
   */
  subscribe(topic: string, handler: PostEventHandler | NotificationEventHandler): () => void {
    if (!this.handlers.has(topic)) {
      this.handlers.set(topic, new Set())
    }

    this.handlers.get(topic)!.add(handler)

    // Song：如果已连接，立即订阅
    if (this.connected) {
      this.subscribeToTopic(topic)
    } else {
      // Song：否则连接后自动订阅
      this.connect().catch(() => {
        console.error('[WebSocket] 自动连接失败')
      })
    }

    // Song：返回取消订阅函数
    return () => {
      const handlers = this.handlers.get(topic)
      if (handlers) {
        handlers.delete(handler)

        // Song：如果该主题没有处理器了，取消订阅
        if (handlers.size === 0) {
          this.handlers.delete(topic)
          const subscription = this.subscriptions.get(topic)
          if (subscription) {
            subscription.unsubscribe()
            this.subscriptions.delete(topic)
            console.log('[WebSocket] 已取消订阅:', topic)
          }
        }
      }

      if (this.handlers.size === 0) {
        this.disconnect()
      }
    }
  }

  isConnected(): boolean {
    return this.connected
  }
}

// Song：单例实例
export const wsClient = new WebSocketClient()

