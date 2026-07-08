export interface SectionLike {
  id?: string | number
  name?: string
  icon?: string
  description?: string
}

const SECTION_ICON_BY_KEYWORD = new Map<string, string>([
  ['help', '❓'],
  ['question', '❓'],
  ['qa', '❓'],
  ['study', '📚'],
  ['book', '📚'],
  ['learn', '📚'],
  ['job', '💼'],
  ['work', '💼'],
  ['chat', '💬'],
  ['talk', '💬'],
  ['life', '🌈'],
  ['tech', '💻'],
  ['code', '💻'],
])

const pickSectionIconFromName = (name: string) => {
  const text = formatSectionName(name)
  if (!text) return '#'
  if (text.includes('技术')) return '💻'
  if (text.includes('学习') || text.includes('资料')) return '📚'
  if (text.includes('答疑') || text.includes('问题')) return '❓'
  if (text.includes('求职') || text.includes('招聘')) return '💼'
  if (text.includes('闲聊') || text.includes('灌水')) return '💬'
  if (text.includes('生活')) return '🌈'
  return '#'
}

export const formatSectionName = (value: string) => {
  const text = String(value || '').trim()
  if (!text) return ''

  const cleaned = text
    .replace(/^[a-z][a-z0-9_\-]{1,20}(?=[\u4e00-\u9fff])/i, '')
    .replace(/^[a-z][a-z0-9_\-]{1,20}\s*[:：|/\\-]\s*(?=[\u4e00-\u9fff])/i, '')
    .trim()

  return cleaned || text
}

export const formatSectionIcon = (icon: string | undefined, sectionName = '') => {
  const text = String(icon || '').trim()
  if (text && /[^\x00-\x7F]/.test(text)) {
    return text
  }

  const normalized = text.toLowerCase()
  if (normalized && SECTION_ICON_BY_KEYWORD.has(normalized)) {
    return SECTION_ICON_BY_KEYWORD.get(normalized) || '#'
  }

  return pickSectionIconFromName(sectionName)
}
