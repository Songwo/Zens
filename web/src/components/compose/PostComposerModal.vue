<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed, nextTick, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { postApi } from '@/api/post'
import { publicDataApi } from '@/api/publicData'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pulseNotification } from '@/utils/pulseNotification'
import {
  Close,
  VideoPlay,
  Promotion,
  MagicStick,
  FullScreen,
  Crop
} from '@element-plus/icons-vue'
import ImageUploader from '@/components/ImageUploader.vue'
import MarkdownTutorial from './MarkdownTutorial.vue'
import PollComposerPanel from '@/components/poll/PollComposerPanel.vue'
import type { PollCreateRequest } from '@/api/poll'
import { uploadApi } from '@/api/upload'
import {
  UPLOAD_VIDEO_MAX_SIZE_BYTES,
  UPLOAD_VIDEO_MAX_SIZE_MB
} from '@/constants/upload'
import { usePostComposerStore } from '@/store/postComposer'
import { usePostDraft } from '@/composables/usePostDraft'
import { canPostLinks, containsExternalLink } from '@/utils/trustLevel'
import { trustLevelApi } from '@/api/trustLevel'

const router = useRouter()
const userStore = useUserStore()
const composerStore = usePostComposerStore()
const draft = usePostDraft()

// Song：说明
const isMaximized = ref(false)
const tutorialRef = ref<any>(null)
const titleInputRef = ref<any>(null)
const tagInput = ref('')
const categories = ref<any[]>([])
const loading = ref(false)
const extracting = ref(false)
const isEditing = ref(false)
const editId = ref('')
const editStatus = ref(1)
const editAuditStatus = ref('')
// 投票草稿：null=不附带投票。仅新发帖支持（v1），编辑态隐藏面板。不进本地草稿持久化。
const pollPanelRef = ref<InstanceType<typeof PollComposerPanel> | null>(null)
const MarkdownEditor = defineAsyncComponent(() => import('@/components/markdown/MarkdownEditor.vue'))
const editorRef = ref<{ insertAtCursor?: (text: string, options?: { ensureBlankLineBefore?: boolean; ensureBlankLineAfter?: boolean }) => void } | null>(null)
const contentVideoInputRef = ref<HTMLInputElement | null>(null)
const contentVideoUploading = ref(false)
const composerTrustLevel = ref<number | null>(null)
const tl0RestrictionTitle = '新用户功能受限'
const tl0RestrictionDescription = '你当前的信任等级为 TL0（新人），暂不能在帖文中发布外链。进入 3 个帖子并阅读 5 分钟后自动晋升为 TL1 即可解锁。'

const titleCharCount = computed(() => draft.form.title.trim().length)
const contentCharCount = computed(() => draft.form.content.trim().length)
const isDraftLikeEditing = computed(() => {
  return isEditing.value && (editStatus.value === 0 || editAuditStatus.value === 'REJECTED' || editAuditStatus.value === 'DRAFT')
})
const showTl0Hint = computed(() => composerTrustLevel.value === 0)
const isLotteryPost = computed({
  get: () => draft.form.postType === 'LOTTERY',
  set: (value: boolean) => {
    draft.form.postType = value ? 'LOTTERY' : 'NORMAL'
    if (value) {
      draft.form.isAnonymous = 0
      draft.form.commentOncePerUser = true
    } else {
      draft.form.commentDeadline = ''
      draft.form.commentOncePerUser = true
    }
  }
})

const categoriesLoading = ref(false)

const normalizeTrustLevel = (value: unknown): number | null => {
  const level = Number(value)
  if (!Number.isFinite(level)) return null
  return Math.max(0, Math.min(4, Math.trunc(level)))
}

const readCachedTrustLevel = () => normalizeTrustLevel(userStore.userInfo?.trustLevel)

const syncTrustLevelToUserStore = (trustLevel: number) => {
  if (!userStore.userInfo) return
  userStore.setUserInfo({
    ...userStore.userInfo,
    trustLevel,
  })
}

