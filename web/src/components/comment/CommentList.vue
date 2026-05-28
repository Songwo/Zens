<script setup lang="ts">
import { ChatLineRound, Coordinate, More, Delete, Warning, Link } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { ElMessage, ElMessageBox } from 'element-plus'
import { timeAgo } from '@/utils/timeAgo'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import { mdComment } from '@/utils/markdownRenderer'
import { warmupHighlighter, preloadLanguages } from '@/utils/shiki'

const props = defineProps<{
  comments: any[]
  activeCommentId?: string | null
  postId: string
  postShortId?: string
  postAuthorId?: string | null
  currentUserId?: string | null
  isAdmin?: boolean
  canModerateSection?: boolean
}>()

const emit = defineEmits<{
  (e: 'like', comment: any): void
  (e: 'reply', comment: any): void
  (e: 'delete', comment: any): void
  (e: 'report', comment: any): void
}>()

const replySortMode = ref<'time' | 'hot'>('time')
const expandedCommentIds = ref<Set<string>>(new Set())
const COMMENT_COLLAPSE_THRESHOLD = 220

const getAvatar = (c: any) => c.userAvatar || c.avatar || c.author?.avatar || ''
const getName = (c: any) => c.nickname || c.author?.name || '匿名用户'
const getTime = (c: any) => timeAgo(c.createTime || c.createdAt || '')
const getLikes = (c: any) => c.likeCount ?? c.likes ?? 0
const getRoles = (c: any) => c.roles || []
const normalizeId = (value: unknown) => String(value ?? '').trim()
const toCommentDomId = (value: unknown) => `comment-${normalizeId(value)}`
const isCommentActive = (value: unknown) => {
  const current = normalizeId(props.activeCommentId)
  if (!current) return false
  return normalizeId(value) === current
}

