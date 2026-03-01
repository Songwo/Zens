import api from '@/lib/api'
import type { Result } from '@/types'

export const uploadApi = {
    uploadImage(file: File, module: string = 'common') {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('module', module)
        return api.post<any, Result<string>>('/common/upload/image', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        })
    }
}
