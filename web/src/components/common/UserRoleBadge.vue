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
  return props.roles.some(role =>
    role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN'
  )
})

const isModerator = computed(() => {
  if (!props.roles || props.roles.length === 0) return false
  return props.roles.includes('ROLE_MODERATOR')
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
      <span class="badge-icon">🛡</span>
      <span class="badge-text">管理</span>
    </div>
  </el-tooltip>
  <el-tooltip
    v-else-if="isModerator"
    content="版主"
    placement="top"
    effect="dark"
  >
    <div class="user-role-badge moderator-badge">
      <span class="badge-icon">⭐</span>
      <span class="badge-text">版主</span>
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
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  text-shadow: 0 1px 1px rgba(0,0,0,0.2);
}

.moderator-badge {
  background: linear-gradient(135deg, var(--el-color-success) 0%, #52c41a 100%);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  text-shadow: 0 1px 1px rgba(0,0,0,0.2);
}

.badge-icon {
  font-size: 12px;
  line-height: 1;
}

/* Song：说明 */
:deep(html.dark) .admin-badge {
  background: linear-gradient(135deg, #b77904 0%, #8a5700 100%);
  border-color: rgba(255, 255, 255, 0.1);
  color: #fcebbb;
}

:deep(html.dark) .moderator-badge {
  background: linear-gradient(135deg, #3a9e1a 0%, #2d7a14 100%);
  border-color: rgba(255, 255, 255, 0.1);
  color: #d4f5c4;
}
</style>
