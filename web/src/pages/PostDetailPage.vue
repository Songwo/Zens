<script setup lang="ts">
import { ref, onMounted, computed, watch, nextTick, onUnmounted, createApp, type App as VueApp } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import ScrollEdgeBar from '@/components/ui/ScrollEdgeBar.vue'
import { postApi, type PostVersionHistory } from '@/api/post'
import { commentApi } from '@/api/comment'
import type { ShortLinkResolveResult } from '@/api/comment'
import { reportApi } from '@/api/report'
import { recommendApi } from '@/api/recommend'
import { viewLogApi } from '@/api/viewLog'
import type { Post, Comment, RecommendPost } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pulseNotification } from '@/utils/pulseNotification'
import { renderMarkdownWithTocResult, renderMarkdownWithTocAsync, type MarkdownTocRenderResult } from '@/utils/markdownToc'
import DOMPurify from 'dompurify'
import { timeAgo } from '@/utils/timeAgo'
import { decodeCommentId, decodePostId, encodePostId } from '@/utils/shortId'
import { generateSummary, stripMarkdown } from '@/utils/markdown'
import { renderGithubRichCards, mergeConsecutiveBlockquotes } from '@/utils/richLink'
import { cachedRequest } from '@/utils/requestCache'
import { md } from '@/utils/markdownRenderer'
import { isTruthyFlag } from '@/utils/flags'
import { 
  View, 
  CaretTop, 
  Star,
  Share,
  ChatLineRound,
  More,
  Warning,
  EditPen,
  Medal,
  Delete,
  RefreshRight,
  CircleCheck,
  Bell,
  BellFilled,
  Clock
} from '@element-plus/icons-vue'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import TrustLevelBadge from '@/components/common/TrustLevelBadge.vue'
import UserBadge from '@/components/common/UserBadge.vue'
import UserQuickCard from '@/components/common/UserQuickCard.vue'
import CommentList from '@/components/comment/CommentList.vue'
import PollCard from '@/components/poll/PollCard.vue'
import { pollApi, type Poll } from '@/api/poll'
import { subscriptionApi } from '@/api/subscription'
import ReactionBar from '@/components/reaction/ReactionBar.vue'
import { reactionApi, type ReactionResp } from '@/api/reaction'
import CommentEditor from '@/components/comment/CommentEditor.vue'
import { useUserStore } from '@/store/user'
import { usePostComposerStore } from '@/store/postComposer'
import { useDwellTime } from '@/composables/useDwellTime'
import OneboxCard from '@/components/common/OneboxCard.vue'
import { followApi } from '@/api/follow'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
// Song：阅读时长追踪（借鉴 Discourse PostTiming），用于 TL 计算与热度加权
const dwellTime = useDwellTime()

const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const commentsTotal = ref(0)
const commentsPage = ref(1)
const commentsLoadingMore = ref(false)
const relatedPosts = ref<RecommendPost[]>([])
const poll = ref<Poll | null>(null)
const contentRef = ref<HTMLElement | null>(null)
let _postFetchToken = 0
const loading = ref(true)
const postAuthorAvatarUrl = computed(() => resolvePublicAssetUrl(post.value?.authorAvatar))
const postCoverImageUrl = computed(() => resolvePublicAssetUrl(post.value?.coverImage))
// 区分"加载失败"和"帖子真的不存在/已删除":网络错误时给重试入口,别误导成已删除
const postLoadError = ref(false)
const commentLoading = ref(false)
const commentsInitialLoading = ref(false)
const relatedPostsLoading = ref(false)
const pollLoading = ref(false)
const replyingTo = ref<string | null>(null)
const replyingToName = ref<string | null>(null)
const regeneratingSummary = ref(false)
const versionHistory = ref<PostVersionHistory[]>([])
const versionHistoryVisible = ref(false)
const versionHistoryLoading = ref(false)
const activeVersion = ref<PostVersionHistory | null>(null)
const activeCommentId = ref('')
const shortLinkTarget = ref<ShortLinkResolveResult | null>(null)
const RELATED_POST_CACHE_TTL = 60 * 1000
const POST_METRICS_UPDATED_EVENT = 'cp:post-metrics-updated'

const postId = computed(() => {
  if (shortLinkTarget.value?.postId) {
    return shortLinkTarget.value.postId
  }
  const rawId = String(route.params.id || '')
  return rawId.startsWith('p') ? decodePostId(rawId) : rawId
})

watch(
  () => route.params.id,
  (rawId) => {
    if (route.name === 'short-link-detail') {
      return
    }
    if (rawId && String(rawId).startsWith('POST_')) {
      const shortId = encodePostId(String(rawId))
      router.replace(`/t/${shortId}`)
    }
  },
  { immediate: true }
)

const toFiniteMetric = (value: unknown): number | null => {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

const visibleCommentCount = computed(() => {
  return toFiniteMetric(post.value?.commentCount) ?? comments.value.length
})

const syncPostMetricsToLists = () => {
  if (!post.value?.id) return
  window.dispatchEvent(new CustomEvent(POST_METRICS_UPDATED_EVENT, {
    detail: {
      postId: post.value.id,
      viewCount: post.value.viewCount,
      commentCount: post.value.commentCount,
      lastActivityAt: post.value.lastActivityAt,
      hasAdoptedAnswer: post.value.hasAdoptedAnswer,
    },
  }))
}

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.some((role: string) => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
})

const moderatedSectionIds = computed(() => {
  const rawIds = userStore.userInfo?.moderatedSectionIds || []
  return new Set(rawIds.map((id: number | string) => Number(id)).filter((id: number) => Number.isFinite(id) && id > 0))
})

const isDeletedPost = computed(() => post.value?.auditStatus === 'DELETED')
const isLotteryPost = computed(() => String(post.value?.postType || '').toUpperCase() === 'LOTTERY')
const lotteryDeadlineText = computed(() => {
  if (!post.value?.commentDeadline) return '不限截止时间'
  return new Date(post.value.commentDeadline).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
})
const lotteryCommentClosed = computed(() => {
  if (!post.value?.commentDeadline) return false
  return Date.now() > new Date(post.value.commentDeadline).getTime()
})
const hasJoinedLottery = computed(() => {
  if (!isLotteryPost.value || !userStore.userId) return false
  const findOwnComment = (items: Comment[]): boolean => {
    return items.some(item => {
      if (item.userId === userStore.userId && item.auditStatus !== 'DELETED') return true
      return Array.isArray(item.children) && findOwnComment(item.children)
    })
  }
  return findOwnComment(comments.value)
})
const lotteryCommentBlockedReason = computed(() => {
  if (!isLotteryPost.value) return ''
  if (!userStore.accessToken) return '请先登录后参与抽奖评论'
  if (lotteryCommentClosed.value) return '该抽奖贴已截止评论'
  if (post.value?.commentOncePerUser && hasJoinedLottery.value) return '你已经评论参与过本次抽奖'
  return ''
})
const canSubmitComment = computed(() => !lotteryCommentBlockedReason.value)

const canModerateCurrentSection = computed(() => {
  if (!post.value?.sectionId) return isAdmin.value
  return isAdmin.value || moderatedSectionIds.value.has(Number(post.value.sectionId))
})

const canManageCurrentPost = computed(() => {
  if (!post.value || !userStore.userId) return false
  return post.value.userId === userStore.userId || canModerateCurrentSection.value
})

const isFollowed = ref(false)
const followLoading = ref(false)

const checkFollowStatus = async (authorId = post.value?.userId, requestToken = _postFetchToken) => {
  if (!userStore.accessToken || !authorId) return
  if (authorId === userStore.userId) return
  try {
    const res = await followApi.isFollowing(authorId)
    if (requestToken === _postFetchToken && post.value?.userId === authorId) {
      isFollowed.value = res.data === true
    }
  } catch {}
}

const handleFollow = async () => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  if (!post.value?.userId) return
  followLoading.value = true
  try {
    if (isFollowed.value) {
      await followApi.unfollow(post.value.userId)
      isFollowed.value = false
      ElMessage.success('已取消关注')
    } else {
      await followApi.follow(post.value.userId)
      isFollowed.value = true
      ElMessage.success('关注成功')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

// 主题追踪：订阅后有新回复收站内通知；开了邮件通知的用户每天还有聚合摘要邮件
const isSubscribed = ref(false)
const subscribeLoading = ref(false)

const checkSubscriptionStatus = async (id = postId.value, requestToken = _postFetchToken) => {
  if (!userStore.accessToken || !id) return
  try {
    const res = await subscriptionApi.getStatus(id)
    if (requestToken === _postFetchToken && postId.value === id) {
      isSubscribed.value = res.data?.subscribed === true
    }
  } catch {}
}

const handleSubscribe = async () => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再追踪主题')
    return
  }
  if (!postId.value) return
  subscribeLoading.value = true
  try {
    if (isSubscribed.value) {
      await subscriptionApi.unsubscribe(postId.value)
      isSubscribed.value = false
      pulseNotification.info('已取消追踪，本主题的新回复将不再提醒你')
    } else {
      await subscriptionApi.subscribe(postId.value)
      isSubscribed.value = true
      pulseNotification.success('追踪成功！本主题有新回复时会通知你', '追踪主题')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '操作失败')
  } finally {
    subscribeLoading.value = false
  }
}

