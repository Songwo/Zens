/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_TURNSTILE_SITE_KEY?: string
}

interface ImportMeta {
    readonly env: ImportMetaEnv
}

interface TurnstileRenderOptions {
    sitekey: string
    theme?: 'light' | 'dark' | 'auto'
    callback?: (token: string) => void
    'error-callback'?: () => void
    'expired-callback'?: () => void
    'timeout-callback'?: () => void
}

interface TurnstileApi {
    render: (container: HTMLElement | string, options: TurnstileRenderOptions) => string
    reset: (widgetId?: string) => void
    remove: (widgetId?: string) => void
}

interface Window {
    turnstile?: TurnstileApi
}

declare module '*.vue' {
    import type { DefineComponent } from 'vue'
    const component: DefineComponent<{}, {}, any>
    export default component
}
