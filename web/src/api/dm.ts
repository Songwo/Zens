import api from '@/lib/api'
import type { Result } from '@/types'

export interface DirectConversation {
  conversationId: string
  peerId: string
  peerName: string
  peerAvatar?: string
  peerBadgeText?: string
  peerBadgeColor?: string
  peerBadgeStyle?: string
  lastMessage: string
  lastTime: string
  unreadCount: number
}

export interface DirectMessage {
  id: number | string
  senderId: string
  receiverId: string
  content: string
  isRead: number
  readReceipt?: 'UNREAD' | 'DELIVERED' | 'READ'
  createdAt: string
  self: boolean
}

export interface PageData<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export const dmApi = {
  send(receiverId: string, content: string) {
    return api.post<any, Result<void>>('/dm/send', { receiverId, content })
  },

  getConversations(page = 1, pageSize = 20) {
    return api.get<any, Result<PageData<DirectConversation>>>('/dm/conversations', {
      params: { page, pageSize },
    })
  },

  getMessages(peerId: string, page = 1, pageSize = 30) {
    return api.get<any, Result<PageData<DirectMessage>>>(`/dm/messages/${peerId}`, {
      params: { page, pageSize },
    })
  },

  markRead(peerId: string) {
    return api.post<any, Result<void>>(`/dm/read/${peerId}`)
  },

  getUnreadCount() {
    return api.get<any, Result<number>>('/dm/unread-count')
  },
}
