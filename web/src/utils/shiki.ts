import {
  createHighlighterCore,
  type HighlighterCore,
} from 'shiki/core'
import { createOnigurumaEngine } from 'shiki/engine/oniguruma'
import {
  transformerNotationDiff,
  transformerNotationHighlight,
  transformerNotationFocus,
} from '@shikijs/transformers'

// 主题：静态导入，体积很小（~5-10KB / 主题）
import vitesseLight from '@shikijs/themes/vitesse-light'
import vitesseDark from '@shikijs/themes/vitesse-dark'

const LIGHT_THEME = 'vitesse-light'
const DARK_THEME = 'vitesse-dark'

/**
 * 各语言走动态 import → Vite 会自动 code-split，按需下载。
 * 这里只列出社区常见语言；未列入的语言由渲染层回退到纯代码。
 */
const LANG_IMPORTS: Record<string, () => Promise<any>> = {
  javascript: () => import('@shikijs/langs/javascript'),
  typescript: () => import('@shikijs/langs/typescript'),
  tsx: () => import('@shikijs/langs/tsx'),
  jsx: () => import('@shikijs/langs/jsx'),
  html: () => import('@shikijs/langs/html'),
  css: () => import('@shikijs/langs/css'),
  scss: () => import('@shikijs/langs/scss'),
  less: () => import('@shikijs/langs/less'),
  json: () => import('@shikijs/langs/json'),
  yaml: () => import('@shikijs/langs/yaml'),
  toml: () => import('@shikijs/langs/toml'),
  ini: () => import('@shikijs/langs/ini'),
  bash: () => import('@shikijs/langs/bash'),
  shellscript: () => import('@shikijs/langs/shellscript'),
  powershell: () => import('@shikijs/langs/powershell'),
  java: () => import('@shikijs/langs/java'),
  kotlin: () => import('@shikijs/langs/kotlin'),
  scala: () => import('@shikijs/langs/scala'),
  groovy: () => import('@shikijs/langs/groovy'),
  python: () => import('@shikijs/langs/python'),
  ruby: () => import('@shikijs/langs/ruby'),
  go: () => import('@shikijs/langs/go'),
  rust: () => import('@shikijs/langs/rust'),
  c: () => import('@shikijs/langs/c'),
  cpp: () => import('@shikijs/langs/cpp'),
  csharp: () => import('@shikijs/langs/csharp'),
  php: () => import('@shikijs/langs/php'),
  swift: () => import('@shikijs/langs/swift'),
  dart: () => import('@shikijs/langs/dart'),
  lua: () => import('@shikijs/langs/lua'),
  sql: () => import('@shikijs/langs/sql'),
  markdown: () => import('@shikijs/langs/markdown'),
  diff: () => import('@shikijs/langs/diff'),
  xml: () => import('@shikijs/langs/xml'),
  vue: () => import('@shikijs/langs/vue'),
  svelte: () => import('@shikijs/langs/svelte'),
  graphql: () => import('@shikijs/langs/graphql'),
  dockerfile: () => import('@shikijs/langs/docker'),
  nginx: () => import('@shikijs/langs/nginx'),
  regex: () => import('@shikijs/langs/regexp'),
  http: () => import('@shikijs/langs/http'),
}

// 别名归一化（用户写的 js → javascript）
const ALIASES: Record<string, string> = {
  js: 'javascript',
  ts: 'typescript',
  py: 'python',
  rb: 'ruby',
  sh: 'bash',
  zsh: 'bash',
  shell: 'shellscript',
  ps1: 'powershell',
  yml: 'yaml',
  md: 'markdown',
  docker: 'dockerfile',
  'c++': 'cpp',
  'c#': 'csharp',
  cs: 'csharp',
  'objective-c': 'c',
  ['gnuplot' as string]: 'bash',
}

// 启动时预热的语言（必装套件）。Vite 也会按动态 import 切片，
// 但这些会在首次 highlighter 构建时直接 await。
const PRELOADED: string[] = [
  'javascript',
  'typescript',
  'html',
  'css',
  'json',
  'bash',
  'java',
  'python',
  'sql',
  'markdown',
  'diff',
]

let _highlighter: HighlighterCore | null = null
let _initPromise: Promise<HighlighterCore> | null = null
const _loadedLangs = new Set<string>()
const _loadingLangs = new Map<string, Promise<void>>()

function normalizeLang(input: string | undefined | null): string | null {
  if (!input) return null
  const raw = input.trim().toLowerCase()
  if (!raw) return null
  const aliased = ALIASES[raw] ?? raw
  return aliased
}

function isKnownLang(lang: string): boolean {
  return Object.prototype.hasOwnProperty.call(LANG_IMPORTS, lang)
}

async function getHighlighter(): Promise<HighlighterCore> {
  if (_highlighter) return _highlighter
  if (_initPromise) return _initPromise
  _initPromise = (async () => {
    const h = await createHighlighterCore({
      themes: [vitesseLight, vitesseDark],
      langs: PRELOADED
        .map(name => LANG_IMPORTS[name])
        .filter((load): load is () => Promise<any> => Boolean(load))
        .map(load => load()),
      engine: createOnigurumaEngine(import('shiki/wasm')),
    })
    PRELOADED.forEach(l => _loadedLangs.add(l))
    _highlighter = h
    return h
  })()
  return _initPromise
}

export async function ensureLanguage(rawLang: string): Promise<string | null> {
  const lang = normalizeLang(rawLang)
  if (!lang) return null
  const h = await getHighlighter()
  if (_loadedLangs.has(lang)) return lang
  if (!isKnownLang(lang)) return null
  const existing = _loadingLangs.get(lang)
  if (existing) {
    await existing
    return _loadedLangs.has(lang) ? lang : null
  }
  const promise = (async () => {
    try {
      const load = LANG_IMPORTS[lang]
      if (!load) return
      const mod = await load()
      await h.loadLanguage(mod.default ?? mod)
      _loadedLangs.add(lang)
    } catch {
      /* silent */
    }
  })().finally(() => _loadingLangs.delete(lang))
  _loadingLangs.set(lang, promise)
  await promise
  return _loadedLangs.has(lang) ? lang : null
}

export function hasLanguageLoaded(rawLang: string): boolean {
  const lang = normalizeLang(rawLang)
  return !!lang && _loadedLangs.has(lang)
}

export function isHighlighterReady(): boolean {
  return _highlighter !== null
}

export function highlightSync(code: string, rawLang: string): string | null {
  if (!_highlighter) return null
  const lang = normalizeLang(rawLang)
  if (!lang || !_loadedLangs.has(lang)) return null
  try {
    return _highlighter.codeToHtml(code, {
      lang,
      themes: { light: LIGHT_THEME, dark: DARK_THEME },
      defaultColor: false,
      transformers: [
        transformerNotationDiff({ matchAlgorithm: 'v3' }),
        transformerNotationHighlight({ matchAlgorithm: 'v3' }),
        transformerNotationFocus({ matchAlgorithm: 'v3' }),
      ],
    })
  } catch {
    return null
  }
}

export async function preloadLanguages(rawLangs: Array<string | null | undefined>): Promise<void> {
  const langs = Array.from(
    new Set(
      rawLangs
        .map(normalizeLang)
        .filter((l): l is string => !!l && !_loadedLangs.has(l) && isKnownLang(l))
    )
  )
  if (langs.length === 0) {
    if (!_highlighter) await getHighlighter()
    return
  }
  await Promise.all(langs.map(l => ensureLanguage(l)))
}

export async function warmupHighlighter(): Promise<void> {
  await getHighlighter()
}
