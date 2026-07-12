<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { ElMessage } from 'element-plus'
import { Activity, ArrowUpRight, Bot, BookOpen, FileText, LoaderCircle, MessageCircleQuestion, SendHorizontal } from 'lucide-vue-next'
import MainLayout from '@/layouts/MainLayout.vue'
import PageBackButton from '@/components/common/PageBackButton.vue'
import {
  agentApi,
  type AgentCitation,
  type AgentCommunityHealthResponse,
  type AgentHealthResponse,
  type AgentInsightPost,
  type AgentRelatedPost,
  type AgentTrace,
} from '@/api/agent'
import { publicDataApi } from '@/api/publicData'
import {
  buildAgentPromptFromTopic,
  DEFAULT_ENTERPRISE_AGENT_PROMPTS,
  dedupeTextList,
  formatDiscoveryTagName,
  isStableEnterpriseDiscoveryTopic,
} from '@/utils/communityDiscovery'
import { formatSectionName } from '@/utils/communitySections'
import { stripMarkdown } from '@/utils/markdown'
import { md } from '@/utils/markdownRenderer'
import { renderMarkdownWithTocAsync } from '@/utils/markdownToc'

interface SectionOption {
  id: number
  name: string
}

interface ConversationTurn {
  id: string
  question: string
  answer: string
  answerHtml: string
  followUpDraft: string
  parentQuestion?: string
  confidence?: 'low' | 'medium' | 'high'
  citations: AgentCitation[]
  relatedPosts: AgentRelatedPost[]
  trace?: AgentTrace
  isStreaming: boolean
  error: string
}

const route = useRoute()
const router = useRouter()

const question = ref('')
const selectedSectionId = ref<number | ''>('')
const sections = ref<SectionOption[]>([])
const hotPrompts = ref<string[]>([])
const turns = ref<ConversationTurn[]>([])
const health = ref<AgentHealthResponse | null>(null)
const healthLoading = ref(false)
const isStreaming = ref(false)
const currentTurnId = ref('')
const conversationRef = ref<HTMLElement | null>(null)
const insightLoading = ref<'weekly' | 'unanswered' | 'health' | ''>('')
const activeInsight = ref<'weekly' | 'unanswered' | 'health' | ''>('')
const insightPosts = ref<AgentInsightPost[]>([])
const communityHealth = ref<AgentCommunityHealthResponse | null>(null)
const insightError = ref('')

let streamAbortController: AbortController | null = null
let scrollFrameId = 0
let markdownRenderToken = 0
const FOLLOW_UP_CONTEXT_LIMIT = 260
const RETRIEVAL_TERM_LIMIT = 6
const FOLLOW_UP_SUGGESTIONS = [
  '展开一下实现细节',
  '如果放到生产环境要注意什么',
  '有没有更推荐的写法',
  '能给一个完整示例吗',
]
const QUESTION_STOP_WORDS = [
  '怎么', '如何', '一下', '一个', '这个', '那个', '是否', '有没有',
  '还是', '以及', '然后', '继续', '追问', '基于', '上文', '上一轮',
  '当前', '社区', '帖子', '评论', '回答', '问题', '帮我', '给我',
  '可以', '应该', '什么', '为啥', '为什么', '需要', '想问', '想知道',
]
const ENTERPRISE_PROMPT_KEYWORDS = [
  'agent', 'ai', 'api', 'app', 'bug', 'docker', 'go', 'java', 'js',
  'mysql', 'nginx', 'node', 'openai', 'python', 'redis', 'spring', 'sql',
  'sse', 'typescript', 'vue', 'websocket',
  '前端', '后端', '性能', '部署', '监控', '告警', '缓存', '索引', '事务',
  '并发', '架构', '鉴权', '权限', '登录', '接口', '检索', '搜索', '报错',
  '优化', '排查', '数据库', '主从', '网关', '流式', '问答',
]

const heroPrompts = computed(() => {
  return dedupeTextList([
    ...DEFAULT_ENTERPRISE_AGENT_PROMPTS.slice(0, 6),
    ...hotPrompts.value,
  ], 6)
})

const backendLabel = computed(() => {
  if (health.value?.backend === 'postgres') return 'PostgreSQL 检索副本'
  if (health.value?.backend === 'mysql') return 'MySQL 只读副本'
  return '等待连接'
})

const statusLabel = computed(() => {
  if (healthLoading.value) return '检测中'
  if (health.value?.status === 'ok') return '服务在线'
  if (health.value?.status === 'degraded') return '服务降级'
  return '未连接'
})

const currentSectionLabel = computed(() => {
  const matched = sections.value.find((item) => Number(item.id) === Number(selectedSectionId.value))
  return matched?.name || '全站帖子与评论'
})

const canSubmit = computed(() => question.value.trim().length >= 2 && !isStreaming.value)

const healthStatusLabel = computed(() => {
  if (communityHealth.value?.status === 'healthy') return '状态良好'
  if (communityHealth.value?.status === 'watch') return '持续观察'
  return '需要关注'
})

const buildTurnKey = (questionText: string, sectionId: number | '') =>
  `${questionText.trim()}::${sectionId ? Number(sectionId) : ''}`

const formatConfidence = (value?: 'low' | 'medium' | 'high') => {
  if (value === 'high') return '高置信'
  if (value === 'medium') return '中置信'
  if (value === 'low') return '低置信'
  return '待生成'
}

const buildTurn = (questionText: string, parentTurn?: ConversationTurn | null): ConversationTurn => ({
  id: createTurnId(),
  question: questionText,
  answer: '',
  answerHtml: '',
  followUpDraft: '',
  parentQuestion: parentTurn?.question,
  citations: [],
  relatedPosts: [],
  isStreaming: true,
  error: '',
})

