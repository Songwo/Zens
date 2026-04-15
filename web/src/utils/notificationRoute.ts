import type { RouteLocationRaw } from 'vue-router'

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
    if (uid) return { path: `/user/${uid}` }
    return null
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
        path: `/t/${postId}`,
        query: { commentId },
      }
    }
  }

  const barIndex = raw.indexOf('|')
  if (barIndex > 0) {
    const postId = raw.slice(0, barIndex).trim()
    const commentId = raw.slice(barIndex + 1).trim()
    if (postId && commentId) {
      return {
        path: `/t/${postId}`,
        query: { commentId },
      }
    }
  }

  if (raw.startsWith('/t/')) {
    return { path: raw }
  }
  if (raw.startsWith('/p/')) {
    return { path: raw.replace('/p/', '/t/') }
  }
  if (raw.startsWith('/')) {
    return { path: raw }
  }
  return { path: `/t/${raw}` }
}