const renderedContent = computed(() => {
  const rawHtml = tocRenderResult.value.html
  const lazyImageHtml = rawHtml.replace(/<img\b(?![^>]*\bloading=)/gi, '<img loading="lazy" decoding="async" ')
  const mergedHtml = mergeConsecutiveBlockquotes(lazyImageHtml)
  const richHtml = renderGithubRichCards(normalizeContentAssetUrls(mergedHtml))
  return DOMPurify.sanitize(richHtml, {
    ALLOWED_TAGS: [
      'h1','h2','h3','h4','h5','h6','p','br','hr',
      'ul','ol','li','blockquote','pre','code',
      'table','thead','tbody','tr','th','td',
      'a','img','strong','em','del','s','mark','kbd',
      'sup','sub','details','summary','input',
      'div','span','video','source',
      'svg','path',
      'button'
    ],
    ALLOWED_ATTR: [
      'href','src','alt','title','class','id','target','rel',
      'type','checked','disabled','loading','decoding',
      'controls','preload','poster','playsinline','muted','loop','autoplay',
      'viewBox','width','height','aria-hidden','aria-label','aria-live','role','tabindex','fill','d',
      'style',
      'data-lang','data-line','data-highlighted-chars','data-highlighted-chars-id',
      // Song：Onebox 富链接卡片标记，挂载后由 OneboxCard 异步加载 OG 元数据
      'data-onebox-url'
    ],
    FORCE_BODY: true,
    ALLOW_DATA_ATTR: false,
  })
})

function normalizeContentAssetUrls(html: string) {
  if (typeof document === 'undefined' || !html) return html
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('img, video, source').forEach((el) => {
    for (const attr of ['src', 'poster']) {
      const nextUrl = resolvePublicAssetUrl(el.getAttribute(attr))
      if (nextUrl) el.setAttribute(attr, nextUrl)
    }
  })
  return template.innerHTML
}

const tocRenderResult = ref<MarkdownTocRenderResult>({ html: '', headings: [], hasTocTag: false })
let _renderToken = 0

// Song：Onebox 富链接动态挂载 —— 内容渲染后遍历 a[data-onebox-url]，用 OneboxCard 替换
const oneboxApps: VueApp[] = []
const hydrateOneboxCards = () => {
  // Song：先卸载旧的动态挂载实例，避免重复/内存泄漏
  for (const app of oneboxApps) {
    try { app.unmount() } catch {}
  }
  oneboxApps.length = 0

  const anchors = contentRef.value?.querySelectorAll('a[data-onebox-url]') || []
  anchors.forEach((anchor) => {
    const url = anchor.getAttribute('data-onebox-url')
    if (!url) return
    // Song：用一个新的 div 容器替换原 a 标签，在容器上挂载 OneboxCard
    const mountPoint = document.createElement('div')
    mountPoint.className = 'onebox-mount'
    anchor.replaceWith(mountPoint)
    try {
      const app = createApp(OneboxCard, { url })
      app.mount(mountPoint)
      oneboxApps.push(app)
    } catch (e) {
      // Song：挂载失败时把原 a 标签放回去，至少保留可点击链接
      mountPoint.replaceWith(anchor)
    }
  })
}

// Song：内容渲染后挂载 Onebox 卡片（nextTick 确保 DOM 已更新）
watch(renderedContent, () => {
  nextTick(() => hydrateOneboxCards())
}, { immediate: true })
watch(
  () => post.value?.content ?? '',
  async (content) => {
    const token = ++_renderToken
    if (!content) {
      _renderToken === token && (tocRenderResult.value = { html: '', headings: [], hasTocTag: false })
      return
    }
    // 先用同步渲染保证首屏立刻可见（代码块此时可能仍是纯文本）
    tocRenderResult.value = renderMarkdownWithTocResult(md, content, { inlineToc: false })
    // 再异步预热 Shiki + 加载语言后用同一 API 重渲染，第二次出彩
    const result = await renderMarkdownWithTocAsync(md, content, { inlineToc: false })
    if (token === _renderToken) {
      tocRenderResult.value = result
    }
  },
  { immediate: true }
)

const aiSummaryText = computed(() => {
  if (!post.value) return ''
  if (post.value.summary && post.value.summary.trim()) return post.value.summary.trim()
  return generateSummary(post.value.content || '', 180)
})

// 摘要一次性呈现，避免长文逐字定时器占用主线程，也避免动态文字影响辅助技术阅读。
const displayedSummaryText = computed(() => aiSummaryText.value)

onUnmounted(() => {
  // Song：卸载所有动态挂载的 Onebox 卡片实例
  for (const app of oneboxApps) {
    try { app.unmount() } catch {}
  }
  oneboxApps.length = 0
})

const canRegenerateSummary = computed(() => {
  if (!post.value || !userStore.userId) return false
  return !isDeletedPost.value && canManageCurrentPost.value
})

const versionChangeCount = computed(() => versionHistory.value.length)

const showSidebarToc = computed(() => tocRenderResult.value.hasTocTag)
const TOC_COLLAPSE_THRESHOLD = 8
const TOC_PREVIEW_COUNT = 6
const tocCollapsed = ref(false)

const shouldShowTocCollapseToggle = computed(
  () => tocRenderResult.value.headings.length > TOC_COLLAPSE_THRESHOLD
)

const visibleTocHeadings = computed(() => {
  if (!tocCollapsed.value || !shouldShowTocCollapseToggle.value) {
    return tocRenderResult.value.headings
  }
  return tocRenderResult.value.headings.slice(0, TOC_PREVIEW_COUNT)
})

const handleTocJump = (id: string) => {
  const target = document.getElementById(id)
  if (!target) return
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  window.history.replaceState(null, '', `#${id}`)
}

watch(
  () => tocRenderResult.value.headings.length,
  len => {
    tocCollapsed.value = len > TOC_COLLAPSE_THRESHOLD
  },
  { immediate: true }
)

const INITIAL_COMMENT_PAGE_SIZE = 30

const fetchSecondaryData = (id: string, requestToken: number) => {
  const recommendCacheKey = `post:detail:recommend:${userStore.userId || 'anonymous'}:${id}`

  void commentApi.getByPostId(id, 1, INITIAL_COMMENT_PAGE_SIZE)
    .then((commentRes) => {
      if (requestToken !== _postFetchToken) return
      comments.value = commentRes.data.records || []
      commentsTotal.value = Number(commentRes.data.total || comments.value.length)
      commentsPage.value = 1
      void scrollToRouteComment()
    })
    .catch(() => {
      if (requestToken === _postFetchToken) comments.value = []
    })
    .finally(() => {
      if (requestToken === _postFetchToken) commentsInitialLoading.value = false
    })

  void cachedRequest(
      recommendCacheKey,
      RELATED_POST_CACHE_TTL,
      () => recommendApi.getSimilar(id)
    )
    .then((relatedRes) => {
      if (requestToken !== _postFetchToken) return
      relatedPosts.value = (relatedRes.data?.slice(0, 5) || []) as RecommendPost[]
    })
    .catch(() => {
      if (requestToken === _postFetchToken) relatedPosts.value = []
    })
    .finally(() => {
      if (requestToken === _postFetchToken) relatedPostsLoading.value = false
    })

  // 投票单独拉取，不进帖子详情缓存（票数频繁变动）；无投票时 data=null。
  void pollApi.getByPost(id)
    .then((pollRes) => {
      if (requestToken === _postFetchToken) poll.value = pollRes.data || null
    })
    .catch(() => {
      if (requestToken === _postFetchToken) poll.value = null
    })
    .finally(() => {
      if (requestToken === _postFetchToken) pollLoading.value = false
    })
}

const loadMoreComments = async () => {
  if (commentsLoadingMore.value || comments.value.length >= commentsTotal.value) return
  const id = postId.value
  const requestToken = _postFetchToken
  const nextPage = commentsPage.value + 1
  commentsLoadingMore.value = true
  try {
    const res = await commentApi.getByPostId(id, nextPage, INITIAL_COMMENT_PAGE_SIZE)
    if (requestToken !== _postFetchToken || postId.value !== id) return
    const records = (res.data.records || []) as Comment[]
    const knownIds = new Set(comments.value.map(item => String(item.id)))
    comments.value = comments.value.concat(records.filter(item => !knownIds.has(String(item.id))))
    commentsTotal.value = Number(res.data.total || comments.value.length)
    commentsPage.value = nextPage
  } catch {
    if (requestToken === _postFetchToken) {
      ElMessage.error('加载更多评论失败，请稍后重试')
    }
  } finally {
    if (requestToken === _postFetchToken) commentsLoadingMore.value = false
  }
}

const handlePrivateMessage = () => {
  if (!post.value?.userId) return
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再私信')
    return
  }
  if (post.value.userId === userStore.userId) {
    ElMessage.warning('不能给自己发送私信')
    return
  }
  router.push({
    path: '/messages',
    query: {
      peerId: post.value.userId,
      peerName: post.value.authorName || '',
    },
  })
}

