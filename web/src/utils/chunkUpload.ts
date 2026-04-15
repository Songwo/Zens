/**
 * 分片上传工具
 * - 将大文件切分为多个分片并发上传
 * - 支持进度回调
 * - 失败自动重试
 */
import api from '@/lib/api'
import type { Result } from '@/types'

const CHUNK_SIZE = 2 * 1024 * 1024  // 2MB per chunk
const MAX_CONCURRENT = 3             // 最大并发分片数
const MAX_RETRY = 2                  // 每个分片最大重试次数

export interface ChunkUploadOptions {
  file: File
  module?: string
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

export interface ChunkUploadResult {
  url: string
}

/** 初始化分片上传，获取 uploadId */
async function initChunkUpload(filename: string, totalChunks: number, fileSize: number, module: string): Promise<string> {
  const res = await api.post<any, Result<{ uploadId: string }>>('/common/upload/chunk/init', {
    filename,
    totalChunks,
    fileSize,
    module,
  })
  return res.data.uploadId
}

/** 上传单个分片 */
async function uploadChunk(
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
  signal?: AbortSignal
): Promise<void> {
  const form = new FormData()
  form.append('uploadId', uploadId)
  form.append('chunkIndex', String(chunkIndex))
  form.append('chunk', chunk)
  await api.post('/common/upload/chunk/part', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
    signal,
  })
}

/** 合并分片，返回最终 URL */
async function mergeChunks(uploadId: string, filename: string, totalChunks: number): Promise<string> {
  const res = await api.post<any, Result<{ url: string }>>('/common/upload/chunk/merge', {
    uploadId,
    filename,
    totalChunks,
  })
  return res.data.url
}

/** 带重试的单分片上传 */
async function uploadChunkWithRetry(
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
  signal?: AbortSignal
): Promise<void> {
  let lastError: any
  for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
    try {
      await uploadChunk(uploadId, chunkIndex, chunk, signal)
      return
    } catch (e: any) {
      if (e?.code === 'ERR_CANCELED') throw e
      lastError = e
      if (attempt < MAX_RETRY) {
        await new Promise(r => setTimeout(r, 500 * (attempt + 1)))
      }
    }
  }
  throw lastError
}

/**
 * 主入口：分片上传文件
 * 小于 CHUNK_SIZE 的文件直接用普通上传接口
 */
export async function chunkUpload(options: ChunkUploadOptions): Promise<string> {
  const { file, module = 'common', onProgress, signal } = options

  // 小文件直接上传
  if (file.size <= CHUNK_SIZE) {
    const form = new FormData()
    form.append('file', file)
    form.append('module', module)
    const endpoint = file.type.startsWith('video/') ? '/common/upload/video' : '/common/upload/image'
    const res = await api.post<any, Result<string>>(endpoint, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60000,
      signal,
      onUploadProgress: (e) => {
        if (e.total) onProgress?.(Math.round((e.loaded / e.total) * 100))
      },
    })
    onProgress?.(100)
    return res.data
  }

  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  const uploadId = await initChunkUpload(file.name, totalChunks, file.size, module)

  let completedChunks = 0
  const updateProgress = () => {
    completedChunks++
    onProgress?.(Math.round((completedChunks / totalChunks) * 95)) // 留5%给merge
  }

  // 并发上传（限制并发数）
  const queue = Array.from({ length: totalChunks }, (_, i) => i)
  const worker = async () => {
    while (queue.length > 0) {
      const idx = queue.shift()!
      const start = idx * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)
      await uploadChunkWithRetry(uploadId, idx, chunk, signal)
      updateProgress()
    }
  }

  const workers = Array.from({ length: Math.min(MAX_CONCURRENT, totalChunks) }, () => worker())
  await Promise.all(workers)

  const url = await mergeChunks(uploadId, file.name, totalChunks)
  onProgress?.(100)
  return url
}
