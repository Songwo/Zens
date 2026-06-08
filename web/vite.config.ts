import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

// Song：构建配置参考链接
export default defineConfig({
  plugins: [
    vue(),
    // Song：Element Plus 按需引入——模板里的 <el-xxx> 自动 import 组件与其 CSS，
    // 大幅缩减首屏 vue-vendor 体积。指令(v-loading)在 main.ts 全局注册，故 directives:false；
    // dirs:[] 关闭对 src/components 的扫描，只按需解析 Element Plus，不接管项目自有组件。
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      directives: false,
      dirs: [],
      dts: 'src/components.d.ts',
    }),
  ],
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    cssCodeSplit: true,
    chunkSizeWarningLimit: 800,
    minify: 'esbuild',
    target: 'es2018',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined

          if (
            id.includes('/vue/') ||
            id.includes('/vue-router/') ||
            id.includes('/pinia/') ||
            id.includes('/@element-plus/icons-vue/') ||
            id.includes('/element-plus/') ||
            id.includes('/@element-plus/')
          ) {
            return 'vue-vendor'
          }
          if (id.includes('/markdown-it/') || id.includes('/dompurify/')) {
            return 'editor-vendor'
          }
          if (
            id.includes('/@codemirror/') ||
            id.includes('/codemirror/') ||
            id.includes('/@lezer/')
          ) {
            return 'codemirror-vendor'
          }
          if (id.includes('/@shikijs/langs/')) {
            // 让每个语言走独立 chunk，按需懒加载（不混进 shiki-vendor）
            return undefined
          }
          if (id.includes('/shiki/') || id.includes('/@shikijs/')) {
            return 'shiki-vendor'
          }
          if (id.includes('/echarts/') || id.includes('/vue-echarts/')) {
            return 'chart-vendor'
          }
          if (id.includes('/sockjs-client/') || id.includes('/@stomp/stompjs/')) {
            return 'ws-vendor'
          }
          if (
            id.includes('/axios/') ||
            id.includes('/date-fns/') ||
            id.includes('/lucide-vue-next/')
          ) {
            return 'app-utils-vendor'
          }
          return 'misc-vendor'
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:7800',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        ws: true
      },
      '/uploads': {
        target: 'http://localhost:7800',
        changeOrigin: true
      },
      '/static': {
        target: 'http://localhost:7800',
        changeOrigin: true
      }
    }
  }
})
