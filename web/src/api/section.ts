import api from '@/lib/api'
import type { Result } from '@/types'

export interface Section {
    id: string
    name: string
    description?: string
    postCount?: number
    icon?: string
    order?: number
}

export const sectionApi = {
    getList() {
        return api.get<any, Result<Section[]>>('/section/list')
    },

    getById(id: string) {
        return api.get<any, Result<Section>>(`/section/${id}`)
    },

    create(data: { name: string; description?: string; icon?: string }) {
        return api.post<any, Result<void>>('/section', data)
    },

    update(id: string, data: { name?: string; description?: string; icon?: string }) {
        return api.put<any, Result<void>>(`/section/${id}`, data)
    },

    delete(id: string) {
        return api.delete<any, Result<void>>(`/section/${id}`)
    }
}
