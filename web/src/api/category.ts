import api from '@/lib/api'
import type { Result } from '@/types'

export interface Category {
    id: string
    name: string
    code: string
    icon?: string
    sort: number
    postCount?: number
}

export const categoryApi = {
    // Get all categories
    getList() {
        return api.get<any, Result<Category[]>>('/category/list')
    },

    // Get category by ID
    getById(id: string) {
        return api.get<any, Result<Category>>(`/category/${id}`)
    },

    // Get category by code
    getByCode(code: string) {
        return api.get<any, Result<Category>>(`/category/code/${code}`)
    }
}
