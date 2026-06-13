<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Histogram } from '@element-plus/icons-vue'
import type { PollCreateRequest } from '@/api/poll'

/**
 * 发帖时的投票创建面板。
 * 通过 v-model 把投票草稿回传给 composer：
 *   - null  = 不附带投票
 *   - 对象  = 附带投票（已做基本规整，发帖前父组件再调 buildPayload 校验）
 * 投票数据不进草稿持久化（v1 仅新发帖支持，见 spec）。
 */
const model = defineModel<PollCreateRequest | null>({ default: null })

const MIN_OPTIONS = 2
const MAX_OPTIONS = 10

const enabled = ref(false)
const title = ref('')
const options = ref<string[]>(['', ''])
const multiChoice = ref(false)
const maxChoices = ref<number>(0) // 0 = 不限
const deadline = ref<string | null>(null)

const optionCount = computed(() => options.value.filter(o => o.trim()).length)

const addOption = () => {
  if (options.value.length >= MAX_OPTIONS) {
    ElMessage.warning(`最多 ${MAX_OPTIONS} 个选项`)
    return
  }
  options.value.push('')
}

const removeOption = (index: number) => {
  if (options.value.length <= MIN_OPTIONS) {
    ElMessage.warning(`至少保留 ${MIN_OPTIONS} 个选项`)
    return
  }
  options.value.splice(index, 1)
}

// 多选上限不超过选项数
const maxChoicesCeil = computed(() => Math.max(MIN_OPTIONS, options.value.length))

// 把当前面板状态规整为请求对象（或 null）。父组件 publish 前调用做最终校验。
const buildPayload = (): { ok: true; value: PollCreateRequest | null } | { ok: false; message: string } => {
  if (!enabled.value) {
    return { ok: true, value: null }
  }
  const cleaned: string[] = []
  const seen = new Set<string>()
  for (const raw of options.value) {
    const t = raw.trim()
    if (!t) continue
    if (seen.has(t)) continue
    seen.add(t)
    cleaned.push(t.slice(0, 200))
  }
  if (cleaned.length < MIN_OPTIONS) {
    return { ok: false, message: `投票去重后至少需要 ${MIN_OPTIONS} 个有效选项` }
  }
  if (cleaned.length > MAX_OPTIONS) {
    return { ok: false, message: `投票选项最多 ${MAX_OPTIONS} 个` }
  }
  if (deadline.value) {
    const dl = new Date(deadline.value).getTime()
    if (!Number.isFinite(dl) || dl <= Date.now()) {
      return { ok: false, message: '投票截止时间必须晚于当前时间' }
    }
  }
  const multi = multiChoice.value ? 1 : 0
  const max = multi === 1 ? Math.min(maxChoices.value || 0, cleaned.length) : 1
  return {
    ok: true,
    value: {
      title: title.value.trim() || undefined,
      multiChoice: multi,
      maxChoices: multi === 1 ? max : undefined,
      deadline: deadline.value || undefined,
      options: cleaned,
    },
  }
}

// 暴露给父组件：发帖前校验 + 取值；重置
defineExpose({
  buildPayload,
  reset() {
    enabled.value = false
    title.value = ''
    options.value = ['', '']
    multiChoice.value = false
    maxChoices.value = 0
    deadline.value = null
  },
})

// 实时把"轻量"状态同步进 v-model，便于父组件感知是否启用（最终值以 buildPayload 为准）
watch([enabled, title, options, multiChoice, maxChoices, deadline], () => {
  if (!enabled.value) {
    model.value = null
    return
  }
  const built = buildPayload()
  model.value = built.ok ? built.value : null
}, { deep: true })

// 禁用多选时把上限重置为不限
watch(multiChoice, (v) => {
  if (!v) maxChoices.value = 0
})

// 限制截止时间不能选过去
const disabledDate = (date: Date) => date.getTime() < Date.now() - 24 * 3600 * 1000
</script>

<template>
  <div class="poll-composer">
    <div class="poll-composer-head">
      <div class="head-label">
        <el-icon class="head-icon"><Histogram /></el-icon>
        <span>附加投票</span>
        <span class="head-tip">（可选 · 发布后不可修改）</span>
      </div>
      <el-switch v-model="enabled" />
    </div>

    <div v-if="enabled" class="poll-composer-body">
      <!-- 投票问题 -->
      <el-input
        v-model="title"
        placeholder="投票问题（可空，默认用帖子标题）"
        maxlength="200"
        show-word-limit
        class="poll-q-input"
      />

      <!-- 选项 -->
      <div class="poll-opt-list">
        <div
          v-for="(_, index) in options"
          :key="index"
          class="poll-opt-row"
        >
          <span class="opt-index">{{ index + 1 }}</span>
          <el-input
            v-model="options[index]"
            :placeholder="`选项 ${index + 1}`"
            maxlength="200"
          />
          <el-button
            link
            type="danger"
            :icon="Delete"
            :disabled="options.length <= MIN_OPTIONS"
            @click="removeOption(index)"
          />
        </div>
        <el-button
          link
          type="primary"
          :icon="Plus"
          :disabled="options.length >= MAX_OPTIONS"
          class="add-opt-btn"
          @click="addOption"
        >
          添加选项（{{ optionCount }}/{{ MAX_OPTIONS }}）
        </el-button>
      </div>

      <!-- 单/多选 + 截止 -->
      <div class="poll-settings">
        <div class="setting-item">
          <span class="setting-label">允许多选</span>
          <el-switch v-model="multiChoice" />
        </div>
        <div v-if="multiChoice" class="setting-item">
          <span class="setting-label">最多可选</span>
          <el-select v-model="maxChoices" size="small" class="max-select">
            <el-option :value="0" label="不限" />
            <el-option
              v-for="n in maxChoicesCeil"
              :key="n"
              :value="n"
              :label="`${n} 项`"
            />
          </el-select>
        </div>
        <div class="setting-item deadline-item">
          <span class="setting-label">截止时间</span>
          <el-date-picker
            v-model="deadline"
            type="datetime"
            placeholder="不限期"
            size="small"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled-date="disabledDate"
            class="deadline-picker"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.poll-composer {
  border: 1px solid var(--cp-border, var(--el-border-color));
  border-radius: 10px;
  padding: 14px 16px;
  background: var(--el-fill-color-lighter);
}

.poll-composer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.head-icon {
  color: var(--el-color-primary);
}

.head-tip {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.poll-composer-body {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.poll-opt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.poll-opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.opt-index {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.add-opt-btn {
  align-self: flex-start;
}

.poll-settings {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  padding-top: 4px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.max-select {
  width: 90px;
}

.deadline-item {
  flex: 1;
  min-width: 200px;
}
</style>
