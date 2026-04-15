type GithubLinkKind = 'repo' | 'issue' | 'pull' | 'commit' | 'release' | 'discussion'

interface GithubLinkMeta {
  kind: GithubLinkKind
  owner: string
  repo: string
  issueNo?: string
  pullNo?: string
  commitSha?: string
  releaseTag?: string
  discussionNo?: string
  href: string
}

const GITHUB_HOST_REGEX = /^(?:www\.)?github\.com$/i

function parseGithubLink(href: string): GithubLinkMeta | null {
  let url: URL
  try {
    url = new URL(href)
  } catch {
    return null
  }

  if (!GITHUB_HOST_REGEX.test(url.hostname)) {
    return null
  }

  const segments = url.pathname.split('/').filter(Boolean)
  if (segments.length < 2) {
    return null
  }

  const owner = segments[0]
  const repo = segments[1]?.replace(/\.git$/i, '')
  if (!owner || !repo) {
    return null
  }

  const type = segments[2]
  const id = segments[3]
  if (!type) {
    return { kind: 'repo', owner, repo, href: url.href }
  }

  if (type === 'issues' && /^\d+$/.test(id || '')) {
    return { kind: 'issue', owner, repo, issueNo: id, href: url.href }
  }
  if (type === 'pull' && /^\d+$/.test(id || '')) {
    return { kind: 'pull', owner, repo, pullNo: id, href: url.href }
  }
  if (type === 'commit' && id) {
    return { kind: 'commit', owner, repo, commitSha: id, href: url.href }
  }
  if (type === 'releases' && segments[3] === 'tag' && segments[4]) {
    return { kind: 'release', owner, repo, releaseTag: segments[4], href: url.href }
  }
  if (type === 'discussions' && /^\d+$/.test(id || '')) {
    return { kind: 'discussion', owner, repo, discussionNo: id, href: url.href }
  }

  return { kind: 'repo', owner, repo, href: url.href }
}

function buildGithubSubtitle(meta: GithubLinkMeta): string {
  if (meta.kind === 'issue') return `Issue #${meta.issueNo}`
  if (meta.kind === 'pull') return `Pull Request #${meta.pullNo}`
  if (meta.kind === 'commit') return `Commit ${String(meta.commitSha || '').slice(0, 7)}`
  if (meta.kind === 'release') return `Release ${meta.releaseTag}`
  if (meta.kind === 'discussion') return `Discussion #${meta.discussionNo}`
  return 'Repository'
}

function githubKindIcon(kind: GithubLinkKind): string {
  if (kind === 'issue') return '🐛'
  if (kind === 'pull') return '🔀'
  if (kind === 'commit') return '📝'
  if (kind === 'release') return '🏷️'
  if (kind === 'discussion') return '💬'
  return '📦'
}

function shouldCardify(anchor: HTMLAnchorElement): boolean {
  const parent = anchor.parentElement
  if (!parent) return true

  const parentTag = parent.tagName.toLowerCase()
  if (!['p', 'li', 'div', 'blockquote'].includes(parentTag)) {
    return false
  }

  const text = (parent.textContent || '').trim()
  const linkText = (anchor.textContent || '').trim()
  if (!text || !linkText) return false

  const normalizedHref = anchor.href.replace(/\/$/, '')
  const normalizedLinkText = linkText.replace(/\/$/, '')
  if (text === linkText || normalizedLinkText === normalizedHref) {
    return true
  }

  return false
}

function createGithubCard(doc: Document, meta: GithubLinkMeta): HTMLElement {
  const card = doc.createElement('a')
  card.className = 'github-link-card'
  card.href = meta.href
  card.target = '_blank'
  card.rel = 'noopener noreferrer nofollow'

  // Left: icon column
  const iconCol = doc.createElement('span')
  iconCol.className = 'github-card-icon'
  iconCol.textContent = githubKindIcon(meta.kind)

  // Right: info column
  const infoCol = doc.createElement('span')
  infoCol.className = 'github-card-info'

  const titleRow = doc.createElement('span')
  titleRow.className = 'github-card-title'
  titleRow.textContent = `${meta.owner} / ${meta.repo}`

  const subtitleRow = doc.createElement('span')
  subtitleRow.className = 'github-card-subtitle'
  subtitleRow.textContent = buildGithubSubtitle(meta)

  const urlRow = doc.createElement('span')
  urlRow.className = 'github-card-url'
  urlRow.textContent = meta.href

  infoCol.appendChild(titleRow)
  infoCol.appendChild(subtitleRow)
  infoCol.appendChild(urlRow)

  // Right: badge
  const badge = doc.createElement('span')
  badge.className = 'github-card-badge'
  badge.innerHTML = `<svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/></svg> GitHub`

  card.appendChild(iconCol)
  card.appendChild(infoCol)
  card.appendChild(badge)
  return card
}

