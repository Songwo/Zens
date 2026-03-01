<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Trophy, Star, ChatLineRound, Clock, Check, Close } from '@element-plus/icons-vue'
import { moderatorApi, type ModeratorApplicationItem } from '@/api/moderator'
import { sectionApi, type Section } from '@/api/section'
import { useUserStore } from '@/store/user'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const userStore = useUserStore()
const loading = ref(false)
const sections = ref<Section[]>([])
const myApplications = ref<ModeratorApplicationItem[]>([])
const activeTab = ref('apply')

const form = ref({
  sectionId: null as number | null,
  reason: ''
})

const isLoggedIn = computed(() => !!userStore.accessToken)
const userLevel = computed(() => userStore.userInfo?.level ?? 0)
const canApply = computed(() => userLevel.value >= 5)

const statusMap: Record<number, { text: string; type: string; icon: any }> = {
  0: { text: '审核中', type: 'warning', icon: Clock },
  1: { text: '已通过', type: 'success', icon: Check },
  2: { text: '已拒绝', type: 'danger', icon: Close },
}

const fetchData = async () => {
  loading.value = true
  try {
    const [secRes, appRes] = await Promise.all([
      sectionApi.getList(),
      isLoggedIn.value ? moderatorApi.getMyApplications() : Promise.resolve({ code: 2000, data: [] } as any)
    ])
    if (secRes.code === 2000) sections.value = secRes.data || []
    if (appRes.code === 2000) myApplications.value = appRes.data || []
  } catch {
    // Song：说明
  } finally {
    loading.value = false
  }
}

const handleApply = async () => {
  if (!form.value.sectionId) {
    ElMessage.warning('请选择目标板块')
    return
  }
  if (!form.value.reason || form.value.reason.trim().length < 10) {
    ElMessage.warning('申请理由至少10个字符')
    return
  }
  loading.value = true
  try {
    const res = await moderatorApi.apply(form.value.sectionId, form.value.reason)
    if (Number(res.code) === 2000) {
      ElMessage.success('申请已提交，请耐心等待管理员审核 🎉')
      form.value = { sectionId: null, reason: '' }
      activeTab.value = 'history'
      await fetchData()
    } else {
      ElMessage.error(res.message || '申请失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    loading.value = false
  }
}

const getSectionName = (id: number) => {
  return sections.value.find(s => Number(s.id) === Number(id))?.name || `板块#${id}`
}

onMounted(fetchData)
</script>

<template>
  <el-dialog
    :model-value="props.visible"
    @update:model-value="emit('update:visible', $event)"
    title=""
    width="600px"
    :close-on-click-modal="false"
    class="mod-apply-dialog"
  >
    <div class="dialog-hero">
      <div class="hero-icon">
        <el-icon :size="36"><Trophy /></el-icon>
      </div>
      <h2>版主申请中心</h2>
      <p>成为板块版主，守护社区秩序，享有管理权限与专属标识</p>
    </div>

    <div class="level-requirement" :class="{ met: canApply }">
      <div class="req-left">
        <el-icon><Star /></el-icon>
        <span>等级要求: <strong>Lv5 及以上</strong></span>
      </div>
      <div class="req-right">
        <span>您的等级: </span>
        <el-tag :type="canApply ? 'success' : 'info'" effect="dark" round>
          Lv{{ userLevel }}
        </el-tag>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="mod-tabs">
      <el-tab-pane label="提交申请" name="apply">
        <div v-if="!isLoggedIn" class="not-logged-in">
          <el-icon :size="40"><Trophy /></el-icon>
          <p>请先登录后再申请版主</p>
        </div>
        <div v-else-if="!canApply" class="level-low">
          <div class="low-icon">🔒</div>
          <h3>等级尚未达标</h3>
          <p>当您的账号等级达到 <strong>Lv5</strong> 后即可申请。</p>
          <p class="tip">继续发帖、评论和互动来提升等级吧！</p>
        </div>

        <el-form v-else label-position="top" class="apply-form" v-loading="loading">
          <el-form-item label="目标板块">
            <el-select
              v-model="form.sectionId"
              placeholder="请选择您想管理的板块"
              size="large"
              style="width: 100%"
            >
              <el-option
                v-for="sec in sections"
                :key="sec.id"
                :label="sec.name"
                :value="Number(sec.id)"
              >
                <div class="section-option">
                  <span>{{ sec.name }}</span>
                  <span class="sec-desc">{{ sec.description }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="申请理由">
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="4"
              placeholder="请阐述您为什么适合担任该板块的版主（至少10字）..."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <div class="privileges-info">
            <h4>版主权限包含:</h4>
            <div class="priv-list">
              <div class="priv-item"><el-icon><ChatLineRound /></el-icon>删除违规帖子</div>
              <div class="priv-item"><el-icon><Star /></el-icon>置顶优质内容</div>
              <div class="priv-item"><el-icon><Trophy /></el-icon>专属版主标识</div>
            </div>
          </div>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleApply"
            round
          >
            🚀 提交申请
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane :label="`我的申请(${myApplications.length})`" name="history">
        <div v-if="myApplications.length === 0" class="empty-history">
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
              <span class="app-section">{{ getSectionName(app.sectionId) }}</span>
              <el-tag :type="(statusMap[app.status]?.type as any) || 'info'" size="small" effect="dark" round>
                {{ statusMap[app.status]?.text || '未知' }}
              </el-tag>
            </div>
            <p class="app-reason">{{ app.reason }}</p>
            <div class="app-meta">
              <span>申请时间: {{ app.createdAt?.replace('T', ' ').slice(0, 16) }}</span>
              <span v-if="app.reviewNote" class="review-note">审批备注: {{ app.reviewNote }}</span>
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
  margin-bottom: 28px;
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
}

.level-requirement {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-radius: 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 24px;
  transition: all 0.3s;
}

.level-requirement.met {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-5);
}

.req-left, .req-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.mod-tabs :deep(.el-tabs__item) {
  font-weight: 600;
}

.not-logged-in,
.level-low {
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
}

.level-low h3 {
  margin: 16px 0 12px;
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.low-icon {
  font-size: 48px;
}

.level-low .tip {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.section-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.sec-desc {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.priv-list {
  display: flex;
  gap: 24px;
}

.priv-item {
  display: flex;
  align-items: center;
  gap: 6px;
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

/* Song：说明 */
.empty-history {
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-placeholder);
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
  transition: all 0.2s;
}

.app-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.04);
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
  align-items: center;
  margin-bottom: 8px;
}

.app-section {
  font-weight: 700;
  font-size: 15px;
}

.app-reason {
  margin: 0 0 8px 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.app-meta {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.review-note {
  color: var(--el-color-primary);
}
</style>
