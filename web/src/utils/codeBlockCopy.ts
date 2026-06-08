/**
 * 全局代码块复制：通过事件委托一次性挂载到 document.body，
 * 任意 v-html 渲染的 .code-block-wrapper 都自动获得「复制」能力。
 */
let _installed = false

function fallbackCopy(text: string): Promise<void> {
  return new Promise((resolve, reject) => {
    try {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.top = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(textarea)
      ok ? resolve() : reject(new Error('execCommand copy failed'))
    } catch (err) {
      reject(err)
    }
  })
}

async function copyToClipboard(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // fall through
    }
  }
  await fallbackCopy(text)
}

function flashFeedback(btn: HTMLButtonElement, ok: boolean) {
  const originalText = btn.dataset._origText ?? btn.textContent ?? '复制'
  if (!btn.dataset._origText) {
    btn.dataset._origText = originalText
  }
  btn.textContent = ok ? '已复制' : '失败'
  btn.classList.add(ok ? 'is-copied' : 'is-failed')
  btn.disabled = true
  window.setTimeout(() => {
    btn.textContent = btn.dataset._origText ?? '复制'
    btn.classList.remove('is-copied', 'is-failed')
    btn.disabled = false
  }, 1400)
}

function handleClick(event: Event) {
  const target = event.target as Element | null
  if (!target) return
  const btn = target.closest('.code-copy-btn') as HTMLButtonElement | null
  if (!btn) return
  const wrapper = btn.closest('.code-block-wrapper') as HTMLElement | null
  if (!wrapper) return

  // 优先用 data-raw（保留原始代码、含转义还原），否则回退到 textContent。
  const rawAttr = wrapper.getAttribute('data-raw')
  const codeEl = wrapper.querySelector('pre code') as HTMLElement | null
  const text = rawAttr ?? codeEl?.innerText ?? ''
  if (!text) return

  event.preventDefault()
  event.stopPropagation()

  copyToClipboard(text)
    .then(() => flashFeedback(btn, true))
    .catch(() => flashFeedback(btn, false))
}

export function installCodeBlockCopy(): void {
  if (_installed || typeof document === 'undefined') return
  document.addEventListener('click', handleClick, { capture: false })
  _installed = true
}
