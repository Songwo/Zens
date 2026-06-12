<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, PieChart, Clock, Lock } from '@element-plus/icons-vue'
import { pollApi, type Poll } from '@/api/poll'
import { useUserStore } from '@/store/user'
import { timeAgo } from '@/utils/timeAgo'

const props = defineProps<{
  poll: Poll
  /** 帖子标题，投票无 title 时兜底展示 */
  postTitle?: string
}>()

const emit = defineEmits<{
  (e: 'update', poll: Poll): void
}>()

const userStore = useUserStore()

// 本地可变副本：投票/关闭后用后端返回的最新数据覆盖
const local = ref<Poll>(props.poll)
watch(() => props.poll, (p) => { local.value = p })

const submitting = ref(false)
const closing = ref(false)

// 单选用单值，多选用集合
const selectedSingle = ref<number | null>(null)
const selectedMulti = ref<number[]>([])

const isMulti = computed(() => local.value.multiChoice === 1)

const displayTitle = computed(() =>
  local.value.title?.trim() || props.postTitle?.trim() || '投票'
)

// 是否展示结果（已投 / 已截止 / 创建者）——后端已算好
const showResult = computed(() => local.value.showResult)

// 当前用户能否投票
const canVote = computed(() => local.value.canVote)

const isAuthor = computed(() =>
  !!userStore.userId && userStore.userId === local.value.createdBy
)

// 作者/版主/管理员可关闭（关闭按钮仅在未截止时显示；后端最终校验权限）
const canClose = computed(() => {
  if (local.value.closed) return false
  if (isAuthor.value) return true
  const roles = userStore.userInfo?.roles || []
  const rolesStr = Array.isArray(roles) ? roles.join(',') : String(roles)
  return rolesStr.includes('ADMIN') || rolesStr.includes('MODERATOR') || rolesStr.includes('版主')
})

const deadlineText = computed(() => {
  if (!local.value.deadline) return ''
  const dl = new Date(local.value.deadline)
  if (local.value.closed) return '投票已截止'
  return `截止于 ${dl.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}`
})

const maxChoicesHint = computed(() => {
  if (!isMulti.value) return '单选'
  const m = local.value.maxChoices
  return m && m > 0 ? `多选 · 最多选 ${m} 项` : '多选 · 不限项数'
})

const toggleMulti = (optionId: number) => {
  const idx = selectedMulti.value.indexOf(optionId)
  if (idx >= 0) {
    selectedMulti.value.splice(idx, 1)
    return
  }
  const max = local.value.maxChoices
  if (max && max > 0 && selectedMulti.value.length >= max) {
    ElMessage.warning(`最多只能选择 ${max} 项`)
    return
  }
  selectedMulti.value.push(optionId)
}

const hasSelection = computed(() =>
  isMulti.value ? selectedMulti.value.length > 0 : selectedSingle.value != null
)

const submitVote = async () => {
  if (!userStore.userId) {
    ElMessage.warning('请先登录后再投票')
    return
  }
  const optionIds = isMulti.value
    ? [...selectedMulti.value]
    : (selectedSingle.value != null ? [selectedSingle.value] : [])
  if (optionIds.length === 0) {
    ElMessage.warning('请至少选择一个选项')
    return
  }
  submitting.value = true
  try {
    const res = await pollApi.vote(local.value.id, optionIds)
    if (res.data) {
      local.value = res.data
      emit('update', res.data)
      ElMessage.success('投票成功')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '投票失败，请重试')
  } finally {
    submitting.value = false
  }
}

const handleClose = async () => {
  closing.value = true
  try {
    const res = await pollApi.close(local.value.id)
    if (res.data) {
      local.value = res.data
      emit('update', res.data)
      ElMessage.success('投票已关闭')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '关闭投票失败')
  } finally {
    closing.value = false
  }
}
</script>