const resolveCurrentTrustLevel = async (options: { force?: boolean } = {}) => {
  const cachedTrustLevel = readCachedTrustLevel()

  if (!options.force && cachedTrustLevel !== null && canPostLinks(cachedTrustLevel)) {
    composerTrustLevel.value = cachedTrustLevel
    return cachedTrustLevel
  }

  try {
    const res = await trustLevelApi.info()
    const freshTrustLevel = normalizeTrustLevel(res.data?.trustLevel)
    if (res.code === 2000 && freshTrustLevel !== null) {
      composerTrustLevel.value = freshTrustLevel
      syncTrustLevelToUserStore(freshTrustLevel)
      return freshTrustLevel
    }
  } catch (error) {
    console.warn('刷新信任等级失败，使用本地缓存继续校验', error)
  }

  const fallbackTrustLevel = cachedTrustLevel ?? 0
  composerTrustLevel.value = fallbackTrustLevel
  return fallbackTrustLevel
}

const refreshComposerTrustLevel = () => {
  const cachedTrustLevel = readCachedTrustLevel()
  composerTrustLevel.value = cachedTrustLevel !== null && canPostLinks(cachedTrustLevel)
    ? cachedTrustLevel
    : null
  void resolveCurrentTrustLevel({ force: true })
}

const showTl0RestrictionMessage = () => {
  ElMessage({
    type: 'warning',
    message: tl0RestrictionDescription,
    duration: 6500,
    showClose: true,
  })
}

// Song：说明
const fetchCategories = async (force = false) => {
  if (categoriesLoading.value) return
  // 已有数据且非强制刷新则跳过
  if (!force && categories.value.length > 0) return
  categoriesLoading.value = true
  try {
    // force 时清除缓存，确保拿到最新数据
    if (force) {
      const { clearRequestCache } = await import('@/utils/requestCache')
      clearRequestCache('public:sections:active')
    }
    const res = await publicDataApi.getActiveSectionsCached()
    const list = res?.data || res
    if (Array.isArray(list) && list.length > 0) {
      categories.value = list
    } else if (res?.code === 2000 || res?.code === 200) {
      const data = res.data
      if (Array.isArray(data) && data.length > 0) {
        categories.value = data
      } else if (!force) {
        categoriesLoading.value = false
        return fetchCategories(true)
      }
    }
    // 加载完板块后，若尚未选择板块则自动选第一个
    if (categories.value.length > 0 && !draft.form.sectionId) {
      draft.form.sectionId = categories.value[0].id
    }
  } catch (error) {
    console.warn('获取板块列表失败，将重试', error)
    if (!force) {
      setTimeout(() => fetchCategories(true), 1500)
    }
  } finally {
    categoriesLoading.value = false
  }
}

// Song：说明
const addTag = () => {
  const tag = tagInput.value.trim()
  if (!tag) return
  if (draft.form.tags.includes(tag)) {
    ElMessage.warning('标签已存在')
    return
  }
  if (draft.form.tags.length >= 5) {
    ElMessage.warning('最多添加5个标签')
    return
  }
  draft.form.tags.push(tag)
  tagInput.value = ''
}

const removeTag = (tag: string) => {
  draft.form.tags = draft.form.tags.filter(t => t !== tag)
}

// Song：说明
const handleAIAnalysis = async () => {
  if (!draft.form.content || extracting.value) return
  
  extracting.value = true
  try {
    const res = await postApi.extractTags({
      title: draft.form.title,
      content: draft.form.content
    })
    if (res.data && res.data.tags && res.data.tags.length > 0) {
      if (Array.isArray(res.data.tags)) {
        draft.form.tags = res.data.tags
      } else if (typeof res.data.tags === 'string') {
        draft.form.tags = (res.data.tags as string).split(',').filter(Boolean)
      }
      ElMessage.success('AI 分析完成')
    } else {
      ElMessage.warning('未能提取出有效标签')
    }
  } catch (error) {
    // Song：说明
  } finally {
    extracting.value = false
  }
}

