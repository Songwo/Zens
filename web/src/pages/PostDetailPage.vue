<script setup lang="ts">
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import ScrollEdgeBar from '@/components/ui/ScrollEdgeBar.vue'
import { postApi } from '@/api/post'
import { commentApi } from '@/api/comment'
import { recommendApi } from '@/api/recommend'
import { viewLogApi } from '@/api/viewLog'
import type { Post, Comment, RecommendPost } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { timeAgo } from '@/utils/timeAgo'
import { renderMarkdownWithTocResult } from '@/utils/markdownToc'
import { generateSummary } from '@/utils/markdown'
import { renderGithubRichCards, mergeConsecutiveBlockquotes } from '@/utils/richLink'
import { cachedRequest } from '@/utils/requestCache'
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
  RefreshRight
} from '@element-plus/icons-vue'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import UserQuickCard from '@/components/common/UserQuickCard.vue'
import CommentList from '@/components/comment/CommentList.vue'
import CommentEditor from '@/components/comment/CommentEditor.vue'
import { useUserStore } from '@/store/user'
import { usePostComposerStore } from '@/store/postComposer'
import { followApi } from '@/api/follow'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const relatedPosts = ref<RecommendPost[]>([])
const loading = ref(true)
const commentLoading = ref(false)
const replyingTo = ref<string | null>(null)
const replyingToName = ref<string | null>(null)
const regeneratingSummary = ref(false)
const activeCommentId = ref('')
const RELATED_POST_CACHE_TTL = 60 * 1000
const POST_METRICS_UPDATED_EVENT = 'cp:post-metrics-updated'

const postId = computed(() => String(route.params.id || ''))

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
    },
  }))
}

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.some((role: string) => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
})

const isGlobalModerator = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.includes('ROLE_MODERATOR')
})

const moderatedSectionIds = computed(() => {
  const rawIds = userStore.userInfo?.moderatedSectionIds || []
  return new Set(rawIds.map((id: number | string) => Number(id)).filter((id: number) => Number.isFinite(id) && id > 0))
})

const isDeletedPost = computed(() => post.value?.auditStatus === 'DELETED')

const canModerateCurrentSection = computed(() => {
  if (!post.value?.sectionId) return isAdmin.value || isGlobalModerator.value
  return isAdmin.value || isGlobalModerator.value || moderatedSectionIds.value.has(Number(post.value.sectionId))
})

const canManageCurrentPost = computed(() => {
  if (!post.value || !userStore.userId) return false
  return post.value.userId === userStore.userId || canModerateCurrentSection.value
})

const isFollowed = ref(false)
const followLoading = ref(false)

