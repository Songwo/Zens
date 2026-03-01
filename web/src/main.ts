import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// Song：说明
import 'element-plus/dist/index.css'
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
app.use(ElementPlus)

// Song：说明
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