// Song：说明
const publish = async () => {
  const title = draft.form.title.trim()
  const content = draft.form.content.trim()

  if (!title) {
    ElMessage.warning('请输入标题')
    return
  }

  if (title.length <= 3) {
    ElMessage.warning('标题需超过3个字符')
    return
  }

  if (!content) {
    ElMessage.warning('请输入内容')
    return
  }

  if (content.length <= 30) {
    ElMessage.warning('正文需超过30个字符')
    return
  }

  if (!draft.form.sectionId) {
    ElMessage.warning('请选择板块')
    return
  }

  // Song：TL0 新用户硬拦截 —— 发布前强制刷新信任等级，避免登录态缓存缺失时把 TL4 误判成 TL0。
  if (containsExternalLink(content)) {
    const trustLevel = await resolveCurrentTrustLevel({ force: true })
    if (!canPostLinks(trustLevel)) {
      showTl0RestrictionMessage()
      return
    }
  }

  // Song：新发帖时校验投票面板（编辑态不支持附加投票，见 spec）
  let pollPayload: PollCreateRequest | null = null
  if (!isEditing.value && pollPanelRef.value) {
    const built = pollPanelRef.value.buildPayload()
    if (!built.ok) {
      ElMessage.warning(built.message)
      return
    }
    pollPayload = built.value
  }

  loading.value = true
  try {
    if (isEditing.value) {
      const wasDraftLikeEditing = isDraftLikeEditing.value
      await postApi.update({
        postId: editId.value,
        title,
        content,
        sectionId: draft.form.sectionId,
        tags: draft.form.tags.join(','),
        coverImage: draft.form.coverImage || undefined,
        postType: draft.form.postType,
        commentDeadline: draft.form.postType === 'LOTTERY' && draft.form.commentDeadline ? draft.form.commentDeadline : undefined,
        commentOncePerUser: draft.form.postType === 'LOTTERY' ? draft.form.commentOncePerUser : false,
        status: 1,
        publish: true
      })
      editStatus.value = 1
      editAuditStatus.value = 'PENDING'
      pulseNotification.success(wasDraftLikeEditing ? '已重新提交至人工审核，请静候佳音' : '话题内容更新成功，已完美同步', '内容更新完毕')
    } else {
      await postApi.create({
        title,
        content,
        sectionId: draft.form.sectionId,
        tags: draft.form.tags.join(','),
        coverImage: draft.form.coverImage || undefined,
        postType: draft.form.postType,
        commentDeadline: draft.form.postType === 'LOTTERY' && draft.form.commentDeadline ? draft.form.commentDeadline : undefined,
        commentOncePerUser: draft.form.postType === 'LOTTERY' ? draft.form.commentOncePerUser : false,
        status: 1,
        poll: pollPayload || undefined
      })
      pulseNotification.post(`「${title}」已成功发布到社区内容流，等待更多成员参与讨论。`, '发布话题成功')
    }

    draft.clearDraft() // Song：重要: 成功后清空草稿
    pollPanelRef.value?.reset() // 重置投票面板，避免下次发帖残留
    handleClose(true) // Song：无需二次确认直接关闭
    
    // Song：说明
    if (router.currentRoute.value.path === '/') {
      window.location.reload()
    } else {
      router.push('/')
    }
  } catch (error: any) {
    if (error?.response) {
      return
    }
    const message = typeof error?.message === 'string' ? error.message.trim() : ''
    if (message && message !== 'Network Error') {
      ElMessage.error(message)
      return
    }
    ElMessage.error(isEditing.value ? '更新失败' : '发布失败')
  } finally {
    loading.value = false
  }
}

