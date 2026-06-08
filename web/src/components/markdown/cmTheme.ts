/**
 * CodeMirror 6 Vitesse-style 主题（亮 + 暗）。
 * 数值与品牌 Zens-Yellow / Editorial 风格对齐：
 *   - 选区淡黄 #f5c04c 透明色
 *   - cursor #b07d48（vitesse 紫赭）
 *   - gutter 极淡
 */
import { EditorView } from '@codemirror/view'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'

const lightBg = '#ffffff'
const lightFg = '#393a34'
const lightMuted = '#aaaaaa'
const lightSelectionBg = 'rgba(245, 192, 76, 0.30)'
const lightCursor = '#b07d48'

const darkBg = '#121212'
const darkFg = '#dbd7caee'
const darkMuted = '#666666'
const darkSelectionBg = 'rgba(245, 192, 76, 0.22)'
const darkCursor = '#bd976a'

export const vitesseLightTheme = EditorView.theme(
  {
    '&': {
      color: lightFg,
      backgroundColor: 'transparent',
      fontSize: '14.5px',
      fontFamily: "'JetBrains Mono', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
    },
    '.cm-content': {
      caretColor: lightCursor,
      padding: '14px 18px',
      lineHeight: '1.7',
    },
    '.cm-cursor, .cm-dropCursor': {
      borderLeftColor: lightCursor,
      borderLeftWidth: '2px',
    },
    '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': {
      backgroundColor: lightSelectionBg,
    },
    '.cm-line': {
      padding: '0 2px',
    },
    '.cm-activeLine': {
      backgroundColor: 'rgba(0, 0, 0, 0.025)',
    },
    '.cm-activeLineGutter': {
      backgroundColor: 'transparent',
      color: '#999',
    },
    '.cm-gutters': {
      backgroundColor: 'transparent',
      color: lightMuted,
      border: 'none',
      borderRight: '1px solid rgba(0, 0, 0, 0.05)',
      paddingRight: '6px',
    },
    '.cm-foldGutter .cm-gutterElement': {
      color: lightMuted,
    },
    '.cm-tooltip': {
      backgroundColor: '#ffffff',
      border: '1px solid rgba(0, 0, 0, 0.1)',
      borderRadius: '6px',
      boxShadow: '0 4px 14px rgba(0, 0, 0, 0.08)',
    },
    '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected]': {
      backgroundColor: 'rgba(245, 192, 76, 0.20)',
      color: lightFg,
    },
    '.cm-scroller': {
      fontFamily: 'inherit',
      overflow: 'auto',
    },
    '.cm-placeholder': {
      color: lightMuted,
      fontStyle: 'italic',
    },
  },
  { dark: false }
)

export const vitesseDarkTheme = EditorView.theme(
  {
    '&': {
      color: darkFg,
      backgroundColor: 'transparent',
      fontSize: '14.5px',
      fontFamily: "'JetBrains Mono', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
    },
    '.cm-content': {
      caretColor: darkCursor,
      padding: '14px 18px',
      lineHeight: '1.7',
    },
    '.cm-cursor, .cm-dropCursor': {
      borderLeftColor: darkCursor,
      borderLeftWidth: '2px',
    },
    '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': {
      backgroundColor: darkSelectionBg,
    },
    '.cm-line': {
      padding: '0 2px',
    },
    '.cm-activeLine': {
      backgroundColor: 'rgba(255, 255, 255, 0.025)',
    },
    '.cm-activeLineGutter': {
      backgroundColor: 'transparent',
      color: '#888',
    },
    '.cm-gutters': {
      backgroundColor: 'transparent',
      color: darkMuted,
      border: 'none',
      borderRight: '1px solid rgba(255, 255, 255, 0.06)',
      paddingRight: '6px',
    },
    '.cm-foldGutter .cm-gutterElement': {
      color: darkMuted,
    },
    '.cm-tooltip': {
      backgroundColor: '#1c1c1c',
      border: '1px solid rgba(255, 255, 255, 0.12)',
      borderRadius: '6px',
      boxShadow: '0 4px 14px rgba(0, 0, 0, 0.4)',
      color: darkFg,
    },
    '.cm-tooltip.cm-tooltip-autocomplete > ul > li[aria-selected]': {
      backgroundColor: 'rgba(245, 192, 76, 0.18)',
      color: '#fff',
    },
    '.cm-scroller': {
      fontFamily: 'inherit',
      overflow: 'auto',
    },
    '.cm-placeholder': {
      color: darkMuted,
      fontStyle: 'italic',
    },
  },
  { dark: true }
)

/** Markdown 语法高亮配色（亮 / 暗共用同一组 highlightStyle，颜色随 token 走 */
export const vitesseLightHighlight = HighlightStyle.define(
  [
    { tag: [t.heading1, t.heading2, t.heading3, t.heading4, t.heading5, t.heading6], color: '#1e754f', fontWeight: '700' },
    { tag: t.strong, fontWeight: '700', color: '#393a34' },
    { tag: t.emphasis, fontStyle: 'italic', color: '#393a34' },
    { tag: t.strikethrough, textDecoration: 'line-through', color: '#a0ada0' },
    { tag: t.link, color: '#2e8f82', textDecoration: 'underline' },
    { tag: t.url, color: '#2e8f82' },
    { tag: t.quote, color: '#a0ada0', fontStyle: 'italic' },
    { tag: t.list, color: '#b07d48' },
    { tag: [t.monospace, t.literal], color: '#b56959', backgroundColor: 'rgba(245, 192, 76, 0.08)' },
    { tag: t.processingInstruction, color: '#999' },
    { tag: t.contentSeparator, color: '#cccccc' },
    { tag: t.meta, color: '#a0ada0' },
    { tag: t.comment, color: '#a0ada0', fontStyle: 'italic' },
    { tag: t.keyword, color: '#1e754f' },
    { tag: t.string, color: '#b56959' },
    { tag: t.number, color: '#2f798a' },
  ],
  { themeType: 'light' }
)

export const vitesseDarkHighlight = HighlightStyle.define(
  [
    { tag: [t.heading1, t.heading2, t.heading3, t.heading4, t.heading5, t.heading6], color: '#4d9375', fontWeight: '700' },
    { tag: t.strong, fontWeight: '700', color: '#dbd7caee' },
    { tag: t.emphasis, fontStyle: 'italic', color: '#dbd7caee' },
    { tag: t.strikethrough, textDecoration: 'line-through', color: '#758575dd' },
    { tag: t.link, color: '#5eaab5', textDecoration: 'underline' },
    { tag: t.url, color: '#5eaab5' },
    { tag: t.quote, color: '#758575dd', fontStyle: 'italic' },
    { tag: t.list, color: '#cb7676' },
    { tag: [t.monospace, t.literal], color: '#c98a7d', backgroundColor: 'rgba(245, 192, 76, 0.08)' },
    { tag: t.processingInstruction, color: '#666' },
    { tag: t.contentSeparator, color: '#3a3a3a' },
    { tag: t.meta, color: '#758575dd' },
    { tag: t.comment, color: '#758575dd', fontStyle: 'italic' },
    { tag: t.keyword, color: '#4d9375' },
    { tag: t.string, color: '#c98a7d' },
    { tag: t.number, color: '#4c9a91' },
  ],
  { themeType: 'dark' }
)

export const vitesseLightSyntax = syntaxHighlighting(vitesseLightHighlight)
export const vitesseDarkSyntax = syntaxHighlighting(vitesseDarkHighlight)
