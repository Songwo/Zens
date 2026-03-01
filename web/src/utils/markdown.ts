/**
 * Song：说明
 * Song：说明
 */

/**
 * Song：说明
 * Song：说明
 * Song：说明
 */
export function stripMarkdown(content: string): string {
  if (!content) return ''

  let text = content

  // Song：说明
  text = text.replace(/<[^>]*>/g, '')

  // Song：移除代码块标记 ```
  text = text.replace(/```[\s\S]*?```/g, '')
  text = text.replace(/```.*/g, '')

  // Song：移除行内代码 `
  text = text.replace(/`[^`]+`/g, '')
  text = text.replace(/`/g, '')

  // Song：移除标题符号 # ## ### 等
  text = text.replace(/^#{1,6}\s+/gm, '')
  text = text.replace(/\n#{1,6}\s+/g, '\n')

  // Song：移除加粗 ** 和 __
  text = text.replace(/\*\*([^*]+)\*\*/g, '$1')
  text = text.replace(/__([^_]+)__/g, '$1')

  // Song：移除斜体 * 和 _
  text = text.replace(/\*([^*]+)\*/g, '$1')
  text = text.replace(/_([^_]+)_/g, '$1')

  // Song：移除删除线 ~~
  text = text.replace(/~~([^~]+)~~/g, '$1')

  // Song：移除分割线 --- 或 ***
  text = text.replace(/^[-*]{3,}$/gm, '')
  text = text.replace(/\n[-*]{3,}\n/g, '\n')

  // Song：移除引用符号 >
  text = text.replace(/^>\s+/gm, '')
  text = text.replace(/\n>\s+/g, '\n')

  // Song：说明
  text = text.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')

  // Song：说明
  text = text.replace(/!\[([^\]]*)\]\([^)]+\)/g, '')

  // Song：移除列表符号 - * +
  text = text.replace(/^[-*+]\s+/gm, '')
  text = text.replace(/\n[-*+]\s+/g, '\n')

  // Song：移除有序列表 1. 2. 等
  text = text.replace(/^\d+\.\s+/gm, '')
  text = text.replace(/\n\d+\.\s+/g, '\n')

  // Song：说明
  text = text.replace(/&nbsp;/g, ' ')
  text = text.replace(/&lt;/g, '<')
  text = text.replace(/&gt;/g, '>')
  text = text.replace(/&amp;/g, '&')
  text = text.replace(/&quot;/g, '"')

  // Song：清理多余空白
  text = text.replace(/\s+/g, ' ')
  text = text.trim()

  return text
}

/**
 * Song：生成文本摘要
 * Song：说明
 * Song：说明
 * Song：说明
 */
export function generateSummary(content: string, maxLength: number = 150): string {
  const pureText = stripMarkdown(content)

  if (pureText.length <= maxLength) {
    return pureText
  }

  return pureText.substring(0, maxLength) + '...'
}
