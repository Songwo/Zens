<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cacheAdminApi, type CacheOverview } from '@/api/cacheAdmin'
import { ResultCode } from '@/types'

const loading = ref(false)
const patternLoading = ref(false)
const overview = ref<CacheOverview | null>(null)

const pattern = ref('media:service:*')
const patternCount = ref<number | null>(null)

const statCards = computed(() => {
  const data = overview.value
  if (!data) return []
  return [
    { key: 'tagHot', label: '标签缓存', value: data.tagHot },
    { key: 'postFeed', label: '帖子流缓存', value: data.postFeed },
    { key: 'postFeedVersion', label: '帖子流版本键', value: data.postFeedVersion },
    { key: 'postDetail', label: '帖子详情缓存', value: data.postDetail },
    { key: 'userRecommend', label: '推荐缓存', value: data.userRecommend },
    { key: 'tokenTotal', label: '令牌相关缓存', value: data.tokenTotal },
    { key: 'mediaToken', label: '旧媒体 Token 痕迹', value: data.mediaToken },
    { key: 'mediaUpload', label: '旧媒体上传痕迹', value: data.mediaUpload },
    { key: 'mediaChunk', label: '旧媒体分片痕迹', value: data.mediaChunk },
    { key: 'mediaAdmin', label: '旧媒体代理痕迹', value: data.mediaAdmin },
    { key: 'mediaHealth', label: '旧媒体健康检查痕迹', value: data.mediaHealth },
    { key: 'mediaError', label: '旧媒体错误痕迹', value: data.mediaError },
    { key: 'mediaMetric', label: '旧媒体指标键', value: data.mediaMetric },
    { key: 'mediaTotal', label: '旧媒体链路相关总量', value: data.mediaTotal },
    { key: 'captcha', label: '验证码缓存', value: data.captcha },
    { key: 'lock', label: '登录锁定缓存', value: data.lock },
    { key: 'total', label: '统计总量', value: data.total },
  ]
})

const patternPresets = [
  { label: '旧媒体链路全部', value: 'media:service:*' },
  { label: '旧媒体上传', value: 'media:service:upload:*' },
  { label: '旧媒体分片', value: 'media:service:chunk:*' },
  { label: '旧媒体管理代理', value: 'media:service:admin:*' },
  { label: '帖子流缓存', value: 'post:feed:cache:*' },
  { label: '令牌缓存', value: 'auth:*' },
]

const fetchOverview = async () => {
  loading.value = true
  try {
    const res = await cacheAdminApi.getOverview()
    if (res.code === ResultCode.SUCCESS) {
      overview.value = res.data
    }
  } catch (error) {
    overview.value = null
    ElMessage.error('获取缓存概览失败')
  } finally {
    loading.value = false
  }
}

const countPattern = async () => {
  if (!pattern.value.trim()) {
    ElMessage.warning('请输入缓存模式，例如 tag:*')
    return
  }
  patternLoading.value = true
  try {
    const res = await cacheAdminApi.countByPattern(pattern.value.trim())
    if (res.code === ResultCode.SUCCESS) {
      patternCount.value = res.data
    }
  } catch (error) {
    patternCount.value = null
    ElMessage.error('统计缓存数量失败')
  } finally {
    patternLoading.value = false
  }
}

const clearTagCache = async () => {
  await ElMessageBox.confirm('确认清除标签缓存？', '操作确认', { type: 'warning' })
  await cacheAdminApi.clearTagCache()
  ElMessage.success('标签缓存已清除')
  await fetchOverview()
}

const clearTokenCache = async () => {
  await ElMessageBox.confirm('确认清除令牌缓存？用户可能需要重新登录。', '操作确认', { type: 'warning' })
  await cacheAdminApi.clearTokenCache()
  ElMessage.success('令牌缓存已清除')
  await fetchOverview()
}

const clearMediaCache = async () => {
  await ElMessageBox.confirm('确认清除旧媒体链路写入 Redis 的联调痕迹？', '操作确认', { type: 'warning' })
  await cacheAdminApi.clearMediaCache()
  ElMessage.success('旧媒体链路缓存痕迹已清除')
  await fetchOverview()
}

const applyPatternPreset = (value: string) => {
  pattern.value = value
  patternCount.value = null
}

const clearByPattern = async () => {
  if (!pattern.value.trim()) {
    ElMessage.warning('请输入缓存模式，例如 post:feed:cache:*')
    return
  }
  await ElMessageBox.confirm(`确认清除模式 "${pattern.value.trim()}" 的缓存？`, '操作确认', { type: 'warning' })
  const res = await cacheAdminApi.clearByPattern(pattern.value.trim())
  if (res.code === ResultCode.SUCCESS) {
    ElMessage.success(res.message || '清理完成')
    patternCount.value = null
    await fetchOverview()
  }
}

onMounted(() => {
  fetchOverview()
})
</script>

<template>
  <div class="cache-manage-page" v-loading="loading">
    <div class="page-header">
      <h1 class="title">缓存管理</h1>
      <el-button type="primary" @click="fetchOverview">刷新概览</el-button>
    </div>

    <el-row :gutter="12" class="stats-row">
      <el-col :xs="12" :sm="8" :md="6" :lg="6" v-for="item in statCards" :key="item.key">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-title">快捷清理</div>
      </template>
      <div class="quick-actions">
        <el-button type="warning" @click="clearTagCache">清理标签缓存</el-button>
        <el-button type="danger" @click="clearTokenCache">清理令牌缓存</el-button>
        <el-button type="primary" plain @click="clearMediaCache">清理媒体联调缓存</el-button>
      </div>
    </el-card>

    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-title">按模式操作</div>
      </template>

      <div class="pattern-row">
        <el-input
          v-model="pattern"
          placeholder="输入缓存模式，例如旧链路 media:service:* 或 post:feed:cache:*"
          clearable
        />
        <el-button :loading="patternLoading" @click="countPattern">统计数量</el-button>
        <el-button type="danger" :loading="patternLoading" @click="clearByPattern">按模式清理</el-button>
      </div>

      <div class="pattern-presets">
        <span class="pattern-presets__label">快捷模式：</span>
        <el-button
          v-for="item in patternPresets"
          :key="item.value"
          size="small"
          text
          @click="applyPatternPreset(item.value)"
        >
          {{ item.label }}
        </el-button>
      </div>

      <div class="pattern-result" v-if="patternCount !== null">
        当前模式命中缓存数量：<strong>{{ patternCount }}</strong>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.cache-manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
}

.stats-row {
  margin-bottom: 4px;
}

.stat-card {
  min-height: 92px;
}

.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.stat-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.action-card {
  border-radius: 12px;
}

.card-title {
  font-weight: 700;
}

.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.pattern-row {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto auto;
  gap: 10px;
}

.pattern-result {
  margin-top: 12px;
  color: var(--el-text-color-regular);
}

.pattern-presets {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.pattern-presets__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 768px) {
  .pattern-row {
    grid-template-columns: 1fr;
  }
}
</style>
