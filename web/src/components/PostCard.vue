<script setup lang="ts">
import { ChatDotRound, Pointer, View, MagicStick, Discount, More, EditPen } from '@element-plus/icons-vue'
import { postApi } from '@/api/post'
import { ElMessage } from 'element-plus'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { usePostComposerStore } from '@/store/postComposer'
import UserRoleBadge from '@/components/common/UserRoleBadge.vue'
import { stripMarkdown } from '@/utils/markdown'

const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
const props = defineProps<{
  post: any
}>()

const isAdmin = computed(() => {
  const roles = userStore.userInfo?.roles || []
  return roles.some((role: string) => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
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
const reportForm = ref({
  reason: '',
  details: ''
})

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
    composerStore.open({
      editId: props.post.id,
      title: props.post.title,
      content: props.post.content,
      sectionId: props.post.sectionId,
      tags: props.post.tags,
      coverImage: props.post.coverImage
    })
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

const goToPost = () => {
  router.push('/p/' + props.post.id)
}

const handleLike = async (e: Event) => {
  e.stopPropagation()
  try {
    await postApi.like(props.post.id)
    props.post.isLiked = !props.post.isLiked
    props.post.likeCount += props.post.isLiked ? 1 : -1
    ElMessage.success(props.post.isLiked ? '已点赞' : '已取消点赞')
  } catch (error) {
    ElMessage.error('操作失败')
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
</script>

<template>
  <div class="post-card" @click="goToPost">
    <div v-if="post.recommendReason" class="recommend-badge">
      <el-icon><MagicStick /></el-icon> 推荐内容
    </div>

    <div class="card-content">
      <!-- Author info and Section -->
      <div class="card-header-info">
        <div class="author-block">
          <div class="avatar-wrapper">
            <el-avatar :size="32" :src="post.authorAvatar" class="author-avatar">
              {{ post.authorName?.charAt(0) || 'U' }}
            </el-avatar>
          </div>
          <div class="author-details">
            <div style="display:flex; align-items:center;">
              <span class="author-name">{{ post.authorName }}</span>
              <UserRoleBadge :roles="post.authorRoles" />
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
                  <el-dropdown-item v-if="userStore.userId === post.userId || isAdmin" command="edit">
                    <el-icon><EditPen /></el-icon> 编辑此贴
                  </el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" command="pin">
                    {{ post.isPinned === 1 ? '取消置顶' : '置顶帖' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="report" :divided="isAdmin">
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
            <el-tag v-if="post.isPinned === 1" type="warning" size="small" effect="dark" class="hot-tag">
              置顶
            </el-tag>
            <el-tag v-if="post.trendLevel === 'hot'" type="danger" size="small" effect="dark" class="hot-tag">
              <el-icon><Discount /></el-icon> HOT
            </el-tag>
            {{ post.title }}
          </h2>
          <p class="post-excerpt">
            {{ postSummary }}
          </p>
        </div>

        <!-- Optional Thumbnail -->
        <div 
          v-if="post.coverImage && post.coverImage !== 'null' && post.coverImage !== 'undefined' && post.coverImage !== '' && !imageError"
          class="post-thumbnail"
        >
          <img 
            :src="post.coverImage" 
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
          <span class="interaction-btn" :class="{ 'is-active': post.isLiked }" @click.stop="handleLike">
            <el-icon><Pointer /></el-icon> {{ post.likeCount || 0 }}
          </span>
          <span class="interaction-btn">
            <el-icon><ChatDotRound /></el-icon> {{ post.commentCount || 0 }}
          </span>
          <span class="interaction-btn hidden-xs-only">
            <el-icon><View /></el-icon> {{ post.viewCount || 0 }}
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
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
  background-color: var(--cp-bg-card);
  position: relative;
  overflow: hidden;
}

.post-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-light);
  border-color: var(--cp-primary);
  background-color: var(--cp-hover);
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
}

.thumbnail-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.post-card:hover .thumbnail-image {
  transform: scale(1.05);
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
  transition: color 0.2s;
}

.interaction-btn:hover {
  color: var(--el-color-primary);
}

.interaction-btn.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

@media (max-width: 600px) {
  .post-thumbnail {
    display: none;
  }
}
</style>
