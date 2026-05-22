import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// Song：构建配置参考链接
export default defineConfig({
  plugins: [
    vue()
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
