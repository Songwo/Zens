<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { EditorState, Compartment } from '@codemirror/state'
import { EditorView, keymap, drawSelection, highlightSpecialChars, dropCursor, rectangularSelection, crosshairCursor, lineNumbers, highlightActiveLine, highlightActiveLineGutter, placeholder as cmPlaceholder } from '@codemirror/view'
import { history, defaultKeymap, historyKeymap, indentWithTab } from '@codemirror/commands'
import { markdown, markdownLanguage } from '@codemirror/lang-markdown'
import { indentOnInput, foldKeymap, bracketMatching, defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { searchKeymap, highlightSelectionMatches } from '@codemirror/search'
import { autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete'
import { vitesseLightTheme, vitesseDarkTheme, vitesseLightSyntax, vitesseDarkSyntax } from './cmTheme'
import { markdownListKeymap } from './markdownBehavior'
import * as cmd from './editorCommands'

interface Props {
  modelValue: string
  placeholder?: string
  readonly?: boolean
  showLineNumbers?: boolean
  /** 当宿主想以 ref.value.api 调用编辑指令时使用 */
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  readonly: false,
  showLineNumbers: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'focus'): void
  (e: 'blur'): void
  (e: 'submit'): void
  (e: 'paste-files', files: File[], view: EditorView): void
  (e: 'drop-files', files: File[], view: EditorView): void
  (e: 'cm-ready', view: EditorView): void
}>()

const rootEl = ref<HTMLDivElement | null>(null)
let view: EditorView | null = null
let suppressEmit = false

const themeCompartment = new Compartment()
const syntaxCompartment = new Compartment()
const editableCompartment = new Compartment()

const isDarkMode = () => document.documentElement.classList.contains('dark')

function currentThemeExt() {
  return isDarkMode() ? vitesseDarkTheme : vitesseLightTheme
}

function currentSyntaxExt() {
  return isDarkMode() ? vitesseDarkSyntax : vitesseLightSyntax
}

function createState(initial: string) {
  return EditorState.create({
    doc: initial,
    extensions: [
      props.showLineNumbers ? lineNumbers() : [],
      highlightActiveLineGutter(),
      highlightSpecialChars(),
      history(),
      drawSelection(),
      dropCursor(),
      EditorState.allowMultipleSelections.of(true),
      indentOnInput(),
      bracketMatching(),
      closeBrackets(),
      autocompletion(),
      rectangularSelection(),
      crosshairCursor(),
      highlightActiveLine(),
      highlightSelectionMatches(),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      themeCompartment.of(currentThemeExt()),
      syntaxCompartment.of(currentSyntaxExt()),
      markdown({ base: markdownLanguage, addKeymaps: false }),
      cmPlaceholder(props.placeholder),
      editableCompartment.of(EditorView.editable.of(!props.readonly)),
      EditorView.lineWrapping,
      keymap.of([
        ...markdownListKeymap,
        ...closeBracketsKeymap,
        ...defaultKeymap,
        ...searchKeymap,
        ...historyKeymap,
        ...foldKeymap,
        ...completionKeymap,
        indentWithTab,
        // Markdown 快捷键
        { key: 'Mod-b', preventDefault: true, run: (v) => { cmd.wrapInline(v, { before: '**', placeholder: '加粗' }); return true } },
        { key: 'Mod-i', preventDefault: true, run: (v) => { cmd.wrapInline(v, { before: '*', placeholder: '斜体' }); return true } },
        { key: 'Mod-k', preventDefault: true, run: (v) => { cmd.insertLink(v); return true } },
        { key: 'Mod-e', preventDefault: true, run: (v) => { cmd.wrapInline(v, { before: '`', placeholder: 'code' }); return true } },
        { key: 'Mod-Shift-k', preventDefault: true, run: (v) => { cmd.wrapCodeFence(v); return true } },
        { key: 'Mod-Shift-8', preventDefault: true, run: (v) => { cmd.togglePrefix(v, '- ', { unique: true }); return true } },
        { key: 'Mod-Shift-7', preventDefault: true, run: (v) => { cmd.togglePrefix(v, '1. ', { unique: true }); return true } },
        { key: 'Mod-Shift-.', preventDefault: true, run: (v) => { cmd.togglePrefix(v, '> ', { unique: true }); return true } },
        { key: 'Mod-Enter', preventDefault: true, run: () => { emit('submit'); return true } },
      ]),
      EditorView.updateListener.of(update => {
        if (update.docChanged && !suppressEmit) {
          emit('update:modelValue', update.state.doc.toString())
        }
        if (update.focusChanged) {
          emit(update.view.hasFocus ? 'focus' : 'blur')
        }
      }),
      EditorView.domEventHandlers({
        paste: (event, v) => {
          const items = event.clipboardData?.items
          if (!items) return false
          const files: File[] = []
          for (const it of Array.from(items)) {
            if (it.kind === 'file' && it.type.startsWith('image/')) {
              const f = it.getAsFile()
              if (f) files.push(f)
            }
          }
          if (files.length > 0) {
            event.preventDefault()
            emit('paste-files', files, v)
            return true
          }
          return false
        },
        drop: (event, v) => {
          const dt = event.dataTransfer
          if (!dt?.files?.length) return false
          const files = Array.from(dt.files).filter(f => f.type.startsWith('image/'))
          if (files.length > 0) {
            event.preventDefault()
            emit('drop-files', files, v)
            return true
          }
          return false
        },
        dragover: event => {
          if (event.dataTransfer?.types.includes('Files')) {
            event.preventDefault()
          }
          return false
        },
      }),
    ],
  })
}

onMounted(() => {
  if (!rootEl.value) return
  const state = createState(props.modelValue || '')
  view = new EditorView({ state, parent: rootEl.value })
  emit('cm-ready', view)

  // 监听 html.dark 切换，重新挂主题
  themeObserver = new MutationObserver(() => {
    if (!view) return
    view.dispatch({
      effects: [
        themeCompartment.reconfigure(currentThemeExt()),
        syntaxCompartment.reconfigure(currentSyntaxExt()),
      ],
    })
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

let themeObserver: MutationObserver | null = null

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  themeObserver = null
  view?.destroy()
  view = null
})

watch(
  () => props.modelValue,
  newVal => {
    if (!view) return
    const current = view.state.doc.toString()
    if (current === newVal) return
    suppressEmit = true
    view.dispatch({
      changes: { from: 0, to: current.length, insert: newVal ?? '' },
    })
    suppressEmit = false
  }
)

watch(
  () => props.readonly,
  v => {
    if (!view) return
    view.dispatch({
      effects: editableCompartment.reconfigure(EditorView.editable.of(!v)),
    })
  }
)

// 暴露给宿主使用
const focus = () => view?.focus()
const getView = () => view
const getValue = () => view?.state.doc.toString() ?? ''
const insertAtCursor = (text: string, opts?: { ensureBlankLineBefore?: boolean; ensureBlankLineAfter?: boolean }) => {
  if (!view) return
  cmd.insertAtCursor(view, text, opts)
}
const wrapInline = (before: string, after?: string, placeholder?: string) => {
  if (!view) return
  cmd.wrapInline(view, { before, after, placeholder })
}
const togglePrefix = (prefix: string, opts?: { unique?: boolean }) => {
  if (!view) return
  cmd.togglePrefix(view, prefix, opts)
}
const wrapCodeFence = (lang?: string) => {
  if (!view) return
  cmd.wrapCodeFence(view, lang)
}
const insertLink = () => view && cmd.insertLink(view)
const insertImageSyntax = (url: string, alt?: string) => view && cmd.insertImageSyntax(view, url, alt)
const insertTable = () => view && cmd.insertTable(view)
const insertDivider = () => view && cmd.insertDivider(view)
const replaceAll = (text: string) => {
  if (!view) return
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text } })
}

defineExpose({
  focus,
  getView,
  getValue,
  insertAtCursor,
  wrapInline,
  togglePrefix,
  wrapCodeFence,
  insertLink,
  insertImageSyntax,
  insertTable,
  insertDivider,
  replaceAll,
})

const rootClass = computed(() => ({
  'cp-cm-editor': true,
}))
</script>

<template>
  <div ref="rootEl" :class="rootClass" />
</template>

<style scoped>
.cp-cm-editor {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cp-cm-editor :deep(.cm-editor) {
  height: 100%;
  outline: none;
}

.cp-cm-editor :deep(.cm-editor.cm-focused) {
  outline: none;
}

.cp-cm-editor :deep(.cm-scroller) {
  height: 100%;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>
