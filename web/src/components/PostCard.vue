<script setup lang="ts">
import { ChatDotRound, Pointer, View, MagicStick, Discount, More, EditPen, Star, StarFilled, Share, Delete, RefreshRight, CircleCheck } from '@element-plus/icons-vue'
import { postApi } from '@/api/post'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pulseNotification } from '@/utils/pulseNotification'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { usePostComposerStore } from '@/store/postComposer'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import TrustLevelBadge from '@/components/common/TrustLevelBadge.vue'
import UserBadge from '@/components/common/UserBadge.vue'
import { stripMarkdown } from '@/utils/markdown'
import { encodePostId, encodeUserId } from '@/utils/shortId'
import { isTruthyFlag } from '@/utils/flags'
import { resolvePublicAssetUrl } from '@/utils/assetUrl'

const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
const props = defineProps<{
  post: any
  highlightKeyword?: string
}>()
const emit = defineEmits<{
  deleted: [postId: string]
  restored: [postId: string]
}>()

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.some((role: string) => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
})

const moderatedSectionIds = computed(() => {
  const rawIds = userStore.userInfo?.moderatedSectionIds || []
  return new Set(rawIds.map((id: number | string) => Number(id)).filter((id: number) => Number.isFinite(id) && id > 0))
})

const isDeletedPost = computed(() => props.post?.auditStatus === 'DELETED')
const currentUserId = computed(() => userStore.userId || userStore.userInfo?.id || '')