const saveDraftToServer = async (silent = false) => {
  const hasMeaningfulContent = Boolean(
    draft.form.title.trim() ||
    draft.form.content.trim() ||
    draft.form.sectionId ||
    draft.form.tags.length ||
    draft.form.coverImage
  )
  if (!hasMeaningfulContent) {
    if (!silent) {
      ElMessage.warning('请先填写一些内容再保存草稿')
    }
    return false
  }

  if (!draft.form.title.trim()) {
    if (!silent) {
      ElMessage.warning('保存到服务器前请先填写标题')
    }
    return false
  }

  if (!draft.form.sectionId) {
    if (!silent) {
      ElMessage.warning('保存到服务器前请先选择板块')
    }
    return false
  }

  loading.value = true
  try {
    const res = await postApi.saveDraft({
      postId: isEditing.value ? editId.value : undefined,
      title: draft.form.title.trim() || undefined,
      content: draft.form.content.trim() || undefined,
      sectionId: draft.form.sectionId || undefined,
      tags: draft.form.tags.join(',') || undefined,
      coverImage: draft.form.coverImage || undefined,
      isAnonymous: draft.form.isAnonymous,
      postType: draft.form.postType,
      commentDeadline: draft.form.postType === 'LOTTERY' && draft.form.commentDeadline ? draft.form.commentDeadline : undefined,
      commentOncePerUser: draft.form.postType === 'LOTTERY' ? draft.form.commentOncePerUser : false,
    })

    if (res.data?.id) {
      isEditing.value = true
      editId.value = res.data.id
      editStatus.value = res.data.status ?? 0
      editAuditStatus.value = res.data.auditStatus || 'DRAFT'
    }
    draft.saveDraft(true)
    if (!silent) {
      ElMessage.success(editAuditStatus.value === 'REJECTED' ? '修改内容已暂存，稍后可继续编辑并重新发布' : '草稿已保存到服务器')
    }
    return true
  } catch (error: any) {
    if (!silent) {
      ElMessage.error(error?.message || '草稿保存失败')
    }
    return false
  } finally {
    loading.value = false
  }
}

const persistDraftForUser = async (options?: { silent?: boolean; preferServer?: boolean }) => {
  const silent = options?.silent ?? false
  const preferServer = options?.preferServer ?? true
  const canPersistToServer = !isEditing.value || isDraftLikeEditing.value

  if (preferServer && canPersistToServer) {
    const serverSaved = await saveDraftToServer(silent)
    if (serverSaved) {
      return 'server'
    }
  }

  const localSaved = draft.saveDraft(true)
  if (localSaved) {
    if (!silent) {
      ElMessage.info('草稿已暂存到本地，补全标题和板块后可同步到“我的草稿”')
    }
    return 'local'
  }

  return 'none'
}

const toggleMaximize = () => {
  isMaximized.value = !isMaximized.value
}

const handleUploadContentVideo = () => {
  if (contentVideoUploading.value) return
  contentVideoInputRef.value?.click()
}

const handleContentVideoChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!file.type.startsWith('video/')) {
    ElMessage.warning('请选择视频文件')
    return
  }

  if (file.size > UPLOAD_VIDEO_MAX_SIZE_BYTES) {
    ElMessage.warning(`视频不能超过 ${UPLOAD_VIDEO_MAX_SIZE_MB}MB`)
    return
  }

  contentVideoUploading.value = true
  try {
    const videoUrl = await uploadApi.uploadVideo(file, 'post')
    if (!videoUrl) {
      throw new Error('视频上传失败')
    }
    editorRef.value?.insertAtCursor?.(
      `<video controls preload="metadata" src="${videoUrl}"></video>`,
      { ensureBlankLineBefore: true, ensureBlankLineAfter: true }
    )
    ElMessage.success('视频已插入正文')
  } catch (error: any) {
    if (error?.response) return
    ElMessage.error(error?.message || '视频上传失败')
  } finally {
    contentVideoUploading.value = false
  }
}

const handleClose = (force = false) => {
  if (isEditing.value) {
    if (!force && draft.isDirty.value && draft.hasContent.value) {
      if (isDraftLikeEditing.value) {
        ElMessageBox.confirm('修改尚未保存，是否先保存草稿后关闭？保存成功后会显示在“我的草稿”中。', '保存草稿', {
          confirmButtonText: '保存并关闭',
          cancelButtonText: '继续编辑',
          type: 'warning'
        }).then(async () => {
          await persistDraftForUser()
          draft.resetForm()
          composerStore.close()
        }).catch(() => {})
      } else {
        ElMessageBox.confirm('编辑内容尚未保存，确定放弃修改吗？', '确认离开', {
          confirmButtonText: '确定离开',
          cancelButtonText: '继续编辑',
          type: 'warning'
        }).then(() => {
          draft.resetForm()
          composerStore.close()
        }).catch(() => {})
      }
    } else {
      draft.resetForm()
      composerStore.close()
    }
  } else {
    // Song：说明
    if (!force && draft.isDirty.value && draft.hasContent.value) {
      ElMessageBox.confirm('内容尚未保存，是否保存草稿后关闭？已填写标题和板块时会同步到“我的草稿”，否则仅暂存在本地。', '保存草稿', {
        confirmButtonText: '保存并关闭',
        cancelButtonText: '直接舍弃',
        distinguishCancelAndClose: true,
        type: 'warning'
      }).then(async () => {
        await persistDraftForUser()
        draft.resetForm()
        composerStore.close()
      }).catch((action) => {
        if (action === 'cancel') {
          draft.resetForm()
          composerStore.close()
        }
      })
    } else {
      draft.resetForm() // Song：内容为空时重置表单
      composerStore.close()
    }
  }
}