function createExternalLinkCard(doc: Document, anchor: HTMLAnchorElement): HTMLElement {
  let hostname = ''
  try {
    hostname = new URL(anchor.href).hostname.replace(/^www\./, '')
  } catch {
    hostname = anchor.href
  }

  const card = doc.createElement('a')
  card.className = 'ext-link-card'
  card.href = anchor.href
  card.target = '_blank'
  card.rel = 'noopener noreferrer nofollow'

  const iconSpan = doc.createElement('span')
  iconSpan.className = 'ext-link-icon'
  iconSpan.textContent = '🔗'

  const infoCol = doc.createElement('span')
  infoCol.className = 'ext-link-info'

  const titleSpan = doc.createElement('span')
  titleSpan.className = 'ext-link-title'
  titleSpan.textContent = anchor.textContent?.trim() || anchor.href

  const hostSpan = doc.createElement('span')
  hostSpan.className = 'ext-link-host'
  hostSpan.textContent = hostname

  infoCol.appendChild(titleSpan)
  infoCol.appendChild(hostSpan)

  const arrowSpan = doc.createElement('span')
  arrowSpan.className = 'ext-link-arrow'
  arrowSpan.textContent = '↗'

  card.appendChild(iconSpan)
  card.appendChild(infoCol)
  card.appendChild(arrowSpan)
  return card
}

function isExternalLink(anchor: HTMLAnchorElement): boolean {
  try {
    const url = new URL(anchor.href)
    return url.hostname !== window.location.hostname
  } catch {
    return false
  }
}

/**
 * Merge consecutive <blockquote> siblings into one.
 * e.g. > line1\n> line2 in some MD parsers becomes two blockquotes.
 */
export function mergeConsecutiveBlockquotes(html: string): string {
  if (!html || html.indexOf('<blockquote>') === -1 || typeof DOMParser === 'undefined') {
    return html
  }
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')

  let changed = false
  const allBq = Array.from(doc.querySelectorAll('blockquote'))
  for (const bq of allBq) {
    // Only process top-level consecutive pairs
    let next = bq.nextSibling
    // skip text nodes that are only whitespace
    while (next && next.nodeType === Node.TEXT_NODE && !(next.textContent || '').trim()) {
      next = next.nextSibling
    }
    if (next && next.nodeName === 'BLOCKQUOTE') {
      // Move all children of next into bq
      while (next.firstChild) {
        bq.appendChild(next.firstChild)
      }
      next.parentNode?.removeChild(next)
      changed = true
    }
  }
  return changed ? doc.body.innerHTML : html
}

export function renderGithubRichCards(html: string): string {
  if (typeof DOMParser === 'undefined') return html

  const hasGithub = html.indexOf('github.com/') !== -1
  if (!html) return html

  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  const anchors = Array.from(doc.querySelectorAll('a[href]')) as HTMLAnchorElement[]
  if (!anchors.length) return html

  for (const anchor of anchors) {
    if (anchor.closest('.github-link-card') || anchor.closest('.ext-link-card')) {
      continue
    }
    if (!shouldCardify(anchor)) {
      continue
    }

    // GitHub card
    if (hasGithub) {
      const meta = parseGithubLink(anchor.href)
      if (meta) {
        const card = createGithubCard(doc, meta)
        const parent = anchor.parentElement
        if (parent && (parent.tagName.toLowerCase() === 'p' || parent.tagName.toLowerCase() === 'div')) {
          parent.replaceWith(card)
        } else {
          anchor.replaceWith(card)
        }
        continue
      }
    }

    // External link card (non-relative, non-anchor-only)
    if (isExternalLink(anchor)) {
      const card = createExternalLinkCard(doc, anchor)
      const parent = anchor.parentElement
      if (parent && (parent.tagName.toLowerCase() === 'p' || parent.tagName.toLowerCase() === 'div')) {
        parent.replaceWith(card)
      } else {
        anchor.replaceWith(card)
      }
    }
  }

  return doc.body.innerHTML
}