const createTurnId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `turn-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

const parseRouteSectionId = () => {
  const raw = route.query.sectionId
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : ''
}

const escapeHtml = (text: string) => {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const isEnterprisePromptCandidate = (value: string) => {
  const text = String(value || '').trim()
  if (!text) return false
  if (text.length < 4 || text.length > 28) return false
  if (/^\d+$/.test(text)) return false
  if (!/[a-zA-Z\u4e00-\u9fff]/.test(text)) return false

  const lowerText = text.toLowerCase()
  return ENTERPRISE_PROMPT_KEYWORDS.some((keyword) => lowerText.includes(keyword))
}

const extractQuestionKeywords = (text: string) => {
  const normalized = String(text || '')
    .toLowerCase()
    .replace(/[\r\n]+/g, ' ')
    .replace(/[，。！？、,.!?:：;；()[\]{}【】<>《》"“”'‘’`~@#$%^&*=+|\\/]+/g, ' ')

  let working = normalized
  QUESTION_STOP_WORDS.forEach((item) => {
    working = working.split(item).join(' ')
  })

  const matches = working.match(/[a-z0-9+#._-]+|[\u4e00-\u9fff]{2,}/g) ?? []
  const deduped: string[] = []
  const seen = new Set<string>()
  matches.forEach((item) => {
    const keyword = item.trim()
    if (!keyword || keyword.length < 2 || seen.has(keyword)) {
      return
    }
    seen.add(keyword)
    deduped.push(keyword)
  })
  return deduped
}

const getMatchedTerms = (questionText: string, content: string, extraTerms: string[] = []) => {
  const source = String(content || '').toLowerCase()
  const candidates = [...extraTerms, ...extractQuestionKeywords(questionText)]
  const deduped: string[] = []
  const seen = new Set<string>()
  candidates.forEach((item) => {
    const keyword = String(item || '').trim()
    if (!keyword) return
    const lowerKey = keyword.toLowerCase()
    if (seen.has(lowerKey) || !source.includes(lowerKey)) {
      return
    }
    seen.add(lowerKey)
    deduped.push(keyword)
  })
  return deduped.slice(0, 4)
}

const highlightPlainText = (text: string, terms: string[]) => {
  const escaped = escapeHtml(text)
  if (!escaped || !terms.length) {
    return escaped
  }
  const pattern = new RegExp(
    terms
      .filter(Boolean)
      .sort((a, b) => b.length - a.length)
      .map((item) => escapeRegExp(item))
      .join('|'),
    'gi'
  )
  return escaped.replace(pattern, '<mark class="hit-mark">$&</mark>')
}

const getCitationMatchedTerms = (turn: ConversationTurn, citation: AgentCitation) => {
  return getMatchedTerms(turn.question, `${citation.title} ${citation.excerpt} ${citation.section_name || ''}`)
}

const getRelatedMatchedTerms = (turn: ConversationTurn, post: AgentRelatedPost) => {
  return getMatchedTerms(turn.question, `${post.title} ${post.summary || ''} ${post.section_name || ''}`)
}

const buildConversationContext = (turn: ConversationTurn) => {
  const answerSummary = stripMarkdown(turn.answer).slice(0, FOLLOW_UP_CONTEXT_LIMIT)
  const evidenceSummary = turn.citations
    .slice(0, 2)
    .map((item) => `[${item.index}] ${item.title}: ${item.excerpt}`)
    .join('\n')
  return [
    `上一轮问题：${turn.question}`,
    answerSummary ? `上一轮回答摘要：${answerSummary}` : '',
    evidenceSummary ? `上一轮关键引用：\n${evidenceSummary}` : '',
  ]
    .filter(Boolean)
    .join('\n')
    .slice(0, 1800)
}

const buildRetrievalQuery = (turn: ConversationTurn, followUpQuestion: string) => {
  const merged = [...extractQuestionKeywords(followUpQuestion), ...extractQuestionKeywords(turn.question)]
  const deduped: string[] = []
  const seen = new Set<string>()
  merged.forEach((item) => {
    const keyword = item.trim()
    if (!keyword || seen.has(keyword)) return
    seen.add(keyword)
    deduped.push(keyword)
  })
  if (deduped.length) {
    return deduped.slice(0, RETRIEVAL_TERM_LIMIT).join(' ')
  }
  return `${turn.question} ${followUpQuestion}`.trim().slice(0, 500)
}

const getFollowUpSuggestions = (turn: ConversationTurn) => {
  const keywords = extractQuestionKeywords(turn.question)
  const dynamic = keywords.length
    ? [`围绕 ${keywords.slice(0, 2).join(' / ')} 再展开一下`]
    : []
  return [...dynamic, ...FOLLOW_UP_SUGGESTIONS].slice(0, 4)
}

const formatRetryKeywordPhrase = (value: string) => {
  return String(value || '')
    .split(/\s+/)
    .filter(Boolean)
    .map((item) => {
      const canonical = formatDiscoveryTagName(item)
      if (/^[a-z][a-z0-9+._-]*$/i.test(canonical)) {
        return canonical.charAt(0).toUpperCase() + canonical.slice(1)
      }
      return canonical
    })
    .join(' ')
}

const isLowSignalTurn = (turn: ConversationTurn) => {
  const hitCount = Number(turn.trace?.hit_count ?? 0)
  return !turn.isStreaming
    && Boolean(turn.answer.trim())
    && turn.confidence === 'low'
    && hitCount <= 0
    && turn.citations.length === 0
}

const getRetryPromptSuggestions = (turn: ConversationTurn) => {
  const keywords = extractQuestionKeywords(turn.question)
  const primaryKeyword = keywords[0] ?? ''
  const sectionPrompt = currentSectionLabel.value !== '全站帖子与评论'
    ? `${currentSectionLabel.value} 里有没有相关经验帖？`
    : ''
  const keywordPrompts = keywords.length
    ? [
        `${formatRetryKeywordPhrase(keywords.slice(0, 2).join(' '))} 有哪些实际排查案例？`,
        `${formatRetryKeywordPhrase(primaryKeyword)} 相关问题一般会怎么定位？`,
      ]
    : []

  return dedupeTextList([
    ...keywordPrompts,
    sectionPrompt,
    ...DEFAULT_ENTERPRISE_AGENT_PROMPTS.slice(0, 3),
  ].filter((item) => item.trim().toLowerCase() !== turn.question.trim().toLowerCase()), 4)
}

const applyFollowUpSuggestion = (turn: ConversationTurn, suggestion: string) => {
  turn.followUpDraft = suggestion
}

const openSearchFromTurn = async (turn: ConversationTurn) => {
  const query: Record<string, string> = { q: turn.question }
  if (selectedSectionId.value) {
    query.sectionId = String(selectedSectionId.value)
  }
  await router.push({ path: '/search', query })
}

const syncRouteState = (syncAsk = false) => {
  const q = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  const routeSectionId = parseRouteSectionId()
  if (q) {
    question.value = q
  }
  selectedSectionId.value = routeSectionId

  if (!syncAsk || !q) {
    return
  }

  const turnKey = buildTurnKey(q, routeSectionId)
  if (turns.value.some((item) => buildTurnKey(item.question, routeSectionId) === turnKey)) {
    return
  }
  void askAgent(q, { syncRoute: false })
}

const updateRouteQuery = async (questionText: string) => {
  const nextQuery: Record<string, string> = { q: questionText }
  if (selectedSectionId.value) {
    nextQuery.sectionId = String(selectedSectionId.value)
  }
  await router.replace({ path: '/agent', query: nextQuery })
}

const scrollConversationToLatest = () => {
  if (scrollFrameId) {
    cancelAnimationFrame(scrollFrameId)
  }
  scrollFrameId = requestAnimationFrame(() => {
    const el = conversationRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  })
}

const sanitizeAnswerHtml = (html: string) => {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'p', 'br', 'hr', 'blockquote', 'pre', 'code',
      'ul', 'ol', 'li', 'strong', 'em', 'del', 's', 'mark',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'a', 'span', 'div', 'button',
    ],
    ALLOWED_ATTR: [
      'href', 'target', 'rel', 'class', 'id', 'style',
      'data-lang', 'data-raw', 'data-line', 'data-highlighted-chars', 'data-highlighted-chars-id',
      'aria-label', 'type',
    ],
    FORCE_BODY: true,
    ALLOW_DATA_ATTR: false,
  })
}

