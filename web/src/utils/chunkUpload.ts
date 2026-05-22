import api from '@/lib/api'
import type { Result } from '@/types'
import axios, { type AxiosProgressEvent } from 'axios'

const HASH_MAX_BYTES = 5 * 1024 * 1024
const MAX_CONCURRENT = 6
const SINGLE_UPLOAD_TIMEOUT_MS = 5 * 60 * 1000

export interface ChunkUploadOptions {
  file: File
  module?: string
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

interface R2PartUploadResp {
  partNumber: number
  uploadUrl: string
}

interface R2InitResp {
  mode: 'single' | 'multipart' | 'hit'
  fileKey?: string
  accessUrl: string
  uploadUrl?: string
  uploadId?: string
  partCount?: number
  partSizeBytes?: number
  cacheControl?: string
  parts?: R2PartUploadResp[]
  fileId?: string
}

interface R2CompleteResp {
  fileId: string
  accessUrl: string
}

interface R2PartEtagReq {
  partNumber: number
  etag: string
}

function validateMultipartEtags(expectedParts: R2PartUploadResp[], etags: R2PartEtagReq[]): void {
  if (expectedParts.length === 0) {
    throw new Error('缺少分片上传参数')
  }
  if (etags.length !== expectedParts.length) {
    throw new Error(`分片上传不完整，期望 ${expectedParts.length} 片，实际完成 ${etags.length} 片`)
  }

  const expectedNumbers = expectedParts.map(part => part.partNumber).sort((left, right) => left - right)
  const actualNumbers = [...new Set(etags.map(item => item.partNumber))].sort((left, right) => left - right)

  if (actualNumbers.length !== expectedNumbers.length) {
    throw new Error('分片上传存在重复或缺失，请重新上传')
  }

  for (let index = 0; index < expectedNumbers.length; index += 1) {
    if (expectedNumbers[index] !== actualNumbers[index]) {
      throw new Error(`分片上传缺少第 ${expectedNumbers[index]} 片，请重新上传`)
    }
  }
}

function resolveMediaType(file: File): 'image' | 'video' {
  return file.type.startsWith('video/') ? 'video' : 'image'
}

function resolveMimeType(file: File, mediaType: 'image' | 'video'): string {
  if (file.type) return file.type
  const lower = file.name.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.gif')) return 'image/gif'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.mp4')) return 'video/mp4'
  if (lower.endsWith('.webm')) return 'video/webm'
  if (lower.endsWith('.ogg')) return 'video/ogg'
  if (lower.endsWith('.mov')) return 'video/quicktime'
  return mediaType === 'video' ? 'video/mp4' : 'image/jpeg'
}

async function computeFileSha256(file: File): Promise<string> {
  if (typeof crypto === 'undefined' || !crypto.subtle) return ''
  if (file.size <= 0 || file.size > HASH_MAX_BYTES) return ''
  try {
    const buffer = await file.arrayBuffer()
    const digest = await crypto.subtle.digest('SHA-256', buffer)
    return Array.from(new Uint8Array(digest))
      .map(byte => byte.toString(16).padStart(2, '0'))
      .join('')
  } catch {
    return ''
  }
}

async function initUpload(
  file: File,
  module: string,
  sha256: string,
  mediaType: 'image' | 'video',
  mimeType: string,
): Promise<R2InitResp> {
  const res = await api.post<any, Result<R2InitResp>>('/common/upload/r2/init', {
    originalName: file.name,
    mediaType,
    sizeBytes: file.size,
    sha256: sha256 || undefined,
    bizType: module,
    mimeType,
  })
  return res.data
}

async function completeUpload(
  file: File,
  module: string,
  sha256: string,
  mediaType: 'image' | 'video',
  mimeType: string,
  initResp: R2InitResp,
  etags?: R2PartEtagReq[],
): Promise<R2CompleteResp> {
  const res = await api.post<any, Result<R2CompleteResp>>('/common/upload/r2/complete', {
    mode: initResp.mode,
    fileKey: initResp.fileKey,
    uploadId: initResp.uploadId,
    etags,
    originalName: file.name,
    mediaType,
    sizeBytes: file.size,
    sha256: sha256 || undefined,
    bizType: module,
    mimeType,
  })
  return res.data
}

async function abortMultipart(fileKey: string, uploadId: string): Promise<void> {
  try {
    await api.post('/common/upload/r2/abort', { fileKey, uploadId })
  } catch {
    // abort 失败不覆盖原始错误
  }
}

