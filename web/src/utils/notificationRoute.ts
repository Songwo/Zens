import type { RouteLocationRaw } from 'vue-router'
import { encodeCommentId, encodePostId, encodeUserId } from '@/utils/shortId'

type RelatedIdLike = string | number | null | undefined

const COMMENT_HASH_PREFIX = '#comment-'

export interface NotificationRouteContext {
  type?: string
  relatedUserId?: string | null
}

export const resolveNotificationRoute = (
  relatedId: RelatedIdLike,
  ctx?: NotificationRouteContext
): RouteLocationRaw | null => {
  // follow notifications → go to the follower's profile page
  if (ctx?.type === 'follow') {
    const uid = ctx.relatedUserId
    if (uid) return { path: `/user/${encodeUserId(uid)}` }
    return null
  }

  if (ctx?.type && ['system', 'security_alert', 'new_device_login', 'session_terminated', 'password_changed', 'password_reset', 'two_factor_enabled', 'two_factor_disabled', 'login_failed_burst'].includes(ctx.type)) {
    return { path: '/notifications' }
  }

  if (relatedId === null || relatedId === undefined) {
    return null
  }
  const raw = String(relatedId).trim()
  if (!raw) {
    return null
  }

  const hashIndex = raw.indexOf(COMMENT_HASH_PREFIX)
  if (hashIndex > 0) {
    const postId = raw.slice(0, hashIndex).trim()
    const commentId = raw.slice(hashIndex + COMMENT_HASH_PREFIX.length).trim()
    if (postId && commentId) {
      return {
        path: `/t/${encodePostId(postId)}`,
        query: { c: encodeCommentId(commentId) },
      }
    }
  }

  const barIndex = raw.indexOf('|')
  if (barIndex > 0) {
    const postId = raw.slice(0, barIndex).trim()
    const commentId = raw.slice(barIndex + 1).trim()
    if (postId && commentId) {
      return {
        path: `/t/${encodePostId(postId)}`,
        query: { c: encodeCommentId(commentId) },
      }
    }
  }

  if (raw.startsWith('/t/')) {
    const id = raw.replace('/t/', '').trim()
    return { path: `/t/${encodePostId(id)}` }
  }
  if (raw.startsWith('/p/')) {
    const id = raw.replace('/p/', '').trim()
    return { path: `/t/${encodePostId(id)}` }
  }
  if (raw.startsWith('/')) {
    return { path: raw }
  }
  return { path: `/t/${encodePostId(raw)}` }
}