const fetchPost = async () => {
  const id = postId.value
  const requestToken = ++_postFetchToken
  if (!id) {
    loading.value = false
    return
  }
  loading.value = true
  postLoadError.value = false
  post.value = null
  comments.value = []
  commentsTotal.value = 0
  commentsPage.value = 1
  commentsLoadingMore.value = false
  relatedPosts.value = []
  poll.value = null
  commentsInitialLoading.value = true
  relatedPostsLoading.value = true
  pollLoading.value = true
  versionHistory.value = []
  activeVersion.value = null
  isFollowed.value = false
  isSubscribed.value = false
  acceptedAnswerExpanded.value = false
  cancelReply()
  activeCommentId.value = getRouteCommentId()
  // 次要接口与详情并行；模板仍以详情接口为首屏门槛，评论/推荐/投票各自渐进呈现。
  fetchSecondaryData(id, requestToken)
  try {
    const postRes = await postApi.getDetail(id)
    if (requestToken !== _postFetchToken) return

    post.value = postRes.data || null
    loading.value = false
    syncPostMetricsToLists()
    applyPostSeo()
    if (canManageCurrentPost.value) {
      void loadVersionHistory()
    }
    void checkFollowStatus(post.value?.userId, requestToken)
    void checkSubscriptionStatus(id, requestToken)
    void viewLogApi.recordView(id).catch(() => {})
    // Song：开始追踪本帖阅读时长（前端心跳上报）
    dwellTime.start(id)
  } catch (error) {
    if (requestToken !== _postFetchToken) return
    post.value = null
    postLoadError.value = true
    ensureMetaByName('robots', 'noindex,nofollow')
    // 首屏失败由页面内"加载失败+重试"承载,不弹 toast
  } finally {
    if (requestToken === _postFetchToken) loading.value = false
  }
}

const isLiking = ref(false)
const isCollecting = ref(false)

const handleLike = async () => {
  if (!post.value) return
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再点赞')
    return
  }
  if (isLiking.value) return // 防抖:请求未回前忽略重复点击
  isLiking.value = true
  // 乐观更新:先翻转 UI,失败回滚
  const prevLiked = post.value.isLiked
  const prevCount = post.value.likeCount
  const nextLiked = !prevLiked
  post.value.isLiked = nextLiked
  post.value.likeCount = prevCount + (nextLiked ? 1 : -1)
  try {
    await postApi.like(post.value.id)
    if (nextLiked) {
      pulseNotification.like(`你点赞了「${post.value.title}」！已为该帖子注入一次共鸣脉冲。`)
    } else {
      pulseNotification.info(`已取消对帖子的点赞`)
    }
  } catch (error) {
    if (post.value) {
      post.value.isLiked = prevLiked
      post.value.likeCount = prevCount
    }
    pulseNotification.error('点赞操作失败，请重试')
  } finally {
    isLiking.value = false
  }
}

const handleCollect = async () => {
  if (!post.value) return
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再收藏')
    return
  }
  if (isCollecting.value) return
  isCollecting.value = true
  const prevCollected = post.value.isCollected
  const prevCount = post.value.collectCount
  const nextCollected = !prevCollected
  post.value.isCollected = nextCollected
  post.value.collectCount = prevCount + (nextCollected ? 1 : -1)
  try {
    await postApi.collect(post.value.id)
    if (nextCollected) {
      pulseNotification.success(`成功收纳「${post.value.title}」至你的灵感库`, '收藏成功')
    } else {
      pulseNotification.info(`已将该帖子移出你的灵感库`)
    }
  } catch (error) {
    if (post.value) {
      post.value.isCollected = prevCollected
      post.value.collectCount = prevCount
    }
    pulseNotification.error('收藏操作失败，请重试')
  } finally {
    isCollecting.value = false
  }
}

const handleCommentLike = async (comment: any) => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再点赞评论')
    return
  }
  const prevLiked = comment.isLiked
  const prevCount = comment.likeCount || 0
  const nextLiked = !prevLiked
  comment.isLiked = nextLiked
  comment.likeCount = prevCount + (nextLiked ? 1 : -1)
  try {
    await commentApi.like(comment.id)
    if (nextLiked) {
      pulseNotification.like(`你对 @${comment.nickname || '用户'} 的评论产生了共鸣`)
    } else {
      pulseNotification.info(`已取消对该评论的点赞`)
    }
  } catch (error) {
    comment.isLiked = prevLiked
    comment.likeCount = prevCount
    pulseNotification.error('点赞评论失败，请重试')
  }
}

const handleReply = (comment: any) => {
  replyingTo.value = comment.id
  replyingToName.value = comment.nickname || comment.author?.name || '匿名'
  // Song：说明
  setTimeout(() => {
    document.querySelector('.comment-editor')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, 100)
}

const cancelReply = () => {
  replyingTo.value = null
  replyingToName.value = null
}

const handleCommentDelete = async (comment: any) => {
  try {
    await commentApi.delete(comment.id)
    comment.auditStatus = 'DELETED'
    if (post.value) {
      const cur = toFiniteMetric(post.value.commentCount) ?? comments.value.length
      post.value.commentCount = Math.max(0, cur - 1)
      syncPostMetricsToLists()
    }
    pulseNotification.info('评论已删除')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

const handleCommentCollect = async (comment: any) => {
  try {
    const res = await commentApi.collect(comment.id)
    const nextCollected = typeof res.data?.isCollected === 'boolean'
      ? res.data.isCollected
      : !comment.isCollected
    const currentCount = Math.max(0, Number(comment.collectCount || 0))
    comment.isCollected = nextCollected
    comment.collectCount = Math.max(0, currentCount + (nextCollected ? 1 : -1))

    if (comment.isCollected) {
      pulseNotification.success(`已收藏 @${comment.nickname || '用户'} 的评论`, '收藏评论成功')
    } else {
      pulseNotification.info('已取消收藏该评论')
    }
  } catch (error: any) {
    pulseNotification.error(error?.response?.data?.message || '收藏评论失败，请重试')
  }
}

const handleCommentRestore = async (comment: any) => {
  try {
    await commentApi.restore(comment.id)
    if (post.value) {
      const cur = toFiniteMetric(post.value.commentCount) ?? comments.value.length
      post.value.commentCount = cur + 1
      syncPostMetricsToLists()
    }
    const commentRes = await commentApi.getByPostId(postId.value, 1, 120)
    comments.value = commentRes.data.records || []
    activeCommentId.value = String(comment.id || '')
    await scrollToComment(String(comment.id || ''))
    pulseNotification.success('评论已恢复，内容重新回到讨论区。', '恢复评论成功')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '恢复失败')
  }
}

const handleCommentReport = (comment: any) => {
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再进行举报')
    return
  }
  reportTarget.value = { type: 'comment', id: comment.id }
  reportForm.value.reason = ''
  reportForm.value.details = ''
  reportVisible.value = true
}

const submitComment = async (content: string) => {
  if (lotteryCommentBlockedReason.value) {
    ElMessage.warning(lotteryCommentBlockedReason.value)
    return
  }
  commentLoading.value = true
  try {
    await commentApi.add({
      postId: postId.value,
      content: content,
      parentId: replyingTo.value || undefined
    })

    pulseNotification.comment('你的想法已完美传递到主题讨论中，激荡智慧火花！', '回复评论成功')
    cancelReply()
    // 评论后后端会自动订阅本帖，前端同步状态避免再请求
    isSubscribed.value = true
    if (post.value) {
      const currentCount = toFiniteMetric(post.value.commentCount) ?? comments.value.length
      post.value.commentCount = currentCount + 1
      post.value.lastActivityAt = new Date().toISOString()
      syncPostMetricsToLists()
    }

    // Song：说明
    const commentRes = await commentApi.getByPostId(postId.value, 1, 120)
    comments.value = commentRes.data.records || []
    scrollToRouteComment()
  } catch (error: any) {
    pulseNotification.error(error?.response?.data?.message || '发送评论失败，请重试')
  } finally {
    commentLoading.value = false
  }
}

const reportVisible = ref(false)
const isReporting = ref(false)
const reportForm = ref({
  reason: '',
  details: ''
})
const reportTarget = ref<{ type: 'post' | 'comment'; id: string }>({ type: 'post', id: '' })

const ensureMetaByName = (name: string, content: string) => {
  let el = document.querySelector(`meta[name="${name}"]`) as HTMLMetaElement | null
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('name', name)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

const ensureMetaByProperty = (property: string, content: string) => {
  let el = document.querySelector(`meta[property="${property}"]`) as HTMLMetaElement | null
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('property', property)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

const ensureCanonical = (path: string) => {
  let link = document.querySelector('link[rel="canonical"]') as HTMLLinkElement | null
  if (!link) {
    link = document.createElement('link')
    link.setAttribute('rel', 'canonical')
    document.head.appendChild(link)
  }
  link.setAttribute('href', `https://www.allinsong.top${path}`)
}

const applyPostStructuredData = (canonicalUrl: string, description: string) => {
  if (!post.value) return
  document.querySelectorAll('[data-zens-post-seo]').forEach(element => element.remove())
  const script = document.createElement('script')
  script.type = 'application/ld+json'
  script.setAttribute('data-zens-post-seo', 'true')
  script.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'DiscussionForumPosting',
        '@id': `${canonicalUrl}#post`,
        headline: post.value.title,
        description,
        url: canonicalUrl,
        mainEntityOfPage: canonicalUrl,
        datePublished: post.value.createTime,
        dateModified: post.value.updateTime || post.value.createTime,
        author: { '@type': 'Person', name: post.value.authorName || 'Zens 社区成员' },
        image: postCoverImageUrl.value || undefined,
        interactionStatistic: [
          { '@type': 'InteractionCounter', interactionType: 'https://schema.org/CommentAction', userInteractionCount: post.value.commentCount || 0 },
          { '@type': 'InteractionCounter', interactionType: 'https://schema.org/LikeAction', userInteractionCount: post.value.likeCount || 0 },
        ],
      },
      {
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: 'Zens 首页', item: 'https://www.allinsong.top/' },
          { '@type': 'ListItem', position: 2, name: post.value.sectionName || '社区帖子', item: canonicalUrl },
        ],
      },
    ],
  })
  document.head.appendChild(script)
}

