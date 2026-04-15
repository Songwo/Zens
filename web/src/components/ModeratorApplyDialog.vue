<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineRound, Check, Close, Clock, RefreshRight, Star, Trophy } from '@element-plus/icons-vue'
import { levelApi } from '@/api/level'
import { moderatorApi, type ModeratorApplicationItem } from '@/api/moderator'
import { sectionApi, type Section } from '@/api/section'
import { useUserStore } from '@/store/user'
import { ensureCurrentUserProfile, patchCurrentUserProfile } from '@/utils/sessionProfile'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const MIN_APPLY_LEVEL = 5

const userStore = useUserStore()
const loading = ref(false)
const levelLoading = ref(false)
const sections = ref<Section[]>([])
const myApplications = ref<ModeratorApplicationItem[]>([])
const activeTab = ref<'apply' | 'history'>('apply')
const currentLevel = ref(0)

const form = ref({
  sectionId: null as number | null,
  reason: ''
})

const isLoggedIn = computed(() => !!userStore.accessToken)
const currentRoles = computed(() => userStore.userInfo?.roles || [])
const isAdminUser = computed(() => {
  return currentRoles.value.some(role => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
})
const moderatedSectionIds = computed(() => {
  const rawIds = userStore.userInfo?.moderatedSectionIds || []
  return new Set(rawIds.map(id => Number(id)).filter(id => Number.isFinite(id) && id > 0))
})
const approvedSectionIds = computed(() => {
  const ids = new Set<number>(moderatedSectionIds.value)
  myApplications.value
    .filter(app => app.status === 1)
    .forEach(app => {
      const sectionId = Number(app.sectionId)
      if (Number.isFinite(sectionId) && sectionId > 0) {
        ids.add(sectionId)
      }
    })
  return ids
})
const canApply = computed(() => {
  return isLoggedIn.value && !isAdminUser.value && currentLevel.value >= MIN_APPLY_LEVEL
})
const pendingSectionIds = computed(() => {
  return new Set(
    myApplications.value
      .filter(app => app.status === 0)
      .map(app => Number(app.sectionId))
  )
})
const unavailableSectionIds = computed(() => {
  return new Set<number>([
    ...pendingSectionIds.value,
    ...approvedSectionIds.value
  ])
})
const availableSections = computed(() => {
  return sections.value.filter(section => !unavailableSectionIds.value.has(Number(section.id)))
})
const selectedSectionUnavailableText = computed(() => {
  if (!form.value.sectionId) return ''
  const sectionId = Number(form.value.sectionId)
  if (approvedSectionIds.value.has(sectionId)) {
    return '该板块已经通过审核并授予你版务权限，无需重复申请。'
  }
  if (pendingSectionIds.value.has(sectionId)) {
    return '该板块已有待审核申请，请等待管理员处理后再提交。'
  }
  return ''
})

const statusMap: Record<number, { text: string; type: string; icon: any }> = {
  0: { text: '审核中', type: 'warning', icon: Clock },
  1: { text: '已通过', type: 'success', icon: Check },
  2: { text: '已拒绝', type: 'danger', icon: Close },
}

const syncLevelToSessionProfile = (level: number) => {
  if (Number.isFinite(level) && level > 0) {
    patchCurrentUserProfile({ level } as any)
  }
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

const getSectionLabel = (section: Section) => {
  const sectionId = Number(section.id)
  if (approvedSectionIds.value.has(sectionId)) {
    return `${section.name}（已拥有该板块权限）`
  }
  if (pendingSectionIds.value.has(sectionId)) {
    return `${section.name}（已有待审申请）`
  }
  return section.name
}

const resetForm = () => {
  form.value = {
    sectionId: null,
    reason: ''
  }
}

const fetchData = async () => {
  loading.value = true
  levelLoading.value = isLoggedIn.value
  try {
    if (isLoggedIn.value) {
      await ensureCurrentUserProfile({ force: true }).catch(() => null)
    }

    const [sectionRes, myApplicationRes, levelRes] = await Promise.all([
      sectionApi.getActiveList(),
      isLoggedIn.value
        ? moderatorApi.getMyApplications()
        : Promise.resolve({ code: 2000, data: [] } as any),
      isLoggedIn.value
        ? levelApi.getInfo()
        : Promise.resolve({ code: 2000, data: { level: 0 } } as any)
    ])

    if (sectionRes.code === 2000) {
      sections.value = sectionRes.data || []
    }
    if (myApplicationRes.code === 2000) {
      myApplications.value = myApplicationRes.data || []
    }
    if (levelRes.code === 2000 && levelRes.data) {
      currentLevel.value = Number(levelRes.data.level || 0)
      syncLevelToSessionProfile(currentLevel.value)
    } else {
      currentLevel.value = Number(userStore.userInfo?.level || 0)
    }

    if (form.value.sectionId && unavailableSectionIds.value.has(Number(form.value.sectionId))) {
      form.value.sectionId = null
    }
  } catch (error: any) {
    if (error?.response) {
      return
    }
    ElMessage.error(error?.message || '版主申请信息加载失败')
  } finally {
    loading.value = false
    levelLoading.value = false
  }
}

const handleApply = async () => {
  if (isAdminUser.value) {
    ElMessage.info('管理员账号无需通过版主申请流程')
    return
  }
  if (!form.value.sectionId) {
    ElMessage.warning('请选择目标板块')
    return
  }
  const selectedSectionId = Number(form.value.sectionId)
  if (approvedSectionIds.value.has(selectedSectionId)) {
    ElMessage.warning('你已拥有该板块的版务权限，无需重复申请')
    return
  }
  if (pendingSectionIds.value.has(selectedSectionId)) {
    ElMessage.warning('该板块已有待审核申请，请勿重复提交')
    return
  }
  const reason = form.value.reason.trim()
  if (reason.length < 10) {
    ElMessage.warning('申请理由至少 10 个字符')
    return
  }

  loading.value = true
  try {
    const res = await moderatorApi.apply(Number(form.value.sectionId), reason)
    if (Number(res.code) === 2000) {
      ElMessage.success('申请已提交，请等待管理员审核')
      resetForm()
      activeTab.value = 'history'
      await fetchData()
      return
    }
    ElMessage.error(res.message || '申请失败')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      activeTab.value = 'apply'
      return
    }
    void fetchData()
  },
  { immediate: true }
)
</script>

<template>
  <el-dialog
    :model-value="props.visible"
    @update:model-value="emit('update:visible', $event)"
    title=""
    width="640px"
    :close-on-click-modal="false"
    class="mod-apply-dialog"
  >
    <div class="dialog-hero">
      <div class="hero-icon">
        <el-icon :size="36"><Trophy /></el-icon>
      </div>
      <h2>版主申请中心</h2>
      <p>实时校验当前等级，提交板块申请，由管理员审核，并同步站内通知与邮箱提醒。</p>
    </div>

    <div class="level-requirement" :class="{ met: canApply }">
      <div class="req-left">
        <el-icon><Star /></el-icon>
        <span>等级要求: <strong>Lv{{ MIN_APPLY_LEVEL }} 及以上</strong></span>
      </div>
      <div class="req-right">
        <span>当前等级:</span>
        <el-tag :type="canApply ? 'success' : 'info'" effect="dark" round>
          {{ levelLoading ? '加载中' : `Lv${currentLevel}` }}
        </el-tag>
        <el-button text type="primary" :icon="RefreshRight" @click="fetchData">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="mod-tabs">
      <el-tab-pane label="提交申请" name="apply">
        <div v-if="!isLoggedIn" class="empty-state">
          <el-icon :size="40"><Trophy /></el-icon>
          <p>请先登录后再申请版主</p>
        </div>

        <div v-else-if="isAdminUser" class="empty-state">
          <div class="status-icon success">M</div>
          <h3>管理员账号无需申请</h3>
          <p>管理员已具备站点管理权限，不走板块版主申请流程。</p>
        </div>

        <div v-else-if="!canApply" class="empty-state">
          <div class="status-icon">Lv</div>
          <h3>等级尚未达标</h3>
          <p>当你的账号等级达到 <strong>Lv{{ MIN_APPLY_LEVEL }}</strong> 后即可申请。</p>
          <p class="tip">继续发帖、评论和互动来提升等级。</p>
        </div>

        <div v-else-if="!loading && availableSections.length === 0" class="empty-state">
          <div class="status-icon success">OK</div>
          <h3>当前没有可重复申请的板块</h3>
          <p>你已覆盖所有板块，或这些板块都已有待审核申请。</p>
          <p class="tip">管理员审核完成后，板块权限会自动同步到当前账号。</p>
        </div>

        <el-form v-else label-position="top" class="apply-form" v-loading="loading">
          <el-form-item label="目标板块">
            <el-select
              v-model="form.sectionId"
              placeholder="请选择你想申请管理的板块"
              size="large"
              style="width: 100%"
            >
              <el-option
                v-for="sec in sections"
                :key="sec.id"
                :label="getSectionLabel(sec)"
                :value="Number(sec.id)"
                :disabled="unavailableSectionIds.has(Number(sec.id))"
              >
                <div class="section-option">
                  <span>{{ sec.name }}</span>
                  <span class="sec-desc">{{ sec.description || '暂无描述' }}</span>
                </div>
              </el-option>
            </el-select>
            <div v-if="selectedSectionUnavailableText" class="inline-tip">
              {{ selectedSectionUnavailableText }}
            </div>
          </el-form-item>

          <el-form-item label="申请理由">
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="5"
              placeholder="请具体说明你在该板块的活跃度、管理经验、能承担的职责以及申请动机（至少 10 字）"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <div class="privileges-info">
            <h4>通过后会发生什么</h4>
            <div class="priv-list">
              <div class="priv-item"><el-icon><ChatLineRound /></el-icon>管理员会在后台审核申请理由</div>
              <div class="priv-item"><el-icon><Check /></el-icon>通过后仅为该板块开通内容管理与举报处理权限</div>
              <div class="priv-item"><el-icon><Trophy /></el-icon>审核结果会同步到站内通知与邮箱提醒</div>
            </div>
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            :disabled="form.sectionId !== null && unavailableSectionIds.has(Number(form.sectionId))"
            @click="handleApply"
            round
          >
            提交申请
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane :label="`我的申请(${myApplications.length})`" name="history">
        <div v-if="myApplications.length === 0" class="empty-state compact">
          <p>暂无申请记录</p>
        </div>

        <div v-else class="application-list">
          <div
            v-for="app in myApplications"
            :key="app.id"
            class="app-card"
            :class="'status-' + app.status"
          >
            <div class="app-header">
              <div class="app-title-block">
                <span class="app-section">{{ app.sectionName || `板块#${app.sectionId}` }}</span>
                <span class="app-time">申请时间: {{ formatDateTime(app.createdAt) }}</span>
              </div>
              <el-tag :type="(statusMap[app.status]?.type as any) || 'info'" size="small" effect="dark" round>
                {{ statusMap[app.status]?.text || '未知状态' }}
              </el-tag>
            </div>

            <p class="app-reason">{{ app.reason }}</p>

            <div class="app-meta">
              <span v-if="app.reviewedAt">审核时间: {{ formatDateTime(app.reviewedAt) }}</span>
              <span v-if="app.reviewNote" class="review-note">审核备注: {{ app.reviewNote }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<style scoped>
.dialog-hero {
  text-align: center;
  margin-bottom: 24px;
}

.hero-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  box-shadow: 0 12px 24px var(--el-color-primary-light-7);
}

.dialog-hero h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 800;
}

