/**
 * CodeMirror 6 上的 Markdown 编辑指令。所有指令通过 EditorView 操作 state，
 * 设计目标：保留多选区、保留 selection，不依赖具体组件。
 */
import { EditorSelection, Transaction } from '@codemirror/state'
import type { EditorView } from '@codemirror/view'

interface InlineWrapOptions {
  before: string
  after?: string
  placeholder?: string
}

/** 在每个选区两侧插入符号；若选区为空，则插入占位文本并选中之。 */
export function wrapInline(view: EditorView, opts: InlineWrapOptions) {
  if (!view) return
  const after = opts.after ?? opts.before
  const placeholder = opts.placeholder ?? ''
  const changes = view.state.changeByRange(range => {
    const selectedText = view.state.sliceDoc(range.from, range.to)
    const inner = selectedText.length === 0 ? placeholder : selectedText
    const insert = opts.before + inner + after
    return {
      changes: { from: range.from, to: range.to, insert },
      range: EditorSelection.range(
        range.from + opts.before.length,
        range.from + opts.before.length + inner.length
      ),
    }
  })
  view.dispatch(view.state.update(changes, { scrollIntoView: true, annotations: Transaction.userEvent.of('input.wrap') }))
  view.focus()
}

/** 给每个选区所覆盖的行加前缀（标题 / 引用 / 列表 / 任务列表） */
export function togglePrefix(view: EditorView, prefix: string, options?: { unique?: boolean }) {
  if (!view) return
  const unique = options?.unique ?? false
  const doc = view.state.doc
  const changes = view.state.changeByRange(range => {
    const fromLine = doc.lineAt(range.from)
    const toLine = doc.lineAt(range.to)
    const edits: Array<{ from: number; to: number; insert: string }> = []
    for (let n = fromLine.number; n <= toLine.number; n++) {
      const line = doc.line(n)
      const text = line.text
      const trimmedPrefix = prefix.replace(/\s+$/, '')
      // 已有相同前缀（unique 模式下保留唯一）→ 移除
      if (unique && (text.startsWith(prefix) || text.startsWith(trimmedPrefix))) {
        const stripLen = text.startsWith(prefix) ? prefix.length : trimmedPrefix.length
        edits.push({ from: line.from, to: line.from + stripLen, insert: '' })
      } else {
        edits.push({ from: line.from, to: line.from, insert: prefix })
      }
    }
    return {
      changes: edits,
      range,
    }
  })
  view.dispatch(view.state.update(changes, { scrollIntoView: true }))
  view.focus()
}

/** 替换每个选区为给定文本（在前后保证空行）。 */
export function replaceSelectionAsBlock(view: EditorView, block: string) {
  if (!view) return
  const changes = view.state.changeByRange(range => {
    const fromLine = view.state.doc.lineAt(range.from)
    const toLine = view.state.doc.lineAt(range.to)
    const before = fromLine.from > 0 ? '\n' : ''
    const after = toLine.to < view.state.doc.length ? '\n' : ''
    const insert = `${before}${block}${after}`
    const insertOffset = before.length
    return {
      changes: { from: fromLine.from, to: toLine.to, insert },
      range: EditorSelection.cursor(fromLine.from + insertOffset + block.length),
    }
  })
  view.dispatch(view.state.update(changes, { scrollIntoView: true }))
  view.focus()
}

/** 在光标位置插入文本（保留前后断行整洁）。 */
export function insertAtCursor(
  view: EditorView,
  text: string,
  opts: { ensureBlankLineBefore?: boolean; ensureBlankLineAfter?: boolean } = {}
) {
  if (!view) return
  const changes = view.state.changeByRange(range => {
    const doc = view.state.doc
    const beforeCharCode = range.from > 0 ? doc.sliceString(range.from - 1, range.from) : ''
    const afterCharCode = range.to < doc.length ? doc.sliceString(range.to, range.to + 1) : ''
    const needLead = opts.ensureBlankLineBefore && beforeCharCode && beforeCharCode !== '\n' ? '\n' : ''
    const needTail = opts.ensureBlankLineAfter && afterCharCode && afterCharCode !== '\n' ? '\n' : ''
    const insert = needLead + text + needTail
    return {
      changes: { from: range.from, to: range.to, insert },
      range: EditorSelection.cursor(range.from + needLead.length + text.length),
    }
  })
  view.dispatch(view.state.update(changes, { scrollIntoView: true }))
  view.focus()
}

/** 包成代码块（围栏式）。选区为空时插入空代码块并把光标置于内部。 */
export function wrapCodeFence(view: EditorView, lang = '') {
  if (!view) return
  const changes = view.state.changeByRange(range => {
    const selectedText = view.state.sliceDoc(range.from, range.to)
    const inner = selectedText.length === 0 ? '' : selectedText.replace(/\n+$/, '')
    const open = '```' + lang + '\n'
    const close = '\n```'
    const insertedText = open + inner + close
    const doc = view.state.doc
    const beforeCharCode = range.from > 0 ? doc.sliceString(range.from - 1, range.from) : ''
    const afterCharCode = range.to < doc.length ? doc.sliceString(range.to, range.to + 1) : ''
    const needLead = beforeCharCode && beforeCharCode !== '\n' ? '\n' : ''
    const needTail = afterCharCode && afterCharCode !== '\n' ? '\n' : ''
    const finalInsert = needLead + insertedText + needTail
    const innerStart = range.from + needLead.length + open.length
    const innerEnd = innerStart + inner.length
    return {
      changes: { from: range.from, to: range.to, insert: finalInsert },
      range: inner.length > 0
        ? EditorSelection.range(innerStart, innerEnd)
        : EditorSelection.cursor(innerStart),
    }
  })
  view.dispatch(view.state.update(changes, { scrollIntoView: true }))
  view.focus()
}

/** 插入链接 [text](url)。选区为空时插入占位符并选中文本。 */
export function insertLink(view: EditorView) {
  if (!view) return
  const changes = view.state.changeByRange(range => {
    const selected = view.state.sliceDoc(range.from, range.to)
    const text = selected.length > 0 ? selected : '链接文本'
    const url = '链接地址'
    const insert = `[${text}](${url})`
    return {
      changes: { from: range.from, to: range.to, insert },
      // 选中 URL 部分让用户直接替换
      range: EditorSelection.range(
        range.from + text.length + 3,
        range.from + text.length + 3 + url.length
      ),
    }
  })
  view.dispatch(view.state.update(changes, { scrollIntoView: true }))
  view.focus()
}

/** 插入图片 ![](url) */
export function insertImageSyntax(view: EditorView, url: string, alt = 'image') {
  if (!view || !url) return
  insertAtCursor(view, `![${alt}](${url})`, {
    ensureBlankLineBefore: true,
    ensureBlankLineAfter: true,
  })
}

/** 插入表格（默认 3 列 2 行） */
export function insertTable(view: EditorView) {
  const tpl =
    '| 列 1 | 列 2 | 列 3 |\n' +
    '| --- | --- | --- |\n' +
    '| 内容 | 内容 | 内容 |\n' +
    '| 内容 | 内容 | 内容 |'
  replaceSelectionAsBlock(view, tpl)
}

/** 插入分割线 */
export function insertDivider(view: EditorView) {
  replaceSelectionAsBlock(view, '---')
}

/** 取得当前所有选区拼接而成的文本（空则返回 ''）。 */
export function getSelectionText(view: EditorView): string {
  if (!view) return ''
  return view.state.selection.ranges
    .map(r => view.state.sliceDoc(r.from, r.to))
    .join('\n')
}