const highlightMentionHtml = (value: string) => {
  return value.replace(/(^|[\s(>])@([\u4e00-\u9fa5A-Za-z0-9_.-]{2,32})/g, '$1<span class="comment-mention">@$2</span>')
}

// \u5f3a\u5236 v-html \u91cd\u6e32\u67d3\u7684\u7248\u672c\u53f7\uff1aShiki \u5f02\u6b65\u52a0\u8f7d\u5b8c\u8bed\u8a00\u540e bump \u4e00\u6b21\uff0c\u8ba9\u4ee3\u7801\u5757\u4e0a\u8272\u3002
const renderVersion = ref(0)

const FENCED_LANG_RE = /(^|\n) {0,3}(`{3,}|~{3,})[ \t]*([\w+\-#./]*)[^\n]*\n/g

function collectLangs(comments: any[]): string[] {
  const langs: string[] = []
  const walk = (list: any[]) => {
    for (const c of list || []) {
      const src = String(c?.content ?? '')
      if (src) {
        FENCED_LANG_RE.lastIndex = 0
        let m: RegExpExecArray | null
        while ((m = FENCED_LANG_RE.exec(src)) !== null) {
          if (m[3]) langs.push(m[3])
        }
      }
      if (c?.children?.length) walk(c.children)
    }
  }
  walk(comments)
  return langs
}

watch(
  () => props.comments,
  async (list) => {
    if (!list || list.length === 0) return
    const langs = collectLangs(list)
    await warmupHighlighter()
    if (langs.length > 0) {
      await preloadLanguages(langs)
    }
    renderVersion.value++
  },
  { immediate: true, deep: false }
)

const sanitizeCommentHtml = (raw: any) => {
  // \u89e6\u8fbe renderVersion \u8ba9\u6b64\u6a21\u677f\u8c03\u7528\u4e0e Shiki \u52a0\u8f7d\u72b6\u6001\u8054\u52a8
  void renderVersion.value

  const source = String(raw ?? '').trim()
  if (!source) return ''
  // 1) markdown \u6e32\u67d3\uff08\u542b Shiki \u4ee3\u7801\u9ad8\u4eae + .code-block-wrapper \u5305\u88c5\uff09
  const rendered = mdComment.render(source)
  // 2) \u628a @username \u8f6c\u4e3a\u9ad8\u4eae span\uff08\u5728 HTML \u4e0a\u8dd1\uff0c\u907f\u5f00 tag \u5185\u90e8\uff09
  const withMention = highlightMentionHtml(rendered)
  // 3) \u540c\u4e3b\u5e16\u4e00\u81f4\u7684 sanitize \u767d\u540d\u5355\uff08\u4fdd\u7559 style/data-*/button \u4f9b Shiki + \u590d\u5236\u6309\u94ae\u5de5\u4f5c\uff09
  return DOMPurify.sanitize(withMention, {
    ALLOWED_TAGS: [
      'h1','h2','h3','h4','h5','h6','p','br','hr',
      'ul','ol','li','blockquote','pre','code',
      'table','thead','tbody','tr','th','td',
      'a','img','strong','em','del','s','mark','kbd',
      'sup','sub','div','span','button'
    ],
    ALLOWED_ATTR: [
      'href','src','alt','title','class','id','target','rel',
      'type','loading','decoding','aria-label',
      'style',
      'data-lang','data-raw','data-line','data-highlighted-chars','data-highlighted-chars-id'
    ],
    FORCE_BODY: true,
    ALLOW_DATA_ATTR: false,
  })
}

const getCommentKey = (comment: any) => normalizeId(comment?.id)

const isLongComment = (raw: any) => String(raw ?? '').length > COMMENT_COLLAPSE_THRESHOLD

const isCollapsed = (comment: any) => {
  const id = getCommentKey(comment)
  if (!id || !isLongComment(comment?.content)) return false
  return !expandedCommentIds.value.has(id)
}

const toggleExpand = (comment: any) => {
  const id = getCommentKey(comment)
  if (!id) return
  const next = new Set(expandedCommentIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedCommentIds.value = next
}

const getSortedChildren = (children: any[]) => {
  const list = Array.isArray(children) ? [...children] : []
  if (replySortMode.value === 'hot') {
    return list.sort((a, b) => getLikes(b) - getLikes(a))
  }
  return list.sort((a, b) => {
    const t1 = new Date(a?.createTime || a?.createdAt || 0).getTime()
    const t2 = new Date(b?.createTime || b?.createdAt || 0).getTime()
    return t1 - t2
  })
}

const isDeletedComment = (c: any) => c?.auditStatus === 'DELETED'

const canDelete = (c: any) => {
  if (!props.currentUserId) return false
  if (isDeletedComment(c)) return false
  return c.userId === props.currentUserId
      || (!!props.postAuthorId && props.postAuthorId === props.currentUserId)
      || props.isAdmin === true
      || props.canModerateSection === true
}

const canReport = (c: any) => {
  if (!props.currentUserId) return false
  if (isDeletedComment(c)) return false
  return c.userId !== props.currentUserId
}

const buildCommentLink = (commentId: string) => {
  const shortId = props.postShortId || props.postId
  return `${location.origin}/t/${shortId}?commentId=${commentId}`
}

const handleAction = async (cmd: string, comment: any) => {
  if (cmd === 'copy') {
    const link = buildCommentLink(comment.id)
    try {
      await navigator.clipboard.writeText(link)
      ElMessage.success('评论链接已复制')
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = link
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      ElMessage.success('评论链接已复制')
    }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(
        '确认删除该评论吗？删除后 3 天内可由管理员恢复，超过 3 天将永久清除。',
        '删除评论',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning',
          confirmButtonClass: 'el-button--danger',
        }
      )
      emit('delete', comment)
    } catch {
      // 取消，无操作
    }
  } else if (cmd === 'report') {
    emit('report', comment)
  }
}

</script>

<template>
  <div class="comment-list">
    <div class="list-header">
      <h3>{{ comments.length }} 条回复</h3>
      <div class="list-tools">
        <span class="sort-label">楼中楼排序</span>
        <el-radio-group v-model="replySortMode" size="small">
          <el-radio-button label="time">按时间</el-radio-button>
          <el-radio-button label="hot">按热度</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div
      v-for="(comment, index) in comments"
      :key="comment.id"
      :id="toCommentDomId(comment.id)"
      class="comment-item"
      :class="{ 'is-active': isCommentActive(comment.id) }"
    >
      <div class="comment-avatar">
        <el-avatar :size="40" :src="getAvatar(comment)">
          {{ getName(comment).charAt(0) }}
        </el-avatar>
      </div>

      <div class="comment-main">
        <div class="comment-meta">
          <div class="meta-left">
            <span class="author-name">{{ getName(comment) }}</span>
            <UserRoleBadge :roles="getRoles(comment)" />
            <span class="time-label">{{ getTime(comment) }}</span>
          </div>
          <div class="meta-right">
            <span class="floor-num">#{{ comment.floor || index + 1 }}</span>
          </div>
        </div>

        <template v-if="isDeletedComment(comment)">
          <div class="comment-deleted-placeholder">
            <el-icon><Delete/></el-icon>
            <span>该评论已删除</span>
          </div>
        </template>
        <template v-else>
          <div class="comment-text markdown-body" :class="{ collapsed: isCollapsed(comment) }" v-html="sanitizeCommentHtml(comment.content)"></div>
          <div v-if="isLongComment(comment.content)" class="expand-wrap">
            <el-button link type="primary" size="small" @click.stop="toggleExpand(comment)">
              {{ isCollapsed(comment) ? '展开全文' : '收起' }}
            </el-button>
          </div>
        </template>

        <div v-if="!isDeletedComment(comment)" class="comment-actions">
          <div class="actions-group">
            <el-button
              link
              :type="comment.isLiked ? 'primary' : 'info'"
              size="small"
              :icon="Coordinate"
              @click.stop="emit('like', comment)"
            >
              {{ getLikes(comment) > 0 ? getLikes(comment) : '点赞' }}
            </el-button>
            <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', comment)">
              回复
            </el-button>
          </div>
          <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, comment)">
            <el-button link size="small" :icon="More" class="action-trigger" @click.stop/>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="copy">
                  <el-icon><Link/></el-icon>&nbsp;复制评论链接
                </el-dropdown-item>
                <el-dropdown-item v-if="canReport(comment)" command="report">
                  <el-icon><Warning/></el-icon>&nbsp;举报
                </el-dropdown-item>
                <el-dropdown-item v-if="canDelete(comment)" command="delete" divided>
                  <el-icon><Delete/></el-icon>&nbsp;删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- Nested Replies (children) -->
        <div v-if="comment.children && comment.children.length > 0" class="replies-block">
          <template v-for="child in getSortedChildren(comment.children)" :key="child.id">
            <div
              :id="toCommentDomId(child.id)"
              class="reply-item"
              :class="{ 'is-active': isCommentActive(child.id) }"
            >
              <div class="reply-avatar">
                <el-avatar :size="30" :src="getAvatar(child)">
                  {{ getName(child).charAt(0) }}
                </el-avatar>
              </div>
              <div class="reply-main">
                <div class="reply-meta">
                  <span class="author-name">{{ getName(child) }}</span>
                  <UserRoleBadge :roles="getRoles(child)" />
                  <span v-if="child.replyUserNickname" class="reply-to">
                    <span class="reply-arrow">▸</span>回复 <strong>{{ child.replyUserNickname }}</strong>
                  </span>
                  <span class="time-label">{{ getTime(child) }}</span>
                </div>
                <template v-if="isDeletedComment(child)">
                  <div class="comment-deleted-placeholder">
                    <el-icon><Delete/></el-icon>
                    <span>该评论已删除</span>
                  </div>
                </template>
                <template v-else>
                  <div class="reply-text markdown-body" :class="{ collapsed: isCollapsed(child) }" v-html="sanitizeCommentHtml(child.content)"></div>
                  <div v-if="isLongComment(child.content)" class="expand-wrap">
                    <el-button link type="primary" size="small" @click.stop="toggleExpand(child)">
                      {{ isCollapsed(child) ? '展开全文' : '收起' }}
                    </el-button>
                  </div>
                </template>
                <div v-if="!isDeletedComment(child)" class="comment-actions">
                  <div class="actions-group">
                    <el-button
                      link
                      :type="child.isLiked ? 'primary' : 'info'"
                      size="small"
                      :icon="Coordinate"
                      @click.stop="emit('like', child)"
                    >
                      {{ getLikes(child) > 0 ? getLikes(child) : '点赞' }}
                    </el-button>
                    <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', child)">
                      回复
                    </el-button>
                  </div>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, child)">
                    <el-button link size="small" :icon="More" class="action-trigger" @click.stop/>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="copy">
                          <el-icon><Link/></el-icon>&nbsp;复制评论链接
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canReport(child)" command="report">
                          <el-icon><Warning/></el-icon>&nbsp;举报
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canDelete(child)" command="delete" divided>
                          <el-icon><Delete/></el-icon>&nbsp;删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </div>
            <!-- 子评论的子评论（楼中楼平铺展示） -->
            <div
              v-for="grandChild in getSortedChildren(child.children || [])"
              :key="grandChild.id"
              :id="toCommentDomId(grandChild.id)"
              class="reply-item reply-item--deep"
              :class="{ 'is-active': isCommentActive(grandChild.id) }"
            >
              <div class="reply-avatar">
                <el-avatar :size="26" :src="getAvatar(grandChild)">
                  {{ getName(grandChild).charAt(0) }}
                </el-avatar>
              </div>
              <div class="reply-main">
                <div class="reply-meta">
                  <span class="author-name">{{ getName(grandChild) }}</span>
                  <UserRoleBadge :roles="getRoles(grandChild)" />
                  <span class="reply-to">
                    <span class="reply-arrow">▸</span>回复 <strong>{{ grandChild.replyUserNickname || getName(child) }}</strong>
                  </span>
                  <span class="time-label">{{ getTime(grandChild) }}</span>
                </div>
                <template v-if="isDeletedComment(grandChild)">
                  <div class="comment-deleted-placeholder">
                    <el-icon><Delete/></el-icon>
                    <span>该评论已删除</span>
                  </div>
                </template>
                <template v-else>
                  <div class="reply-text markdown-body" :class="{ collapsed: isCollapsed(grandChild) }" v-html="sanitizeCommentHtml(grandChild.content)"></div>
                  <div v-if="isLongComment(grandChild.content)" class="expand-wrap">
                    <el-button link type="primary" size="small" @click.stop="toggleExpand(grandChild)">
                      {{ isCollapsed(grandChild) ? '展开全文' : '收起' }}
                    </el-button>
                  </div>
                </template>
                <div v-if="!isDeletedComment(grandChild)" class="comment-actions">
                  <div class="actions-group">
                    <el-button
                      link
                      :type="grandChild.isLiked ? 'primary' : 'info'"
                      size="small"
                      :icon="Coordinate"
                      @click.stop="emit('like', grandChild)"
                    >
                      {{ getLikes(grandChild) > 0 ? getLikes(grandChild) : '点赞' }}
                    </el-button>
                    <el-button link type="info" size="small" :icon="ChatLineRound" @click.stop="emit('reply', grandChild)">
                      回复
                    </el-button>
                  </div>
                  <el-dropdown trigger="click" @command="(cmd: string) => handleAction(cmd, grandChild)">
                    <el-button link size="small" :icon="More" class="action-trigger" @click.stop/>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="copy">
                          <el-icon><Link/></el-icon>&nbsp;复制评论链接
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canReport(grandChild)" command="report">
                          <el-icon><Warning/></el-icon>&nbsp;举报
                        </el-dropdown-item>
                        <el-dropdown-item v-if="canDelete(grandChild)" command="delete" divided>
                          <el-icon><Delete/></el-icon>&nbsp;删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-list {
  background: var(--cp-bg-card);
  border-radius: var(--el-border-radius-base);
  box-shadow: var(--el-box-shadow-lighter);
  margin-bottom: 24px;
}

.list-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.list-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
  font-weight: 700;
}

.comment-item {
  display: flex;
  padding: 24px;
  border-bottom: 1px solid var(--cp-divider);
  transition: background-color 0.2s;
  scroll-margin-top: calc(var(--header-height, 64px) + 20px);
}

.comment-item:hover {
  background-color: var(--cp-hover);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-avatar {
  flex-shrink: 0;
  margin-right: 16px;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.meta-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.time-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.floor-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--cp-text-muted);
  background-color: var(--cp-bg-elevated);
  padding: 2px 6px;
  border-radius: 4px;
}

.comment-text,
.reply-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  margin-bottom: 12px;
  word-break: break-word;
}

/* 评论内 markdown 渲染适配：所有元素紧凑化，标题缩小避免在小空间里抢戏 */
.comment-text.markdown-body :deep(h1),
.reply-text.markdown-body :deep(h1) {
  font-size: 1.3em; margin: 0.8em 0 0.35em; border-bottom: none; padding-bottom: 0; line-height: 1.4;
}
.comment-text.markdown-body :deep(h2),
.reply-text.markdown-body :deep(h2) {
  font-size: 1.2em; margin: 0.7em 0 0.35em; border-bottom: none; padding-bottom: 0; line-height: 1.4;
}
.comment-text.markdown-body :deep(h3),
.reply-text.markdown-body :deep(h3) {
  font-size: 1.1em; margin: 0.6em 0 0.3em; line-height: 1.4;
}
.comment-text.markdown-body :deep(h4),
.comment-text.markdown-body :deep(h5),
.comment-text.markdown-body :deep(h6),
.reply-text.markdown-body :deep(h4),
.reply-text.markdown-body :deep(h5),
.reply-text.markdown-body :deep(h6) {
  font-size: 1em; margin: 0.5em 0 0.3em; line-height: 1.4;
}
.comment-text.markdown-body :deep(p),
.reply-text.markdown-body :deep(p) {
  margin: 0 0 0.55em 0;
  line-height: 1.7;
}
.comment-text.markdown-body :deep(p:last-child),
.reply-text.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.comment-text.markdown-body :deep(ul),
.comment-text.markdown-body :deep(ol),
.reply-text.markdown-body :deep(ul),
.reply-text.markdown-body :deep(ol) {
  margin: 0.25em 0 0.55em 0;
  padding-left: 1.6em;
}
.comment-text.markdown-body :deep(li),
.reply-text.markdown-body :deep(li) { margin-bottom: 0.15em; }
.comment-text.markdown-body :deep(blockquote),
.reply-text.markdown-body :deep(blockquote) {
  margin: 0.5em 0;
  padding: 8px 12px;
  font-size: 0.95em;
}
.comment-text.markdown-body :deep(hr),
.reply-text.markdown-body :deep(hr) { margin: 0.8em 0; }
.comment-text.markdown-body :deep(.code-block-wrapper),
.reply-text.markdown-body :deep(.code-block-wrapper) {
  margin: 0.6em 0;
  font-size: 12.5px;
}
.comment-text.markdown-body :deep(img),
.reply-text.markdown-body :deep(img) {
  max-width: 100%;
  max-height: 320px;
  margin: 0.4em 0;
}
.comment-text.markdown-body :deep(table),
.reply-text.markdown-body :deep(table) {
  font-size: 13px;
}

.comment-text.collapsed,
.reply-text.collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.expand-wrap {
  margin: -6px 0 8px;
}

.comment-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  opacity: 0.7;
  transition: opacity 0.2s;
}

.comment-item:hover .comment-actions,
.reply-item:hover .comment-actions {
  opacity: 1;
}

.actions-group {
  display: flex;
  gap: 16px;
}

/* Song：说明 */
.replies-block {
  margin-top: 16px;
  padding: 16px 20px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.85) 0%, rgba(241, 245, 249, 0.45) 100%);
  border-radius: 12px;
  border-left: 3px solid var(--el-color-primary-light-4);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.01) inset;
}

.reply-item {
  display: flex;
  padding: 14px 0;
  border-bottom: 1px dashed var(--el-border-color-extra-light);
  scroll-margin-top: calc(var(--header-height, 64px) + 20px);
}

.comment-item.is-active,
.reply-item.is-active {
  animation: comment-focus 1.8s ease;
  background-color: var(--el-color-primary-light-9);
}

.reply-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.reply-item:first-child {
  padding-top: 0;
}

.reply-avatar {
  flex-shrink: 0;
  margin-right: 12px;
}

.reply-main {
  flex: 1;
  min-width: 0;
}

.reply-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.reply-to {
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
}

.reply-arrow {
  margin: 0 6px;
  color: var(--el-text-color-placeholder);
  font-weight: bold;
}

.reply-to strong {
  color: var(--el-color-primary);
  font-weight: 600;
}

.reply-item--deep {
  padding-left: 12px;
  margin-top: 8px;
  background-color: rgba(241, 245, 249, 0.35);
  border-radius: 6px;
  border-left: 2px solid var(--el-border-color-light);
}

:deep(.comment-mention) {
  color: var(--el-color-primary);
  font-weight: 600;
}

@keyframes comment-focus {
  0% {
    box-shadow: inset 0 0 0 1px rgba(64, 158, 255, 0.55);
  }
  100% {
    box-shadow: inset 0 0 0 1px transparent;
  }
}

.comment-deleted-placeholder {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  font-style: italic;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  margin-bottom: 12px;
}

.comment-actions {
  position: relative;
}

.action-trigger {
  opacity: 0.5;
  transition: opacity 0.2s;
  margin-left: auto;
}

.comment-item:hover .action-trigger,
.reply-item:hover .action-trigger {
  opacity: 1;
}
</style>
