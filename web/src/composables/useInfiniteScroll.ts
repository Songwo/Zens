import { onMounted, onBeforeUnmount, ref, watch } from 'vue'

export interface UseInfiniteScrollOptions {
  /** 返回 false 时不触发加载（通常为 () => hasMore.value && !loading.value） */
  canLoadMore?: () => boolean
  /** 提前触发距离，默认 200px（在到达底部前预加载，体验更顺滑） */
  rootMargin?: string
  threshold?: number
}

/**
 * Song：通用无限滚动 composable。
 * 把哨兵元素 sentinel 绑到列表底部的占位元素上即可：<div ref="sentinel" />
 * 当哨兵进入视口且 canLoadMore() 为真时调用 onLoadMore。
 */
export function useInfiniteScroll(
  onLoadMore: () => void,
  options: UseInfiniteScrollOptions = {}
) {
  const sentinel = ref<HTMLElement | null>(null)
  let observer: IntersectionObserver | null = null

  const handle = (entries: IntersectionObserverEntry[]) => {
    if (!entries[0]?.isIntersecting) return
    if (options.canLoadMore && !options.canLoadMore()) return
    onLoadMore()
  }

  const disconnect = () => {
    if (observer) {
      observer.disconnect()
      observer = null
    }
  }

  const connect = () => {
    if (typeof IntersectionObserver === 'undefined') return
    disconnect()
    observer = new IntersectionObserver(handle, {
      rootMargin: options.rootMargin ?? '200px',
      threshold: options.threshold ?? 0,
    })
    if (sentinel.value) observer.observe(sentinel.value)
  }

  onMounted(connect)
  watch(sentinel, connect)
  onBeforeUnmount(disconnect)

  return { sentinel, connect, disconnect }
}
