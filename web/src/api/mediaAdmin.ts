import api from '@/lib/api'
import type { Result } from '@/types'

export interface MediaHealth {
  status: string
  service?: string
  version?: string
  uptimeSeconds?: number
  timestamp?: string
  [key: string]: unknown
}

export interface MediaDiskUsage {
  rootDir: string
  totalBytes: number
  usedBytes: number
  freeBytes: number
  usedPercent: number
}

export interface MediaRecentUpload {
  taskId: string
  fileId?: string
  bizType?: string
  bizId?: string
  mediaType?: string
  originalName?: string
  sizeBytes?: number
  status?: string
  createdAt?: string
}

export interface MediaDashboard {
  serviceStatus: string
  currentUploadingTasks: number
  uploadSuccessCount: number
  uploadFailureCount: number
  imageCount: number
  videoCount: number
  fileTotalSizeBytes: number
  disk: MediaDiskUsage
  qps: number
  averageResponseMs: number
  errorRate: number
  activeConnections: number
  chunkInProgressCount: number
  recentUploads: MediaRecentUpload[]
  rateLimit?: Record<string, unknown>
  runtimeConfig?: Record<string, unknown>
}

export interface MediaR2Config {
  enabled: boolean
  endpoint: string
  region: string
  bucket: string
  publicBaseUrl: string
  presignTtlSeconds: number
  singlePutThresholdMb: number
  partSizeMb: number
  maxImageSizeMb: number
  maxVideoSizeMb: number
  allowedImageExtensions: string
  allowedVideoExtensions: string
  cacheControl: string
}

export interface MediaConfigSnapshot {
  configFile: string
  credentialsConfigured: boolean
  savedConfig: MediaR2Config
  runtimeConfig: MediaR2Config
  runtimeMatchesSaved: boolean
  recommendedCacheControl: string
  corsAllowedHeaders: string[]
  restartHint: string
}

export interface MediaPublicAccessCheck {
  checkedAt: string
  publicBaseUrl: string
  baseStatus?: number | null
  baseReachable: boolean
  sampleFileId?: string
  sampleFileKey?: string
  sampleAccessUrl?: string
  sampleObjectExists?: boolean
  samplePublicStatus?: number | null
  ok: boolean
  advice: string[]
}

export interface MediaAccessUrlRebuildResult {
  publicBaseUrl: string
  scannedCount: number
  updatedCount: number
  checkedAt: string
  message: string
}

export interface MediaSystemStatus {
  hostname: string
  uptimeSeconds: number
  processCpuPercent: number
  systemCpuPercent: number
  processMemoryBytes: number
  systemMemoryBytes: number
  systemMemoryUsedBytes: number
  systemMemoryPercent: number
  goroutines: number
  gcCount: number
  lastConfigUpdateAt?: string
  startedAt: string
}

export interface MediaStats {
  imageCount: number
  videoCount: number
  fileTotalSizeBytes: number
  uploadSuccessCount: number
  uploadFailureCount: number
  chunkInProgressCount: number
}

export const mediaAdminApi = {
  health() {
    return api.get<any, Result<MediaHealth>>('/admin/media/health')
  },
  dashboard() {
    return api.get<any, Result<MediaDashboard>>('/admin/media/dashboard')
  },
  system() {
    return api.get<any, Result<MediaSystemStatus>>('/admin/media/system')
  },
  stats() {
    return api.get<any, Result<MediaStats>>('/admin/media/stats')
  },
  config() {
    return api.get<any, Result<MediaConfigSnapshot>>('/admin/media/config')
  },
  saveConfig(payload: Partial<MediaR2Config>) {
    return api.post<any, Result<MediaConfigSnapshot & { savedAt: string; restartRequired: boolean }>>('/admin/media/config', payload)
  },
  checkPublicAccess(publicBaseUrl?: string) {
    return api.post<any, Result<MediaPublicAccessCheck>>('/admin/media/maintenance/public-check', {
      publicBaseUrl,
    })
  },
  rebuildAccessUrls(publicBaseUrl?: string) {
    return api.post<any, Result<MediaAccessUrlRebuildResult>>('/admin/media/maintenance/rebuild-access-urls', {
      publicBaseUrl,
    })
  },
}