const renderTurnAnswer = async (turn: ConversationTurn) => {
  const token = ++markdownRenderToken
  const { html } = await renderMarkdownWithTocAsync(md, turn.answer, { inlineToc: false })
  if (token !== markdownRenderToken) {
    return
  }
  turn.answerHtml = sanitizeAnswerHtml(html)
  nextTick(() => scrollConversationToLatest())
}

const stopStreaming = (silent = false) => {
  if (!streamAbortController) {
    return
  }
  streamAbortController.abort()
  streamAbortController = null
  isStreaming.value = false

  const activeTurn = turns.value.find((item) => item.id === currentTurnId.value)
  if (activeTurn) {
    activeTurn.isStreaming = false
    if (!activeTurn.answer && !silent) {
      activeTurn.error = '已停止生成'
    }
  }
  currentTurnId.value = ''
}

const loadSections = async () => {
  try {
    const res = await publicDataApi.getActiveSectionsCached()
    sections.value = Array.isArray(res.data)
      ? res.data.map((item: any) => ({
          ...item,
          id: Number(item.id),
          name: formatSectionName(item.name || ''),
        }))
      : []
  } catch {
    sections.value = []
  }
}

const loadHotPrompts = async () => {
  try {
    const res = await publicDataApi.getHotTagsCached(10)
    hotPrompts.value = (Array.isArray(res.data) ? res.data : [])
      .map((item) => item?.name?.trim())
      .filter((item): item is string => Boolean(item)
        && isEnterprisePromptCandidate(item)
        && isStableEnterpriseDiscoveryTopic(item))
      .map((item) => buildAgentPromptFromTopic(item))
  } catch {
    hotPrompts.value = []
  }
}

const loadHealth = async () => {
  healthLoading.value = true
  try {
    const res = await agentApi.health({ skipGlobalProgress: true, silentError: true })
    health.value = res.data
  } catch {
    health.value = null
  } finally {
    healthLoading.value = false
  }
}

const loadInsight = async (kind: 'weekly' | 'unanswered' | 'health') => {
  if (insightLoading.value) return
  activeInsight.value = kind
  insightLoading.value = kind
  insightError.value = ''
  insightPosts.value = []
  communityHealth.value = null
  try {
    if (kind === 'weekly') {
      const res = await agentApi.weeklyDigest(7, 8, { skipGlobalProgress: true, silentError: true })
      insightPosts.value = res.data.highlights || []
    } else if (kind === 'unanswered') {
      const res = await agentApi.unanswered(14, 8, 0, { skipGlobalProgress: true, silentError: true })
      insightPosts.value = res.data.questions || []
    } else {
      const res = await agentApi.communityHealth(7, { skipGlobalProgress: true, silentError: true })
      communityHealth.value = res.data
    }
  } catch (error: any) {
    insightError.value = error?.message || '洞察服务暂时不可用'
  } finally {
    insightLoading.value = ''
  }
}

const openRoute = async (url: string) => {
  if (!url) return
  if (/^https?:\/\//i.test(url)) {
    window.open(url, '_blank', 'noopener')
    return
  }
  await router.push(url)
}

const openOriginalPost = async (citation: AgentCitation) => {
  const url = citation.url.split('#')[0] || citation.url
  await openRoute(url)
}

