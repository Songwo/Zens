import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeName = 'default' | 'teal' | 'blue'
export type LayoutMode = 'boxed' | 'wide'

export const useUiStore = defineStore('ui', () => {
    // Song：说明
    const themeName = ref<ThemeName>((localStorage.getItem('cp_theme') as ThemeName) || 'default')
    const isDark = ref<boolean>(localStorage.getItem('cp_dark') === 'true')
    const isWide = ref<boolean>(localStorage.getItem('cp_wide') === 'true')

    // Song：说明
    const applyUiSettings = () => {
        const root = document.documentElement

        // Song：说明
        root.setAttribute('data-theme', themeName.value)
        localStorage.setItem('cp_theme', themeName.value)

        // Song：说明
        root.setAttribute('data-mode', isDark.value ? 'dark' : 'light')
        if (isDark.value) {
            root.classList.add('dark') // Song：说明
        } else {
            root.classList.remove('dark')
        }
        localStorage.setItem('cp_dark', String(isDark.value))

        // Song：说明
        root.setAttribute('data-layout', isWide.value ? 'wide' : 'boxed')
        localStorage.setItem('cp_wide', String(isWide.value))
    }

    // Song：说明
    watch([themeName, isDark, isWide], () => {
        applyUiSettings()
    })

    // Song：说明
    const setTheme = (name: ThemeName) => { themeName.value = name }
    const toggleDark = () => { isDark.value = !isDark.value }
    const toggleWide = () => { isWide.value = !isWide.value }

    return {
        themeName,
        isDark,
        isWide,
        setTheme,
        toggleDark,
        toggleWide,
        applyUiSettings
    }
})
