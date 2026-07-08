export interface DiscoveryTagLike {
  id?: string | number
  name?: string
  postCount?: number | string | null
}

export interface DiscoveryTopicEntry {
  key: string
  name: string
  postCount: number
  source: 'default' | 'live'
  tagId?: string | number
  tagRouteName?: string
}

export const DEFAULT_DISCOVERY_SEARCH_TERMS = [
  'Spring Boot',
  'Vue 3',
  'MySQL',
  'Redis',
  'Docker',
  '接口鉴权',
  '性能优化',
  '故障排查',
]

export const DEFAULT_ENTERPRISE_AGENT_PROMPTS = [
  'Spring Boot 登录态频繁失效怎么排查',
  'Vue 3 页面首屏性能优化思路',
  'MySQL 索引失效常见原因',
  'Redis 缓存穿透怎么治理',
  'Docker 部署 Spring Boot 需要注意什么',
  '接口鉴权设计有哪些常见坑',
  '线上慢 SQL 应该怎么定位',
  'Agent 服务接入主站应该怎么设计',
]

const NOISE_TERMS = new Set([
  '88',
  '啊',
  '注水',
  '奇怪',
  '好用',
  '搜索',
  '集锦',
])

const DISPLAY_CANONICAL_NAME_MAP = new Map<string, string>([
  ['ai', 'AI'],
  ['api', 'API'],
  ['docker', 'Docker'],
  ['ip', 'IP'],
  ['java', 'Java'],
  ['mysql', 'MySQL'],
  ['openai', 'OpenAI'],
  ['python', 'Python'],
  ['redis', 'Redis'],
  ['springboot', 'Spring Boot'],
  ['spring boot', 'Spring Boot'],
  ['sql', 'SQL'],
  ['typescript', 'TypeScript'],
  ['vue', 'Vue'],
])

const DEFAULT_DISCOVERY_TERM_KEYS = new Set(
  DEFAULT_DISCOVERY_SEARCH_TERMS.map((item) => item.toLowerCase())
)
const DEFAULT_DISCOVERY_SCORE_BONUS = 1.5

const PRIORITY_KEYWORDS = [
  'agent', 'ai', 'api', 'docker', 'go', 'ip', 'java', 'mysql', 'nginx',
  'openai', 'python', 'redis', 'spring', 'sql', 'typescript', 'vue',
  '技术', '开发', '环境', '配置', '安全', '验证', '检测', '排查', '性能', '部署',
  '运维', '数据库', '索引', '缓存', '登录', '鉴权', '项目', '服务', '故障', '监控',
]

const NEUTRAL_KEYWORDS = [
  '功能', '建议', '问题', '讨论', '互动', '实践', '指南', '复盘', '案例',
]

const OFF_TOPIC_KEYWORDS = [
  'offer', '二手', '内推', '公益站', '吐槽', '岗位', '房贷', '掌控感', '数字领地',
  '校招', '考研', '简历', '闲置', '面经', '面试', '首付', '情绪',
]

const NICHE_DISCOVERY_KEYWORDS = [
  'gmail',
  'google',
  '账号创建',
  '验证机制',
  '纯净度',
]

const normalizeTagName = (value: string) => String(value || '').trim()
const toDiscoveryKey = (value: string) => normalizeTagName(value).toLowerCase()

export const formatDiscoveryTagName = (value: string) => {
  const text = normalizeTagName(value)
  if (!text) return ''
  return DISPLAY_CANONICAL_NAME_MAP.get(text.toLowerCase()) || text
}

export const isMeaningfulDiscoveryTagName = (value: string) => {
  const text = formatDiscoveryTagName(value)
  if (!text) return false
  if (NOISE_TERMS.has(text.toLowerCase())) return false
  if (text.length < 2 || text.length > 20) return false
  if (/^\d+$/.test(text)) return false
  if (/^(.)\1{2,}$/.test(text)) return false
  return /[a-zA-Z\u4e00-\u9fff]/.test(text)
}

export const scoreDiscoveryTagName = (value: string) => {
  const text = formatDiscoveryTagName(value).toLowerCase()
  if (!isMeaningfulDiscoveryTagName(text)) return -100

  let score = 0
  if (PRIORITY_KEYWORDS.some((keyword) => text.includes(keyword))) score += 4
  if (NEUTRAL_KEYWORDS.some((keyword) => text.includes(keyword))) score += 2
  if (/[a-z]/.test(text)) score += 1
  if (text.length >= 3 && text.length <= 10) score += 0.5
  if (OFF_TOPIC_KEYWORDS.some((keyword) => text.includes(keyword))) score -= 3.5
  if (NICHE_DISCOVERY_KEYWORDS.some((keyword) => text.includes(keyword))) score -= 4
  return score
}

export const isStableEnterpriseDiscoveryTopic = (value: string) => {
  const text = formatDiscoveryTagName(value)
  if (!isMeaningfulDiscoveryTagName(text)) return false
  return scoreDiscoveryTagName(text) >= 2
}

