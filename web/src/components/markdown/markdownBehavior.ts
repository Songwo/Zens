/**
 * CodeMirror 6 上的 Markdown 增强行为：
 *  - Enter：智能续列表 / 引用 / 任务列表；空项时退出列表
 *  - Tab / Shift+Tab：在列表内增加/减少缩进
 */
import { EditorSelection } from '@codemirror/state'
import type { EditorView } from '@codemirror/view'
import type { KeyBinding } from '@codemirror/view'

interface ListMatch {
  indent: string
  marker: string
  /** marker 后缀（数字列表会带数字 + . + 空格） */
  rawPrefix: string
  /** 用于下一行续写的 prefix（数字会递增） */
  nextPrefix: string
  /** 列表项目内容（剔除前缀后剩余的部分） */
  body: string
  isTaskItem: boolean
  isEmpty: boolean
}

const LIST_PATTERNS: Array<{
  re: RegExp
  build: (m: RegExpExecArray) => ListMatch
}> = [
  // 有序列表 1. 2. 3.
  {
    re: /^(\s*)(\d+)([.)])(\s+)(\[[ xX]\]\s+)?(.*)$/,
    build: m => {
      const indent = m[1] || ''
      const num = parseInt(m[2], 10)
      const sep = m[3]
      const tail = m[4] || ' '
      const task = m[5] || ''
      const body = m[6] || ''
      const rawPrefix = `${num}${sep}${tail}${task}`
      const nextPrefix = `${num + 1}${sep}${tail}${task ? '[ ] ' : ''}`
      return {
        indent,
        marker: `${num}${sep}`,
        rawPrefix,
        nextPrefix,
        body,
        isTaskItem: !!task,
        isEmpty: body.trim().length === 0,
      }
    },
  },
  // 无序列表 - * +
  {
    re: /^(\s*)([-*+])(\s+)(\[[ xX]\]\s+)?(.*)$/,
    build: m => {
      const indent = m[1] || ''
      const marker = m[2]
      const tail = m[3] || ' '
      const task = m[4] || ''
      const body = m[5] || ''
      const rawPrefix = `${marker}${tail}${task}`
      const nextPrefix = `${marker}${tail}${task ? '[ ] ' : ''}`
      return {
        indent,
        marker,
        rawPrefix,
        nextPrefix,
        body,
        isTaskItem: !!task,
        isEmpty: body.trim().length === 0,
      }
    },
  },
  // 引用 >
  {
    re: /^(\s*)(>+\s?)(.*)$/,
    build: m => {
      const indent = m[1] || ''
      const marker = m[2]
      const body = m[3] || ''
      return {
        indent,
        marker,
        rawPrefix: marker,
        nextPrefix: marker.endsWith(' ') ? marker : marker + ' ',
        body,
        isTaskItem: false,
        isEmpty: body.trim().length === 0,
      }
    },
  },
]

function matchList(lineText: string): ListMatch | null {
  for (const { re, build } of LIST_PATTERNS) {
    const m = re.exec(lineText)
    if (m) return build(m)
  }
  return null
}

function isInsideFencedCode(view: EditorView, pos: number): boolean {
  const doc = view.state.doc
  const before = doc.sliceString(0, pos)
  // 简单粗暴：统计 ``` 出现奇数次即在代码块内
  const fences = before.match(/^```/gm)
  return !!fences && fences.length % 2 === 1
}

const handleListContinuation = (view: EditorView): boolean => {
  const ranges = view.state.selection.ranges
  if (ranges.some(r => !r.empty)) return false  // 有选区时让原生 Enter 接管

  const range = ranges[0]
  if (isInsideFencedCode(view, range.from)) return false

  const line = view.state.doc.lineAt(range.from)
  const lineText = line.text
  const match = matchList(lineText)
  if (!match) return false

  // 空列表项 + Enter → 取消列表前缀，退出列表
  if (match.isEmpty) {
    const eraseFrom = line.from
    const eraseTo = line.to
    view.dispatch(
      view.state.update({
        changes: { from: eraseFrom, to: eraseTo, insert: match.indent },
        selection: EditorSelection.cursor(eraseFrom + match.indent.length),
        scrollIntoView: true,
      })
    )
    return true
  }

  // 续接列表
  const insert = '\n' + match.indent + match.nextPrefix
  const at = range.from
  view.dispatch(
    view.state.update({
      changes: { from: at, to: at, insert },
      selection: EditorSelection.cursor(at + insert.length),
      scrollIntoView: true,
    })
  )
  return true
}

export const markdownListKeymap: KeyBinding[] = [
  {
    key: 'Enter',
    run: handleListContinuation,
  },
]
