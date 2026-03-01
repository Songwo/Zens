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
  relatedId?: number
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
  private maxReconnectAttempts = 5
  private reconnectDelay = 3000
  private handlers: Map<string, Set<PostEventHandler | NotificationEventHandler>> = new Map()
  private subscriptions: Map<string, any> = new Map()
  private currentUserId: string | null = null
  private baseUrl: string

  constructor(baseUrl: string = '/api/ws') {
    this.baseUrl = baseUrl
  }

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.connected) {
        resolve()
        return
      }

      try {
        this.client = Stomp.over(() => new SockJS(this.baseUrl))
        const connectHeaders: Record<string, string> = {}
        if (this.currentUserId) {
          connectHeaders['X-User-Id'] = this.currentUserId
        }

        // Song：禁用调试日志（生产环境）
        this.client.debug = () => {}

        this.client.connect(
          connectHeaders,
          () => {
            this.connected = true
            this.reconnectAttempts = 0
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
            this.handleReconnect()
            reject(error)
          }
        )
      } catch (error) {
        console.error('[WebSocket] 初始化失败:', error)
        reject(error)
      }
    })
  }

  disconnect() {
    if (this.client && this.connected) {
      this.client.disconnect(() => {
        console.log('[WebSocket] 已断开连接')
      })
      this.connected = false
      this.subscriptions.clear()
    }
  }

  private handleReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      console.log(`[WebSocket] 尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)

      setTimeout(() => {
        this.connect().catch(() => {
          // Song：重连失败，继续尝试
        })
      }, this.reconnectDelay * this.reconnectAttempts)
    } else {
      console.error('[WebSocket] 重连失败，已达到最大尝试次数')
    }
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
    }
  }

  isConnected(): boolean {
    return this.connected
  }
}

// Song：单例实例
export const wsClient = new WebSocketClient()