const applyPostSeo = () => {
  if (!post.value) return
  const canonicalPath = `/t/${encodePostId(post.value.id)}`
  const canonicalUrl = `https://www.allinsong.top${canonicalPath}`
  const title = `${post.value.title} - Zens`
  const description = (aiSummaryText.value || generateSummary(post.value.content || '', 160) || '开发者社区帖子详情').slice(0, 160)

  document.title = title
  ensureMetaByName('description', description)
  ensureMetaByName('robots', 'index,follow,max-image-preview:large')
  ensureMetaByName('twitter:card', 'summary_large_image')
  ensureMetaByName('twitter:title', title)
  ensureMetaByName('twitter:description', description)
  ensureMetaByProperty('og:title', title)
  ensureMetaByProperty('og:description', description)
  ensureMetaByProperty('og:type', 'article')
  ensureMetaByProperty('og:url', canonicalUrl)
  // Song：分享卡片配图（有封面时）
  if (post.value.coverImage) {
    const imageUrl = postCoverImageUrl.value || String(post.value.coverImage)
    ensureMetaByProperty('og:image', imageUrl)
    ensureMetaByName('twitter:image', imageUrl)
  }
  ensureCanonical(canonicalPath)
  applyPostStructuredData(canonicalUrl, description)
}

const handleCommand = async (command: string) => {
  if (command === 'report') {
    if (!userStore.accessToken) {
      ElMessage.warning('请先登录后再进行举报')
      return
    }
    reportTarget.value = { type: 'post', id: postId.value }
    reportForm.value.reason = ''
    reportForm.value.details = ''
    reportVisible.value = true
  } else if (command === 'edit') {
    composerStore.open({
      editId: post.value?.id,
      title: post.value?.title,
      content: post.value?.content,
      sectionId: post.value?.sectionId,
      tags: post.value?.tags,
      coverImage: post.value?.coverImage,
      postType: post.value?.postType,
      commentDeadline: post.value?.commentDeadline,
      commentOncePerUser: post.value?.commentOncePerUser
    })
  } else if (command === 'feature') {
    await handleFeature()
  } else if (command === 'delete') {
    await deletePost()
  } else if (command === 'restore') {
    await restorePost()
  } else if (command === 'versions') {
    await openVersionHistory()
  }
}

const loadVersionHistory = async () => {
  if (!post.value || !canManageCurrentPost.value) return
  versionHistoryLoading.value = true
  try {
    const res = await postApi.getVersions(post.value.id)
    versionHistory.value = res.data || []
    activeVersion.value = versionHistory.value[0] || null
  } catch {
    versionHistory.value = []
  } finally {
    versionHistoryLoading.value = false
  }
}

const openVersionHistory = async () => {
  versionHistoryVisible.value = true
  await loadVersionHistory()
}

const lineDiff = (oldText = '', newText = '') => {
  const oldLines = oldText.split('\n')
  const newLines = newText.split('\n')
  const max = Math.max(oldLines.length, newLines.length)
  const rows: Array<{ type: 'same' | 'add' | 'remove'; text: string }> = []
  for (let i = 0; i < max; i += 1) {
    const oldLine = oldLines[i]
    const newLine = newLines[i]
    if (oldLine === newLine) {
      if (oldLine !== undefined) rows.push({ type: 'same', text: oldLine })
    } else {
      if (oldLine !== undefined) rows.push({ type: 'remove', text: oldLine })
      if (newLine !== undefined) rows.push({ type: 'add', text: newLine })
    }
  }
  return rows
}

const activeVersionDiffRows = computed(() =>
  lineDiff(activeVersion.value?.content || '', post.value?.content || '')
)

