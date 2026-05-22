import api from '@/lib/api'
import type { Result } from '@/types'

export interface LogOverview {
  baseDir: string
  backupDir: string
  activeFileName: string
  activeFilePath: string
  activeFileAccessible: boolean
  archivePattern: string
  rollingMaxFileSize: string
  rollingMaxHistory: number
  rollingTotalSizeCap: string
  cleanHistoryOnStart: boolean
  autoBackupEnabled: boolean
  autoBackupCron: string
  cleanupCron: string
  backupRetentionDays: number
  maxBackupFiles: number
  maxBackupTotalSizeBytes: number
  previewMaxLines: number
  previewMaxBytes: number
  listLimit: number
  totalFiles: number
  totalSizeBytes: number
  archiveFiles: number
  backupFiles: number
  compressedFiles: number
  activeSizeBytes: number
}

export interface LogFileSummary {
  relativePath: string
  fileName: string
  type: 'active' | 'archive' | 'backup' | 'log' | string
  active: boolean
  compressed: boolean
  previewTailSupported: boolean
  downloadable: boolean
  deletable: boolean
  sizeBytes: number
  modifiedTime: string
}

export interface LogContent {
  relativePath: string
  fileName: string
  mode: 'head' | 'tail' | string
  lineCount: number
  maxLines: number
  maxBytes: number
  truncated: boolean
  compressed: boolean
  sizeBytes: number
  modifiedTime: string
  content: string
}

export interface LogBackupResult {
  relativePath: string
  fileName: string
  sizeBytes: number
  createdTime: string
  compressed: boolean
}

export interface LogCleanupResult {
  scannedFiles: number
  keptFiles: number
  deletedFiles: number
  deletedBytes: number
}

export const logAdminApi = {
  getOverview() {
    return api.get<any, Result<LogOverview>>('/admin/logs/overview')
  },

  listFiles(keyword?: string) {
    return api.get<any, Result<LogFileSummary[]>>('/admin/logs/files', {
      params: keyword?.trim() ? { keyword: keyword.trim() } : undefined,
    })
  },

  getContent(file: string, mode: 'head' | 'tail', lines?: number, maxBytes?: number) {
    return api.get<any, Result<LogContent>>('/admin/logs/content', {
      params: {
        file,
        mode,
        lines,
        maxBytes,
      },
    })
  },

  createBackup() {
    return api.post<any, Result<LogBackupResult>>('/admin/logs/backup')
  },

  cleanup() {
    return api.post<any, Result<LogCleanupResult>>('/admin/logs/cleanup')
  },

  deleteFile(file: string) {
    return api.delete<any, Result<string>>('/admin/logs/file', {
      params: { file },
    })
  },

  downloadFile(file: string) {
    return api.get<any, Blob>('/admin/logs/download', {
      params: { file },
      responseType: 'blob',
    })
  },
}