const askAgent = async (
  rawQuestion?: string,
  options: { syncRoute?: boolean; parentTurn?: ConversationTurn | null } = {}
) => {
  const normalizedQuestion = String(rawQuestion ?? question.value).trim()
  if (normalizedQuestion.length < 2) {
    ElMessage.warning('问题至少需要 2 个字符')
    return
  }

  if (isStreaming.value) {
    stopStreaming(true)
  }

  question.value = normalizedQuestion
  const turn = buildTurn(normalizedQuestion, options.parentTurn)
  turns.value.push(turn)
  currentTurnId.value = turn.id
  isStreaming.value = true
  scrollConversationToLatest()

  if (options.syncRoute !== false) {
    await updateRouteQuery(normalizedQuestion)
  }

  const controller = new AbortController()
  streamAbortController = controller

  try {
    await agentApi.streamAsk(
      {
        question: normalizedQuestion,
        retrievalQuery: options.parentTurn ? buildRetrievalQuery(options.parentTurn, normalizedQuestion) : undefined,
        conversationContext: options.parentTurn ? buildConversationContext(options.parentTurn) : undefined,
        sectionId: selectedSectionId.value ? Number(selectedSectionId.value) : undefined,
        limit: 6,
        includeComments: true,
        commentsPerPost: 2,
      },
      {
        signal: controller.signal,
        onContext(event) {
          turn.citations = event.citations || []
          turn.relatedPosts = event.related_posts || []
          turn.trace = event.trace
          turn.confidence = event.confidence
          scrollConversationToLatest()
        },
        onDelta(delta) {
          turn.answer += delta
          scrollConversationToLatest()
        },
        onDone(event) {
          turn.answer = event.answer || turn.answer
          turn.trace = event.trace
          turn.confidence = event.confidence
        },
      }
    )

    turn.isStreaming = false
    if (!turn.answer.trim()) {
      turn.error = '这次没有返回有效内容，请换个问法试试'
    } else {
      await renderTurnAnswer(turn)
    }
  } catch (error: any) {
    const isAbort = error?.name === 'AbortError' || controller.signal.aborted
    turn.isStreaming = false
    if (isAbort) {
      if (!turn.answer.trim()) {
        turn.error = '已停止生成'
      } else {
        turn.error = '已提前停止，本次回答保留已生成内容'
        await renderTurnAnswer(turn)
      }
      return
    }

    turn.error = error?.message || 'Agent 服务暂时不可用'
    if (turn.answer.trim()) {
      await renderTurnAnswer(turn)
    }
  } finally {
    if (streamAbortController === controller) {
      streamAbortController = null
    }
    isStreaming.value = false
    currentTurnId.value = ''
    void loadHealth()
  }
}

const handleSubmit = async () => {
  await askAgent()
}

const submitFollowUp = async (turn: ConversationTurn) => {
  const followUp = turn.followUpDraft.trim()
  if (followUp.length < 2) {
    ElMessage.warning('追问内容至少需要 2 个字符')
    return
  }
  turn.followUpDraft = ''
  await askAgent(followUp, { parentTurn: turn })
}

onMounted(() => {
  void loadSections()
  void loadHotPrompts()
  void loadHealth()
  syncRouteState(true)
})

watch(
  () => route.fullPath,
  () => {
    syncRouteState(turns.value.length === 0)
  }
)

onUnmounted(() => {
  stopStreaming(true)
  if (scrollFrameId) {
    cancelAnimationFrame(scrollFrameId)
    scrollFrameId = 0
  }
})
</script>

