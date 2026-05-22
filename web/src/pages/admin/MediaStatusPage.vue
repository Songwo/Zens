<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Cpu, Monitor, Refresh, Timer, DataLine, Files, Setting, Link, Tools } from '@element-plus/icons-vue'
import {
  mediaAdminApi,
  type MediaAccessUrlRebuildResult,
  type MediaConfigSnapshot,
  type MediaDashboard,
  type MediaHealth,
  type MediaPublicAccessCheck,
  type MediaR2Config,
  type MediaStats,
  type MediaSystemStatus,
} from '@/api/mediaAdmin'
import { cacheAdminApi, type CacheOverview } from '@/api/cacheAdmin'
import { ResultCode } from '@/types'

const loading = ref(false)
const autoRefresh = ref(false)
const lastRefreshAt = ref('')

const health = ref<MediaHealth | null>(null)
const dashboard = ref<MediaDashboard | null>(null)
const system = ref<MediaSystemStatus | null>(null)
const stats = ref<MediaStats | null>(null)
const cache = ref<CacheOverview | null>(null)
const configSnapshot = ref<MediaConfigSnapshot | null>(null)
const publicCheck = ref<MediaPublicAccessCheck | null>(null)
const rebuildResult = ref<MediaAccessUrlRebuildResult | null>(null)

const configSaving = ref(false)
const publicCheckLoading = ref(false)
const rebuildLoading = ref(false)

const createDefaultConfig = (): MediaR2Config => ({
  enabled: true,
  endpoint: '',
  region: 'auto',
  bucket: '',
  publicBaseUrl: '',
  presignTtlSeconds: 3600,
  singlePutThresholdMb: 5,
  partSizeMb: 8,
  maxImageSizeMb: 15,
  maxVideoSizeMb: 1024,
  allowedImageExtensions: '.jpg,.jpeg,.png,.gif,.webp',
  allowedVideoExtensions: '.mp4,.webm,.ogg,.mov',
  cacheControl: '',
})

const configForm = ref<MediaR2Config>(createDefaultConfig())

let autoTimer: number | null = null

const serviceOnline = computed(() => {
  if (!health.value) return false
  const status = String(health.value.status || '').toLowerCase()
  return status === 'ok' || status === 'healthy' || status === 'up'
})

const serviceTagType = computed(() => (serviceOnline.value ? 'success' : 'danger'))
const serviceTagText = computed(() => (serviceOnline.value ? '在线' : '离线'))

const configDiffKeys = computed(() => {
  const runtime = configSnapshot.value?.runtimeConfig
  const saved = configSnapshot.value?.savedConfig
  if (!runtime || !saved) return []
  return Object.keys(saved).filter((key) => JSON.stringify(saved[key as keyof MediaR2Config]) !== JSON.stringify(runtime[key as keyof MediaR2Config]))
})

