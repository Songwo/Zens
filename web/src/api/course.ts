import api from '@/lib/api'
import type { Result } from '@/types'

export interface Course {
    id: number
    courseCode: string
    name: string
    teacherName: string
    credits: number
    maxCapacity: number
    currentCapacity: number
    classTime: string
    location: string
    semester: string
    status: number
}

export interface Grade {
    id: number
    userId: string
    courseId: number
    score: number
    semester: string
    isPassed: number
}

export const courseApi = {
    getList(params: { page: number; pageSize: number; keyword?: string }) {
        return api.get<any, Result<{ records: Course[]; total: number }>>('/course/list', { params })
    },
    select(courseId: number) {
        return api.post<any, Result<void>>(`/course/select/${courseId}`)
    },
    drop(courseId: number) {
        return api.post<any, Result<void>>(`/course/drop/${courseId}`)
    },
    getMyCourses() {
        return api.get<any, Result<Course[]>>('/course/my-courses')
    },
    getMyGrades(semester?: string) {
        return api.get<any, Result<Grade[]>>('/course/my-grades', { params: { semester } })
    }
}
