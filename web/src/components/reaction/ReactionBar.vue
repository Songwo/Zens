<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reactionApi, type ReactionResp, type ReactionTarget, type ReactionType } from '@/api/reaction'
import { useUserStore } from '@/store/user'

const props = withDefaults(defineProps<{
  targetType: ReactionTarget
  targetId: string
  initial?: ReactionResp | null
  /** 自行拉取数据；列表场景传 false，由父组件批量注入 initial */
  selfFetch?: boolean
}>(), {
  initial: null,
  selfFetch: true,
})

const userStore = useUserStore()

const REACTIONS: { type: ReactionType; emoji: string; label: string }[] = [
  { type: 'love', emoji: '❤️', label: '喜欢' },
  { type: 'haha', emoji: '😆', label: '哈哈' },
  { type: 'wow', emoji: '😮', label: '哇' },
  { type: 'celebrate', emoji: '🎉', label: '庆祝' },
]

const counts = ref<Record<string, number>>({})
const mine = ref<ReactionType | null>(null)
const pickerVisible = ref(false)
const busy = ref(false)

const chips = computed(() =>
  REACTIONS
    .filter(r => (counts.value[r.type] || 0) > 0)
    .map(r => ({ ...r, count: counts.value[r.type] || 0, active: mine.value === r.type }))
)

const applyResp = (resp?: ReactionResp | null) => {
  counts.value = resp?.counts ? { ...resp.counts } : {}
  mine.value = (resp?.mine as ReactionType) || null
}

const fetchOne = async () => {
  try {
    applyResp((await reactionApi.get(props.targetType, props.targetId)).data)
  } catch { /* 静默 */ }
}

const handleReact = async (type: ReactionType) => {
  pickerVisible.value = false
  if (!userStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  if (busy.value) return
  busy.value = true
  try {
    applyResp((await reactionApi.react(props.targetType, props.targetId, type)).data)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  if (props.initial) applyResp(props.initial)
  else if (props.selfFetch) fetchOne()
})
watch(() => props.initial, v => { if (v) applyResp(v) })
</script>

<template>
  <div class="reaction-bar">
    <button
      v-for="chip in chips"
      :key="chip.type"
      type="button"
      class="reaction-chip"
      :class="{ active: chip.active }"
      :disabled="busy"
      @click.stop="handleReact(chip.type)"
    >
      <span class="reaction-emoji">{{ chip.emoji }}</span>
      <span class="reaction-count">{{ chip.count }}</span>
    </button>

    <el-popover v-model:visible="pickerVisible" trigger="click" :width="200" placement="top">
      <template #reference>
        <button type="button" class="reaction-add" :class="{ active: !!mine }" title="添加表情" @click.stop>
          <span class="reaction-add-icon">☺</span>
          <span class="reaction-add-plus">＋</span>
        </button>
      </template>
      <div class="reaction-picker">
        <button
          v-for="r in REACTIONS"
          :key="r.type"
          type="button"
          class="picker-item"
          :class="{ active: mine === r.type }"
          :title="r.label"
          @click.stop="handleReact(r.type)"
        >
          {{ r.emoji }}
        </button>
      </div>
    </el-popover>
  </div>
</template>

<style scoped>
.reaction-bar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.reaction-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 9px;
  border-radius: 999px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}

.reaction-chip:hover {
  background: var(--el-fill-color);
}

.reaction-chip.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.reaction-emoji {
  font-size: 14px;
  line-height: 1;
}

.reaction-count {
  font-weight: 600;
}

.reaction-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 1px;
  height: 26px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px dashed var(--el-border-color);
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, color 0.2s;
}

.reaction-add:hover,
.reaction-add.active {
  border-style: solid;
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.reaction-add-icon {
  font-size: 14px;
  line-height: 1;
}

.reaction-add-plus {
  font-size: 11px;
  line-height: 1;
}

.reaction-picker {
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 4px;
}

.picker-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}

.picker-item:hover {
  background: var(--el-fill-color-light);
  transform: scale(1.15);
}

.picker-item.active {
  background: var(--el-color-primary-light-9);
}
</style>