<template>
  <MainLayout>
    <div class="agent-page">
      <header class="agent-page-header">
        <PageBackButton class="agent-back" fallback="/" />
        <div class="agent-title-block">
          <p class="agent-kicker">社区问答</p>
          <h1>Agent 引用检索</h1>
          <p class="agent-subtitle">从帖子和评论里找历史经验，回答会尽量附上可回看的来源。</p>
        </div>

        <div class="agent-meta-row">
          <span class="agent-meta-pill">{{ statusLabel }}</span>
          <span class="agent-meta-pill">{{ backendLabel }}</span>
          <span class="agent-meta-pill">{{ currentSectionLabel }}</span>
        </div>
      </header>

      <section class="agent-tools" aria-labelledby="agent-tools-title">
        <div class="tools-header">
          <div>
            <p class="agent-kicker">只读社区洞察</p>
            <h2 id="agent-tools-title">选择一个 Agent 服务</h2>
            <p>按需查询只读从库；不自动轮询，也不会修改帖子、评论或用户数据。</p>
          </div>
          <span class="agent-meta-pill">点击后才请求</span>
        </div>

        <div class="tool-grid">
          <button type="button" class="tool-card" :class="{ active: activeInsight === 'weekly' }" @click="loadInsight('weekly')">
            <BookOpen class="tool-icon" />
            <strong>本周精选 Agent</strong>
            <span>按热度、收藏与真实讨论整理值得回看的内容。</span>
          </button>
          <button type="button" class="tool-card" :class="{ active: activeInsight === 'unanswered' }" @click="loadInsight('unanswered')">
            <MessageCircleQuestion class="tool-icon" />
            <strong>待回应问题 Agent</strong>
            <span>找出近两周尚无回复的问题，方便社区成员提供帮助。</span>
          </button>
          <button type="button" class="tool-card" :class="{ active: activeInsight === 'health' }" @click="loadInsight('health')">
            <Activity class="tool-icon" />
            <strong>社区健康 Agent</strong>
            <span>查看内容供给、回复覆盖、贡献者和阅读的七日快照。</span>
          </button>
        </div>

        <div v-if="activeInsight" class="insight-panel" aria-live="polite">
          <div v-if="insightLoading" class="insight-loading">
            <LoaderCircle class="stream-spinner" />
            <span>正在从只读副本整理数据...</span>
          </div>
          <div v-else-if="insightError" class="answer-error">{{ insightError }}</div>
          <div v-else-if="communityHealth" class="health-overview">
            <div class="health-score">
              <strong>{{ communityHealth.health_score }}</strong>
              <span>{{ healthStatusLabel }}</span>
            </div>
            <div class="health-copy">
              <h3>近 {{ communityHealth.window_days }} 天社区健康快照</h3>
              <p>{{ communityHealth.summary }}</p>
              <div class="health-metrics">
                <span>新帖 {{ communityHealth.published_posts }}</span>
                <span>评论 {{ communityHealth.approved_comments }}</span>
                <span>贡献者 {{ communityHealth.active_contributors }}</span>
                <span>回复覆盖 {{ Math.round(communityHealth.response_rate * 100) }}%</span>
                <span>未回复 {{ communityHealth.unanswered_posts }}</span>
                <span>阅读 {{ communityHealth.total_views }}</span>
              </div>
            </div>
          </div>
          <div v-else-if="insightPosts.length" class="insight-posts">
            <button
              v-for="post in insightPosts"
              :key="`${activeInsight}-${post.post_id}`"
              type="button"
              class="insight-post"
              @click="openRoute(post.url)"
            >
              <div>
                <span class="insight-reason">{{ post.reason }}</span>
                <strong>{{ post.title }}</strong>
                <p>{{ post.summary || '打开原帖查看完整内容与讨论。' }}</p>
                <small>{{ post.section_name || '社区' }} · {{ post.comment_count }} 回复 · {{ post.view_count }} 阅读</small>
              </div>
              <ArrowUpRight class="related-jump" />
            </button>
          </div>
          <div v-else class="conversation-empty compact">
            <h3>当前没有符合条件的内容</h3>
            <p>这通常意味着近期帖子已经得到回应，或当前时间窗口内没有新内容。</p>
          </div>
        </div>
      </section>

      <section class="agent-composer">
        <div class="composer-header">
          <div>
            <h2>发起提问</h2>
            <p>回车发送，Shift + Enter 换行。也可以先限定板块，再让 Agent 汇总。</p>
          </div>
          <div class="composer-header-badge">
            <Bot class="composer-header-icon" />
            <span>流式问答</span>
          </div>
        </div>

        <el-input
          v-model="question"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
          resize="none"
          placeholder="例如：Java Stack 应该怎么声明，社区里有没有更推荐的写法？"
          class="agent-question-input"
          @keydown.enter.exact.prevent="handleSubmit"
        />

        <div class="composer-toolbar">
          <el-select v-model="selectedSectionId" clearable placeholder="全站范围" class="composer-select">
            <el-option label="全站范围" value="" />
            <el-option
              v-for="section in sections"
              :key="section.id"
              :label="section.name"
              :value="section.id"
            />
          </el-select>

          <div class="composer-actions">
            <el-button v-if="isStreaming" plain @click="stopStreaming()">
              停止生成
            </el-button>
            <el-button type="primary" :disabled="!canSubmit" @click="handleSubmit">
              <SendHorizontal class="btn-icon" />
              <span>提问 Agent</span>
            </el-button>
          </div>
        </div>

        <div class="composer-prompts">
          <button
            v-for="prompt in heroPrompts"
            :key="prompt"
            type="button"
            class="prompt-chip"
            @click="askAgent(prompt)"
          >
            {{ prompt }}
          </button>
        </div>
      </section>

      <section ref="conversationRef" class="agent-conversation">
        <div class="conversation-header">
          <div>
            <h2>问答记录</h2>
            <p>每次回答都会附上命中的帖子引用和可跳转的原帖入口。</p>
          </div>
          <div class="conversation-meta">
            <Bot class="conversation-meta-icon" />
            <span>{{ turns.length }} 条会话</span>
          </div>
        </div>

        <div v-if="turns.length === 0" class="conversation-empty">
          <div class="empty-icon-wrap">
            <Bot class="empty-icon" />
          </div>
          <h3>还没有开始提问</h3>
          <p>先问一个具体问题，Agent 会基于社区帖子和评论整理出答案，并把引用来源一并挂出来。</p>
        </div>

        <div v-else class="turn-list">
          <article v-for="turn in turns" :key="turn.id" class="turn-item">
            <div class="turn-question">
              <span class="turn-role user">你的问题</span>
              <p v-if="turn.parentQuestion" class="turn-parent-note">
                延续上一轮：{{ turn.parentQuestion }}
              </p>
              <p>{{ turn.question }}</p>
            </div>

            <div class="turn-answer">
              <div class="turn-answer-head">
                <div class="turn-role agent">
                  <Bot class="turn-role-icon" />
                  <span>Agent 回答</span>
                </div>

                <div class="turn-trace" v-if="turn.trace">
                  <span class="trace-pill">{{ formatConfidence(turn.confidence) }}</span>
                  <span class="trace-pill">命中 {{ turn.trace.hit_count }}</span>
                  <span class="trace-pill">检索 {{ turn.trace.retrieval_ms }}ms</span>
                  <span class="trace-pill">{{ turn.trace.llm_ms > 0 ? `回答 ${turn.trace.llm_ms}ms` : '规则兜底' }}</span>
                </div>
              </div>

              <div v-if="turn.isStreaming" class="answer-stream" aria-live="polite">
                <div class="stream-state">
                  <LoaderCircle class="stream-spinner" />
                  <span>{{ turn.answer ? '正在继续生成回答...' : '正在整理社区讨论...' }}</span>
                </div>
                <div v-if="turn.answer" class="stream-content">{{ turn.answer }}</div>
              </div>

              <div v-else-if="turn.answerHtml" class="answer-markdown markdown-body" v-html="turn.answerHtml"></div>

              <div v-else class="answer-error">
                {{ turn.error || '暂时没有可展示的回答内容' }}
              </div>

              <div v-if="turn.error && turn.answerHtml" class="answer-warning">
                {{ turn.error }}
              </div>

              <div v-if="isLowSignalTurn(turn)" class="turn-section">
                <div class="section-title-row">
                  <h3>换个问法继续试</h3>
                  <span>当前没有检索到直接命中的历史讨论</span>
                </div>
                <div class="retry-panel">
                  <p class="retry-note">
                    可以补充板块范围、报错关键词、技术栈或期望结果，先把问题收窄到更像一条真实排查工单。
                  </p>
                  <div class="retry-actions">
                    <button
                      v-for="prompt in getRetryPromptSuggestions(turn)"
                      :key="`${turn.id}-retry-${prompt}`"
                      type="button"
                      class="retry-chip"
                      @click="askAgent(prompt)"
                    >
                      {{ prompt }}
                    </button>
                  </div>
                  <div class="retry-footer">
                    <span>也可以先去搜索页看看有没有接近的话题，再回到 Agent 汇总。</span>
                    <button type="button" class="retry-link" @click="openSearchFromTurn(turn)">
                      去搜索页查看
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="turn.citations.length" class="turn-section">
                <div class="section-title-row">
                  <h3>引用帖子卡片</h3>
                  <span>{{ turn.citations.length }} 条</span>
                </div>
                <div class="citation-grid">
                  <article
                    v-for="citation in turn.citations"
                    :key="`${turn.id}-${citation.index}-${citation.comment_id || citation.post_id}`"
                    class="citation-card"
                  >
                    <div class="citation-card-head">
                      <span class="citation-index">[{{ citation.index }}]</span>
                      <span class="citation-type">{{ citation.source_type === 'comment' ? '评论引用' : '帖子引用' }}</span>
                    </div>
                    <div v-if="getCitationMatchedTerms(turn, citation).length" class="citation-hit-terms">
                      <span
                        v-for="term in getCitationMatchedTerms(turn, citation)"
                        :key="`${turn.id}-${citation.index}-${term}`"
                        class="hit-term-chip"
                      >
                        {{ term }}
                      </span>
                    </div>
                    <h4 class="citation-highlight" v-html="highlightPlainText(citation.title, getCitationMatchedTerms(turn, citation))"></h4>
                    <p
                      class="citation-excerpt citation-highlight"
                      v-html="highlightPlainText(citation.excerpt, getCitationMatchedTerms(turn, citation))"
                    ></p>
                    <div class="citation-meta">
                      <span v-if="citation.section_name">{{ citation.section_name }}</span>
                      <span v-if="citation.comment_id">评论 ID {{ citation.comment_id }}</span>
                    </div>
                    <div class="citation-actions">
                      <button type="button" class="citation-link primary" @click="openOriginalPost(citation)">
                        查看原帖
                      </button>
                      <button
                        v-if="citation.source_type === 'comment'"
                        type="button"
                        class="citation-link"
                        @click="openRoute(citation.url)"
                      >
                        定位引用
                      </button>
                    </div>
                  </article>
                </div>
              </div>

              <div v-if="turn.relatedPosts.length" class="turn-section">
                <div class="section-title-row">
                  <h3>延伸讨论</h3>
                  <span>继续追溯上下文</span>
                </div>
                <div class="related-list">
                  <button
                    v-for="post in turn.relatedPosts"
                    :key="`${turn.id}-${post.post_id}`"
                    type="button"
                    class="related-item"
                    @click="openRoute(post.url)"
                  >
                    <div class="related-copy">
                      <div v-if="getRelatedMatchedTerms(turn, post).length" class="citation-hit-terms">
                        <span
                          v-for="term in getRelatedMatchedTerms(turn, post)"
                          :key="`${turn.id}-${post.post_id}-${term}`"
                          class="hit-term-chip"
                        >
                          {{ term }}
                        </span>
                      </div>
                      <div class="related-title-row">
                        <FileText class="related-icon" />
                        <strong class="citation-highlight" v-html="highlightPlainText(post.title, getRelatedMatchedTerms(turn, post))"></strong>
                      </div>
                      <p
                        v-if="post.summary"
                        class="citation-highlight"
                        v-html="highlightPlainText(post.summary, getRelatedMatchedTerms(turn, post))"
                      ></p>
                      <div class="related-meta">
                        <span v-if="post.section_name">{{ post.section_name }}</span>
                        <span>相关度 {{ post.score.toFixed(2) }}</span>
                      </div>
                    </div>
                    <ArrowUpRight class="related-jump" />
                  </button>
                </div>
              </div>

              <div v-if="turn.answer.trim() && !turn.isStreaming" class="turn-section">
                <div class="section-title-row">
                  <h3>连续追问</h3>
                  <span>会自动沿用上一轮上下文</span>
                </div>
                <div class="follow-up-panel">
                  <div class="follow-up-suggestions">
                    <button
                      v-for="suggestion in getFollowUpSuggestions(turn)"
                      :key="`${turn.id}-${suggestion}`"
                      type="button"
                      class="follow-up-chip"
                      @click="applyFollowUpSuggestion(turn, suggestion)"
                    >
                      {{ suggestion }}
                    </button>
                  </div>

                  <el-input
                    v-model="turn.followUpDraft"
                    type="textarea"
                    :autosize="{ minRows: 2, maxRows: 4 }"
                    resize="none"
                    placeholder="继续围绕这一轮的主题、实现细节或上线注意事项追问"
                    class="follow-up-input"
                    @keydown.enter.exact.prevent="submitFollowUp(turn)"
                  />

                  <div class="follow-up-footer">
                    <p>检索会保留上一轮的主题词，回答阶段会带上这轮摘要和引用上下文。</p>
                    <el-button
                      type="primary"
                      plain
                      :disabled="isStreaming || turn.followUpDraft.trim().length < 2"
                      @click="submitFollowUp(turn)"
                    >
                      继续追问
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>
  </MainLayout>
