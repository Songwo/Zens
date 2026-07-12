import type { AxiosRequestConfig } from 'axios'
import api from '@/lib/api'
import type { Result } from '@/types'
import { getOrCreateDeviceId } from '@/utils/device'

export interface CommunityQaAskPayload {
  question: string
  retrievalQuery?: string
  conversationContext?: string
  sectionId?: number
  limit?: number
  includeComments?: boolean
  commentsPerPost?: number
}

export interface AgentCitation {
  index: number
  post_id: string
  title: string
  section_name?: string
  excerpt: string
  source_type: 'post' | 'comment'
  comment_id?: string
  url: string
}

export interface AgentRelatedPost {
  post_id: string
  title: string
  section_name?: string
  summary?: string
  tags: string[]
  score: number
  url: string
}

export interface AgentTrace {
  backend: string
  retrieval_ms: number
  llm_ms: number
  total_ms: number
  hit_count: number
  fallback_reason?: string
}

export interface CommunityQaAskResponse {
  answer: string
  confidence: 'low' | 'medium' | 'high'
  citations: AgentCitation[]
  related_posts: AgentRelatedPost[]
  trace: AgentTrace
  backend?: string
  fallback_reason?: string
}

export interface CommunityQaSearchResponse {
  citations: AgentCitation[]
  related_posts: AgentRelatedPost[]
  hit_count: number
  backend: string
  fallback_reason?: string
}

export interface AgentHealthResponse {
  status: string
  backend: string
  postgres: string
  mysql: string
  service?: string
  version?: string
  uptime_seconds?: number
  search_backend?: string
  llm_enabled?: boolean
  llm_configured?: boolean
  llm_status?: string
  llm_model?: string
  default_search_limit?: number
  min_question_length?: number
  default_comments_per_post?: number
  [key: string]: unknown
}

export interface AgentInsightPost {
  post_id: string
  title: string
  summary: string
  section_name?: string
  tags: string[]
  comment_count: number
  like_count: number
  collect_count: number
  view_count: number
  score: number
  reason: string
  created_at: string
  url: string
}

export interface AgentWeeklyDigestResponse {
  window_days: number
  generated_at: string
  highlights: AgentInsightPost[]
  backend: string
}

export interface AgentUnansweredResponse {
  window_days: number
  max_comments: number
  questions: AgentInsightPost[]
  backend: string
}

export interface AgentCommunityHealthResponse {
  window_days: number
  generated_at: string
  published_posts: number
  approved_comments: number
  active_contributors: number
  unanswered_posts: number
  engaged_posts: number
  total_views: number
  response_rate: number
  comments_per_post: number
  health_score: number
  status: 'healthy' | 'watch' | 'needs_attention'
  summary: string
  backend: string
}

export interface AgentAdminStatus {
  enabled: boolean
  baseUrl: string
  connectTimeoutMs: number
  readTimeoutMs: number
  checkedAt: string
  status: string
  reachable: boolean
  latencyMs?: number
  backend?: string
  postgres?: string
  mysql?: string
  llmEnabled?: boolean
  llmConfigured?: boolean
  llmStatus?: string
  llmModel?: string
  health?: AgentHealthResponse
  error?: string
  advice?: string[]
}

export interface AgentSmokeTestResult {
  ok: boolean
  latencyMs: number
  checkedAt: string
  question: string
  response?: CommunityQaAskResponse
  error?: string
}

export interface AgentStreamContextEvent {
  question: string
  confidence: 'low' | 'medium' | 'high'
  citations: AgentCitation[]
  related_posts: AgentRelatedPost[]
  trace: AgentTrace
}

export interface AgentStreamDoneEvent {
  answer: string
  confidence: 'low' | 'medium' | 'high'
  trace: AgentTrace
}

export interface AgentStreamErrorEvent {
  message: string
}

export interface AgentStreamHandlers {
  signal?: AbortSignal
  onContext?: (event: AgentStreamContextEvent) => void
  onDelta?: (delta: string) => void
  onDone?: (event: AgentStreamDoneEvent) => void
  onError?: (event: AgentStreamErrorEvent) => void
}

const API_BASE_URL = `${(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')}/api`

function toSnakePayload(payload: CommunityQaAskPayload) {
  return {
    question: payload.question,
    retrievalQuery: payload.retrievalQuery,
    conversationContext: payload.conversationContext,
    sectionId: payload.sectionId,
    limit: payload.limit,
    includeComments: payload.includeComments,
    commentsPerPost: payload.commentsPerPost,
  }
}