const checkFollowStatus = async () => {
  if (!userStore.accessToken || !post.value?.userId) return
  if (post.value.userId === userStore.userId) return
  try {
    const res = await followApi.isFollowing(post.value.userId)
    isFollowed.value = res.data === true
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

const renderedContent = computed(() => {
  const rawHtml = tocRenderResult.value.html
  const lazyImageHtml = rawHtml.replace(/<img\b(?![^>]*\bloading=)/gi, '<img loading="lazy" decoding="async" ')
  const mergedHtml = mergeConsecutiveBlockquotes(lazyImageHtml)
  const richHtml = renderGithubRichCards(mergedHtml)
  return DOMPurify.sanitize(richHtml, {
    ALLOWED_TAGS: [
      'h1','h2','h3','h4','h5','h6','p','br','hr',
      'ul','ol','li','blockquote','pre','code',
      'table','thead','tbody','tr','th','td',
      'a','img','strong','em','del','s','mark','kbd',
      'sup','sub','details','summary','input',
      'div','span','video','source',
      'svg','path'
    ],
    ALLOWED_ATTR: [
      'href','src','alt','title','class','id','target','rel',
      'type','checked','disabled','loading','decoding',
      'controls','preload','poster','playsinline','muted','loop','autoplay',
      'viewBox','width','height','aria-hidden','fill','d'
    ],
    FORCE_BODY: true,
    ALLOW_DATA_ATTR: false,
  })
})

const tocRenderResult = computed(() =>
  renderMarkdownWithTocResult(md, post.value?.content ?? '', { inlineToc: false })
)

const aiSummaryText = computed(() => {
  if (!post.value) return ''
  if (post.value.summary && post.value.summary.trim()) return post.value.summary.trim()
  return generateSummary(post.value.content || '', 180)
})

const canRegenerateSummary = computed(() => {
  if (!post.value || !userStore.userId) return false
  return !isDeletedPost.value && canManageCurrentPost.value
})

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

const fetchSecondaryData = async (id: string) => {
  const recommendCacheKey = `post:detail:recommend:${userStore.userId || 'anonymous'}:${id}`
  const [commentRes, relatedRes] = await Promise.allSettled([
    commentApi.getByPostId(id, 1, 120),
    cachedRequest(
      recommendCacheKey,
      RELATED_POST_CACHE_TTL,
      () => recommendApi.getSimilar(id)
    )
  ])

  return {
    comments: commentRes.status === 'fulfilled' ? commentRes.value.data.records || [] : [],
    relatedPosts: relatedRes.status === 'fulfilled'
      ? ((relatedRes.value.data?.slice(0, 5) || []) as RecommendPost[])
      : [],
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
  if (!id) {
    loading.value = false
    return
  }

  loading.value = true
  post.value = null
  comments.value = []
  relatedPosts.value = []
  cancelReply()
  activeCommentId.value = getRouteCommentId()
  try {
    const secondaryTask = fetchSecondaryData(id)
    const postRes = await postApi.getDetail(id)
    const secondaryData = await secondaryTask

    post.value = postRes.data || null
    comments.value = secondaryData.comments
    relatedPosts.value = secondaryData.relatedPosts
    syncPostMetricsToLists()
    applyPostSeo()
    scrollToRouteComment()
    void checkFollowStatus()
    void viewLogApi.recordView(id).catch(() => {})
  } catch (error) {
    post.value = null
    ElMessage.error('获取详情失败')
  } finally {
    loading.value = false
  }
}

const handleLike = async () => {
  if (!post.value) return
  try {
    await postApi.like(post.value.id)
    post.value.isLiked = !post.value.isLiked
    post.value.likeCount += post.value.isLiked ? 1 : -1
    ElMessage.success(post.value.isLiked ? '点赞成功' : '取消点赞')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleCollect = async () => {
  if (!post.value) return
  try {
    await postApi.collect(post.value.id)
    post.value.isCollected = !post.value.isCollected
    post.value.collectCount += post.value.isCollected ? 1 : -1
    ElMessage.success(post.value.isCollected ? '收藏成功' : '取消收藏')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const handleCommentLike = async (comment: any) => {
  try {
    await commentApi.like(comment.id)
    comment.isLiked = !comment.isLiked
    comment.likeCount = (comment.likeCount || 0) + (comment.isLiked ? 1 : -1)
  } catch (error) {
    ElMessage.error('操作失败')
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

const submitComment = async (content: string) => {
  commentLoading.value = true
  try {
    await commentApi.add({
      postId: postId.value,
      content: content,
      parentId: replyingTo.value || undefined
    })

    ElMessage.success('评论成功')
    cancelReply()
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
  } catch (error) {
    ElMessage.error('评论失败')
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
  link.setAttribute('href', `${window.location.origin}${path}`)
}

const applyPostSeo = () => {
  if (!post.value) return
  const canonicalPath = `/t/${post.value.id}`
  const title = `${post.value.title} - Zens`
  const description = (aiSummaryText.value || generateSummary(post.value.content || '', 160) || '校园社区帖子详情').slice(0, 160)

  document.title = title
  ensureMetaByName('description', description)
  ensureMetaByProperty('og:title', title)
  ensureMetaByProperty('og:description', description)
  ensureMetaByProperty('og:type', 'article')
  ensureMetaByProperty('og:url', `${window.location.origin}${canonicalPath}`)
  ensureCanonical(canonicalPath)
}

const handleCommand = async (command: string) => {
  if (command === 'report') {
    if (!userStore.accessToken) {
      ElMessage.warning('请先登录后再进行举报')
      return
    }
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
      coverImage: post.value?.coverImage
    })
  } else if (command === 'feature') {
    await handleFeature()
  } else if (command === 'delete') {
    await deletePost()
  } else if (command === 'restore') {
    await restorePost()
  }
}

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
  isReporting.value = true
  try {
    await postApi.report({
      targetType: 'post',
      targetId: postId.value,
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
  const queryCommentId = route.query.commentId
  if (typeof queryCommentId === 'string' && queryCommentId.trim()) {
    return queryCommentId.trim()
  }
  if (route.hash?.startsWith('#comment-')) {
    const hashCommentId = route.hash.replace('#comment-', '').trim()
    if (hashCommentId) {
      return hashCommentId
    }
  }
  return ''
}

const scrollToRouteComment = async (retry = 0) => {
  const commentId = getRouteCommentId()
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
      scrollToRouteComment(retry + 1)
    }, 180)
  }
}

watch(
  () => postId.value,
  (newId, oldId) => {
    if (!newId || newId === oldId) {
      return
    }
    window.scrollTo({ top: 0, behavior: 'auto' })
    fetchPost()
  }
)

watch(
  () => [route.query.commentId, route.hash],
  () => {
    scrollToRouteComment()
  }
)

onMounted(() => {
  activeCommentId.value = getRouteCommentId()
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
            
            <h1 class="post-title">{{ post.title }}</h1>

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
                :avatar="post.authorAvatar"
                :roles="post.authorRoles"
              >
                <div class="avatar-wrapper">
                  <el-avatar :size="40" :src="post.authorAvatar">
                    {{ post.authorName?.charAt(0) || 'U' }}
                  </el-avatar>
                </div>
              </UserQuickCard>
              <div class="author-details">
                <span style="display:flex; align-items:center;">
                  <UserQuickCard
                    :user-id="post.userId"
                    :nickname="post.authorName"
                    :avatar="post.authorAvatar"
                    :roles="post.authorRoles"
                  >
                    <span class="author-name">{{ post.authorName }}</span>
                  </UserQuickCard>
                  <UserRoleBadge :roles="post.authorRoles" />
                </span>
                <div class="post-stats">
                  <span class="stat-item"><el-icon><View /></el-icon> {{ post.viewCount }}</span>
                  <span class="stat-item"><el-icon><ChatLineRound /></el-icon> {{ visibleCommentCount }}</span>
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

          <!-- Cover Image -->
          <div v-if="post.coverImage" class="post-cover">
            <el-image :src="post.coverImage" fit="contain" lazy />
          </div>

          <!-- AI Summary -->
          <div v-if="aiSummaryText" class="ai-summary-card">
            <div class="summary-header">
              <span class="summary-badge">AI 摘要</span>
              <span class="summary-tip">基于正文自动提炼</span>
              <el-button
                v-if="canRegenerateSummary"
                link
                type="primary"
                :loading="regeneratingSummary"
                class="summary-refresh-btn"
                @click="handleRegenerateSummary"
              >
                重新生成
              </el-button>
            </div>
            <p class="summary-text">{{ aiSummaryText }}</p>
          </div>

          <!-- Article Content -->
          <div class="post-body markdown-body" v-html="renderedContent"></div>

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
        </el-card>

        <!-- Comment Section -->
        <div class="comment-section">
          <div class="section-title">
            <h3>评论区</h3>
            <span class="comment-count">{{ visibleCommentCount }} 条评论</span>
          </div>
          
          <CommentEditor 
            :loading="commentLoading"
            :replying-to="replyingTo"
            :reply-name="replyingToName"
            @submit="submitComment"
            @cancel="cancelReply"
          />
          
          <CommentList 
            :comments="(comments as any)"
            :active-comment-id="activeCommentId"
            @like="handleCommentLike"
            @reply="handleReply"
          />
        </div>
        </div>
        <el-empty v-else-if="!loading" key="post-empty" description="内容不存在或已被删除" />
      </transition>

      <ScrollEdgeBar v-if="post && !loading" />
    </div>

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
        <el-card shadow="never" class="sidebar-card">
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
          </div>
        </el-card>

        <!-- Site Rules / Guidance -->
        <el-card shadow="never" class="sidebar-card rules-card">
          <div class="rules-content">
            <h4>发布准则</h4>
            <ul>
              <li>保持社区友善交流</li>
              <li>请勿发布虚假信息</li>
              <li>尊重知识产权</li>
            </ul>
          </div>
        </el-card>
      </div>
    </template>
  </MainLayout>
</template>

<style scoped>
.post-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  margin: 0 -20px 24px -20px;
  background: var(--el-fill-color-lighter);
}

.post-cover :deep(.el-image) {
  width: 100%;
  max-height: min(70vh, 640px);
  display: block;
}

.post-body {
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  margin-bottom: 32px;
}

.ai-summary-card {
  margin-bottom: 24px;
  padding: 14px 16px;
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 12px;
  background: linear-gradient(180deg, var(--el-color-primary-light-9), var(--el-fill-color-blank));
}

.summary-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.summary-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-8);
}

.summary-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-refresh-btn {
  margin-left: auto;
  padding: 0;
  font-size: 12px;
}

.summary-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--el-text-color-primary);
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
:deep(.markdown-body h1, .markdown-body h2, .markdown-body h3) {
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
}
:deep(.markdown-body p) {
  margin-bottom: 16px;
}
:deep(.markdown-body img) {
  max-width: 100%;
  border-radius: var(--el-border-radius-base);
}

:deep(.markdown-body video) {
  width: 100%;
  max-width: 100%;
  border-radius: 12px;
  background: #000;
  margin: 14px 0;
}

/* ── Blockquote ── */
:deep(.markdown-body blockquote) {
  margin: 16px 0;
  padding: 12px 16px 12px 20px;
  border-left: 4px solid var(--el-color-primary-light-5);
  border-radius: 0 8px 8px 0;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-style: italic;
}
:deep(.markdown-body blockquote p) {
  margin-bottom: 6px;
}
:deep(.markdown-body blockquote p:last-child) {
  margin-bottom: 0;
}
:deep(.markdown-body blockquote blockquote) {
  margin: 8px 0 0;
  border-left-color: var(--el-border-color);
  background: var(--el-fill-color-light);
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
</style>