</template>

<style scoped>
.agent-page {
  --agent-accent: var(--cp-primary, var(--el-color-primary));
  --agent-accent-strong: var(--cp-primary-dark, var(--el-color-primary));
  --agent-surface: var(--cp-bg-surface, var(--el-fill-color-extra-light));
  --agent-surface-strong: color-mix(in srgb, var(--agent-accent) 10%, var(--cp-bg-card, var(--el-bg-color-overlay)));
  --agent-border: var(--cp-border, var(--el-border-color-lighter));
  --agent-border-strong: color-mix(in srgb, var(--agent-accent) 34%, var(--agent-border));
  display: grid;
  gap: 18px;
}

.agent-page-header,
.agent-tools,
.agent-composer,
.agent-conversation {
  border: 1px solid var(--agent-border);
  border-radius: 12px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  box-shadow: var(--shadow-soft, 0 1px 3px rgba(15, 23, 42, 0.05));
}

.agent-tools {
  padding: 20px 22px;
}

.tools-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.tools-header h2 {
  margin: 4px 0 6px;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.tools-header p:not(.agent-kicker) {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.tool-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.tool-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--agent-border);
  border-radius: 10px;
  background: var(--agent-surface);
  color: var(--el-text-color-primary);
  display: grid;
  justify-items: start;
  gap: 8px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, background-color 0.2s ease;
}