export const buildAgentPromptFromTopic = (value: string) => {
  const topic = formatDiscoveryTagName(value)
  if (!topic) return ''
  return `${topic} 最近有哪些值得复用的经验？`
}

export const dedupeTextList = (values: string[], limit = values.length) => {
  const deduped: string[] = []
  const seen = new Set<string>()

  values.forEach((item) => {
    const text = String(item || '').trim()
    const key = text.toLowerCase()
    if (!text || seen.has(key)) return
    seen.add(key)
    deduped.push(text)
  })

  return deduped.slice(0, limit)
}

export const normalizeDiscoveryTagNames = (
  values: string[],
  options: {
    limit?: number
    minScore?: number
    includeDefaults?: boolean
  } = {}
) => {
  const { limit = 8, minScore = 0, includeDefaults = true } = options
  const deduped: string[] = []
  const seen = new Set<string>()
  const source = includeDefaults
    ? [...values, ...DEFAULT_DISCOVERY_SEARCH_TERMS]
    : values

  source
    .map((item) => formatDiscoveryTagName(item))
    .filter((item) => {
      const key = toDiscoveryKey(item)
      if (seen.has(key) || !isMeaningfulDiscoveryTagName(item)) return false
      seen.add(key)
      return true
    })
    .sort((a, b) => {
      const scoreA = scoreDiscoveryTagName(a) + (DEFAULT_DISCOVERY_TERM_KEYS.has(toDiscoveryKey(a)) ? DEFAULT_DISCOVERY_SCORE_BONUS : 0)
      const scoreB = scoreDiscoveryTagName(b) + (DEFAULT_DISCOVERY_TERM_KEYS.has(toDiscoveryKey(b)) ? DEFAULT_DISCOVERY_SCORE_BONUS : 0)
      return scoreB - scoreA || a.localeCompare(b)
    })
    .forEach((item) => {
      if (scoreDiscoveryTagName(item) >= minScore) {
        deduped.push(item)
      }
    })

  return deduped.slice(0, limit)
}

export const pickCuratedDiscoveryTags = <T extends DiscoveryTagLike>(
  items: T[],
  options: {
    limit?: number
    minScore?: number
    requireKnownContent?: boolean
  } = {}
) => {
  const {
    limit = 8,
    minScore = 2,
    requireKnownContent = true,
  } = options

  const ranked = items
    .map((item) => {
      const name = normalizeTagName(item.name || '')
      return {
        raw: item,
        name,
        score: scoreDiscoveryTagName(name),
        postCount: Number(item.postCount ?? 0),
      }
    })
    .filter((item) => isMeaningfulDiscoveryTagName(item.name))
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score
      if (b.postCount !== a.postCount) return b.postCount - a.postCount
      return a.name.localeCompare(b.name)
    })

  const selected = ranked.filter((item) => {
    if (requireKnownContent && item.postCount <= 0) return false
    return item.score >= minScore
  })

  const fallback = ranked.filter((item) => {
    if (requireKnownContent && item.postCount <= 0) return false
    return item.score >= 1
  })

  const merged = [...selected]
  if (merged.length < Math.min(3, limit)) {
    fallback.forEach((item) => {
      if (!merged.some((candidate) => candidate.name.toLowerCase() === item.name.toLowerCase())) {
        merged.push(item)
      }
    })
  }

  return merged.slice(0, limit).map((item) => item.raw)
}

export const buildDiscoveryTopicEntries = <T extends DiscoveryTagLike>(
  items: T[],
  options: {
    limit?: number
    minScore?: number
    minPostCountForLive?: number
  } = {}
) => {
  const {
    limit = 8,
    minScore = 2,
    minPostCountForLive = 2,
  } = options

  const liveCandidates = pickCuratedDiscoveryTags(items, {
    limit,
    minScore,
    requireKnownContent: true,
  }).filter((item) => Number(item.postCount ?? 0) >= minPostCountForLive)

  const liveEntries: DiscoveryTopicEntry[] = liveCandidates.map((item) => ({
    key: `live-${item.id ?? formatDiscoveryTagName(item.name || '')}`,
    name: formatDiscoveryTagName(item.name || ''),
    postCount: Number(item.postCount ?? 0),
    source: 'live' as const,
    tagId: item.id,
    tagRouteName: normalizeTagName(item.name || ''),
  }))

  const fallbackEntries: DiscoveryTopicEntry[] = normalizeDiscoveryTagNames(
    liveEntries.map((item) => item.name),
    { limit, minScore, includeDefaults: true }
  ).map((name) => ({
    key: `default-${name.toLowerCase()}`,
    name,
    postCount: 0,
    source: 'default' as const,
  }))

  const merged: DiscoveryTopicEntry[] = [...liveEntries]
  const seen = new Set(liveEntries.map((item) => item.name.toLowerCase()))
  fallbackEntries.forEach((item) => {
    if (seen.has(item.name.toLowerCase())) return
    seen.add(item.name.toLowerCase())
    merged.push(item)
  })

  return merged.slice(0, limit)
}