<template>
  <div class="poll-card">
    <div class="poll-head">
      <span class="poll-icon"><el-icon><PieChart /></el-icon></span>
      <span class="poll-title">{{ displayTitle }}</span>
      <span class="poll-mode">{{ maxChoicesHint }}</span>
    </div>

    <!-- 投票态：可选 + 提交 -->
    <div v-if="!showResult" class="poll-options votable">
      <template v-if="isMulti">
        <label
          v-for="opt in local.options"
          :key="opt.id"
          class="poll-option-row selectable"
          :class="{ checked: selectedMulti.includes(opt.id) }"
        >
          <el-checkbox
            :model-value="selectedMulti.includes(opt.id)"
            @change="toggleMulti(opt.id)"
          />
          <span class="option-text">{{ opt.optionText }}</span>
        </label>
      </template>
      <template v-else>
        <el-radio-group v-model="selectedSingle" class="single-group">
          <label
            v-for="opt in local.options"
            :key="opt.id"
            class="poll-option-row selectable"
            :class="{ checked: selectedSingle === opt.id }"
          >
            <el-radio :value="opt.id">{{ opt.optionText }}</el-radio>
          </label>
        </el-radio-group>
      </template>

      <div class="poll-actions">
        <el-button
          type="primary"
          round
          :icon="Check"
          :loading="submitting"
          :disabled="!hasSelection"
          @click="submitVote"
        >
          投票
        </el-button>
        <el-button
          v-if="!canVote && !userStore.userId"
          text
          @click="$router.push('/auth?type=login')"
        >
          登录后投票
        </el-button>
      </div>
    </div>

    <!-- 结果态：横条 + 百分比 + 票数 -->
    <div v-else class="poll-options result">
      <div
        v-for="opt in local.options"
        :key="opt.id"
        class="poll-result-row"
        :class="{ mine: opt.votedByMe }"
      >
        <div class="result-bar" :style="{ width: opt.percent + '%' }"></div>
        <div class="result-content">
          <span class="option-text">
            <el-icon v-if="opt.votedByMe" class="mine-check"><Check /></el-icon>
            {{ opt.optionText }}
          </span>
          <span class="result-num">{{ opt.percent }}% · {{ opt.voteCount }} 票</span>
        </div>
      </div>
    </div>

    <!-- 页脚：人数 / 截止 / 关闭操作 -->
    <div class="poll-foot">
      <span class="foot-item">{{ local.voterCount }} 人参与</span>
      <span v-if="deadlineText" class="foot-dot">·</span>
      <span v-if="deadlineText" class="foot-item">
        <el-icon><Clock /></el-icon> {{ deadlineText }}
      </span>
      <span v-if="local.closed" class="foot-dot">·</span>
      <span v-if="local.closed && !deadlineText" class="foot-item closed-label">
        <el-icon><Lock /></el-icon> 已关闭
      </span>
      <el-button
        v-if="canClose"
        link
        type="info"
        size="small"
        class="close-poll-btn"
        :loading="closing"
        @click="handleClose"
      >
        关闭投票
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.poll-card {
  margin-top: 18px;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  background: var(--el-bg-color-overlay);
}

.poll-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.poll-icon {
  display: inline-flex;
  color: var(--el-color-primary);
  font-size: 18px;
}

.poll-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.poll-mode {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  padding: 2px 8px;
  border-radius: 999px;
}

/* ── 投票态 ── */
.poll-options.votable {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.single-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.poll-option-row.selectable {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  margin: 0;
}

.poll-option-row.selectable:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.poll-option-row.selectable.checked {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.poll-option-row .option-text {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

/* el-radio 占满整行 */
.poll-option-row.selectable :deep(.el-radio) {
  width: 100%;
  height: auto;
  margin-right: 0;
}
.poll-option-row.selectable :deep(.el-radio__label) {
  font-size: 14px;
}

.poll-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
}

/* ── 结果态 ── */
.poll-options.result {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.poll-result-row {
  position: relative;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-fill-color-lighter);
}

.poll-result-row.mine {
  border-color: var(--el-color-primary);
}

.result-bar {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: var(--el-color-primary-light-8);
  transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.poll-result-row.mine .result-bar {
  background: var(--el-color-primary-light-7);
}

.result-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
}

.result-content .option-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.mine-check {
  color: var(--el-color-primary);
  font-size: 14px;
}

.result-num {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

/* ── 页脚 ── */
.poll-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.foot-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.foot-dot {
  opacity: 0.5;
}

.closed-label {
  color: var(--el-text-color-placeholder);
}

.close-poll-btn {
  margin-left: auto;
}
</style>
