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

installCodeBlockCopy()
cleanupDevServiceWorker()

app.mount('#app')
