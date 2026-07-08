import { clearRequestCache } from '@/utils/requestCache'

export const COMMUNITY_FEED_REFRESH_EVENT = 'cp:community-feed-refresh'

export function invalidateCommunityContentCaches() {
  clearRequestCache('public:home-bootstrap')
  clearRequestCache('public:hot-rank')
  clearRequestCache('public:tags:hot')
  clearRequestCache('search:list')
  clearRequestCache('search:suggest')
  clearRequestCache('tag:posts')
}

export function notifyCommunityContentChanged() {
  invalidateCommunityContentCaches()
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(COMMUNITY_FEED_REFRESH_EVENT))
  }
}
