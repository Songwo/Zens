import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
// Song：Element Plus 组件由 unplugin-vue-components 自动按需 import；
// 样式全局一次性加载，避免开发环境组件样式依赖预构建失效导致后台懒加载卡死。
// v-loading 指令在下方全局注册。
import { ElLoading } from 'element-plus'
import 'element-plus/theme-chalk/index.css'
// Song：说明
import './styles/ep-theme.scss'
// Song：说明
import './styles/tokens.css'
import './styles/ep-dark-patch.css'
// Song：说明
import './styles/global.css'
import './styles/tailwind.css'
// Song：说明
import './styles/prose.css'

// Song：说明
import App from './App.vue'
import { installCodeBlockCopy } from './utils/codeBlockCopy'
import { cleanupDevServiceWorker } from './utils/devServiceWorkerCleanup'
import { installWebVitals } from './utils/webVitals'

const app = createApp(App)
const pinia = createPinia()

// Song：说明
app.directive('click-away', {
  mounted(el, binding) {
    el.clickOutsideEvent = (event: Event) => {
      if (!(el === event.target || el.contains(event.target as Node))) {
        binding.value(event)
      }
    }
    document.addEventListener('click', el.clickOutsideEvent)
  },
  unmounted(el) {
    document.removeEventListener('click', el.clickOutsideEvent)
  }
})

app.use(pinia)
app.use(router)
// Song：注册 v-loading 指令 + ElLoading 服务（按需模式下需手动注册）
app.use(ElLoading)

app.mount('#app')

const scheduleNonCriticalStartup = (callback: () => void) => {
  const win = window as Window & {
    requestIdleCallback?: (cb: IdleRequestCallback, opts?: { timeout: number }) => number
  }

  if (typeof win.requestIdleCallback === 'function') {
    win.requestIdleCallback(callback, { timeout: 1800 })
    return
  }

  window.setTimeout(callback, 800)
}

const registerProductionServiceWorker = () => {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  if (window.location.hostname !== 'www.allinsong.top') return

  navigator.serviceWorker.register('/sw.js')
    .then((registration) => {
      window.setInterval(() => registration.update().catch(() => {}), 60 * 60 * 1000)
    })
    .catch((error) => {
      console.warn('Service worker registration failed:', error)
    })
}

if (typeof window !== 'undefined') {
  scheduleNonCriticalStartup(() => {
    installCodeBlockCopy()
    cleanupDevServiceWorker()
    installWebVitals()
    registerProductionServiceWorker()
  })
}