const canManagePost = computed(() => {
  if (!props.post || !currentUserId.value) return false
  if (props.post.userId === currentUserId.value) return true
  if (isAdmin.value) return true
  return Boolean(props.post.sectionId && moderatedSectionIds.value.has(Number(props.post.sectionId)))
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffHours = (now.getTime() - date.getTime()) / (1000 * 60 * 60)
  
  if (diffHours < 24) {
    if (diffHours < 1) {
      const mins = Math.max(1, Math.floor(diffHours * 60))
      return `${mins}分钟前`
    }
    return `${Math.floor(diffHours)}小时前`
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const reportVisible = ref(false)
const isReporting = ref(false)
const isOpeningEditor = ref(false)
const likeAnimating = ref(false)
const collectAnimating = ref(false)
const isLiking = ref(false)
const isCollecting = ref(false)
const reportForm = ref({
  reason: '',
  details: ''
})

const openEditor = async () => {
  if (isOpeningEditor.value) return

  isOpeningEditor.value = true
  try {
    const res = await postApi.getDetail(props.post.id)
    const detail = res.data || props.post
    composerStore.open({
      editId: detail.id || props.post.id,
      title: detail.title || props.post.title,
      content: detail.content || '',
      sectionId: detail.sectionId || props.post.sectionId,
      tags: detail.tags || props.post.tags,
      coverImage: detail.coverImage || props.post.coverImage,
      status: detail.status ?? props.post.status,
      auditStatus: detail.auditStatus || props.post.auditStatus,
      postType: detail.postType || props.post.postType,
      commentDeadline: detail.commentDeadline || props.post.commentDeadline,
      commentOncePerUser: detail.commentOncePerUser ?? props.post.commentOncePerUser
    })
  } catch (error) {
    ElMessage.error('获取帖子详情失败，暂时无法编辑')
  } finally {
    isOpeningEditor.value = false
  }
}

const handleCommand = async (command: string) => {
  if (command === 'pin') {
    if (!isAdmin.value) return
    try {
      await postApi.pin(props.post.id)
      props.post.isPinned = props.post.isPinned === 1 ? 0 : 1
      ElMessage.success(props.post.isPinned ? '已置顶帖子' : '已取消置顶')
    } catch (error) {
      ElMessage.error('操作失败')
    }
  } else if (command === 'report') {
    if (!userStore.accessToken) {
      ElMessage.warning('请先登录后再进行举报')
      return
    }
    reportForm.value.reason = ''
    reportForm.value.details = ''
    reportVisible.value = true
  } else if (command === 'edit') {
    await openEditor()
  } else if (command === 'delete') {
    await deletePost()
  } else if (command === 'restore') {
    await restorePost()
  }
}

const deletePost = async () => {
  if (!canManagePost.value || !props.post?.id) return
  try {
    await ElMessageBox.confirm('确定要将这篇帖子移入回收站吗？7 天内可恢复。', '软删除确认', {
      confirmButtonText: '移入回收站',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await postApi.delete(props.post.id)
    props.post.auditStatus = 'DELETED'
    props.post.status = 0
    ElMessage.success('已移入回收站')
    emit('deleted', props.post.id)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const restorePost = async () => {
  if (!canManagePost.value || !props.post?.id) return
  try {
    await postApi.restore(props.post.id)
    props.post.auditStatus = 'APPROVED'
    props.post.status = 1
    ElMessage.success('帖子已恢复')
    emit('restored', props.post.id)
  } catch (error) {
    ElMessage.error('恢复失败')
  }
}

const submitReport = async () => {
  if (!reportForm.value.reason) {
    ElMessage.warning('请选择举报理由')
    return
  }
  isReporting.value = true
  try {
    await postApi.report({
      targetType: 'post',
      targetId: props.post.id,
      reason: reportForm.value.reason,
      details: reportForm.value.details
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    reportVisible.value = false
  } catch (error) {
    ElMessage.error('举报提交失败')
  } finally {
    isReporting.value = false
  }
}

const imageError = ref(false)
const authorAvatarUrl = computed(() => resolvePublicAssetUrl(props.post?.authorAvatar))
const coverImageUrl = computed(() => resolvePublicAssetUrl(props.post?.coverImage))

const goToPost = () => {
  router.push('/t/' + encodePostId(props.post.id))
}

const handleLike = async (e: Event) => {
  e.stopPropagation()
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再点赞')
    return
  }
  if (isLiking.value) return // 防抖:请求未回前忽略重复点击
  isLiking.value = true
  likeAnimating.value = true
  setTimeout(() => { likeAnimating.value = false }, 400)

  // 乐观更新:先翻转 UI,失败再回滚——点击即时生效,消除网络往返的卡顿感
  const prevLiked = props.post.isLiked
  const prevCount = props.post.likeCount
  const nextLiked = !prevLiked
  props.post.isLiked = nextLiked
  props.post.likeCount = prevCount + (nextLiked ? 1 : -1)

  try {
    await postApi.like(props.post.id)
    if (nextLiked) {
      pulseNotification.like(`你为帖子「${props.post.title}」注入了一次共鸣脉冲！`)
    } else {
      pulseNotification.info(`已取消对该帖子的点赞`)
    }
  } catch (error) {
    props.post.isLiked = prevLiked
    props.post.likeCount = prevCount
    pulseNotification.error('点赞脉冲发射失败，请重试')
  } finally {
    isLiking.value = false
  }
}

const handleCollect = async (e: Event) => {
  e.stopPropagation()
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录后再收藏')
    return
  }
  if (isCollecting.value) return
  isCollecting.value = true
  collectAnimating.value = true
  setTimeout(() => { collectAnimating.value = false }, 400)

  const prevCollected = props.post.isCollected
  const prevCount = props.post.collectCount || 0
  const nextCollected = !prevCollected
  props.post.isCollected = nextCollected
  props.post.collectCount = prevCount + (nextCollected ? 1 : -1)

  try {
    await postApi.collect(props.post.id)
    if (nextCollected) {
      pulseNotification.success(`成功将「${props.post.title}」收纳至你的灵感库`, '收藏成功')
    } else {
      pulseNotification.info(`已将该帖子移出你的灵感库`)
    }
  } catch (error) {
    props.post.isCollected = prevCollected
    props.post.collectCount = prevCount
    pulseNotification.error('收藏失败，请重试')
  } finally {
    isCollecting.value = false
  }
}

const handleShare = (e: Event) => {
  e.stopPropagation()
  const url = `${window.location.origin}/t/${encodePostId(props.post.id)}`
  if (navigator.clipboard) {
    navigator.clipboard.writeText(url).then(() => {
      pulseNotification.success('帖子链接已完美复制至剪贴板，快去呼朋唤友吧！', '链接复制成功')
    })
  } else {
    const input = document.createElement('input')
    input.value = url
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    pulseNotification.success('帖子链接已完美复制至剪贴板，快去呼朋唤友吧！', '链接复制成功')
  }
}
const parsedTags = computed(() => {
  if (!props.post?.tags) return []
  return typeof props.post.tags === 'string'
    ? props.post.tags.split(',').filter(Boolean)
    : props.post.tags
})

// Song：计算纯文本摘要（优先使用后端返回的 摘要，否则前端清洗）
const postSummary = computed(() => {
  // Song：如果后端已经返回了 摘要，直接使用
  if (props.post?.summary) {
    return props.post.summary
  }

  // Song：说明
  if (props.post?.content) {
    const pureText = stripMarkdown(props.post.content)
    return pureText.length > 150 ? pureText.substring(0, 150) + '...' : pureText
  }

  return '该帖子暂无纯文本摘要内容...'
})

const keyword = computed(() => (props.highlightKeyword || '').trim())

const escapeRegExp = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const escapeHtml = (text: string) => text
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const highlightText = (text: string) => {
  const raw = text || ''
  const normalizedKeyword = keyword.value
  if (!normalizedKeyword) {
    return escapeHtml(raw)
  }

  const matcher = new RegExp(escapeRegExp(normalizedKeyword), 'gi')
  let output = ''
  let cursor = 0
  let matched = false

  raw.replace(matcher, (fragment, _group, offset) => {
    matched = true
    output += escapeHtml(raw.slice(cursor, offset))
    output += `<mark class="search-hit">${escapeHtml(fragment)}</mark>`
    cursor = offset + fragment.length
    return fragment
  })

  if (!matched) {
    return escapeHtml(raw)
  }

  output += escapeHtml(raw.slice(cursor))
  return output
}

const highlightedTitle = computed(() => highlightText(props.post?.title || ''))
const highlightedSummary = computed(() => highlightText(postSummary.value))
</script>

<template>
  <div class="post-card" @click="goToPost">
    <!-- Song：被打回提示 -->
    <div v-if="post.auditStatus === 'DELETED'" class="deleted-banner">
      <div class="rejected-banner-text">
        <span>该帖子已移入回收站</span>
        <span class="reject-reason">删除后 7 天内可由作者、版主或管理员恢复</span>
      </div>
      <el-button v-if="canManagePost" size="small" type="success" @click.stop="handleCommand('restore')">恢复</el-button>
    </div>
    <div v-else-if="post.auditStatus === 'REJECTED'" class="rejected-banner">
      <div class="rejected-banner-text">
        <span>该帖子已被管理员打回，请修改后重新发布</span>
        <span v-if="post.rejectReason" class="reject-reason">打回原因：{{ post.rejectReason }}</span>
      </div>
      <el-button size="small" type="warning" :loading="isOpeningEditor" @click.stop="handleCommand('edit')">去编辑</el-button>
    </div>
    <div v-else-if="post.auditStatus === 'PENDING'" class="pending-banner">
      <div class="rejected-banner-text">
        <span>该帖子正在审核，当前仅作者和版务可见</span>
        <span class="reject-reason">审核通过后才会进入搜索、推荐和公开列表</span>
      </div>
      <el-button v-if="canManagePost" size="small" type="warning" plain :loading="isOpeningEditor" @click.stop="handleCommand('edit')">继续修改</el-button>
    </div>
    <div v-else-if="post.status === 0 || post.auditStatus === 'DRAFT'" class="draft-banner">
      <span>该内容当前为草稿，尚未提交发布</span>
      <el-button size="small" type="primary" :loading="isOpeningEditor" @click.stop="handleCommand('edit')">继续编辑</el-button>
    </div>

    <div v-if="post.recommendReason" class="recommend-badge">
      <el-icon><MagicStick /></el-icon> 推荐内容
    </div>

    <div class="card-content">
      <!-- Author info and Section -->
      <div class="card-header-info">
        <div class="author-block" @click.stop="post.userId ? router.push(`/user/${encodeUserId(post.userId)}`) : null" :style="post.userId ? 'cursor:pointer' : ''">
          <div class="avatar-wrapper">
            <el-avatar :size="32" :src="authorAvatarUrl" class="author-avatar">
              {{ post.authorName?.charAt(0) || 'U' }}
            </el-avatar>
          </div>
          <div class="author-details">
            <div style="display:flex; align-items:center;">
                <span class="author-name">{{ post.authorName }}</span>
                <UserRoleBadge :roles="post.authorRoles" />
                <TrustLevelBadge :trust-level="post.authorTrustLevel ?? 0" />
                <UserBadge :text="post.authorBadgeText || ''" :color="post.authorBadgeColor" :effect="post.authorBadgeStyle" />
            </div>
            <div class="post-meta-text">
              <span class="post-date">{{ formatDate(post.createTime) }}</span>
            </div>
          </div>
        </div>
        
        <div class="header-right-actions">
          <el-tag v-if="post.sectionName" size="small" type="info" effect="light" class="section-tag">
            {{ post.sectionName }}
          </el-tag>
          
          <span @click.stop>
            <el-dropdown trigger="click" @command="handleCommand">
              <span class="more-options-btn">
                <el-icon><More /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="!isDeletedPost && canManagePost" command="edit">
                    <el-icon><EditPen /></el-icon> 编辑此贴
                  </el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin && !isDeletedPost" command="pin">
                    {{ post.isPinned === 1 ? '取消置顶' : '置顶帖' }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canManagePost && !isDeletedPost" command="delete" divided>
                    <el-icon><Delete /></el-icon> 移入回收站
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canManagePost && isDeletedPost" command="restore" divided>
                    <el-icon><RefreshRight /></el-icon> 恢复帖子
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isDeletedPost" command="report" :divided="isAdmin && !canManagePost">
                    举报违规内容
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </div>
      </div>

      <!-- Title & Excerpt -->
      <div class="card-body-content">
        <div class="text-content">
          <h2 class="post-title">
            <!-- 已解决标记 -->
            <span
              v-if="isTruthyFlag(post.hasAdoptedAnswer)"
              class="solved-tag"
            >
              <el-icon class="solved-tag-icon"><CircleCheck /></el-icon>
              已解决
            </span>
            <el-tag v-if="post.isPinned === 1" type="warning" size="small" effect="dark" class="hot-tag">
              置顶
            </el-tag>
            <el-tag v-if="post.trendLevel === 'hot'" type="danger" size="small" effect="dark" class="hot-tag">
              <el-icon><Discount /></el-icon> HOT
            </el-tag>
            <span class="title-text" v-html="highlightedTitle"></span>
          </h2>
          <p class="post-excerpt" v-html="highlightedSummary"></p>
        </div>

        <!-- Optional Thumbnail -->
        <div 
          v-if="coverImageUrl && !imageError"
          class="post-thumbnail"
        >
          <img 
            :src="coverImageUrl" 
            @error="imageError = true"
            class="thumbnail-image"
            alt="thumbnail"
            loading="lazy"
            decoding="async"
          />
        </div>
      </div>

      <!-- Footer: Tags & Actions -->
      <div class="card-footer">
        <div class="tags-area">
          <template v-if="parsedTags.length">
            <el-tag 
              v-for="tag in parsedTags.slice(0, 3)" 
              :key="tag" 
              size="small" 
              class="custom-tag"
              @click.stop="router.push(`/tag/${tag}`)"
            >
              #{{ tag }}
            </el-tag>
            <span v-if="parsedTags.length > 3" class="text-caption">+{{ parsedTags.length - 3 }}</span>
          </template>
        </div>

        <div class="interaction-area">
          <span class="interaction-btn" :class="{ 'is-active': post.isLiked, 'btn-animate': likeAnimating }" @click.stop="handleLike">
            <el-icon><Pointer /></el-icon> {{ post.likeCount || 0 }}
          </span>
          <span class="interaction-btn" :class="{ 'is-active': post.isCollected, 'btn-animate': collectAnimating }" @click.stop="handleCollect">
            <el-icon><component :is="post.isCollected ? StarFilled : Star" /></el-icon> {{ post.collectCount || 0 }}
          </span>
          <span class="interaction-btn">
            <el-icon><ChatDotRound /></el-icon> {{ post.commentCount || 0 }}
          </span>
          <span class="interaction-btn hidden-xs-only">
            <el-icon><View /></el-icon> {{ post.viewCount || 0 }}
          </span>
          <span class="interaction-btn hidden-xs-only" @click.stop="handleShare">
            <el-icon><Share /></el-icon>
          </span>
        </div>
      </div>
    </div>
  </div>
  
  <!-- 举报弹窗 -->
  <el-dialog v-model="reportVisible" title="举报内容" width="400px" append-to-body>
    <el-form label-position="top">
      <el-form-item label="请选择举报理由（必选）">
        <el-select v-model="reportForm.reason" placeholder="选择举报理由" style="width: 100%;">
          <el-option label="垃圾广告/引流" value="垃圾广告" />
          <el-option label="色情低俗" value="色情低俗" />
          <el-option label="违法违规" value="违法违规" />
          <el-option label="人身攻击/网暴" value="人身攻击" />
          <el-option label="不实信息/谣言" value="不实信息" />
          <el-option label="其他原因" value="其他原因" />
        </el-select>
      </el-form-item>
      <el-form-item label="详细说明（选填）">
        <el-input 
          v-model="reportForm.details" 
          type="textarea" 
          :rows="3" 
          placeholder="请提供更多细节帮助管理员判断..." 
          maxlength="200" 
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReport" :loading="isReporting">提交举报</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style scoped>
.post-card {
  margin-bottom: 16px;
  cursor: pointer;
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
  transition: transform 0.28s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.28s ease, border-color 0.24s ease, background-color 0.24s ease;
  background-color: var(--cp-bg-card);
  position: relative;
  overflow: hidden;
  transform: translateZ(0);
  will-change: transform, box-shadow;
}

.post-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(420px 120px at 100% 0%, rgba(255, 191, 92, 0.12), transparent 60%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0));
  opacity: 0;
  transition: opacity 0.24s ease;
  pointer-events: none;
}

.post-card:hover {
  transform: translate3d(0, -4px, 0);
  box-shadow: 0 18px 38px rgba(15, 23, 42, 0.12);
  border-color: var(--cp-primary);
  background-color: var(--cp-hover);
}

.post-card:hover::before {
  opacity: 1;
}

.recommend-badge {
  position: absolute;
  top: 0;
  right: 0;
  background: linear-gradient(135deg, var(--el-color-primary-light-8), var(--el-color-primary-light-9));
  color: var(--el-color-primary);
  padding: 4px 12px;
  font-size: 10px;
  font-weight: 600;
  border-bottom-left-radius: var(--el-border-radius-base);
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  position: relative;
  z-index: 1;
}

/* Song：说明 */
.card-header-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.author-block {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-wrapper {
  position: relative;
  display: flex;
}

.author-details {
  display: flex;
  flex-direction: column;
}

.author-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.post-meta-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-tag {
  border-radius: 4px;
}

.header-right-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.more-options-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.more-options-btn:hover {
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

/* Song：说明 */
.card-body-content {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-top: 4px;
}

.text-content {
  flex: 1;
  min-width: 0;
}

.post-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 8px 0;
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 8px;
}

.hot-tag {
  font-weight: bold;
}

.solved-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  height: 22px;
  padding: 0 8px;
  border-radius: 6px;
  background: var(--accept-bg);
  border: 1px solid var(--accept-border);
  color: var(--accept-text);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.solved-tag .solved-tag-icon {
  font-size: 13px;
}

.title-text {
  min-width: 0;
}

.post-title :deep(mark.search-hit),
.post-excerpt :deep(mark.search-hit) {
  background: color-mix(in oklab, var(--el-color-warning-light-8) 76%, white 24%);
  color: inherit;
  border-radius: 4px;
  padding: 0 2px;
}

.post-excerpt {
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  overflow: hidden;
  text-overflow: ellipsis;
}

.post-thumbnail {
  width: 120px;
  height: 80px;
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  background-color: var(--el-fill-color-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumbnail-image {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
  transition: transform 0.36s cubic-bezier(0.22, 1, 0.36, 1), filter 0.24s ease;
}

.post-card:hover .thumbnail-image {
  transform: scale(1.06);
  filter: saturate(1.06);
}

/* Song：说明 */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}

.tags-area {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.custom-tag {
  background-color: var(--el-fill-color-light);
  border-color: transparent;
  color: var(--el-text-color-regular);
}

.custom-tag:hover {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.interaction-area {
  display: flex;
  align-items: center;
  gap: 16px;
}

.interaction-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  transition: color 0.2s ease, transform 0.2s ease;
}

.interaction-btn:hover {
  color: var(--el-color-primary);
  transform: translateY(-1px);
}

.interaction-btn.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

@keyframes btn-pop {
  0%   { transform: scale(1); }
  40%  { transform: scale(1.35); }
  70%  { transform: scale(0.9); }
  100% { transform: scale(1); }
}

.interaction-btn.btn-animate {
  animation: btn-pop 0.38s cubic-bezier(0.22, 1, 0.36, 1);
}

@media (max-width: 600px) {
  .post-thumbnail {
    display: none;
  }
}

.rejected-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--el-color-danger-light-9);
  border-bottom: 1px solid var(--el-color-danger-light-5);
  color: var(--el-color-danger);
  font-size: 13px;
  font-weight: 600;
}

.deleted-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--el-color-danger-light-9);
  border-bottom: 1px solid var(--el-color-danger-light-5);
  color: var(--el-color-danger);
  font-size: 13px;
  font-weight: 600;
}

.pending-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--el-color-warning-light-9);
  border-bottom: 1px solid var(--el-color-warning-light-5);
  color: var(--el-color-warning-dark-2);
  font-size: 13px;
  font-weight: 600;
}

.rejected-banner-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.reject-reason {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.85;
}

.draft-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: var(--el-color-info-light-9);
  border-bottom: 1px solid var(--el-color-info-light-5);
  color: var(--el-color-info-dark-2);
  font-size: 13px;
  font-weight: 600;
}
</style>
