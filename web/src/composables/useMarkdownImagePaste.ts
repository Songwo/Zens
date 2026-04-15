import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadApi } from '@/api/upload'
import { UPLOAD_IMAGE_MAX_SIZE_MB } from '@/constants/upload'

interface UseMarkdownImagePasteOptions {
  getTextarea: () => HTMLTextAreaElement | undefined
  getContent: () => string
  setContent: (value: string) => void
  module?: string
  maxImageSizeMb?: number
}

function isImageClipboardItem(item: DataTransferItem): boolean {
  return item.kind === 'file' && item.type.startsWith('image/')
}

function insertTextAtSelection(
  rawContent: string,
  textarea: HTMLTextAreaElement,
  insertedText: string
): { content: string; nextCursor: number } {
  const start = textarea.selectionStart ?? rawContent.length
  const end = textarea.selectionEnd ?? rawContent.length

  const before = rawContent.slice(0, start)
  const after = rawContent.slice(end)

  const needLeadingBreak = before.length > 0 && !before.endsWith('\n') ? '\n' : ''
  const needTrailingBreak = after.length > 0 && !after.startsWith('\n') ? '\n' : ''
  const finalInserted = `${needLeadingBreak}${insertedText}${needTrailingBreak}`
  const content = `${before}${finalInserted}${after}`

  return {
    content,
    nextCursor: (before + finalInserted).length
  }
}

export function useMarkdownImagePaste(options: UseMarkdownImagePasteOptions) {
  const isPastingImage = ref(false)
  const maxImageSizeMb = options.maxImageSizeMb ?? UPLOAD_IMAGE_MAX_SIZE_MB
  const maxSizeBytes = maxImageSizeMb * 1024 * 1024
  const uploadModule = options.module ?? 'post'

  const handlePaste = async (event: ClipboardEvent) => {
    const clipboard = event.clipboardData
    if (!clipboard?.items?.length) return

    const imageFiles: File[] = []
    Array.from(clipboard.items).forEach(item => {
      if (!isImageClipboardItem(item)) return
      const file = item.getAsFile()
      if (file) imageFiles.push(file)
    })

    if (imageFiles.length === 0) return

    const textarea = options.getTextarea()
    if (!textarea) return

    event.preventDefault()
    isPastingImage.value = true

    try {
      for (const file of imageFiles) {
        if (file.size > maxSizeBytes) {
          ElMessage.warning(`图片超过 ${maxImageSizeMb}MB，已跳过`)
          continue
        }

        const imageUrl = await uploadApi.uploadImage(file, uploadModule)
        const imageMarkdown = `![${file.name || 'image'}](${imageUrl})`
        const insertion = insertTextAtSelection(options.getContent(), textarea, imageMarkdown)
        options.setContent(insertion.content)
        textarea.focus()
        textarea.setSelectionRange(insertion.nextCursor, insertion.nextCursor)
      }

      ElMessage.success('图片已插入正文')
    } catch (error) {
      ElMessage.error('粘贴图片上传失败')
    } finally {
      isPastingImage.value = false
    }
  }

  let boundTextarea: HTMLTextAreaElement | null = null

  const bindPasteListener = () => {
    const textarea = options.getTextarea()
    if (!textarea) return
    if (boundTextarea === textarea) return

    if (boundTextarea) {
      boundTextarea.removeEventListener('paste', handlePaste)
    }

    textarea.addEventListener('paste', handlePaste)
    boundTextarea = textarea
  }

  const unbindPasteListener = () => {
    if (!boundTextarea) return
    boundTextarea.removeEventListener('paste', handlePaste)
    boundTextarea = null
  }

  return {
    isPastingImage,
    bindPasteListener,
    unbindPasteListener
  }
}
