import 'axios'

declare module 'axios' {
    interface AxiosRequestConfig {
        skipGlobalProgress?: boolean
        silentError?: boolean
    }

    interface InternalAxiosRequestConfig {
        skipGlobalProgress?: boolean
        silentError?: boolean
    }
}