const fetchAll = async () => {
  loading.value = true
  try {
    const [healthRes, dashRes, sysRes, statsRes, cacheRes, configRes] = await Promise.allSettled([
      mediaAdminApi.health(),
      mediaAdminApi.dashboard(),
      mediaAdminApi.system(),
      mediaAdminApi.stats(),
      cacheAdminApi.getOverview(),
      mediaAdminApi.config(),
    ])

    const pick = <T>(r: PromiseSettledResult<{ code: number; data: T }>): T | null => {
      if (r.status === 'fulfilled' && r.value?.code === ResultCode.SUCCESS) return r.value.data
      return null
    }

    health.value = pick<MediaHealth>(healthRes as any)
    dashboard.value = pick<MediaDashboard>(dashRes as any)
    system.value = pick<MediaSystemStatus>(sysRes as any)
    stats.value = pick<MediaStats>(statsRes as any)
    cache.value = pick<CacheOverview>(cacheRes as any)
    configSnapshot.value = pick<MediaConfigSnapshot>(configRes as any)

    if (configSnapshot.value?.savedConfig) {
      configForm.value = { ...configSnapshot.value.savedConfig }
    }

    lastRefreshAt.value = new Date().toLocaleTimeString()

    if (!health.value) {
      ElMessage.warning('媒体存储状态暂不可用，请检查 Java 后端与 R2 配置')
    }
  } catch {
    ElMessage.error('拉取媒体服务状态失败')
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  configSaving.value = true
  try {
    const res = await mediaAdminApi.saveConfig(configForm.value)
    if (res.code !== ResultCode.SUCCESS || !res.data) {
      ElMessage.error(res.message || '保存 R2 配置失败')
      return
    }
    configSnapshot.value = res.data
    configForm.value = { ...res.data.savedConfig }
    ElMessage.success('R2 设置已写入 .env.local，重启后端后全部生效')
  } catch (err: any) {
    ElMessage.error(err?.message || '保存 R2 配置失败')
  } finally {
    configSaving.value = false
  }
}

const checkPublicAccess = async () => {
  publicCheckLoading.value = true
  try {
    const res = await mediaAdminApi.checkPublicAccess(configForm.value.publicBaseUrl)
    if (res.code !== ResultCode.SUCCESS || !res.data) {
      ElMessage.error(res.message || '公开域名检测失败')
      return
    }
    publicCheck.value = res.data
    if (res.data.ok) {
      ElMessage.success('公开域名检查通过')
    } else {
      ElMessage.warning('公开域名检查未通过，请查看诊断建议')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '公开域名检测失败')
  } finally {
    publicCheckLoading.value = false
  }
}

const rebuildAccessUrls = async () => {
  rebuildLoading.value = true
  try {
    const res = await mediaAdminApi.rebuildAccessUrls(configForm.value.publicBaseUrl)
    if (res.code !== ResultCode.SUCCESS || !res.data) {
      ElMessage.error(res.message || '重建 accessUrl 失败')
      return
    }
    rebuildResult.value = res.data
    ElMessage.success(`已重建 ${res.data.updatedCount} 条媒体访问地址`)
  } catch (err: any) {
    ElMessage.error(err?.message || '重建 accessUrl 失败')
  } finally {
    rebuildLoading.value = false
  }
}

const toggleAutoRefresh = (value: boolean) => {
  autoRefresh.value = value
  if (autoTimer) {
    window.clearInterval(autoTimer)
    autoTimer = null
  }
  if (value) {
    autoTimer = window.setInterval(fetchAll, 5000)
  }
}

onMounted(fetchAll)
onBeforeUnmount(() => {
  if (autoTimer) window.clearInterval(autoTimer)
})

const formatBytes = (value: number | undefined | null) => {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = Number(value)
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size >= 100 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`
}

const formatUptime = (seconds: number | undefined | null) => {
  const total = Number(seconds) || 0
  if (total < 60) return `${total.toFixed(0)} 秒`
  const minutes = Math.floor(total / 60)
  if (minutes < 60) return `${minutes} 分钟`
  const hours = Math.floor(minutes / 60)
  const restMin = minutes % 60
  if (hours < 24) return `${hours} 小时 ${restMin} 分`
  const days = Math.floor(hours / 24)
  const restHr = hours % 24
  return `${days} 天 ${restHr} 小时`
}

const formatPercent = (value: number | undefined | null, digits = 1) => {
  const v = Number(value) || 0
  return `${v.toFixed(digits)}%`
}

const formatNumber = (value: number | undefined | null) => {
  const v = Number(value) || 0
  if (v >= 1000) return v.toLocaleString()
  return String(v)
}

const formatStatusCode = (value: number | null | undefined) => {
  if (value == null) return '不可达'
  return String(value)
}

const coreMetricCards = computed(() => {
  const d = dashboard.value
  if (!d) return []
  return [
    { label: 'QPS', value: (d.qps ?? 0).toFixed(2), icon: DataLine },
    { label: '平均响应', value: `${(d.averageResponseMs ?? 0).toFixed(0)} ms`, icon: Timer },
    { label: '错误率', value: formatPercent((d.errorRate ?? 0) * 100, 2), icon: Monitor },
    { label: '活跃连接', value: formatNumber(d.activeConnections), icon: Cpu },
    { label: '进行中上传', value: formatNumber(d.currentUploadingTasks), icon: Files },
    { label: '进行中分片', value: formatNumber(d.chunkInProgressCount), icon: Files },
  ]
})

const statsCards = computed(() => {
  const s = stats.value
  if (!s) return []
  return [
    { label: '图片总数', value: formatNumber(s.imageCount) },
    { label: '视频总数', value: formatNumber(s.videoCount) },
    { label: '文件总大小', value: formatBytes(s.fileTotalSizeBytes) },
    { label: '上传成功数', value: formatNumber(s.uploadSuccessCount) },
    { label: '上传失败数', value: formatNumber(s.uploadFailureCount) },
    { label: '进行中分片', value: formatNumber(s.chunkInProgressCount) },
  ]
})

const cacheRows = computed(() => {
  const c = cache.value
  if (!c) return []
  return [
    { label: '旧链路 Token 痕迹', value: c.mediaToken },
    { label: '旧链路上传痕迹', value: c.mediaUpload },
    { label: '旧链路分片痕迹', value: c.mediaChunk },
    { label: '旧链路代理痕迹', value: c.mediaAdmin },
    { label: '旧链路健康检查痕迹', value: c.mediaHealth },
    { label: '旧链路错误痕迹', value: c.mediaError },
    { label: '旧链路指标键', value: c.mediaMetric },
    { label: '旧链路相关总量', value: c.mediaTotal },
  ]
})

const diskPercent = computed(() => {
  const d = dashboard.value?.disk
  return d ? Math.min(100, Math.max(0, Number(d.usedPercent) || 0)) : 0
})

const diskPercentColor = (v: number) => {
  if (v >= 90) return '#F56C6C'
  if (v >= 75) return '#E6A23C'
  return '#409EFF'
}

const memPercent = computed(() => {
  const s = system.value
  return s ? Math.min(100, Math.max(0, Number(s.systemMemoryPercent) || 0)) : 0
})

const cpuPercent = computed(() => {
  const s = system.value
  return s ? Math.min(100, Math.max(0, Number(s.systemCpuPercent) || 0)) : 0
})

const recentUploads = computed(() => dashboard.value?.recentUploads ?? [])

const uploadStatusType = (status: string | undefined) => {
  const v = String(status || '').toLowerCase()
  if (v === 'success' || v === 'done' || v === 'ok') return 'success'
  if (v === 'failed' || v === 'error') return 'danger'
  if (v === 'uploading' || v === 'running' || v === 'pending') return 'warning'
  return 'info'
}
</script>

<template>
  <div class="media-status-page" v-loading="loading">
    <div class="page-header">
      <div class="header-left">
        <h1 class="title">媒体存储状态</h1>
        <el-tag :type="serviceTagType" round>{{ serviceTagText }}</el-tag>
        <span class="service-meta" v-if="health">
          <span v-if="health.service">{{ health.service }}</span>
          <span v-if="health.version"> · v{{ health.version }}</span>
        </span>
      </div>
      <div class="header-right">
        <span class="refresh-meta" v-if="lastRefreshAt">上次刷新：{{ lastRefreshAt }}</span>
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          @change="toggleAutoRefresh"
        />
        <el-button type="primary" :icon="Refresh" @click="fetchAll">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="!serviceOnline && !loading"
      class="warn-alert"
      type="warning"
      show-icon
      :closable="false"
      title="媒体存储状态暂不可达"
      description="请确认 Java 后端已配置 R2 凭证，并且 sys_media_file 表可正常访问。"
    />

    <el-row :gutter="12" class="stats-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="item in coreMetricCards" :key="item.label">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-head">
            <el-icon class="stat-icon"><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </div>
          <div class="stat-value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-title-row">
          <div class="card-title"><el-icon><Setting /></el-icon><span>R2 配置管理</span></div>
          <div class="header-actions">
            <el-tag v-if="configSnapshot?.credentialsConfigured" type="success" effect="plain">凭证已配置</el-tag>
            <el-tag v-else type="warning" effect="plain">凭证未配置</el-tag>
            <el-button type="primary" :loading="configSaving" @click="saveConfig">保存设置</el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="configSnapshot && !configSnapshot.runtimeMatchesSaved"
        class="inner-alert"
        type="warning"
        show-icon
        :closable="false"
        :title="`运行时配置与已保存配置不一致，待重启生效（${configDiffKeys.length} 项）`"
        :description="configSnapshot.restartHint"
      />

      <div class="config-meta" v-if="configSnapshot">
        <div>配置文件：<code>{{ configSnapshot.configFile }}</code></div>
        <div>推荐缓存策略：<code>{{ configSnapshot.recommendedCacheControl }}</code></div>
        <div>R2 CORS AllowedHeaders 建议包含：<code>{{ configSnapshot.corsAllowedHeaders.join(', ') }}</code></div>
      </div>

      <el-form label-position="top" class="config-form">
        <div class="config-grid">
          <el-form-item label="启用 R2">
            <el-switch v-model="configForm.enabled" />
          </el-form-item>
          <el-form-item label="R2 Endpoint">
            <el-input v-model="configForm.endpoint" placeholder="https://<account>.r2.cloudflarestorage.com" />
          </el-form-item>
          <el-form-item label="Region">
            <el-input v-model="configForm.region" placeholder="auto" />
          </el-form-item>
          <el-form-item label="Bucket">
            <el-input v-model="configForm.bucket" placeholder="campus-pulse-media" />
          </el-form-item>
          <el-form-item label="公开域名">
            <el-input v-model="configForm.publicBaseUrl" placeholder="https://media.example.com" />
          </el-form-item>
          <el-form-item label="上传签名有效期（秒）">
            <el-input-number v-model="configForm.presignTtlSeconds" :min="60" :max="86400" :step="60" class="full-width" />
          </el-form-item>
          <el-form-item label="单文件直传阈值（MB）">
            <el-input-number v-model="configForm.singlePutThresholdMb" :min="1" :max="1024" class="full-width" />
          </el-form-item>
          <el-form-item label="分片大小（MB）">
            <el-input-number v-model="configForm.partSizeMb" :min="1" :max="1024" class="full-width" />
          </el-form-item>
          <el-form-item label="图片大小上限（MB）">
            <el-input-number v-model="configForm.maxImageSizeMb" :min="1" :max="1024" class="full-width" />
          </el-form-item>
          <el-form-item label="视频大小上限（MB）">
            <el-input-number v-model="configForm.maxVideoSizeMb" :min="1" :max="4096" class="full-width" />
          </el-form-item>
          <el-form-item label="图片扩展名白名单">
            <el-input v-model="configForm.allowedImageExtensions" placeholder=".jpg,.jpeg,.png,.gif,.webp" />
          </el-form-item>
          <el-form-item label="视频扩展名白名单">
            <el-input v-model="configForm.allowedVideoExtensions" placeholder=".mp4,.webm,.ogg,.mov" />
          </el-form-item>
          <el-form-item label="Cache-Control 策略" class="wide">
            <el-input
              v-model="configForm.cacheControl"
              placeholder="例如 public, max-age=3600, stale-while-revalidate=60"
            />
            <div class="form-help">留空表示不额外写入对象缓存头；开启后请确保 R2 Bucket CORS 允许 <code>Cache-Control</code> 请求头。</div>
          </el-form-item>
        </div>
      </el-form>

      <div class="runtime-vs-saved" v-if="configSnapshot">
        <div class="compare-card">
          <div class="compare-title">已保存配置</div>
          <div class="compare-grid">
            <div><span>Bucket</span><code>{{ configSnapshot.savedConfig.bucket || '-' }}</code></div>
            <div><span>公开域名</span><code>{{ configSnapshot.savedConfig.publicBaseUrl || '-' }}</code></div>
            <div><span>分片阈值</span><code>{{ configSnapshot.savedConfig.singlePutThresholdMb }} MB</code></div>
            <div><span>缓存策略</span><code>{{ configSnapshot.savedConfig.cacheControl || '未设置' }}</code></div>
          </div>
        </div>
        <div class="compare-card">
          <div class="compare-title">当前运行时</div>
          <div class="compare-grid">
            <div><span>Bucket</span><code>{{ configSnapshot.runtimeConfig.bucket || '-' }}</code></div>
            <div><span>公开域名</span><code>{{ configSnapshot.runtimeConfig.publicBaseUrl || '-' }}</code></div>
            <div><span>分片阈值</span><code>{{ configSnapshot.runtimeConfig.singlePutThresholdMb }} MB</code></div>
            <div><span>缓存策略</span><code>{{ configSnapshot.runtimeConfig.cacheControl || '未设置' }}</code></div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-title-row">
          <div class="card-title"><el-icon><Tools /></el-icon><span>公开域名诊断与维护</span></div>
          <div class="header-actions">
            <el-button plain :icon="Link" :loading="publicCheckLoading" @click="checkPublicAccess">检查公开域名</el-button>
            <el-button plain type="warning" :loading="rebuildLoading" @click="rebuildAccessUrls">重建 accessUrl</el-button>
          </div>
        </div>
      </template>

      <div class="maintenance-grid">
        <div class="maintenance-panel">
          <div class="panel-title">公开地址探测</div>
          <el-empty v-if="!publicCheck" description="点击“检查公开域名”后显示探测结果" :image-size="80" />
          <div v-else class="check-result">
            <el-tag :type="publicCheck.ok ? 'success' : 'danger'" effect="plain">
              {{ publicCheck.ok ? '检查通过' : '检查未通过' }}
            </el-tag>
            <div class="check-meta">
              <div>公开域名：<code>{{ publicCheck.publicBaseUrl }}</code></div>
              <div>根路径状态：<code>{{ formatStatusCode(publicCheck.baseStatus) }}</code></div>
              <div>样本对象存在：<code>{{ publicCheck.sampleObjectExists ? '是' : '否' }}</code></div>
              <div>样本公开状态：<code>{{ formatStatusCode(publicCheck.samplePublicStatus) }}</code></div>
              <div v-if="publicCheck.sampleAccessUrl">样本访问地址：<a :href="publicCheck.sampleAccessUrl" target="_blank" rel="noreferrer">{{ publicCheck.sampleAccessUrl }}</a></div>
            </div>
            <el-alert
              v-for="item in publicCheck.advice"
              :key="item"
              class="inner-alert"
              :type="publicCheck.ok ? 'success' : 'warning'"
              :closable="false"
              show-icon
              :title="item"
            />
          </div>
        </div>

        <div class="maintenance-panel">
          <div class="panel-title">accessUrl 重建</div>
          <div class="maint-desc">
            当你修改了 <code>R2_PUBLIC_BASE_URL</code> 或自定义域名时，可用这个动作批量修正数据库里的媒体访问地址。
          </div>
          <div v-if="rebuildResult" class="check-meta">
            <div>目标域名：<code>{{ rebuildResult.publicBaseUrl }}</code></div>
            <div>扫描记录：<code>{{ rebuildResult.scannedCount }}</code></div>
            <div>更新记录：<code>{{ rebuildResult.updatedCount }}</code></div>
            <div>结果说明：{{ rebuildResult.message }}</div>
          </div>
          <div v-else class="maint-desc">尚未执行重建。通常在切换公开域名后执行一次即可。</div>
        </div>
      </div>
    </el-card>

    <div class="split-row">
      <el-card class="action-card" shadow="never">
        <template #header>
          <div class="card-title">主机与进程</div>
        </template>
        <div v-if="system" class="system-grid">
          <div class="system-item">
            <div class="label">主机名</div>
            <div class="value">{{ system.hostname || '-' }}</div>
          </div>
          <div class="system-item">
            <div class="label">服务运行时长</div>
            <div class="value">{{ formatUptime(system.uptimeSeconds) }}</div>
          </div>
          <div class="system-item">
            <div class="label">线程数</div>
            <div class="value">{{ formatNumber(system.goroutines) }}</div>
          </div>
          <div class="system-item">
            <div class="label">GC 次数</div>
            <div class="value">{{ formatNumber(system.gcCount) }}</div>
          </div>
          <div class="system-item">
            <div class="label">进程内存</div>
            <div class="value">{{ formatBytes(system.processMemoryBytes) }}</div>
          </div>
          <div class="system-item">
            <div class="label">进程 CPU</div>
            <div class="value">{{ formatPercent(system.processCpuPercent, 2) }}</div>
          </div>
          <div class="system-item full">
            <div class="label">系统 CPU ({{ formatPercent(cpuPercent, 1) }})</div>
            <el-progress :percentage="cpuPercent" :stroke-width="10" />
          </div>
          <div class="system-item full">
            <div class="label">
              系统内存
              <span class="muted"> {{ formatBytes(system.systemMemoryUsedBytes) }} / {{ formatBytes(system.systemMemoryBytes) }}</span>
            </div>
            <el-progress :percentage="memPercent" :stroke-width="10" />
          </div>
        </div>
        <el-empty v-else description="暂无主机数据" :image-size="80" />
      </el-card>

      <el-card class="action-card" shadow="never">
        <template #header>
          <div class="card-title">磁盘与文件</div>
        </template>
        <div v-if="dashboard" class="disk-wrap">
          <div class="disk-main">
            <el-progress
              type="dashboard"
              :percentage="diskPercent"
              :color="diskPercentColor(diskPercent)"
              :stroke-width="10"
              :width="140"
            >
              <div class="disk-center">
                <div class="disk-percent">{{ diskPercent.toFixed(1) }}%</div>
                <div class="disk-label">已使用</div>
              </div>
            </el-progress>
            <div class="disk-info">
              <div>目录：<code>{{ dashboard.disk?.rootDir || '-' }}</code></div>
              <div>已用：{{ formatBytes(dashboard.disk?.usedBytes) }} / {{ formatBytes(dashboard.disk?.totalBytes) }}</div>
              <div>剩余：{{ formatBytes(dashboard.disk?.freeBytes) }}</div>
            </div>
          </div>
          <el-divider />
          <el-row :gutter="10" class="stats-mini">
            <el-col :xs="12" :sm="8" v-for="item in statsCards" :key="item.label">
              <div class="mini-card">
                <div class="label">{{ item.label }}</div>
                <div class="value">{{ item.value }}</div>
              </div>
            </el-col>
          </el-row>
        </div>
        <el-empty v-else description="暂无数据" :image-size="80" />
      </el-card>
    </div>

    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-title-row">
          <div class="card-title">最近上传任务</div>
          <span class="muted">仅展示最新 12 条</span>
        </div>
      </template>
      <el-table :data="recentUploads" stripe size="small" empty-text="暂无上传记录">
        <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="mediaType" label="类型" width="100" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="uploadStatusType(row.status)" size="small" effect="plain">
              {{ row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务" width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="180" />
      </el-table>
    </el-card>

    <el-card class="action-card" shadow="never">
      <template #header>
        <div class="card-title-row">
          <div class="card-title">旧媒体链路 Redis 痕迹（media:service:*）</div>
          <router-link class="muted link" to="/admin/cache">前往缓存管理 →</router-link>
        </div>
      </template>
      <el-row :gutter="10">
        <el-col :xs="12" :sm="8" :md="6" v-for="item in cacheRows" :key="item.label">
          <div class="mini-card">
            <div class="label">{{ item.label }}</div>
            <div class="value">{{ item.value }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.media-status-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left,
.header-right,
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
}

.service-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.refresh-meta {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.warn-alert,
.inner-alert {
  border-radius: 10px;
}

.stats-row {
  margin-bottom: 4px;
}

.stat-card {
  min-height: 96px;
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.stat-icon {
  font-size: 16px;
}

.stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: var(--el-text-color-primary);
}

.split-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 960px) {
  .split-row {
    grid-template-columns: 1fr;
  }
}

.action-card {
  border-radius: 12px;
}

.card-title,
.card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.card-title-row {
  justify-content: space-between;
  width: 100%;
}

.config-meta,
.check-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.config-meta code,
.check-meta code,
.compare-grid code,
.disk-info code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.config-form {
  margin-top: 14px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}

.config-grid :deep(.el-form-item) {
  margin-bottom: 12px;
}

.config-grid :deep(.el-form-item.wide) {
  grid-column: 1 / -1;
}

.form-help {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.full-width {
  width: 100%;
}

.runtime-vs-saved,
.maintenance-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.compare-card,
.maintenance-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 14px;
  background: var(--el-fill-color-blank);
}

.compare-title,
.panel-title {
  font-weight: 700;
  margin-bottom: 12px;
}

.compare-grid {
  display: grid;
  gap: 10px;
}

.compare-grid div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
}

.compare-grid span,
.maint-desc,
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.check-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.maint-desc {
  line-height: 1.6;
}

.system-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.system-item .label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}

.system-item .value {
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.system-item.full {
  grid-column: 1 / -1;
}

.link {
  text-decoration: none;
}

.link:hover {
  color: var(--el-color-primary);
}

.disk-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.disk-main {
  display: flex;
  gap: 20px;
  align-items: center;
}

.disk-center {
  text-align: center;
}

.disk-percent {
  font-size: 20px;
  font-weight: 800;
}

.disk-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.disk-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.stats-mini .mini-card,
.action-card .mini-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
  margin-bottom: 10px;
}

.mini-card .label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mini-card .value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

@media (max-width: 960px) {
  .config-grid,
  .runtime-vs-saved,
  .maintenance-grid,
  .system-grid {
    grid-template-columns: 1fr;
  }
}
</style>
