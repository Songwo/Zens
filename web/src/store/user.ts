import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
    const accessToken = ref(localStorage.getItem('access_token'))
    const refreshToken = ref(localStorage.getItem('refresh_token'))
    const userId = ref(localStorage.getItem('user_id'))
    const userInfo = ref<any>(null)

    const isLoggedIn = computed(() => !!accessToken.value)

    function setAuth(access: string, refresh: string) {
        accessToken.value = access
        refreshToken.value = refresh
        localStorage.setItem('access_token', access)
        localStorage.setItem('refresh_token', refresh)
    }

    function setUserId(newId: string) {
        userId.value = newId
        localStorage.setItem('user_id', newId)
    }

    function setUserInfo(info: any) {
        userInfo.value = info
    }

    function logout() {
        accessToken.value = null
        refreshToken.value = null
        userId.value = null
        userInfo.value = null
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        localStorage.removeItem('user_id')
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
