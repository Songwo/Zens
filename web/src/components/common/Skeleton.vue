<script setup lang="ts">
interface Props {
  width?: string
  height?: string
  rounded?: boolean
  circle?: boolean
}

withDefaults(defineProps<Props>(), {
  width: '100%',
  height: '1rem',
  rounded: false,
  circle: false
})
</script>

<template>
  <div
    class="animate-pulse bg-slate-200"
    :class="{
      'rounded-lg': rounded,
      'rounded-full': circle
    }"
    :style="{ width, height }"
  />
</template>