.tool-card:hover,
.tool-card.active {
  border-color: var(--agent-border-strong);
  background: var(--agent-surface-strong);
  transform: translateY(-1px);
}

.tool-icon {
  width: 22px;
  height: 22px;
  color: var(--agent-accent);
}

.tool-card strong {
  font-size: 15px;
}

.tool-card span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.insight-panel {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid var(--agent-border);
  border-radius: 10px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
}

.insight-loading {
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 700;
}

.insight-posts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.insight-post {
  padding: 14px;
  border: 1px solid var(--agent-border);
  border-radius: 9px;
  background: var(--agent-surface);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  cursor: pointer;
}

.insight-post:hover {
  border-color: var(--agent-border-strong);
}

.insight-post strong,
.insight-post p,
.insight-post small {
  display: block;
}

.insight-post strong {
  margin-top: 7px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.5;
}

.insight-post p {
  margin: 7px 0;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.6;
}

.insight-post small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.insight-reason {
  color: var(--agent-accent-strong);
  font-size: 11px;
  font-weight: 800;
}

.health-overview {
  display: flex;
  align-items: center;
  gap: 20px;
}

.health-score {
  width: 92px;
  min-width: 92px;
  height: 92px;
  border-radius: 50%;
  background: var(--agent-surface-strong);
  display: grid;
  place-content: center;
  text-align: center;
}

.health-score strong {
  color: var(--agent-accent-strong);
  font-size: 30px;
  line-height: 1;
}

