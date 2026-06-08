import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
// Song：Element Plus 按需引入——组件由 unplugin-vue-components 自动按需 import；
// 这里只显式引入命令式服务(ElMessage/ElMessageBox/ElNotification/ElLoading)所需样式，
// 并全局注册 v-loading 指令。
import { ElLoading } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-overlay.css'
import 'element-plus/theme-chalk/el-loading.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-notification.css'
// Song：说明
import './styles/ep-theme.scss'
// Song：说明
import './styles/tokens.css'
import './styles/ep-dark-patch.css'
// Song：说明
import './styles/global.css'
// Song：说明
import './styles/prose.css'

// Song：说明
import App from './App.vue'
import { installCodeBlockCopy } from './utils/codeBlockCopy'

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

app.mount('#app')