async function uploadSingle(
  file: File,
  initResp: R2InitResp,
  mimeType: string,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<void> {
  if (!initResp.uploadUrl) {
    throw new Error('缺少单文件上传地址')
  }
  await axios.put(initResp.uploadUrl, file, {
    headers: {
      'Content-Type': mimeType,
      ...(initResp.cacheControl ? { 'Cache-Control': initResp.cacheControl } : {}),
    },
    signal,
    timeout: SINGLE_UPLOAD_TIMEOUT_MS,
    transformRequest: data => data,
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (!event.total) return
      onProgress?.(Math.min(95, Math.round((event.loaded / event.total) * 95)))
    },
  })
}

async function uploadPart(
  uploadUrl: string,
  chunk: Blob,
  partNumber: number,
  progressMap: Map<number, number>,
  totalBytes: number,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<string> {
  const response = await axios.put(uploadUrl, chunk, {
    signal,
    timeout: SINGLE_UPLOAD_TIMEOUT_MS,
    transformRequest: data => data,
    onUploadProgress: (event: AxiosProgressEvent) => {
      progressMap.set(partNumber, event.loaded || 0)
      if (!onProgress) return
      const uploadedBytes = Array.from(progressMap.values()).reduce((sum, value) => sum + value, 0)
      onProgress(Math.min(95, Math.round((uploadedBytes / totalBytes) * 95)))
    },
  })
  const etag = response.headers?.etag || response.headers?.ETag
  if (!etag || typeof etag !== 'string') {
    throw new Error(`分片 ${partNumber} 上传成功但未返回 ETag`)
  }
  progressMap.set(partNumber, chunk.size)
  if (onProgress) {
    const uploadedBytes = Array.from(progressMap.values()).reduce((sum, value) => sum + value, 0)
    onProgress(Math.min(95, Math.round((uploadedBytes / totalBytes) * 95)))
  }
  return etag
}

async function uploadMultipart(
  file: File,
  initResp: R2InitResp,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<R2PartEtagReq[]> {
  const uploadId = initResp.uploadId
  const parts = [...(initResp.parts || [])].sort((left, right) => left.partNumber - right.partNumber)
  const partSizeBytes = initResp.partSizeBytes || 8 * 1024 * 1024

  if (!uploadId || parts.length === 0) {
    throw new Error('缺少分片上传参数')
  }

  const queue = [...parts]
  const progressMap = new Map<number, number>()
  const etags: R2PartEtagReq[] = []

  const worker = async () => {
    while (queue.length > 0) {
      const current = queue.shift()
      if (!current) return
      const start = (current.partNumber - 1) * partSizeBytes
      const end = Math.min(start + partSizeBytes, file.size)
      const chunk = file.slice(start, end)
      const etag = await uploadPart(
        current.uploadUrl,
        chunk,
        current.partNumber,
        progressMap,
        file.size,
        onProgress,
        signal,
      )
      etags.push({ partNumber: current.partNumber, etag })
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(MAX_CONCURRENT, parts.length) }, () => worker()),
  )

  const sortedEtags = etags.sort((left, right) => left.partNumber - right.partNumber)
  validateMultipartEtags(parts, sortedEtags)
  return sortedEtags
}

export async function chunkUpload(options: ChunkUploadOptions): Promise<string> {
  const { file, module = 'common', onProgress, signal } = options
  const mediaType = resolveMediaType(file)
  const mimeType = resolveMimeType(file, mediaType)
  const sha256 = await computeFileSha256(file)

  const initResp = await initUpload(file, module, sha256, mediaType, mimeType)

  if (initResp.mode === 'hit') {
    onProgress?.(100)
    return initResp.accessUrl
  }

  if (initResp.mode === 'single') {
    await uploadSingle(file, initResp, mimeType, onProgress, signal)
    const completeResp = await completeUpload(file, module, sha256, mediaType, mimeType, initResp)
    onProgress?.(100)
    return completeResp.accessUrl
  }

  try {
    const etags = await uploadMultipart(file, initResp, onProgress, signal)
    const completeResp = await completeUpload(file, module, sha256, mediaType, mimeType, initResp, etags)
    onProgress?.(100)
    return completeResp.accessUrl
  } catch (error) {
    if (initResp.fileKey && initResp.uploadId) {
      await abortMultipart(initResp.fileKey, initResp.uploadId)
    }
    throw error
  }
}
