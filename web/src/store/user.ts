import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
    // Song：优先从 本地持久存储 读取（记住我），其次从 会话存储 读取（非记住我）
    const accessToken = ref(localStorage.getItem('access_token') || sessionStorage.getItem('access_token'))
    const refreshToken = ref(localStorage.getItem('refresh_token') || sessionStorage.getItem('refresh_token'))
    const userId = ref(localStorage.getItem('user_id') || sessionStorage.getItem('user_id'))
    const userInfo = ref<any>(null)

    const isLoggedIn = computed(() => !!(accessToken.value || refreshToken.value))

    /**
     * Song：设置认证信息
     * Song：说明
     */
    function setAuth(access: string, refresh: string, rememberMe: boolean = false) {
        accessToken.value = access
        refreshToken.value = refresh

        if (rememberMe) {
            localStorage.setItem('access_token', access)
            localStorage.setItem('refresh_token', refresh)
            localStorage.setItem('remember_me', 'true')
            // Song：清理 会话存储 的残留
            sessionStorage.removeItem('access_token')
            sessionStorage.removeItem('refresh_token')
        } else {
            sessionStorage.setItem('access_token', access)
            sessionStorage.setItem('refresh_token', refresh)
            // Song：清理 本地持久存储 的残留
            localStorage.removeItem('access_token')
            localStorage.removeItem('refresh_token')
            localStorage.removeItem('remember_me')
        }
    }

    function setUserId(newId: string) {
        userId.value = newId
        // Song：根据当前存储策略写入
        if (localStorage.getItem('remember_me') === 'true') {
            localStorage.setItem('user_id', newId)
        } else {
            sessionStorage.setItem('user_id', newId)
        }
    }

    function setUserInfo(info: any) {
        userInfo.value = info
    }

    function logout() {
        accessToken.value = null
        refreshToken.value = null
        userId.value = null
        userInfo.value = null
        // Song：清理所有存储
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        localStorage.removeItem('user_id')
        localStorage.removeItem('remember_me')
        sessionStorage.removeItem('access_token')
        sessionStorage.removeItem('refresh_token')
        sessionStorage.removeItem('user_id')
    }

    return {
        accessToken,
        refreshToken,
        userId,
        userInfo,
        isLoggedIn,
        setAuth,
        setUserId,
        setUserInfo,
        logout
    }
})
