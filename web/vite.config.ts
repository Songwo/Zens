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
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'ui-vendor': ['element-plus', '@element-plus/icons-vue'],
          'editor-vendor': ['markdown-it', 'dompurify'],
          'chart-vendor': ['echarts', 'vue-echarts'],
          'ws-vendor': ['sockjs-client', '@stomp/stompjs'],
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
