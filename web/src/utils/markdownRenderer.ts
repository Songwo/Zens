import MarkdownIt from 'markdown-it'

type ShikiApi = typeof import('./shiki')

let shikiApi: ShikiApi | null = null

async function loadShikiApi(): Promise<ShikiApi> {
  if (shikiApi) return shikiApi
  shikiApi = await import('./shiki')
  return shikiApi
}

/**
 * markdown-it 实例：
 *  - highlight 钩子返回 Shiki 输出（已包好 <pre><code>），失败时回退到 <pre class="code-block-wrapper">…
 *  - 渲染前请用 renderAsync(src) 异步等待语言就绪；同步使用 md.render(src) 也能跑，但首次加载的语言会先呈现为无色，等下次渲染时才上色。
 */
export const md = createMd({ html: true, linkify: true, typographer: true, breaks: false })

/**
 * 评论场景专用 markdown 实例：breaks: true（单换行 → <br>），
 * 这样用户在评论里按一次回车也能换行，符合社交平台习惯。
 */
export const mdComment = createMd({ html: false, linkify: true, typographer: true, breaks: true })

function createMd(options: { html: boolean; linkify: boolean; typographer: boolean; breaks: boolean }): MarkdownIt {
  const instance = new MarkdownIt({
    ...options,
    highlight: function (str: string, lang: string) {
      const trimmedLang = (lang || '').trim()
      const escapedLang = instance.utils.escapeHtml(trimmedLang)

      const shikiHtml = trimmedLang ? shikiApi?.highlightSync(str, trimmedLang) : null
      if (shikiHtml) {
        return wrapShikiOutput(shikiHtml, escapedLang, str)
      }

      const escapedCode = instance.utils.escapeHtml(str)
      return wrapPlainOutput(escapedCode, escapedLang, str)
    },
  })

  // Song：覆写 image 渲染规则，统一给所有图片加 loading="lazy" decoding="async"，
  // 覆盖正文 / 评论 / 私信等所有走 markdown 的图片（DOMPurify 白名单已放行这两个属性）。
  const defaultImageRenderer = instance.renderer.rules.image
    || ((tokens, idx, opts, _env, self) => self.renderToken(tokens, idx, opts))
  instance.renderer.rules.image = (tokens, idx, opts, env, self) => {
    const token = tokens[idx]
    token.attrSet('loading', 'lazy')
    token.attrSet('decoding', 'async')
    return defaultImageRenderer(tokens, idx, opts, env, self)
  }

  return instance
}

function escapeAttr(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;')
}

/** 包装 Shiki 输出，保留其内部 <pre><code>，外层加自定义壳。 */
function wrapShikiOutput(shikiHtml: string, escapedLang: string, rawCode: string): string {
  const lineCount = countLines(rawCode)
  const showLineNumbers = lineCount >= 8
  const dataLang = escapedLang || 'text'
  const rawAttr = escapeAttr(rawCode)
  return (
    `<div class="code-block-wrapper has-shiki${showLineNumbers ? ' show-line-numbers' : ''}" data-lang="${dataLang}" data-raw="${rawAttr}">` +
    `<button type="button" class="code-copy-btn" aria-label="复制代码">复制</button>` +
    `<span class="code-lang-badge">${dataLang}</span>` +
    shikiHtml +
    `</div>`
  )
}

/** 无高亮回退：保持外层 class 不变，便于样式兼容旧渲染。 */
function wrapPlainOutput(escapedCode: string, escapedLang: string, rawCode: string): string {
  const lineCount = countLines(rawCode)
  const showLineNumbers = lineCount >= 8
  const dataLang = escapedLang || 'text'
  const rawAttr = escapeAttr(rawCode)
  return (
    `<div class="code-block-wrapper is-plain${showLineNumbers ? ' show-line-numbers' : ''}" data-lang="${dataLang}" data-raw="${rawAttr}">` +
    `<button type="button" class="code-copy-btn" aria-label="复制代码">复制</button>` +
    `<span class="code-lang-badge">${dataLang}</span>` +
    `<pre class="cp-code-plain"><code>${escapedCode}</code></pre>` +
    `</div>`
  )
}

function countLines(s: string): number {
  if (!s) return 0
  const trimmed = s.replace(/\n+$/, '')
  if (!trimmed) return 0
  let n = 1
  for (let i = 0; i < trimmed.length; i++) {
    if (trimmed.charCodeAt(i) === 10) n++
  }
  return n
}

/** 从 markdown 源中抽取所有 fenced code block 的语言。 */
function extractFencedLangs(src: string): string[] {
  const langs: string[] = []
  const re = /(^|\n) {0,3}(`{3,}|~{3,})[ \t]*([\w+\-#./]*)[^\n]*\n/g
  let m: RegExpExecArray | null
  while ((m = re.exec(src)) !== null) {
    if (m[3]) langs.push(m[3])
  }
  return langs
}

/**
 * 异步渲染：先预热高亮器与所有用到的语言，然后调用同步 md.render。
 */
export async function renderAsync(src: string): Promise<string> {
  if (!src) return ''
  const shiki = await loadShikiApi()
  await shiki.warmupHighlighter()
  const langs = extractFencedLangs(src)
  if (langs.length > 0) {
    await shiki.preloadLanguages(langs)
  }
  return md.render(src)
}
