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
  <div class="flex flex-col min-h-screen bg-[#F8FAFC]">
    <!-- Navbar (Fixed) -->
    <Navbar />

    <!-- Full Width Content Wrapper -->
    <div class="flex flex-1 pt-4 px-4 lg:px-6 xl:px-8 gap-6 justify-center">
      
      <!-- Left Sidebar (Sticky) -->
      <LeftSidebar v-if="props.showLeftSidebar" class="hidden xl:block w-64 shrink-0 sticky top-20 h-[calc(100vh-6rem)] overflow-y-auto custom-scrollbar pb-10" />

      <!-- Main Content (Flexible) -->
      <main 
        :class="[
          'flex-1 min-w-0 transition-all duration-300',
          props.isFluid ? 'w-full' : 'max-w-3xl'
        ]"
      >
        <slot />
      </main>

      <!-- Right Sidebar (Sticky) -->
      <RightSidebar v-if="props.showRightSidebar" class="hidden lg:block w-80 shrink-0 sticky top-20 h-[calc(100vh-6rem)] overflow-y-auto custom-scrollbar pb-10" />
      
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #E2E8F0;
  border-radius: 4px;
}
.custom-scrollbar:hover::-webkit-scrollbar-thumb {
  background: #CBD5E1;
}
</style>
