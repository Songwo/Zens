import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeName = 'default' | 'teal' | 'blue'
export type LayoutMode = 'boxed' | 'wide'

export const useUiStore = defineStore('ui', () => {
    // Song：说明
    const themeName = ref<ThemeName>((localStorage.getItem('cp_theme') as ThemeName) || 'default')
    const isDark = ref<boolean>(localStorage.getItem('cp_dark') === 'true')
    const colorMode = ref<'light' | 'dark' | 'system'>((localStorage.getItem('cp_color_mode') as any) || (localStorage.getItem('cp_dark') === 'true' ? 'dark' : 'light'))
    const isWide = ref<boolean>(localStorage.getItem('cp_wide') === 'true')

    // 系统主题媒体查询监听
    let systemDarkQuery: MediaQueryList | null = null
    const handleSystemThemeChange = (e: MediaQueryListEvent) => {
        if (colorMode.value === 'system') {
            isDark.value = e.matches
        }
    }

    const applyUiSettings = () => {
        const root = document.documentElement

        root.setAttribute('data-theme', themeName.value)
        localStorage.setItem('cp_theme', themeName.value)

        // 根据 colorMode 决定实际深色状态
        if (colorMode.value === 'system') {
            if (!systemDarkQuery) {
                systemDarkQuery = window.matchMedia('(prefers-color-scheme: dark)')
                systemDarkQuery.addEventListener('change', handleSystemThemeChange)
            }
            isDark.value = systemDarkQuery.matches
        } else {
            if (systemDarkQuery) {
                systemDarkQuery.removeEventListener('change', handleSystemThemeChange)
                systemDarkQuery = null
            }
        }

        root.setAttribute('data-mode', isDark.value ? 'dark' : 'light')
        if (isDark.value) {
            root.classList.add('dark')
        } else {
            root.classList.remove('dark')
        }
        localStorage.setItem('cp_dark', String(isDark.value))
        localStorage.setItem('cp_color_mode', colorMode.value)

        root.setAttribute('data-layout', isWide.value ? 'wide' : 'boxed')
        localStorage.setItem('cp_wide', String(isWide.value))
    }

    watch([themeName, isDark, isWide, colorMode], () => {
        applyUiSettings()
    })

    const setTheme = (name: ThemeName) => { themeName.value = name }
    const toggleDark = () => {
        colorMode.value = isDark.value ? 'light' : 'dark'
        isDark.value = !isDark.value
    }
    const setColorMode = (mode: 'light' | 'dark' | 'system') => {
        colorMode.value = mode
        if (mode === 'dark') isDark.value = true
        else if (mode === 'light') isDark.value = false
        // system 模式由 applyUiSettings 里的 mediaQuery 决定
    }
    const toggleWide = () => { isWide.value = !isWide.value }

    return {
        themeName,
        isDark,
        colorMode,
        isWide,
        setTheme,
        toggleDark,
        setColorMode,
        toggleWide,
        applyUiSettings
    }
})