// Song：说明
watch(() => composerStore.isOpen, async (newVal) => {
  if (newVal) {
    // Song：说明
    if (!userStore.accessToken) {
      ElMessage.error('请先登录再发起话题')
      composerStore.close()
      router.push('/auth/login')
      return
    }
    
    fetchCategories()
    refreshComposerTrustLevel()

    // Song：说明
    if (composerStore.context.editId) {
        isEditing.value = true
        editId.value = composerStore.context.editId
        editStatus.value = composerStore.context.status || 1
        editAuditStatus.value = composerStore.context.auditStatus || ''
        draft.form.title = composerStore.context.title
        draft.form.content = composerStore.context.content
        draft.form.sectionId = composerStore.context.sectionId
        draft.form.tags = composerStore.context.tags ? composerStore.context.tags.split(',') : []
        draft.form.coverImage = composerStore.context.coverImage
        draft.form.postType = composerStore.context.postType === 'LOTTERY' ? 'LOTTERY' : 'NORMAL'
        draft.form.commentDeadline = composerStore.context.commentDeadline || ''
        draft.form.commentOncePerUser = composerStore.context.commentOncePerUser !== false
        if (draft.form.postType === 'LOTTERY') {
          draft.form.isAnonymous = 0
        }
        draft.syncSnapshot()

        composerStore.context.editId = '' // Song：消费一次性上下文
    } else {
        isEditing.value = false
        editId.value = ''
        editStatus.value = 1
        editAuditStatus.value = ''

        // Song：说明
        if (composerStore.context.title) {
            draft.form.title = composerStore.context.title
            composerStore.context.title = '' // Song：消费一次性上下文
        }
        if (composerStore.context.sectionId) {
            draft.form.sectionId = composerStore.context.sectionId
            composerStore.context.sectionId = 0 // 消费一次性上下文
            // 如果已加载板块且未选择，自动选第一个
            if (categories.value.length > 0 && !draft.form.sectionId) {
              draft.form.sectionId = categories.value[0].id
            }
        }

        // Song：说明
        if (!draft.hasContent.value) {
            draft.loadDraft()
        }
    }

    await nextTick()
    if (titleInputRef.value) {
      titleInputRef.value.focus()
    }
  }
})

