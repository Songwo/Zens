<script setup lang="ts">
import Navbar from '@/components/Navbar.vue'
import LeftSidebar from '@/components/LeftSidebar.vue'
import RightSidebar from '@/components/RightSidebar.vue'

interface Props {
  showLeftSidebar?: boolean
  showRightSidebar?: boolean
  wide?: boolean
  isFluid?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showLeftSidebar: true,
  showRightSidebar: true,
  wide: false,
  isFluid: false
})
</script>

<template>
  <div class="flex flex-col min-h-screen bg-slate-50">
    <!-- Navbar (Fixed) -->
    <Navbar />

    <!-- Centered Content Wrapper -->
    <div class="w-full max-w-[1440px] mx-auto flex justify-center flex-1">
      <!-- Left Sidebar (Sticky) -->
      <LeftSidebar v-if="props.showLeftSidebar" class="hidden xl:block sticky top-16" />

      <!-- Main Content (Scrollable) -->
      <main 
        :class="[
          'flex-1 min-w-0 border-x border-slate-200/60 bg-white min-h-[calc(100vh-64px)] overflow-x-hidden shadow-sm transition-all duration-300',
          props.isFluid ? 'max-w-none' : (props.wide ? 'max-w-[1200px]' : 'max-w-[720px]')
        ]"
      >
        <slot />
      </main>

      <!-- Right Sidebar (Sticky) -->
      <RightSidebar v-if="props.showRightSidebar" class="hidden lg:block sticky top-16" />
    </div>
  </div>
</template>

<style scoped>
/* Scrollbar styling for sidebars */
aside::-webkit-scrollbar {
  width: 4px;
}
aside::-webkit-scrollbar-track {
  background: transparent;
}
aside::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 10px;
}
aside:hover::-webkit-scrollbar-thumb {
  background: #cbd5e1;
}
</style>
