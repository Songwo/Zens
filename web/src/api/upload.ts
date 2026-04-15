import api from '@/lib/api'
import { UPLOAD_REQUEST_TIMEOUT_MS } from '@/constants/upload'
import { chunkUpload } from '@/utils/chunkUpload'
import type { Result } from '@/types'

export const uploadApi = {
    /** 图片上传（自动分片，大于2MB走分片） */
    uploadImage(file: File, module: string = 'common', onProgress?: (p: number) => void): Promise<string> {
        return chunkUpload({ file, module, onProgress })
    },

    /** 视频上传（自动分片） */
    uploadVideo(file: File, module: string = 'common', onProgress?: (p: number) => void): Promise<string> {
        return chunkUpload({ file, module, onProgress })
    },

    /** 原始小文件直传（头像等场景，绕过分片） */
    uploadImageDirect(file: File, module: string = 'common') {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('module', module)
        return api.post<any, Result<string>>('/common/upload/image', formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: UPLOAD_REQUEST_TIMEOUT_MS
        }).then(res => res.data)
    }
}