.health-score span {
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.health-copy h3,
.health-copy p {
  margin: 0;
}

.health-copy h3 {
  color: var(--el-text-color-primary);
  font-size: 16px;
}

.health-copy p {
  margin-top: 7px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
}

.health-metrics {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.health-metrics span {
  min-height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--agent-surface);
  color: var(--el-text-color-secondary);
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
}

.conversation-empty.compact {
  min-height: 120px;
}

.agent-page-header {
  padding: 18px 20px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.agent-back {
  flex-shrink: 0;
  margin-right: 6px;
}

.agent-title-block {
  flex: 1;
  min-width: 0;
}

.agent-title-block h1 {
  margin: 4px 0 8px;
  font-size: 24px;
  line-height: 1.25;
  color: var(--el-text-color-primary);
}

.agent-kicker {
  margin: 0;
  color: var(--agent-accent);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0;
}

.agent-subtitle {
  margin: 0;
  max-width: 62ch;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.agent-meta-row {
  max-width: 310px;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.agent-meta-pill {
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid var(--agent-border);
  border-radius: 999px;
  background: var(--agent-surface);
  color: var(--el-text-color-regular);
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.agent-composer {
  padding: 20px 22px;
}

.composer-header,
.conversation-header,
.turn-answer-head,
.section-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.composer-header h2,
.conversation-header h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.composer-header p,
.conversation-header p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.composer-header-badge,
.conversation-meta {
  flex-shrink: 0;
  min-height: 34px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--agent-border);
  background: var(--agent-surface);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 700;
}

.composer-header-icon,
.conversation-meta-icon {
  width: 16px;
  height: 16px;
  color: var(--agent-accent);
}

.agent-question-input {
  margin-top: 16px;
}

.agent-question-input :deep(.el-textarea__inner) {
  border-radius: 10px;
  padding: 16px 18px;
  font-size: 15px;
  line-height: 1.7;
}

.composer-toolbar {
  margin-top: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.composer-select {
  width: 220px;
  max-width: 100%;
}

.composer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-icon {
  width: 16px;
  height: 16px;
  margin-right: 6px;
}

.composer-prompts {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-chip,
.follow-up-chip {
  border: 1px solid var(--agent-border);
  background: var(--agent-surface);
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.prompt-chip {
  min-height: 34px;
  border-radius: 999px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 700;
}

.retry-chip {
  min-height: 34px;
  border: 1px dashed var(--agent-border-strong);
  border-radius: 999px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  color: var(--agent-accent-strong);
  cursor: pointer;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 700;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.prompt-chip:hover,
.follow-up-chip:hover,
.retry-chip:hover,
.citation-link:hover,
.related-item:hover {
  border-color: var(--agent-border-strong);
  background: var(--agent-surface-strong);
  color: var(--agent-accent-strong);
  transform: translateY(-1px);
}

.agent-conversation {
  padding: 20px 22px;
  min-height: 420px;
}

.conversation-empty {
  min-height: 280px;
  display: grid;
  place-items: center;
  text-align: center;
  padding: 24px;
}

.empty-icon-wrap {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 12px;
  background: var(--agent-surface-strong);
  display: grid;
  place-items: center;
}

.empty-icon {
  width: 28px;
  height: 28px;
  color: var(--agent-accent);
}

.conversation-empty h3 {
  margin: 0 0 8px;
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.conversation-empty p {
  margin: 0;
  max-width: 54ch;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}

.turn-list {
  margin-top: 18px;
  display: grid;
  gap: 16px;
}

.turn-item {
  border: 1px solid var(--agent-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
}

.turn-question,
.turn-answer {
  padding: 18px 20px;
}

.turn-question {
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background: var(--agent-surface);
}

.turn-question p {
  margin: 8px 0 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  line-height: 1.7;
}

.turn-parent-note {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.turn-role {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.turn-role.user {
  color: var(--el-text-color-secondary);
}

.turn-role.agent {
  color: var(--agent-accent);
}

.turn-role-icon {
  width: 15px;
  height: 15px;
}

.turn-trace {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.trace-pill {
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--agent-surface-strong);
  color: var(--agent-accent-strong);
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
}

.answer-stream,
.answer-error,
.answer-warning {
  margin-top: 14px;
}

.stream-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 700;
}

.stream-spinner {
  width: 16px;
  height: 16px;
  animation: spin 1s linear infinite;
}

.stream-content {
  margin-top: 14px;
  white-space: pre-wrap;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.8;
}

.stream-content::after {
  content: '';
  display: inline-block;
  width: 8px;
  height: 1.1em;
  margin-left: 4px;
  vertical-align: text-bottom;
  background: color-mix(in srgb, var(--agent-accent) 72%, transparent);
  animation: blink 1s steps(1) infinite;
}

.answer-markdown {
  margin-top: 14px;
}

.answer-markdown :deep(p:first-child) {
  margin-top: 0;
}

.answer-error {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 14px;
  line-height: 1.6;
}

.answer-warning {
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--agent-surface-strong);
  color: var(--agent-accent-strong);
  font-size: 13px;
  line-height: 1.6;
}

.turn-section {
  margin-top: 20px;
}

.section-title-row h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.section-title-row span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.citation-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.citation-card {
  padding: 14px 15px;
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  display: grid;
  gap: 10px;
}

.citation-card-head,
.citation-hit-terms,
.citation-meta,
.citation-actions,
.related-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.citation-hit-terms {
  gap: 6px;
}

.citation-index,
.citation-type,
.citation-meta span,
.related-meta span {
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
}

.citation-card h4 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.5;
}

.citation-excerpt,
.related-copy p {
  margin: 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.7;
}

.hit-term-chip {
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--agent-surface-strong);
  color: var(--agent-accent-strong);
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
}

.citation-highlight :deep(.hit-mark) {
  padding: 0 2px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--agent-accent) 18%, transparent);
  color: inherit;
}

.citation-link {
  min-height: 32px;
  border-radius: 8px;
  border: 1px solid var(--agent-border);
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  color: var(--el-text-color-regular);
  cursor: pointer;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 700;
}

.citation-link.primary {
  color: var(--agent-accent-strong);
}

.related-list {
  margin-top: 12px;
  display: grid;
  gap: 10px;
}

.retry-panel {
  margin-top: 12px;
  padding: 14px 15px;
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  background: var(--agent-surface);
  display: grid;
  gap: 12px;
}

.retry-note {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.retry-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.retry-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.retry-link {
  min-height: 32px;
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  color: var(--agent-accent-strong);
  cursor: pointer;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 700;
}

.related-item {
  width: 100%;
  padding: 14px 15px;
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  cursor: pointer;
}

.related-copy {
  min-width: 0;
}

.related-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.related-title-row strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.55;
}

.related-icon,
.related-jump {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.related-icon {
  color: var(--agent-accent);
}

.related-jump {
  color: var(--el-text-color-placeholder);
}

.follow-up-panel {
  margin-top: 12px;
  padding: 14px 15px;
  border: 1px solid var(--agent-border);
  border-radius: 8px;
  background: var(--cp-bg-card, var(--el-bg-color-overlay));
  display: grid;
  gap: 12px;
}

.follow-up-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.follow-up-chip {
  min-height: 32px;
  border-radius: 999px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 700;
}

.follow-up-input :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 12px 14px;
  line-height: 1.7;
}

.follow-up-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.follow-up-footer p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes blink {
  0%, 49% {
    opacity: 1;
  }
  50%, 100% {
    opacity: 0;
  }
}

@media (max-width: 900px) {
  .agent-page-header,
  .agent-tools,
  .agent-composer,
  .agent-conversation {
    border-radius: 12px;
  }

  .agent-page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
  }

  .agent-meta-row {
    max-width: none;
    justify-content: flex-start;
  }

  .citation-grid {
    grid-template-columns: 1fr;
  }

  .tool-grid,
  .insight-posts {
    grid-template-columns: 1fr;
  }

  .composer-toolbar,
  .turn-answer-head,
  .section-title-row,
  .follow-up-footer,
  .retry-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-select {
    width: 100%;
  }

  .composer-actions {
    justify-content: flex-end;
  }

  .turn-trace {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .agent-page-header,
  .agent-tools,
  .agent-composer,
  .agent-conversation {
    padding-left: 16px;
    padding-right: 16px;
  }

  .agent-title-block h1 {
    font-size: 22px;
  }

  .agent-back {
    margin-right: 0;
  }

  .turn-question,
  .turn-answer {
    padding: 16px;
  }

  .composer-header,
  .tools-header,
  .conversation-header {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-header-badge,
  .conversation-meta {
    width: fit-content;
  }

  .follow-up-suggestions {
    gap: 6px;
  }

  .related-item {
    align-items: flex-start;
  }

  .health-overview {
    align-items: flex-start;
  }
}
</style>
