<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { EditPen, Check, Close } from '@element-plus/icons-vue'
import type { CoverConfig } from '@/utils/coverConfig'

const props = withDefaults(defineProps<{
  imageUrl?: string
  config: CoverConfig
  editable?: boolean
  onSave?: (config: CoverConfig) => Promise<void>
}>(), {
  imageUrl: '',
  editable: false,
})

const emit = defineEmits<{ (e: 'notice', msg: string): void }>()

const SUNSET = 'linear-gradient(135deg, #fff2db 0%, #ffe3b2 48%, #ffd39a 100%)'

const editing = ref(false)
const saving = ref(false)
const draft = reactive<CoverConfig>({ fit: 'cover', x: 50, y: 50, height: 320 })

const active = computed<CoverConfig>(() => (editing.value ? draft : props.config))
const hasImage = computed(() => !!props.imageUrl)
const coverRef = ref<HTMLElement | null>(null)

const clampNum = (n: number, min: number, max: number) => Math.max(min, Math.min(max, n))

const photoStyle = computed(() => ({ backgroundImage: `url("${props.imageUrl}")` }))
const fillStyle = computed(() => ({
  backgroundImage: `url("${props.imageUrl}")`,
  backgroundPosition: `${active.value.x}% ${active.value.y}%`,
}))
const containerStyle = computed(() => ({
  height: `${active.value.height}px`,
  background: hasImage.value ? 'var(--el-fill-color-light)' : SUNSET,
}))

const enterEdit = () => {
  if (!hasImage.value) {
    emit('notice', '请先到「设置」上传资料卡背景图')
    return
  }
  draft.fit = props.config.fit
  draft.x = props.config.x
  draft.y = props.config.y
  draft.height = props.config.height
  editing.value = true
}
const cancelEdit = () => { editing.value = false }
const toggleFit = () => { draft.fit = draft.fit === 'cover' ? 'contain' : 'cover' }
const saveEdit = async () => {
  if (!props.onSave) { editing.value = false; return }
  saving.value = true
  try {
    await props.onSave({ ...draft })
    editing.value = false
  } catch {
    // 失败保留编辑态;错误提示由父层负责
  } finally {
    saving.value = false
  }
}

let posDrag: { px: number; py: number; x: number; y: number } | null = null
const onCoverPointerDown = (e: PointerEvent) => {
  if (!editing.value || draft.fit !== 'cover' || !hasImage.value) return
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  posDrag = { px: e.clientX, py: e.clientY, x: draft.x, y: draft.y }
}
const onCoverPointerMove = (e: PointerEvent) => {
  if (!posDrag) return
  const box = coverRef.value
  if (!box) return
  const dx = e.clientX - posDrag.px
  const dy = e.clientY - posDrag.py
  draft.x = clampNum(posDrag.x - (dx / (box.clientWidth || 1)) * 100, 0, 100)
  draft.y = clampNum(posDrag.y - (dy / (box.clientHeight || 1)) * 100, 0, 100)
}
const onCoverPointerUp = () => { posDrag = null }

let hDrag: { py: number; h: number } | null = null
const onHeightPointerDown = (e: PointerEvent) => {
  if (!editing.value) return
  e.stopPropagation()
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  hDrag = { py: e.clientY, h: draft.height }
}
const onHeightPointerMove = (e: PointerEvent) => {
  if (!hDrag) return
  draft.height = clampNum(hDrag.h + (e.clientY - hDrag.py), 120, 600)
}
const onHeightPointerUp = () => { hDrag = null }
</script>

<template>
  <div
    ref="coverRef"
    class="cover"
    :style="containerStyle"
    :class="{ 'is-editing': editing, 'is-grab': editing && active.fit === 'cover' && hasImage }"
    @pointerdown="onCoverPointerDown"
    @pointermove="onCoverPointerMove"
    @pointerup="onCoverPointerUp"
    @pointercancel="onCoverPointerUp"
  >
    <div v-if="hasImage && active.fit === 'cover'" class="cover-fill" :style="fillStyle"></div>
    <template v-else-if="hasImage">
      <div class="cover-blur" :style="photoStyle"></div>
      <div class="cover-photo" :style="photoStyle"></div>
    </template>

    <button v-if="editable && !editing" type="button" class="cover-edit-btn" @click.stop="enterEdit">
      <el-icon><EditPen /></el-icon><span>编辑封面</span>
    </button>

    <div v-if="editing" class="cover-toolbar" @pointerdown.stop>
      <el-button size="small" @click="toggleFit">{{ draft.fit === 'cover' ? '填满' : '完整' }}</el-button>
      <span class="cover-hint">{{ draft.fit === 'cover' ? '拖动图片调整位置' : '完整模式无需定位' }}</span>
      <el-button size="small" :icon="Close" @click="cancelEdit">取消</el-button>
      <el-button size="small" type="primary" :icon="Check" :loading="saving" @click="saveEdit">保存</el-button>
    </div>

    <div
      v-if="editing"
      class="cover-resize"
      @pointerdown="onHeightPointerDown"
      @pointermove="onHeightPointerMove"
      @pointerup="onHeightPointerUp"
      @pointercancel="onHeightPointerUp"
    ></div>
  </div>
</template>

<style scoped>
.cover { position: relative; border-radius: 10px 10px 0 0; overflow: hidden; }
.cover-fill { position: absolute; inset: 0; background-size: cover; background-repeat: no-repeat; }
.cover-blur, .cover-photo { position: absolute; inset: 0; background-position: center; background-repeat: no-repeat; }
.cover-blur { background-size: cover; filter: blur(22px) brightness(0.92); transform: scale(1.12); }
.cover-photo { background-size: contain; }
.cover.is-grab .cover-fill { cursor: grab; }
.cover.is-editing { outline: 2px dashed var(--el-color-primary); outline-offset: -2px; }
.cover-edit-btn {
  position: absolute; right: 12px; bottom: 12px; z-index: 2;
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border: none; border-radius: 999px; cursor: pointer;
  background: rgba(0, 0, 0, 0.45); color: #fff; font-size: 12px; font-weight: 600;
}
.cover-edit-btn:hover { background: rgba(0, 0, 0, 0.6); }
.cover-toolbar {
  position: absolute; left: 12px; right: 12px; bottom: 12px; z-index: 3;
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 10px; border-radius: 10px; background: rgba(0, 0, 0, 0.5);
}
.cover-hint { color: #fff; font-size: 12px; margin-right: auto; }
.cover-resize {
  position: absolute; left: 0; right: 0; bottom: 0; height: 12px; z-index: 3;
  cursor: ns-resize; background: linear-gradient(transparent, rgba(0, 0, 0, 0.25));
}
@media (max-width: 640px) {
  .cover { border-radius: 0; max-height: 260px; }
}
</style>