// Song：说明
const handleKeydown = (e: KeyboardEvent) => {
  if (!composerStore.isOpen) return

  // Song：说明
  if (e.key === 'Escape') {
    e.preventDefault()
    handleClose()
  }

  // Song：说明
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    publish()
  }

  // Song：说明
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    if (isEditing.value && !isDraftLikeEditing.value) {
      ElMessage.info('当前为已发布内容，请点击“更新话题”提交修改')
      return
    }
    void persistDraftForUser()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  // 提前预加载板块列表，避免打开时显示空/0
  fetchCategories()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="composer-fade">
      <div v-if="composerStore.isOpen" class="composer-overlay" @mousedown.self="handleClose(false)">
        <div class="composer-scroll-shell" :class="{ 'is-maximized': isMaximized }">
          <div
            class="composer-modal"
            :class="{'is-maximized': isMaximized}"
            role="dialog"
            aria-modal="true"
            :aria-label="isEditing ? '编辑话题' : '创建话题'"
          >
          <!-- HEADER -->
          <div class="composer-header">
            <div class="header-left">
              <span class="header-title">{{ isEditing ? '编辑话题' : '创建话题' }}</span>
            </div>
            <div class="header-right">
              <el-button link class="header-icon-btn" @click="toggleMaximize" :title="isMaximized ? '还原' : '最大化'">
                <el-icon><Crop v-if="isMaximized"/><FullScreen v-else/></el-icon>
              </el-button>
              <el-button link class="header-icon-btn close-btn" @click="handleClose(false)" title="关闭 (Esc)">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>

          <!-- BODY -->
          <div class="composer-body">
            <!-- Song：TL0 新用户提示横幅（未达 TL1 时显示，提示外链/附件受限） -->
            <el-alert
              v-if="showTl0Hint"
              class="tl0-hint"
              type="warning"
              :closable="false"
              show-icon
              :title="tl0RestrictionTitle"
              :description="tl0RestrictionDescription"
            />
            <el-form :model="draft.form" label-position="top" class="compose-form">
              <!-- Title Input -->
              <el-form-item>
                <el-input
                  ref="titleInputRef"
                  v-model="draft.form.title"
                  placeholder="输入话题标题..."
                  class="title-input"
                  maxlength="100"
                  show-word-limit
                />
              </el-form-item>

              <div class="meta-row">
                <!-- Category -->
                <el-form-item label="发布板块" class="meta-item">
                  <el-select
                    v-model="draft.form.sectionId"
                    placeholder="选择发布的板块"
                    class="full-width"
                    :loading="categoriesLoading"
                    loading-text="加载板块中..."
                    @visible-change="(v: boolean) => v && categories.length === 0 && fetchCategories(true)"
                  >
                    <el-option
                      v-for="cat in categories"
                      :key="cat.id"
                      :label="cat.name"
                      :value="cat.id"
                    />
                  </el-select>
                </el-form-item>

                <!-- Tags -->
                <el-form-item label="话题标签 (最多5个)" class="meta-item">
                  <div class="tag-input-wrapper">
                    <el-input
                      v-model="tagInput"
                      placeholder="输入标签按回车"
                      @keyup.enter="addTag"
                    >
                      <template #append>
                        <el-button @click="addTag">添加</el-button>
                      </template>
                    </el-input>
                    <div class="tags-list" v-if="draft.form.tags.length > 0">
                      <el-tag
                        v-for="tag in draft.form.tags"
                        :key="tag"
                        closable
                        size="small"
                        class="tag-pill"
                        @close="removeTag(tag)"
                      >
                        {{ tag }}
                      </el-tag>
                    </div>
                  </div>
                </el-form-item>
              </div>

              <!-- Cover Image -->
              <el-form-item label="封面图片 (可选)">
                <ImageUploader v-model="draft.form.coverImage" />
                <div class="cover-hint">封面仅为缩略展示，不会压缩或影响原图</div>
              </el-form-item>

              <!-- 附加投票（仅新发帖支持，编辑态不显示） -->
              <el-form-item v-if="!isEditing" label="投票 (可选)">
                <PollComposerPanel ref="pollPanelRef" />
              </el-form-item>

              <el-form-item label="抽奖贴设置">
                <div class="lottery-settings">
                  <div class="lottery-toggle-row">
                    <div class="lottery-copy">
                      <span class="lottery-title">作为抽奖参与帖</span>
                      <span class="lottery-desc">开启后评论会作为抽奖站参与名单来源，并强制实名登录参与</span>
                    </div>
                    <el-switch v-model="isLotteryPost" />
                  </div>

                  <div v-if="isLotteryPost" class="lottery-options">
                    <el-date-picker
                      v-model="draft.form.commentDeadline"
                      type="datetime"
                      value-format="YYYY-MM-DDTHH:mm:ss"
                      format="YYYY-MM-DD HH:mm"
                      placeholder="选择评论截止时间（可选）"
                      class="lottery-deadline"
                    />
                    <el-checkbox v-model="draft.form.commentOncePerUser">每个账号只能评论参与一次</el-checkbox>
                  </div>
                </div>
              </el-form-item>

              <!-- Content Editor -->
              <el-form-item class="editor-form-item">
                <template #label>
                  <div class="editor-label">
                    <span>正文内容 (Markdown · Shiki 多彩高亮)</span>
                    <div class="editor-actions">
                      <input
                        ref="contentVideoInputRef"
                        class="content-video-input"
                        type="file"
                        accept="video/mp4,video/webm,video/ogg,video/quicktime"
                        @change="handleContentVideoChange"
                      />
                      <el-button
                        link
                        type="primary"
                        :icon="VideoPlay"
                        :loading="contentVideoUploading"
                        @click="handleUploadContentVideo"
                      >
                        上传视频
                      </el-button>
                      <el-button
                        v-if="draft.form.content.length > 10"
                        link
                        type="primary"
                        :icon="MagicStick"
                        :loading="extracting"
                        @click="handleAIAnalysis"
                      >
                        AI 提取标签
                      </el-button>
                      <el-button link type="success" @click="tutorialRef?.open()">
                        📝 Markdown 教程
                      </el-button>
                    </div>
                  </div>
                </template>

                <div class="post-md-editor-wrap">
                  <MarkdownEditor
                    ref="editorRef"
                    v-model="draft.form.content"
                    mode="full"
                    module="post"
                    placeholder="使用 Markdown 编写内容…  Ctrl/Cmd+B 加粗、Ctrl+K 链接、Ctrl+Shift+K 代码块，粘贴图片自动上传"
                    height="100%"
                  />
                </div>
              </el-form-item>
            </el-form>
          </div>

          <!-- FOOTER -->
            <div class="composer-footer">
              <div class="footer-left">
                <el-checkbox
                  v-model="draft.form.isAnonymous"
                  :true-label="1"
                  :false-label="0"
                  :disabled="isLotteryPost"
                >
                  匿名发布
                </el-checkbox>
                <span v-if="isLotteryPost" class="footer-hint">抽奖贴需要实名账号参与</span>
              </div>
              <div class="footer-right">
                <span class="content-metrics">
                  标题 {{ titleCharCount }}/4+
                  · 正文 {{ contentCharCount }}/31+
                </span>
                <span class="draft-status" v-if="draft.isDirty.value && draft.hasContent.value">有未保存更改</span>
                <span class="draft-status saved" v-else-if="draft.hasContent.value && (!isEditing || isDraftLikeEditing)">草稿已保存</span>
                
                <el-button v-if="!isEditing || isDraftLikeEditing" link :loading="loading" @click="() => saveDraftToServer()">保存草稿</el-button>
                <el-button :icon="Promotion" type="primary" :loading="loading" @click="publish">{{ isEditing ? '更新话题' : '创建话题' }}</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
    <MarkdownTutorial ref="tutorialRef" />
  </Teleport>
