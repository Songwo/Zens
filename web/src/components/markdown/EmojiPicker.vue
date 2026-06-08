<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  (e: 'select', emoji: string): void
}>()

const visible = ref(false)
const popoverRef = ref<HTMLDivElement | null>(null)
const triggerRef = ref<HTMLSpanElement | null>(null)

const groups: Array<{ title: string; emojis: string[] }> = [
  {
    title: '常用',
    emojis: ['😀','😁','😂','🤣','😊','😍','😎','🤔','😅','😭','😡','🥺','🥳','🤩','🤯','🫠','🙃','😴','😇','🤗'],
  },
  {
    title: '表达',
    emojis: ['👍','👎','👏','🙏','💪','🙌','🤝','👌','✌️','🤞','🤟','🤘','👋','✊','🫶','💯','🔥','✨','💫','⚡'],
  },
  {
    title: '心情',
    emojis: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','💔','❣️','💕','💞','💓','💗','💖','💝','💟','💋','🥹','🤤'],
  },
  {
    title: '物件',
    emojis: ['🎉','🎊','🎁','🎂','🎈','🏆','🥇','🥈','🥉','🎯','🎨','📚','💡','⭐','🌟','☀️','🌙','🍀','🌈','☕'],
  },
]

function togglePopover() {
  visible.value = !visible.value
}

function close() {
  visible.value = false
}

function pick(emoji: string) {
  emit('select', emoji)
  close()
}

function onDocClick(e: MouseEvent) {
  if (!visible.value) return
  const target = e.target as Node
  if (popoverRef.value?.contains(target)) return
  if (triggerRef.value?.contains(target)) return
  close()
}

import { onMounted, onBeforeUnmount } from 'vue'
onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))
</script>

<template>
  <div class="cp-emoji-picker">
    <span ref="triggerRef" class="cp-emoji-trigger" @click.stop="togglePopover">
      <slot name="trigger" />
    </span>
    <Transition name="cp-emoji-fade">
      <div v-show="visible" ref="popoverRef" class="cp-emoji-panel" @mousedown.stop>
        <div v-for="group in groups" :key="group.title" class="cp-emoji-group">
          <div class="cp-emoji-group-title">{{ group.title }}</div>
          <div class="cp-emoji-grid">
            <button
              v-for="emoji in group.emojis"
              :key="emoji"
              type="button"
              class="cp-emoji-cell"
              @click="pick(emoji)"
            >
              {{ emoji }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.cp-emoji-picker {
  position: relative;
  display: inline-block;
}

.cp-emoji-trigger {
  display: inline-flex;
}

.cp-emoji-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 9000;
  width: 320px;
  max-height: 320px;
  overflow-y: auto;
  padding: 10px;
  background-color: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.10);
}

html.dark .cp-emoji-panel {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.cp-emoji-group + .cp-emoji-group {
  margin-top: 10px;
}

.cp-emoji-group-title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.cp-emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 2px;
}

.cp-emoji-cell {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 18px;
  line-height: 1;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.12s, transform 0.12s;
}

.cp-emoji-cell:hover {
  background-color: var(--el-fill-color);
  transform: scale(1.18);
}

.cp-emoji-fade-enter-active,
.cp-emoji-fade-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}
.cp-emoji-fade-enter-from,
.cp-emoji-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