const handleFeature = async () => {
  if (!post.value) return
  if (isDeletedPost.value) {
    ElMessage.warning('帖子已删除，请先恢复后再操作')
    return
  }
  if (!canModerateCurrentSection.value) {
    ElMessage.warning('仅当前板块版主或管理员可操作精华设置')
    return
  }
  try {
    await postApi.feature(post.value.id)
    post.value.isFeatured = post.value.isFeatured === 1 ? 0 : 1
    ElMessage.success(post.value.isFeatured ? '已设为精华' : '已取消精华主题')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const deletePost = async () => {
  if (!post.value || !canManageCurrentPost.value) return
  try {
    await ElMessageBox.confirm('确定要将这篇帖子移入回收站吗？7 天内可恢复。', '软删除确认', {
      confirmButtonText: '移入回收站',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await postApi.delete(post.value.id)
    post.value.auditStatus = 'DELETED'
    post.value.status = 0
    ElMessage.success('已移入回收站')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const restorePost = async () => {
  if (!post.value || !canManageCurrentPost.value) return
  try {
    await postApi.restore(post.value.id)
    post.value.auditStatus = 'APPROVED'
    post.value.status = 1
    ElMessage.success('帖子已恢复')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '恢复失败')
  }
}

const submitReport = async () => {
  if (!reportForm.value.reason) {
    ElMessage.warning('请选择举报理由')
    return
  }
  if (!reportTarget.value.id) {
    ElMessage.warning('举报对象缺失')
    return
  }
  isReporting.value = true
  try {
    await reportApi.create({
      targetType: reportTarget.value.type,
      targetId: reportTarget.value.id,
      reason: reportForm.value.reason,
      details: reportForm.value.details
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    reportVisible.value = false
  } catch (error) {
    ElMessage.error('举报提交失败')
  } finally {
    isReporting.value = false
  }
}

const handleShare = async () => {
  const url = window.location.href
  const title = post.value?.title || '分享帖子'
  try {
    await navigator.clipboard.writeText(url)
  } catch {
    // Song：说明
    const textarea = document.createElement('textarea')
    textarea.value = url
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
  }
  ElMessageBox.alert(
    `<p style="margin-bottom:8px;font-weight:600;">${title}</p><p style="word-break:break-all;color:var(--el-text-color-secondary);font-size:13px;">${url}</p><p style="margin-top:12px;color:var(--el-color-success);">链接已复制到剪贴板</p>`,
    '分享帖子',
    { dangerouslyUseHTMLString: true, confirmButtonText: '好的' }
  )
}

const handleRegenerateSummary = async () => {
  if (!post.value) return
  if (!canRegenerateSummary.value) {
    ElMessage.warning('仅作者、当前板块版主或管理员可重新生成摘要')
    return
  }
  regeneratingSummary.value = true
  try {
    const res = await postApi.regenerateSummary(post.value.id)
    post.value.summary = (res.data || '').trim()
    ElMessage.success('AI 摘要已更新')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '摘要生成失败')
  } finally {
    regeneratingSummary.value = false
  }
}

const formatDate = (dateStr: string) => {
  return timeAgo(dateStr)
}

const getRouteCommentId = () => {
  if (shortLinkTarget.value?.targetType === 'comment' && shortLinkTarget.value.commentId) {
    return shortLinkTarget.value.commentId
  }
  const queryCommentCode = route.query.c
  if (typeof queryCommentCode === 'string' && queryCommentCode.trim()) {
    return decodeCommentId(queryCommentCode.trim())
  }
  const queryCommentId = route.query.commentId
  if (typeof queryCommentId === 'string' && queryCommentId.trim()) {
    return decodeCommentId(queryCommentId.trim())
  }
  if (route.hash?.startsWith('#comment-')) {
    const hashCommentId = route.hash.replace('#comment-', '').trim()
    if (hashCommentId) {
      return decodeCommentId(hashCommentId)
    }
  }
  return ''
}

const hasCommentInTree = (list: Comment[], targetId: string): boolean => {
  for (const item of list || []) {
    if (String(item?.id || '') === targetId) {
      return true
    }
    if (item.children?.length && hasCommentInTree(item.children, targetId)) {
      return true
    }
  }
  return false
}

const ensureCommentLoadedForAnchor = async (commentId: string) => {
  if (!commentId || hasCommentInTree(comments.value, commentId)) {
    return true
  }

  const targetPostId = postId.value
  const requestToken = _postFetchToken
  const pageSize = INITIAL_COMMENT_PAGE_SIZE
  const collected: Comment[] = []
  let total = 0
  let page = 1
  let lastLoadedPage = 0

  while (page <= 100) {
    const res = await commentApi.getByPostId(targetPostId, page, pageSize)
    if (requestToken !== _postFetchToken || postId.value !== targetPostId) return false
    const records = (res.data.records || []) as Comment[]
    lastLoadedPage = page
    total = Number(res.data.total || 0)
    collected.push(...records)

    if (hasCommentInTree(collected, commentId)) {
      comments.value = collected
      commentsTotal.value = Math.max(total, collected.length)
      commentsPage.value = lastLoadedPage
      return true
    }

    if (records.length === 0 || collected.length >= total) {
      break
    }
    page++
  }

  if (collected.length > comments.value.length) {
    comments.value = collected
    commentsTotal.value = Math.max(total, collected.length)
    commentsPage.value = lastLoadedPage
  }
  return hasCommentInTree(comments.value, commentId)
}

const scrollToComment = async (commentId: string, retry = 0, triedLoading = false) => {
  activeCommentId.value = commentId
  if (!commentId) {
    return
  }
  await nextTick()
  const anchor = document.getElementById(`comment-${commentId}`)
  if (anchor) {
    anchor.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  if (retry < 4) {
    window.setTimeout(() => {
      scrollToComment(commentId, retry + 1, triedLoading)
    }, 180)
    return
  }

  if (!triedLoading) {
    const loaded = await ensureCommentLoadedForAnchor(commentId)
    if (loaded) {
      window.setTimeout(() => {
        scrollToComment(commentId, 0, true)
      }, 80)
    }
  }
}

const scrollToRouteComment = async (retry = 0) => {
  await scrollToComment(getRouteCommentId(), retry)
}

// 从已加载的评论树中找出被采纳的回复（后端按真实采纳记录标记 comment.isAdopted）
const acceptedAnswer = computed<Comment | null>(() => {
  const find = (list: Comment[]): Comment | null => {
    for (const item of list || []) {
      if (isTruthyFlag(item.isAdopted)) {
        return item
      }
      const child = find(item.children || [])
      if (child) return child
    }
    return null
  }
  return find(comments.value)
})
const acceptedAnswerAvatarUrl = computed(() => resolvePublicAssetUrl(acceptedAnswer.value?.userAvatar))

const acceptedAnswerId = computed(() => String(acceptedAnswer.value?.id || ''))

// 帖子下方最佳答案预览：去除 markdown 后截断的纯文本
const acceptedAnswerPreview = computed(() =>
  generateSummary(acceptedAnswer.value?.content || '', 220)
)

// 展开后的完整纯文本，以及是否需要“展开全文”
const acceptedAnswerFullText = computed(() =>
  stripMarkdown(acceptedAnswer.value?.content || '')
)
const acceptedAnswerTruncated = computed(() => acceptedAnswerFullText.value.length > 220)
const acceptedAnswerExpanded = ref(false)

// 被采纳回复在顶层评论中的楼层（后端无楼层字段，按顺序推导；嵌套回复返回 0 不展示）
const acceptedAnswerFloor = computed(() => {
  const id = acceptedAnswerId.value
  if (!id) return 0
  const idx = comments.value.findIndex((c) => String(c.id) === id)
  return idx >= 0 ? idx + 1 : 0
})

// 点击帖子下方“最佳答案入口”：平滑滚动到被采纳回复并短暂高亮
const scrollToAcceptedAnswer = () => {
  const id = acceptedAnswerId.value
  const el = id ? document.getElementById(`comment-${id}`) : null
  if (!el) {
    ElMessage.warning('最佳答案暂未加载，请稍后再试')
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('accepted-answer-highlight')
  window.setTimeout(() => {
    el.classList.remove('accepted-answer-highlight')
  }, 1600)
}

// 评论表情：批量拉取已加载评论(含楼中楼)的反应，避免每条单独请求
const commentReactions = ref<Record<string, ReactionResp>>({})
let _reactionFetchToken = 0

const collectCommentIds = (list: Comment[], acc: string[] = []): string[] => {
  for (const c of list || []) {
    if (c?.id != null) acc.push(String(c.id))
    if (c.children?.length) collectCommentIds(c.children, acc)
  }
  return acc
}

const loadCommentReactions = async () => {
  const requestToken = ++_reactionFetchToken
  const ids = collectCommentIds(comments.value)
  if (ids.length === 0) {
    commentReactions.value = {}
    return
  }
  try {
    const result = (await reactionApi.batch('comment', ids)).data || {}
    if (requestToken === _reactionFetchToken) commentReactions.value = result
  } catch {
    /* 静默：表情不阻塞评论区 */
  }
}

watch(comments, () => { loadCommentReactions() })

watch(
  () => postId.value,
  (newId, oldId) => {
    if (route.name === 'short-link-detail') {
      return
    }
    if (!newId || newId === oldId) {
      return
    }
    window.scrollTo({ top: 0, behavior: 'auto' })
    fetchPost()
  }
)

watch(
  () => route.params.code,
  async (code) => {
    if (route.name !== 'short-link-detail') {
      shortLinkTarget.value = null
      return
    }
    const codeValue = String(code || '').trim()
    if (!codeValue) {
      shortLinkTarget.value = null
      return
    }
    loading.value = true
    try {
      const res = await commentApi.resolveShortLink(codeValue)
      shortLinkTarget.value = res.data || null
      activeCommentId.value = getRouteCommentId()
      await fetchPost()
    } catch (e: any) {
      shortLinkTarget.value = null
      post.value = null
      comments.value = []
      ElMessage.error(e?.response?.data?.message || e?.message || '短链接不存在或已失效')
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)

watch(
  () => [route.query.c, route.query.commentId, route.hash],
  () => {
    scrollToRouteComment()
  }
)

onMounted(() => {
  activeCommentId.value = getRouteCommentId()
  if (route.name === 'short-link-detail') {
    return
  }
  fetchPost()
})
</script>

<template>
  <MainLayout>
    <div class="post-detail-container">
      <PageBackButton class="post-back-button" fallback="/" />
      <!-- 骨架屏 -->
      <div v-if="loading" class="post-skeleton">
        <div class="skel-card">
          <div class="skel-line w40"></div>
          <div class="skel-line w80 tall"></div>
          <div class="skel-line w60"></div>
          <div class="skel-block"></div>
          <div class="skel-line w90"></div>
          <div class="skel-line w70"></div>
          <div class="skel-line w80"></div>
        </div>
      </div>
      <transition name="page" mode="out-in">
        <div v-if="post && !loading" key="post-content" class="post-main">
        <!-- Post Content Card -->
        <el-card class="post-content-card" shadow="never">
          <!-- Article Header -->
          <div class="post-header">
            <div class="post-meta-top">
              <el-tag v-if="post.sectionName" size="small" effect="plain" class="category-tag">
                {{ post.sectionName }}
              </el-tag>
              <span class="post-date">{{ formatDate(post.createTime) }}</span>
            </div>
            
            <div class="post-title-row">
              <span
                v-if="isTruthyFlag(post.hasAdoptedAnswer)"
                class="solved-badge"
              >
                <el-icon class="solved-badge-icon"><CircleCheck /></el-icon>
                已解决
              </span>
              <h1 class="post-title">{{ post.title }}</h1>
            </div>

            <div v-if="isLotteryPost" class="lottery-rule-strip">
              <span class="lottery-rule-badge">抽奖贴</span>
              <span>登录账号评论即参与</span>
              <span v-if="post.commentOncePerUser">每人一次</span>
              <span>{{ lotteryDeadlineText }}</span>
              <span v-if="lotteryCommentClosed" class="lottery-rule-warning">已截止</span>
            </div>

            <el-alert
              v-if="isDeletedPost"
              type="warning"
              show-icon
              :closable="false"
              class="deleted-alert"
              title="该帖子已移入回收站，删除后 7 天内可由作者、版主或管理员恢复。"
            />
            
            <div class="author-info">
              <UserQuickCard
                :user-id="post.userId"
                :nickname="post.authorName"
                :avatar="postAuthorAvatarUrl"
                :roles="post.authorRoles"
              >
                <div class="avatar-wrapper">
                  <el-avatar :size="40" :src="postAuthorAvatarUrl">
                    {{ post.authorName?.charAt(0) || 'U' }}
                  </el-avatar>
                </div>
              </UserQuickCard>
              <div class="author-details">
                <span style="display:flex; align-items:center;">
                  <UserQuickCard
                    :user-id="post.userId"
                    :nickname="post.authorName"
                    :avatar="postAuthorAvatarUrl"
                    :roles="post.authorRoles"
                  >
                    <span class="author-name">{{ post.authorName }}</span>
                  </UserQuickCard>
                  <UserRoleBadge :roles="post.authorRoles" />
                  <TrustLevelBadge :trust-level="post.authorTrustLevel ?? 0" />
                  <UserBadge :text="post.authorBadgeText || ''" :color="post.authorBadgeColor" :effect="post.authorBadgeStyle" />
                </span>
                <div class="post-stats">
                  <span class="stat-item"><el-icon><View /></el-icon> {{ post.viewCount }}</span>
                  <span class="stat-item"><el-icon><ChatLineRound /></el-icon> {{ visibleCommentCount }}</span>
                  <span v-if="canManageCurrentPost" class="stat-item version-count">
                    <el-icon><Clock /></el-icon> 已更改 {{ versionChangeCount }} 次
                  </span>
                </div>
              </div>
              <el-button
                v-if="post.userId !== userStore.userId"
                :type="isFollowed ? 'default' : 'primary'"
                plain round size="small" class="follow-btn"
                :loading="followLoading"
                @click="handleFollow"
              >{{ isFollowed ? '已关注' : '关注' }}</el-button>
              <el-button
                v-if="post.userId !== userStore.userId"
                plain
                round
                size="small"
                @click="handlePrivateMessage"
              >
                私信
              </el-button>
              <el-button
                :type="isSubscribed ? 'default' : 'warning'"
                plain
                round
                size="small"
                class="subscribe-btn"
                :loading="subscribeLoading"
                @click="handleSubscribe"
              >
                <el-icon style="margin-right: 4px;"><BellFilled v-if="isSubscribed" /><Bell v-else /></el-icon>
                {{ isSubscribed ? '已追踪' : '追踪' }}
              </el-button>
              <el-dropdown trigger="click" @command="handleCommand" style="margin-left: 12px;">
                <el-button circle plain size="small">
                   <el-icon><More /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-if="!isDeletedPost && canManageCurrentPost"
                      command="edit"
                    >
                      <el-icon><EditPen /></el-icon> 编辑此贴
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="canManageCurrentPost"
                      command="versions"
                    >
                      <el-icon><Clock /></el-icon> 版本历史（{{ versionChangeCount }}）
                    </el-dropdown-item>
                    <el-dropdown-item v-if="!isDeletedPost" command="report">
                      <el-icon><Warning /></el-icon> 举报违规内容
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="!isDeletedPost && canModerateCurrentSection"
                      command="feature"
                    >
                      <el-icon><Medal /></el-icon> {{ post.isFeatured === 1 ? '取消精华' : '设为精华' }}
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="!isDeletedPost && canManageCurrentPost"
                      command="delete"
                      divided
                    >
                      <el-icon><Delete /></el-icon> 移入回收站
                    </el-dropdown-item>
                    <el-dropdown-item
                      v-if="isDeletedPost && canManageCurrentPost"
                      command="restore"
                      divided
                    >
                      <el-icon><RefreshRight /></el-icon> 恢复帖子
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <!-- Cover Image & Article Body (Hidden if deleted) -->
          <template v-if="!isDeletedPost">
            <!-- Cover Image -->
            <div v-if="postCoverImageUrl" class="post-cover">
              <img
                :src="postCoverImageUrl"
                :alt="post?.title ? `${post.title} 封面` : '帖子封面'"
                class="post-cover-image"
                loading="eager"
                decoding="async"
                fetchpriority="high"
              />
            </div>

            <!-- AI Summary -->
            <div v-if="aiSummaryText" class="ai-summary-card-premium">
              <div class="summary-header">
                <div class="summary-title-group">
                  <span class="summary-icon" aria-hidden="true">AI</span>
                  <span class="summary-badge-premium">AI 智能摘要</span>
                </div>
                <span class="summary-tip">DeepSeek-Chat 自动提炼</span>
                <el-button
                  v-if="canRegenerateSummary"
                  link
                  type="primary"
                  :loading="regeneratingSummary"
                  class="summary-refresh-btn-premium"
                  @click="handleRegenerateSummary"
                >
                  <el-icon style="margin-right: 4px;"><RefreshRight /></el-icon>重新生成
                </el-button>
              </div>
              <p class="summary-text">
                {{ displayedSummaryText }}
              </p>
              <div class="summary-footer">
                <span class="footer-meta">上下文摘要</span>
                <span class="footer-meta">已过滤格式噪声</span>
                <span class="footer-meta">结果仅供快速阅读</span>
              </div>
            </div>

            <!-- Article Content -->
            <div ref="contentRef" class="content-body markdown-body" v-html="renderedContent"></div>

            <!-- 帖子投票：紧跟正文，独立接口数据，不进详情缓存 -->
            <div v-if="pollLoading" class="secondary-loading poll-loading" aria-live="polite">
              正在加载投票…
            </div>
            <PollCard
              v-if="poll"
              :poll="poll"
              :post-title="post.title"
              @update="(p: Poll) => (poll = p)"
            />

            <!-- Tags -->
            <div v-if="post.tags" class="post-tags">
              <template v-for="tag in (typeof post.tags === 'string' ? post.tags.split(',') : post.tags)" :key="tag">
                 <el-tag 
                   v-if="tag && tag.trim()"
                   size="small"
                   class="tag-item"
                   @click="$router.push(`/tag/${tag.trim()}`)"
                   style="cursor: pointer;"
                 >
                   # {{ tag.trim() }}
                 </el-tag>
              </template>
            </div>

            <!-- 最佳答案快捷入口：已采纳时显示作者与内容预览，点击平滑滚动到被采纳的回复 -->
            <div
              v-if="isTruthyFlag(post.hasAdoptedAnswer)"
              class="accepted-answer-card"
              role="button"
              tabindex="0"
              @click="scrollToAcceptedAnswer"
              @keyup.enter="scrollToAcceptedAnswer"
            >
              <div class="accepted-card-head">
                <span class="accepted-entry-icon"><el-icon><CircleCheck /></el-icon></span>
                <span class="accepted-entry-title">已采纳最佳答案</span>
                <span class="accepted-card-action">查看完整回答 →</span>
              </div>

              <template v-if="acceptedAnswer">
                <div class="accepted-answer-author">
                  <el-avatar :size="22" :src="acceptedAnswerAvatarUrl">
                    {{ (acceptedAnswer.nickname || 'U').charAt(0) }}
                  </el-avatar>
                  <span class="accepted-answer-name">{{ acceptedAnswer.nickname || '匿名用户' }}</span>
                  <UserRoleBadge :roles="acceptedAnswer.roles || []" />
                  <UserBadge
                    :text="acceptedAnswer.userBadgeText || ''"
                    :color="acceptedAnswer.userBadgeColor"
                    :effect="acceptedAnswer.userBadgeStyle"
                  />
                </div>
                <p class="accepted-answer-snippet" :class="{ expanded: acceptedAnswerExpanded }">
                  {{ acceptedAnswerExpanded ? acceptedAnswerFullText : acceptedAnswerPreview }}
                </p>
                <button
                  v-if="acceptedAnswerTruncated"
                  type="button"
                  class="accepted-answer-toggle"
                  @click.stop="acceptedAnswerExpanded = !acceptedAnswerExpanded"
                >
                  {{ acceptedAnswerExpanded ? '收起' : '展开全文' }}
                </button>
                <div class="accepted-answer-meta">
                  <span v-if="acceptedAnswerFloor" class="accepted-meta-item">#{{ acceptedAnswerFloor }} 楼</span>
                  <span v-if="acceptedAnswerFloor" class="accepted-meta-dot">·</span>
                  <span class="accepted-meta-item"><el-icon><CaretTop /></el-icon>{{ acceptedAnswer.likeCount || 0 }} 赞</span>
                  <span class="accepted-meta-dot">·</span>
                  <span class="accepted-meta-item">{{ formatDate(acceptedAnswer.createTime) }}</span>
                </div>
              </template>
              <p v-else class="accepted-entry-desc">点击查看被采纳的回复</p>
            </div>

            <!-- Actions -->
            <div class="post-actions">
              <el-button-group>
                <el-button 
                  :type="post.isLiked ? 'primary' : 'default'" 
                  :icon="CaretTop"
                  @click="handleLike"
                >
                  赞 {{ post.likeCount }}
                </el-button>
                <el-button 
                  :type="post.isCollected ? 'warning' : 'default'" 
                  :icon="Star"
                  @click="handleCollect"
                >
                  收藏
                </el-button>
                <el-button :icon="Share" @click="handleShare">分享</el-button>
              </el-button-group>
            </div>

            <!-- Reactions -->
            <div class="post-reactions">
              <ReactionBar target-type="post" :target-id="post.id" />
            </div>
          </template>

          <!-- Deleted Post Premium Placeholder -->
          <div v-else class="deleted-post-placeholder">
            <div class="placeholder-icon-wrapper">
              <el-icon class="placeholder-icon"><Delete /></el-icon>
            </div>
            <h3 class="placeholder-title">该帖子已被移入回收站</h3>
            <p class="placeholder-tip">
              {{ canManageCurrentPost 
                ? '此话题已被软删除并移入回收站保护。在 7 天冷静期内，身为作者或社区管理人员的您可以随时点击下方恢复按钮快捷恢复。' 
                : '抱歉，该内容已被作者或管理员移入回收站。在恢复发布之前，普通用户将无法查阅其详细文本和多媒体正文。' 
              }}
            </p>
            <div v-if="canManageCurrentPost" class="placeholder-actions">
              <el-button 
                type="success" 
                size="large" 
                round
                :icon="RefreshRight" 
                @click="restorePost" 
                class="premium-restore-btn"
              >
                快捷恢复帖子
              </el-button>
            </div>
          </div>
        </el-card>

        <!-- Comment Section (Hidden if deleted) -->
        <div
          v-if="!isDeletedPost"
          v-loading="commentsInitialLoading"
          element-loading-text="正在加载评论"
          class="comment-section"
        >
          <div class="section-title">
            <h3>评论区</h3>
            <span class="comment-count">{{ visibleCommentCount }} 条评论</span>
          </div>

          <el-alert
            v-if="lotteryCommentBlockedReason"
            class="lottery-comment-alert"
            type="warning"
            :closable="false"
            show-icon
            :title="lotteryCommentBlockedReason"
          />
          
          <CommentEditor 
            v-if="canSubmitComment"
            :loading="commentLoading"
            :replying-to="replyingTo"
            :reply-name="replyingToName"
            @submit="submitComment"
            @cancel="cancelReply"
          />
          
          <CommentList
            :comments="(comments as any)"
            :total-comments="commentsTotal"
            :loading-more="commentsLoadingMore"
            :active-comment-id="activeCommentId"
            :post-id="postId"
            :post-short-id="encodePostId(postId)"
            :current-user-id="userStore.userId"
            :is-admin="isAdmin"
            :can-moderate-section="canModerateCurrentSection"
            :post-author-id="post?.userId"
            :reaction-map="commentReactions"
            :has-adoption="isTruthyFlag(post?.hasAdoptedAnswer)"
            :allow-adoption="isTruthyFlag(post?.allowAdoption)"
            @like="handleCommentLike"
            @collect="handleCommentCollect"
            @reply="handleReply"
            @delete="handleCommentDelete"
            @restore="handleCommentRestore"
            @report="handleCommentReport"
            @adopted="fetchPost"
            @canceled="fetchPost"
            @load-more="loadMoreComments"
          />
        </div>
        </div>
        <div v-else-if="postLoadError && !loading" key="post-error" class="post-load-error">
          <el-icon :size="44" color="#f0a020"><Warning /></el-icon>
          <p class="post-load-error-text">内容加载失败，请检查网络后重试</p>
          <el-button type="primary" round :loading="loading" @click="fetchPost">重新加载</el-button>
        </div>
        <el-empty v-else-if="!loading" key="post-empty" description="内容不存在或已被删除" />
      </transition>

      <ScrollEdgeBar v-if="post && !loading" />
    </div>

    <el-dialog
      v-model="versionHistoryVisible"
      :title="`帖子版本历史（已更改 ${versionChangeCount} 次）`"
      width="860px"
      append-to-body
      class="version-dialog"
    >
      <div v-loading="versionHistoryLoading" class="version-history">
        <div class="version-list">
          <button
            v-for="item in versionHistory"
            :key="item.id"
            type="button"
            class="version-item"
            :class="{ active: activeVersion?.id === item.id }"
            @click="activeVersion = item"
          >
            <span class="version-no">第 {{ item.versionNo }} 次更改</span>
            <span class="version-summary">{{ item.changeSummary || '内容更新' }}</span>
            <span class="version-meta">{{ item.editorName || '未知用户' }} · {{ formatDate(item.createdAt) }}</span>
          </button>
          <el-empty v-if="!versionHistoryLoading && versionHistory.length === 0" description="暂无版本历史" :image-size="72" />
        </div>

        <div class="version-detail" v-if="activeVersion">
          <div class="field-diff">
            <div class="field-row">
              <span class="field-label">标题</span>
              <div class="field-values">
                <span class="old-value">{{ activeVersion.title || '无标题' }}</span>
                <span class="arrow">→</span>
                <span class="new-value">{{ post?.title || '无标题' }}</span>
              </div>
            </div>
            <div class="field-row">
              <span class="field-label">标签</span>
              <div class="field-values">
                <span class="old-value">{{ activeVersion.tags || '无' }}</span>
                <span class="arrow">→</span>
                <span class="new-value">{{ post?.tags || '无' }}</span>
              </div>
            </div>
          </div>

          <div class="diff-panel">
            <div class="diff-head">正文差异</div>
            <div class="diff-body">
              <div
                v-for="(row, idx) in activeVersionDiffRows"
                :key="idx"
                class="diff-line"
                :class="row.type"
              >
                <span class="diff-mark">{{ row.type === 'add' ? '+' : row.type === 'remove' ? '-' : ' ' }}</span>
                <span class="diff-text">{{ row.text || ' ' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportVisible" title="举报内容" width="400px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="请选择举报理由（必选）">
          <el-select v-model="reportForm.reason" placeholder="选择举报理由" style="width: 100%;">
            <el-option label="垃圾广告/引流" value="垃圾广告" />
            <el-option label="色情低俗" value="色情低俗" />
            <el-option label="违法违规" value="违法违规" />
            <el-option label="人身攻击/网暴" value="人身攻击" />
            <el-option label="不实信息/谣言" value="不实信息" />
            <el-option label="其他原因" value="其他原因" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细说明（选填）">
          <el-input 
            v-model="reportForm.details" 
            type="textarea" 
            :rows="3" 
            placeholder="请提供更多细节帮助管理员判断..." 
            maxlength="200" 
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="reportVisible = false">取消</el-button>
          <el-button type="primary" @click="submitReport" :loading="isReporting">提交举报</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Right Rail Injection -->
    <template #right-rail>
      <div class="post-sidebar" v-if="post">
        <!-- Table of Contents -->
        <el-card v-if="showSidebarToc" shadow="never" class="sidebar-card toc-card">
          <template #header>
            <div class="card-header">
              <span>目录</span>
            </div>
          </template>
          <div v-if="tocRenderResult.headings.length > 0" class="toc-list">
            <a
              v-for="item in visibleTocHeadings"
              :key="item.id"
              :href="`#${item.id}`"
              class="toc-item"
              :class="`level-${item.level}`"
              @click.prevent="handleTocJump(item.id)"
            >
              {{ item.text }}
            </a>
            <el-button
              v-if="shouldShowTocCollapseToggle"
              link
              type="primary"
              class="toc-toggle-btn"
              @click="tocCollapsed = !tocCollapsed"
            >
              {{ tocCollapsed ? `展开全部（${tocRenderResult.headings.length}）` : '收起目录' }}
            </el-button>
          </div>
          <div v-else class="toc-empty">暂无可用标题</div>
        </el-card>

        <!-- Related Posts -->
        <el-card
          v-loading="relatedPostsLoading"
          element-loading-text="正在加载推荐"
          shadow="never"
          class="sidebar-card"
        >
          <template #header>
            <div class="card-header">
              <span>相关推荐</span>
            </div>
          </template>
          <div class="related-list">
            <div 
              v-for="item in relatedPosts" 
              :key="item.id" 
              class="related-item"
              @click="$router.push(`/p/${item.id}`)"
            >
              <div class="related-title">{{ item.title }}</div>
              <div v-if="item.recommendReason" class="related-reason">{{ item.recommendReason }}</div>
              <div v-if="item.summary" class="related-summary">{{ item.summary }}</div>
              <div class="related-meta">
                <span>{{ item.likeCount }} 赞</span>
                <span>{{ item.viewCount }} 浏览</span>
              </div>
            </div>
            <div v-if="!relatedPostsLoading && relatedPosts.length === 0" class="secondary-empty">
              暂无相关推荐
            </div>
          </div>
        </el-card>

        <!-- Site Rules / Guidance -->
        <el-card shadow="never" class="sidebar-card rules-card">
          <div class="rules-content">
            <template v-if="isLotteryPost">
              <h4>抽奖参与规则</h4>
              <ul>
                <li>需要登录主站账号参与</li>
                <li v-if="post.commentOncePerUser">每个账号仅保留一次有效评论</li>
                <li>匿名评论不会进入抽奖名单</li>
                <li v-if="post.commentDeadline">截止时间：{{ lotteryDeadlineText }}</li>
              </ul>
            </template>
            <template v-else>
              <h4>发布准则</h4>
              <ul>
                <li>保持社区友善交流</li>
                <li>请勿发布虚假信息</li>
                <li>尊重知识产权</li>
              </ul>
            </template>
          </div>
        </el-card>
      </div>
    </template>
  </MainLayout>
</template>

<style scoped>
.post-detail-page {
  min-height: 100vh;
  flex-direction: column;
  gap: 16px;
}

.post-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-load-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 64px 16px;
  text-align: center;
}

.post-load-error-text {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.skel-card {
  background: var(--el-bg-color-overlay);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skel-line, .skel-block {
  border-radius: 8px;
  background: linear-gradient(90deg, var(--el-fill-color) 25%, var(--el-fill-color-light) 37%, var(--el-fill-color) 63%);
  background-size: 400% 100%;
  animation: skel-shimmer 1.4s ease infinite;
}

.skel-line { height: 14px; width: 100%; }
.skel-line.tall { height: 28px; }
.skel-line.w40 { width: 40%; }
.skel-line.w60 { width: 60%; }
.skel-line.w70 { width: 70%; }
.skel-line.w80 { width: 80%; }
.skel-line.w90 { width: 90%; }
.skel-block { height: 200px; width: 100%; border-radius: 12px; }

@keyframes skel-shimmer {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

.post-detail-container {
  max-width: 100%;
}

.post-main {
  min-width: 0;
}

.post-back-button {
  margin-bottom: 12px;
}

.post-content-card {
  border-radius: var(--el-border-radius-base);
  margin-bottom: 24px;
}

.post-header {
  margin-bottom: 24px;
}

.post-meta-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.post-date {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.post-title {
  font-size: 28px;
  font-weight: 800;
  color: var(--el-text-color-primary);
  line-height: 1.3;
  margin: 0 0 20px 0;
}

.post-title-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 0 0 20px 0;
}

.post-title-row .post-title {
  min-width: 0;
  margin: 0;
}

.solved-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  margin-top: 6px;
  height: 26px;
  padding: 0 10px;
  border-radius: 7px;
  background: var(--accept-bg);
  border: 1px solid var(--accept-border);
  color: var(--accept-text);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.solved-badge-icon {
  font-size: 14px;
}

.lottery-rule-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: -8px 0 18px;
  padding: 9px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-color-primary-light-9) 70%, var(--cp-bg-card));
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}

.lottery-rule-strip span:not(:last-child)::after {
  content: '';
}

.lottery-rule-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
}

.lottery-rule-warning {
  color: var(--el-color-danger);
  font-weight: 700;
}

.lottery-comment-alert {
  margin-bottom: 16px;
}

/* 帖子正文下方：最佳答案预览卡片 */
.accepted-answer-card {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 10px;
  background: linear-gradient(90deg, var(--accept-bg) 0%, var(--accept-bg-soft) 100%);
  border: 1px solid var(--accept-border);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.accepted-answer-card:hover {
  background: var(--accept-bg-hover);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(246, 184, 0, 0.15);
}

.accepted-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.accepted-entry-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--accept-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: bold;
  flex-shrink: 0;
}

.accepted-entry-title {
  color: var(--accept-text);
  font-weight: 600;
  font-size: 14px;
}

.accepted-card-action {
  margin-left: auto;
  color: var(--accept-action);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.accepted-answer-author {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.accepted-answer-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--accept-text);
}

.accepted-answer-snippet {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.accepted-answer-snippet.expanded {
  display: block;
  -webkit-line-clamp: unset;
  overflow: visible;
}

.accepted-answer-toggle {
  margin-top: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--accept-action);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.2s ease;
}

.accepted-answer-toggle:hover {
  color: var(--accept-primary-hover);
  text-decoration: underline;
}

.accepted-answer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--accept-text-soft);
}

.accepted-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.accepted-meta-dot {
  opacity: 0.55;
}

.accepted-entry-desc {
  margin: 8px 0 0;
  color: var(--accept-text-soft);
  font-size: 13px;
}

.deleted-alert {
  margin: -4px 0 18px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.avatar-wrapper {
  position: relative;
  display: flex;
}

.admin-badge {
  position: absolute;
  right: -4px;
  bottom: -4px;
  font-size: 14px;
  color: #fff;
  background-color: var(--el-color-primary);
  border-radius: 50%;
  padding: 2px;
  border: 2px solid var(--cp-bg-card);
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  z-index: 2;
}

.author-details {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.post-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.post-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  margin: 0 0 24px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
}

.post-cover-image {
  display: block;
  width: auto;
  height: auto;
  max-width: min(100%, 720px);
  max-height: min(72vh, 760px);
  object-fit: contain;
  object-position: center;
}

.post-body {
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  margin-bottom: 32px;
}

.ai-summary-card-premium {
  margin-bottom: 24px;
  padding: 16px 20px;
  border: 1px solid rgba(245, 158, 11, 0.22);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(254, 243, 199, 0.2) 0%, rgba(255, 255, 255, 0.6) 100%);
  backdrop-filter: blur(10px);
  box-shadow: 0 4px 20px -2px rgba(245, 158, 11, 0.04);
  position: relative;
  overflow: hidden;
}

.ai-summary-card-premium::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #F59E0B 0%, #EF4444 100%);
}

.summary-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.summary-title-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.summary-icon {
  font-size: 15px;
  animation: pulseSparkle 2s infinite alternate;
}

@keyframes pulseSparkle {
  0% { transform: scale(0.92) rotate(0deg); opacity: 0.8; }
  100% { transform: scale(1.08) rotate(10deg); opacity: 1; }
}

.summary-badge-premium {
  font-size: 13px;
  font-weight: 800;
  color: #B45309;
}

.summary-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-refresh-btn-premium {
  margin-left: auto;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  color: #D97706 !important;
}

.summary-refresh-btn-premium:hover {
  color: #B45309 !important;
}

.summary-text {
  margin: 10px 0 12px 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
  text-align: justify;
}

.summary-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  border-top: 1px dashed rgba(245, 158, 11, 0.12);
  padding-top: 8px;
}

.footer-meta {
  white-space: nowrap;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}

.tag-item {
  cursor: pointer;
  transition: all 0.2s;
}

.tag-item:hover {
  background-color: var(--el-color-primary-light-9);
}

.post-actions {
  display: flex;
  justify-content: center;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.post-reactions {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}

.comment-section {
  background-color: var(--el-bg-color);
  padding: 24px;
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 24px;
}

.section-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
}

.comment-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* Song：说明 */
.sidebar-card {
  margin-bottom: 16px;
  overflow: hidden;
  min-width: 0;
  max-width: 100%;
  width: 100%;
}

.post-sidebar {
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: 100%;
  width: 100%;
}

.post-sidebar :deep(.el-card__header),
.post-sidebar :deep(.el-card__body) {
  display: block;
  min-width: 0;
  max-width: 100%;
  width: 100%;
  overflow: hidden;
}

.card-header,
.card-header > span,
.toc-list,
.related-list,
.related-meta,
.related-meta > span {
  display: block;
  min-width: 0;
  max-width: 100%;
  width: 100%;
}

.related-item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  padding: 12px 0;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  min-width: 0;
  max-width: 100%;
  width: 100%;
  overflow: hidden;
}

.related-item:last-child {
  border-bottom: none;
}

.related-item:hover .related-title {
  color: var(--el-color-primary);
}

.related-title {
  display: block;
  min-width: 0;
  max-width: 100%;
  width: 100%;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  line-height: 1.4;
  margin-bottom: 4px;
  transition: color 0.2s;
  white-space: normal;
  overflow: hidden;
  text-overflow: unset;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.related-reason {
  display: block;
  min-width: 0;
  max-width: 100%;
  width: 100%;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
  white-space: normal;
  overflow: hidden;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.related-summary {
  min-width: 0;
  max-width: 100%;
  width: 100%;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  white-space: normal;
  overflow: hidden;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.related-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.related-meta > span {
  display: inline-flex;
  width: auto;
  min-width: 0;
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.rules-card {
  background-color: var(--el-color-info-light-9);
  border-color: var(--el-color-info-light-8);
}

.rules-content h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.rules-content ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.rules-content li {
  margin-bottom: 6px;
}

.toc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  width: 100%;
}

.toc-item {
  display: block;
  min-width: 0;
  max-width: 100%;
  width: 100%;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  text-decoration: none;
  transition: color 0.2s;
  white-space: normal;
  overflow: hidden;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.toc-item:hover {
  color: var(--el-color-primary);
}

.toc-item.level-2 {
  padding-left: 10px;
}

.toc-item.level-3 {
  padding-left: 20px;
}

.toc-item.level-4 {
  padding-left: 30px;
}

.toc-item.level-5 {
  padding-left: 40px;
}

.toc-item.level-6 {
  padding-left: 50px;
}

.toc-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.toc-toggle-btn {
  align-self: flex-start;
  margin-top: 6px;
  padding-left: 0;
  font-size: 12px;
}

/* Song：说明 */
:deep(.markdown-body) {
  font-family: inherit;
}

.secondary-loading,
.secondary-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.poll-loading {
  margin: 18px 0;
  border: 1px dashed var(--el-border-color);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}
:deep(.content-body.markdown-body img) {
  display: block;
  width: auto !important;
  height: auto !important;
  max-width: min(100%, 720px) !important;
  max-height: min(72vh, 760px) !important;
  object-fit: contain !important;
  object-position: center;
  margin: 18px auto;
  border-radius: var(--el-border-radius-base);
}

:deep(.markdown-body video) {
  width: 100%;
  max-width: 100%;
  border-radius: 12px;
  background: #000;
  margin: 14px 0;
}

/* ── GitHub Link Card ── */
:deep(.markdown-body .github-link-card) {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 16px 0;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
  text-decoration: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
  cursor: pointer;
}
:deep(.markdown-body .github-link-card:hover) {
  border-color: #333;
  box-shadow: 0 2px 12px rgba(0,0,0,0.10);
  background: var(--el-fill-color-lighter);
}
:deep(.markdown-body .github-card-icon) {
  font-size: 28px;
  line-height: 1;
  flex-shrink: 0;
}
:deep(.markdown-body .github-card-info) {
  display: flex;
  flex-direction: column;
  gap: 3px;
  flex: 1;
  min-width: 0;
}
:deep(.markdown-body .github-card-title) {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.markdown-body .github-card-subtitle) {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
:deep(.markdown-body .github-card-url) {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.markdown-body .github-card-badge) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #24292f;
  flex-shrink: 0;
  white-space: nowrap;
}

/* ── External Link Card ── */
:deep(.markdown-body .ext-link-card) {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color-overlay);
  text-decoration: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  cursor: pointer;
}
:deep(.markdown-body .ext-link-card:hover) {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgba(0,0,0,0.07);
}
:deep(.markdown-body .ext-link-icon) {
  font-size: 20px;
  flex-shrink: 0;
}
:deep(.markdown-body .ext-link-info) {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
:deep(.markdown-body .ext-link-title) {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
:deep(.markdown-body .ext-link-host) {
  font-size: 12px;
  color: var(--el-color-primary);
}
:deep(.markdown-body .ext-link-arrow) {
  font-size: 16px;
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}

/* Deleted Post Placeholder premium styling */
.deleted-post-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
  background: radial-gradient(circle at top, rgba(245, 108, 108, 0.05), transparent 70%);
  border-radius: 12px;
  border: 1px dashed var(--el-border-color-lighter);
  margin: 20px 0;
}

.placeholder-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  border-radius: 50%;
  font-size: 28px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.2);
  animation: float-deleted-icon 3s ease-in-out infinite;
}

@keyframes float-deleted-icon {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.placeholder-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 12px 0;
  letter-spacing: 0.5px;
}

.placeholder-tip {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  max-width: 480px;
  margin: 0 0 28px 0;
}

.placeholder-actions {
  display: flex;
  justify-content: center;
}

.premium-restore-btn {
  font-weight: 600;
  padding: 12px 28px;
  font-size: 14px;
  box-shadow: 0 4px 14px rgba(103, 194, 58, 0.25);
  transition: transform 0.2s, box-shadow 0.2s;
}

.premium-restore-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(103, 194, 58, 0.35);
}

.premium-restore-btn:active {
  transform: translateY(0);
}

.version-count {
  color: var(--el-color-primary);
}

.version-history {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 16px;
  min-height: 420px;
}

.version-list {
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 12px;
  overflow: auto;
}

.version-item {
  width: 100%;
  text-align: left;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
}

.version-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.version-no,
.version-summary,
.version-meta {
  display: block;
}

.version-no {
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.version-summary {
  margin-top: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.version-meta {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.version-detail {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field-diff,
.diff-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.field-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.field-row:last-child {
  border-bottom: none;
}

.field-label {
  color: var(--el-text-color-secondary);
}

.field-values {
  display: flex;
  gap: 8px;
  min-width: 0;
}

.old-value,
.new-value {
  min-width: 0;
  overflow-wrap: anywhere;
}

.old-value {
  color: var(--el-color-danger);
}

.new-value {
  color: var(--el-color-success);
}

.diff-head {
  padding: 10px 12px;
  font-weight: 700;
  background: var(--el-fill-color-light);
}

.diff-body {
  max-height: 420px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.diff-line {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  padding: 2px 10px;
  white-space: pre-wrap;
}

.diff-line.add {
  background: rgba(103, 194, 58, 0.12);
}

.diff-line.remove {
  background: rgba(245, 108, 108, 0.12);
}

.diff-mark {
  user-select: none;
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .version-history {
    grid-template-columns: 1fr;
  }

  .version-list {
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-lighter);
    padding-right: 0;
    padding-bottom: 10px;
    max-height: 180px;
  }
}
</style>