</template>

<style scoped>
/* Song：说明 */
.composer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(2px);
  z-index: 1500;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

html.dark .composer-overlay {
  background-color: rgba(0, 0, 0, 0.6);
}

.composer-scroll-shell {
  width: min(100%, 1220px);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  overflow-x: hidden;
  border-radius: var(--el-border-radius-base);
}

.composer-scroll-shell::-webkit-scrollbar {
  width: 8px;
}

.composer-scroll-shell::-webkit-scrollbar-track {
  background: transparent;
}

.composer-scroll-shell::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 8px;
}

.composer-scroll-shell::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

.composer-modal {
  background-color: var(--cp-bg-surface);
  border-radius: var(--el-border-radius-base);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  width: 100%;
  max-width: 1200px;
  min-height: min(92vh, 900px);
  height: auto;
  display: flex;
  flex-direction: column;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  border: 1px solid var(--cp-border);
  margin: 0 auto;
}

html.dark .composer-modal {
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  border-color: var(--el-border-color-dark);
}

.composer-modal.is-maximized {
  max-width: 100vw;
  max-height: 100vh;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  border-radius: 0;
  border: none;
}

.composer-scroll-shell.is-maximized {
  width: 100vw;
  max-height: 100vh;
  border-radius: 0;
}

/* Song：说明 */
.composer-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--cp-border);
  background-color: var(--cp-bg-card);
  border-radius: var(--el-border-radius-base) var(--el-border-radius-base) 0 0;
}

.composer-modal.is-maximized .composer-header {
  border-radius: 0;
}

html.dark .composer-header {
  border-bottom-color: var(--el-border-color-dark);
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cp-text);
}

.header-right {
  display: flex;
  gap: 8px;
}

.header-icon-btn {
  font-size: 16px;
  color: var(--cp-text-muted);
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 6px;
}

.header-icon-btn:hover {
  background-color: var(--cp-hover);
  color: var(--cp-text);
}

