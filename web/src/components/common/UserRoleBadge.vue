<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  roles: {
    type: Array as () => string[],
    default: () => []
  }
})

const isAdmin = computed(() => {
  if (!props.roles || props.roles.length === 0) return false
  return props.roles.some(role => role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN')
})
</script>

<template>
  <el-tooltip
    v-if="isAdmin"
    content="官方认证"
    placement="top"
    effect="dark"
  >
    <div class="user-role-badge admin-badge">
      <span class="badge-text">管理</span>
    </div>
  </el-tooltip>
</template>

<style scoped>
.user-role-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.5px;
  cursor: default;
  margin-left: 6px;
  user-select: none;
  vertical-align: middle;
}

.admin-badge {
  background: linear-gradient(135deg, var(--el-color-warning) 0%, #d48806 100%);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.2);
}

:deep(html.dark) .admin-badge {
  background: linear-gradient(135deg, #b77904 0%, #8a5700 100%);
  border-color: rgba(255, 255, 255, 0.1);
  color: #fcebbb;
}
</style>
