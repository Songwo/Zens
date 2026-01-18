import api from '@/lib/api'
import type { Result } from '@/types'

export interface StudentProfile {
    userId: string
    studentNo: string
    realName: string
    idCard: string
    college: string
    className: string
    enrollmentDate: string
    graduationStatus: number
    phone: string
    campus: string
    dormBuilding: string
    dormRoom: string
    dormBed: string
    totalCredits: number
}

export const studentApi = {
    getProfile() {
        return api.get<any, Result<StudentProfile>>('/student/profile')
    },
    updateProfile(data: Partial<StudentProfile>) {
        return api.post<any, Result<void>>('/student/profile/update', data)
    },
    checkGraduation() {
        return api.get<any, Result<string>>('/student/graduation/check')
    }
}
