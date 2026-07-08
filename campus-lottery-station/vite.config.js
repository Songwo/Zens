import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
export default defineConfig({
    plugins: [react()],
    build: {
        outDir: "server/web",
        emptyOutDir: true,
    },
    server: {
        // 本地联调：/api 全部转发给 Go 抽奖后端(:8093)，
        // 否则 SSO start / bootstrap 会被 vite 当成 SPA 路由返回 index.html，登录按钮点了没反应。
        proxy: {
            "/api": {
                target: "http://localhost:8093",
                changeOrigin: true,
            },
        },
    },
});
