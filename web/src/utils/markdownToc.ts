import type MarkdownIt from 'markdown-it'

type ShikiApi = typeof import('./shiki')

let shikiApi: ShikiApi | null = null

async function loadShikiApi(): Promise<ShikiApi> {
  if (shikiApi) return shikiApi
  shikiApi = await import('./shiki')
  return shikiApi
}

export const TOC_MARKDOWN_TAG = '[TOC]'

const FENCED_LANG_RE = /(^|\n) {0,3}(`{3,}|~{3,})[ \t]*([\w+\-#./]*)[^\n]*\n/g

const TOC_PLACEHOLDER = '<!--CP_TOC_PLACEHOLDER-->'
const TOC_TAG_PATTERN = /\[\s*(?:toc|目录)\s*]/gi

export interface TocHeading {
  id: string
  level: number
  text: string
}

interface RenderMarkdownWithTocOptions {
  inlineToc?: boolean
}

export interface MarkdownTocRenderResult {
  html: string
  headings: TocHeading[]
  hasTocTag: boolean
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function slugifyHeading(text: string): string {
  const slug = text
    .trim()
    .toLowerCase()
    .replace(/[^\w\u4e00-\u9fff\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')

  return slug || 'section'
}

function extractHeadings(md: MarkdownIt, markdown: string): TocHeading[] {
  const tokens = md.parse(markdown, {})
  const slugCounter = new Map<string, number>()
  const headings: TocHeading[] = []

  for (let i = 0; i < tokens.length; i += 1) {
    const token = tokens[i]
    if (!token) continue
    if (token.type !== 'heading_open' || !/^h[1-6]$/.test(token.tag)) continue

    const level = Number(token.tag.slice(1))
    const inlineToken = tokens[i + 1]
    const headingText = (inlineToken?.content || '').trim()
    if (!headingText) continue

    const baseSlug = slugifyHeading(headingText)
    const used = slugCounter.get(baseSlug) || 0
    slugCounter.set(baseSlug, used + 1)
    const id = used === 0 ? baseSlug : `${baseSlug}-${used + 1}`

    headings.push({
      id,
      level,
      text: headingText
    })
  }

  return headings
}

function injectHeadingIds(html: string, headings: TocHeading[]): string {
  let index = 0
  return html.replace(/<h([1-6])>/g, (match, level) => {
    const heading = headings[index]
    index += 1
    if (!heading) return match
    return `<h${level} id="${heading.id}" class="cp-heading-anchor">`
  })
}

function buildTocHtml(headings: TocHeading[]): string {
  if (headings.length === 0) {
    return [
      '<div class="cp-toc">',
      '<div class="cp-toc-title">目录</div>',
      '<p class="cp-toc-empty">暂无可用标题</p>',
      '</div>'
    ].join('')
  }

  const items = headings
    .map(
      heading =>
        `<li class="cp-toc-item level-${heading.level}"><a href="#${heading.id}">${escapeHtml(heading.text)}</a></li>`
    )
    .join('')

  return [
    '<div class="cp-toc">',
    '<div class="cp-toc-title">目录</div>',
    `<ul class="cp-toc-list">${items}</ul>`,
    '</div>'
  ].join('')
}

function replaceTocPlaceholder(html: string, tocHtml: string): string {
  return html.replace(/<!--CP_TOC_PLACEHOLDER-->/g, tocHtml)
}

export function renderMarkdownWithToc(md: MarkdownIt, content: string): string {
  return renderMarkdownWithTocResult(md, content).html
}

export function renderMarkdownWithTocResult(
  md: MarkdownIt,
  content: string,
  options: RenderMarkdownWithTocOptions = {}
): MarkdownTocRenderResult {
  const markdown = content || ''
  const withPlaceholder = markdown.replace(TOC_TAG_PATTERN, TOC_PLACEHOLDER)
  const hasTocTag = withPlaceholder.includes(TOC_PLACEHOLDER)
  const headings = extractHeadings(md, withPlaceholder)
  const inlineToc = options.inlineToc !== false

  let html = md.render(withPlaceholder)
  html = injectHeadingIds(html, headings)

  if (hasTocTag) {
    html = inlineToc
      ? replaceTocPlaceholder(html, buildTocHtml(headings))
      : replaceTocPlaceholder(html, '')
  }

  return {
    html,
    headings,
    hasTocTag
  }
}

/** 异步版本：先预热 Shiki 与所有出现的语言再调用同步渲染。 */
export async function renderMarkdownWithTocAsync(
  md: MarkdownIt,
  content: string,
  options: RenderMarkdownWithTocOptions = {}
): Promise<MarkdownTocRenderResult> {
  const src = content || ''
  const langs: string[] = []
  let m: RegExpExecArray | null
  FENCED_LANG_RE.lastIndex = 0
  while ((m = FENCED_LANG_RE.exec(src)) !== null) {
    if (m[3]) langs.push(m[3])
  }
  if (langs.length === 0) return renderMarkdownWithTocResult(md, src, options)
  const shiki = await loadShikiApi()
  await shiki.preloadLanguages(langs)
  return renderMarkdownWithTocResult(md, src, options)
}
