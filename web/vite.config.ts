import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

const rootDir = path.resolve(__dirname)

// Song：构建配置参考链接
export default defineConfig({
  root: rootDir,
  plugins: [
    vue(),
    // Element Plus 组件与样式均按需拆分；消息、通知、Loading 等服务型样式在 main.ts 显式引入。
    // 指令(v-loading)在 main.ts 全局注册，故 directives:false；
    // dirs:[] 关闭对 src/components 的扫描，只按需解析 Element Plus，不接管项目自有组件。
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      directives: false,
      dirs: [],
      dts: 'src/components.d.ts',
    }),
    // Song：PWA —— Workbox generateSW 自动生成 Service Worker 与 manifest。
    // Service Worker 由 main.ts 仅在规范域名上手动注册，避免 apex 域名请求 registerSW.js 被 Cloudflare 403。
    // 安全要点：生产前端与后端 API 同源(allinsong.top)，SW 作用域覆盖全站，因此：
    //   1) navigateFallbackDenylist 排除 /api、/ws、/uploads、/static，避免 SPA 回退劫持后端路由；
    //   2) /api 绝不进 runtime 缓存（请求带 token，CacheFirst 会串号/返回过期数据）；
    //   3) 仅对 /uploads、/static 静态资源做 StaleWhileRevalidate，让看过的图片离线可见。
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: null,
      manifestFilename: 'manifest.json',
      includeAssets: ['logo.png', 'logo-horizontal.png', 'robots.txt'],
      manifest: {
        name: 'Zens 开放社区',
        short_name: 'Zens',
        description: 'Zens 是一个开放的兴趣与知识社区，欢迎分享经验、作品、观点与真实生活，找到值得交流的人和内容。',
        lang: 'zh-CN',
        dir: 'ltr',
        theme_color: '#f4b400',
        background_color: '#f8fafc',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        scope: '/',
        icons: [
          { src: 'icons/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'icons/pwa-maskable-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // 只预缓存 App Shell 与首页关键块。编辑器、图表、代码高亮语言包等重型异步块改为按需缓存，
        // 避免移动端首次安装/更新 SW 时一次性下载数 MB 的非首屏资源。
        globPatterns: [
          'index.html',
          // manifest 图标与 includeAssets 会由插件单独加入；这里不再通配全部图片，
          // 避免安装 SW 时下载用户尚未看到的媒体资源。
          'assets/index-*.{js,css}',
          'assets/MainLayout-*.{js,css}',
          'assets/HomePage-*.{js,css}',
          'assets/TopicList-*.{js,css}',
        ],
        // SPA 导航回退到 index.html，但绝不劫持后端路由
        navigateFallback: 'index.html',
        navigateFallbackDenylist: [
          /^\/api\//,
          /^\/ws\b/,
          /^\/uploads\//,
          /^\/static\//,
        ],
        cleanupOutdatedCaches: true,
        clientsClaim: true,
        runtimeCaching: [
          {
            // 构建产物都带内容 hash，首次访问对应功能后缓存，后续直接复用。
            urlPattern: ({ url }) =>
              /^\/assets\/.+\.(?:js|css)$/.test(url.pathname),
            handler: 'CacheFirst',
            options: {
              cacheName: 'cp-build-assets',
              expiration: { maxEntries: 96, maxAgeSeconds: 60 * 60 * 24 * 14 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // 上传文件名由服务端/R2 唯一生成，资源 URL 对应不可变内容。
            // CacheFirst 才能在页面往返时完全避免 StaleWhileRevalidate 的后台重复请求。
            urlPattern: ({ url }) =>
              url.pathname.startsWith('/uploads/')
              || url.pathname.startsWith('/static/')
              || url.pathname.startsWith('/official/')
              || url.hostname === 'media.allinsong.top',
            handler: 'CacheFirst',
            options: {
              cacheName: 'cp-media',
              expiration: { maxEntries: 300, maxAgeSeconds: 60 * 60 * 24 * 30 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
      devOptions: {
        // 开发环境不启用 SW，避免本地缓存干扰热更新
        enabled: false,
      },
    }),
  ],
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': path.resolve(rootDir, 'src'),
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

          // Element Plus 与 Vue 依赖链存在循环引用，强制拆 vendor 在生产压缩后可能触发 TDZ。
          // 这里交给 Rollup 自动排序，只保留编辑器/图表/高亮等按需加载重型依赖的分包。
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
          if (id.includes('/sockjs-client/') || id.includes('/@stomp/stompjs/')) {
            return 'ws-vendor'
          }
          // 仅把 echarts 本体钉进 chart-vendor；不要 vue-echarts。
          // vue-echarts 静态依赖 vue，强制分块会把共享的 Vue runtime co-locate 进
          // chart-vendor，导致入口需 Vue 而静态依赖此 chunk，645k echarts 被白拖上首屏。
          // echarts 本体不依赖 vue，仅被两个异步组件引用，自然落进异步链；
          // vue-echarts 交还 Rollup 自动放置，其 Vue 依赖留在入口。
          if (id.includes('/echarts/')) {
            return 'chart-vendor'
          }
          if (id.includes('/axios/') || id.includes('/date-fns/')) {
            return 'app-utils-vendor'
          }
          return undefined
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
