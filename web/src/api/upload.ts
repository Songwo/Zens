import { chunkUpload } from '@/utils/chunkUpload'

export const uploadApi = {
    /** 图片上传（浏览器直传 Cloudflare R2，Java 只签 presigned URL） */
    uploadImage(file: File, module: string = 'common', onProgress?: (p: number) => void): Promise<string> {
        return chunkUpload({ file, module, onProgress })
    },

    /** 视频上传（浏览器直传 Cloudflare R2，自动 multipart） */
    uploadVideo(file: File, module: string = 'common', onProgress?: (p: number) => void): Promise<string> {
        return chunkUpload({ file, module, onProgress })
    },

    /** 小文件直传（头像等），chunkUpload 内部已区分 single/multipart */
    uploadImageDirect(file: File, module: string = 'common') {
        return chunkUpload({ file, module })
    }
}
