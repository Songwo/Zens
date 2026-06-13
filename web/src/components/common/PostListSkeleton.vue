<script setup lang="ts">
// Song：帖子列表骨架屏，统一各列表页的首屏加载占位（沿用 PostList.vue 的卡片样式）
withDefaults(defineProps<{ count?: number }>(), {
  count: 5,
})
</script>

<template>
  <div class="post-list-skeleton">
    <el-skeleton v-for="i in count" :key="i" animated class="skeleton-block">
      <template #template>
        <div class="skeleton-card">
          <div class="skeleton-head">
            <el-skeleton-item variant="circle" style="width: 32px; height: 32px" />
            <div class="skeleton-head-text">
              <el-skeleton-item variant="text" style="width: 100px" />
              <el-skeleton-item variant="text" style="width: 60px" />
            </div>
          </div>
          <el-skeleton-item variant="p" style="width: 80%" />
          <el-skeleton-item variant="p" style="width: 60%" />
          <div class="skeleton-foot">
            <el-skeleton-item variant="text" style="width: 40px" />
            <el-skeleton-item variant="text" style="width: 40px" />
          </div>
        </div>
      </template>
    </el-skeleton>
  </div>
</template>

<style scoped>
.post-list-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.skeleton-card {
  padding: 20px;
  border-radius: var(--el-border-radius-base);
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.skeleton-head {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.skeleton-head-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
}

.skeleton-foot {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
</style>