.close-btn:hover {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

html.dark .close-btn:hover {
  background-color: rgba(245, 108, 108, 0.2);
}

/* Song：说明 */
.tl0-hint {
  margin-bottom: 12px;
  border-radius: 8px;
}

.composer-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.compose-form {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.title-input :deep(.el-input__inner) {
  font-size: 20px;
  font-weight: 700;
  height: 44px;
  padding: 0;
  border-bottom: 2px solid var(--cp-border);
  border-radius: 0;
  color: var(--cp-text);
  background: transparent;
}
html.dark .title-input :deep(.el-input__inner) {
  border-bottom-color: var(--el-border-color-dark);
}

.title-input :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent;
}

.meta-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 16px;
}

.full-width {
  width: 100%;
}

.tag-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.editor-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.editor-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.content-video-input {
  display: none;
}

.cover-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}

.lottery-settings {
  width: 100%;
  border: 1px solid var(--cp-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--el-color-primary-light-9) 36%, var(--cp-bg-card));
  padding: 14px 16px;
}

.lottery-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.lottery-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.lottery-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--cp-text);
}

.lottery-desc,
.footer-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.lottery-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--cp-border);
}

.lottery-deadline {
  width: min(100%, 280px);
}

.editor-form-item {
  margin-bottom: 0;
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
}
.editor-form-item :deep(.el-form-item__content) {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.post-md-editor-wrap {
  flex: 1;
  min-height: 540px;
  display: flex;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
.post-md-editor-wrap > * {
  flex: 1;
  min-height: 0;
  min-width: 0;
}

/* Song：说明 */
.composer-footer {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-top: 1px solid var(--cp-border);
  background-color: var(--cp-bg-card);
  border-radius: 0 0 var(--el-border-radius-base) var(--el-border-radius-base);
}

.composer-modal.is-maximized .composer-footer {
  border-radius: 0;
}

html.dark .composer-footer {
  border-top-color: var(--el-border-color-dark);
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.draft-status {
  font-size: 12px;
  color: var(--el-color-warning);
}
.draft-status.saved {
  color: var(--el-color-success);
}

.content-metrics {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* Song：说明 */
.composer-fade-enter-active,
.composer-fade-leave-active {
  transition: opacity 0.25s ease;
}

.composer-fade-enter-from,
.composer-fade-leave-to {
  opacity: 0;
}

.composer-fade-enter-active .composer-modal {
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.composer-fade-enter-from .composer-modal {
  transform: translateY(20px) scale(0.98);
}

.composer-fade-leave-active .composer-modal {
  transition: transform 0.25s ease;
}

.composer-fade-leave-to .composer-modal {
  transform: translateY(10px) scale(0.98);
}

/* Song：说明 */
@media (max-width: 1024px) {
  .composer-overlay {
    padding: 0;
    align-items: flex-end; /* Song：移动端抽屉样式 */
  }

  .composer-scroll-shell {
    width: 100%;
    max-height: 90vh;
    border-radius: 12px 12px 0 0;
  }

  .composer-modal {
    min-height: 90vh;
    border-radius: 12px 12px 0 0;
    border-bottom: none;
    border-left: none;
    border-right: none;
    max-width: none;
  }

  .composer-header {
    border-radius: 12px 12px 0 0;
  }

  .meta-row {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* Song：项目特有覆盖（视频 / GitHub 链接卡片） */
:deep(.markdown-body video) {
  width: 100%;
  max-width: 100%;
  border-radius: 10px;
  background: #000;
  margin: 12px 0;
}

:deep(.markdown-body .github-link-card) {
  display: block;
  margin: 12px 0;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  background: linear-gradient(180deg, var(--el-fill-color-light), var(--el-bg-color));
}

:deep(.markdown-body .github-link-badge) {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  color: var(--el-color-primary-dark-2);
  background: var(--el-color-primary-light-8);
}

:deep(.markdown-body .github-link-title) {
  display: block;
  margin-top: 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--cp-text);
  text-decoration: none;
}

:deep(.markdown-body .github-link-subtitle) {
  margin: 6px 0 4px;
  font-size: 13px;
  color: var(--cp-text-muted);
}

:deep(.markdown-body .github-link-url) {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-decoration: none;
  word-break: break-all;
}
</style>