.dialog-hero p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.level-requirement {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 24px;
}

.level-requirement.met {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.req-left,
.req-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.mod-tabs :deep(.el-tabs__item) {
  font-weight: 600;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
}

.empty-state.compact {
  padding: 28px 0;
}

.empty-state h3 {
  margin: 14px 0 10px;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.status-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.status-icon.success {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.tip {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.section-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 12px;
}

.sec-desc {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-color-warning);
}

.privileges-info {
  padding: 16px 20px;
  background: var(--el-fill-color-light);
  border-radius: 12px;
  margin-bottom: 24px;
}

.privileges-info h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.priv-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.priv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.priv-item .el-icon {
  color: var(--el-color-primary);
}

.submit-btn {
  width: 100%;
  font-size: 16px;
  padding: 14px;
}

.application-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-card {
  padding: 16px 20px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  transition: box-shadow 0.2s ease;
}

.app-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.app-card.status-1 {
  border-left: 3px solid var(--el-color-success);
}

.app-card.status-2 {
  border-left: 3px solid var(--el-color-danger);
}

.app-card.status-0 {
  border-left: 3px solid var(--el-color-warning);
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.app-title-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-section {
  font-weight: 700;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.app-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.app-reason {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.app-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.review-note {
  color: var(--el-color-primary);
}

@media (max-width: 768px) {
  .level-requirement {
    flex-direction: column;
    align-items: flex-start;
  }

  .req-right {
    flex-wrap: wrap;
  }

  .app-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