export const agentApi = {
  ask(payload: CommunityQaAskPayload, config?: AxiosRequestConfig) {
    return api.post<any, Result<CommunityQaAskResponse>>(
      '/agent/community-qa/ask',
      toSnakePayload(payload),
      config
    )
  },

  search(payload: CommunityQaAskPayload, config?: AxiosRequestConfig) {
    return api.post<any, Result<CommunityQaSearchResponse>>(
      '/agent/community-qa/search',
      toSnakePayload(payload),
      config
    )
  },

  health(config?: AxiosRequestConfig) {
    return api.get<any, Result<AgentHealthResponse>>('/agent/health', config)
  },

  weeklyDigest(days = 7, limit = 8, config?: AxiosRequestConfig) {
    return api.get<any, Result<AgentWeeklyDigestResponse>>('/agent/insights/weekly-digest', {
      ...config,
      params: { ...(config?.params || {}), days, limit },
    })
  },

  unanswered(days = 14, limit = 8, maxComments = 0, config?: AxiosRequestConfig) {
    return api.get<any, Result<AgentUnansweredResponse>>('/agent/insights/unanswered', {
      ...config,
      params: { ...(config?.params || {}), days, limit, maxComments },
    })
  },

  communityHealth(days = 7, config?: AxiosRequestConfig) {
    return api.get<any, Result<AgentCommunityHealthResponse>>('/agent/insights/community-health', {
      ...config,
      params: { ...(config?.params || {}), days },
    })
  },

  adminStatus(config?: AxiosRequestConfig) {
    return api.get<any, Result<AgentAdminStatus>>('/admin/agent/status', config)
  },

  adminSmokeTest(payload: CommunityQaAskPayload, config?: AxiosRequestConfig) {
    return api.post<any, Result<AgentSmokeTestResult>>(
      '/admin/agent/smoke-test',
      toSnakePayload(payload),
      config
    )
  },

  async streamAsk(payload: CommunityQaAskPayload, handlers: AgentStreamHandlers = {}) {
    const response = await fetch(`${API_BASE_URL}/agent/community-qa/ask-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Device-Id': getOrCreateDeviceId(),
      },
      body: JSON.stringify(toSnakePayload(payload)),
      signal: handlers.signal,
    })

    if (!response.ok) {
      const message = await readErrorMessage(response)
      throw new Error(message)
    }

    if (!response.body) {
      throw new Error('Agent 流式响应不可用')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      const remaining = consumeSseBuffer(buffer, handlers)
      buffer = remaining
      if (done) {
        break
      }
    }

    const finalBuffer = decoder.decode()
    if (finalBuffer) {
      buffer += finalBuffer
    }
    if (buffer.trim()) {
      consumeSseBuffer(`${buffer}\n\n`, handlers)
    }
  },
}

function consumeSseBuffer(buffer: string, handlers: AgentStreamHandlers) {
  const normalized = buffer.replace(/\r\n/g, '\n')
  let working = normalized

  while (true) {
    const boundaryIndex = working.indexOf('\n\n')
    if (boundaryIndex === -1) {
      return working
    }

    const block = working.slice(0, boundaryIndex).trim()
    working = working.slice(boundaryIndex + 2)
    if (!block) {
      continue
    }
    handleSseBlock(block, handlers)
  }
}

function handleSseBlock(block: string, handlers: AgentStreamHandlers) {
  const lines = block.split('\n')
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim() || 'message'
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  if (!dataLines.length) {
    return
  }

  const rawPayload = dataLines.join('\n')
  let payload: any = rawPayload
  try {
    payload = JSON.parse(rawPayload)
  } catch {
    // ignore non-json events
  }

  if (eventName === 'context') {
    handlers.onContext?.(payload as AgentStreamContextEvent)
    return
  }
  if (eventName === 'delta') {
    handlers.onDelta?.(String(payload?.delta || ''))
    return
  }
  if (eventName === 'done') {
    handlers.onDone?.(payload as AgentStreamDoneEvent)
    return
  }
  if (eventName === 'error') {
    const errorEvent = payload as AgentStreamErrorEvent
    handlers.onError?.(errorEvent)
    throw new Error(errorEvent?.message || 'Agent 流式响应失败')
  }
}

async function readErrorMessage(response: Response) {
  try {
    const contentType = response.headers.get('content-type') || ''
    if (contentType.includes('application/json')) {
      const payload = await response.json()
      return payload?.message || payload?.detail || 'Agent 服务暂时不可用'
    }
    const text = await response.text()
    const sseMatch = text.match(/data:\s*(\{.*\})/s)
    if (sseMatch?.[1]) {
      try {
        const payload = JSON.parse(sseMatch[1])
        if (payload?.message) {
          return payload.message
        }
      } catch {
        // ignore invalid sse payload
      }
    }
    return text || 'Agent 服务暂时不可用'
  } catch {
    return 'Agent 服务暂时不可用'
  }
}
