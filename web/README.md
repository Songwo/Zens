<div align="center">

# 🎨 Campus Pulse · Web

**Vue 3 + TypeScript + Vite 的校园社区前端**

[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178c6?logo=typescript)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-7-646cff?logo=vite&logoColor=white)](https://vitejs.dev/)
[![Pinia](https://img.shields.io/badge/Pinia-3-ffd859?logo=pinia&logoColor=white)](https://pinia.vuejs.org/)
[![ECharts](https://img.shields.io/badge/ECharts-5-AA344D?logo=apacheecharts&logoColor=white)](https://echarts.apache.org/)

</div>

---

## 🚀 快速开始

```bash
npm install
npm run dev          # 开发环境：http://localhost:5173
npm run build        # 生产构建：dist/
npm run build:check  # 类型检查 + 构建
npm run preview      # 本地预览生产构建
```

开发环境的 `/api` 默认代理到 Spring Boot `:7800`；大文件上传会 `POST /common/upload/chunk/*`，由 Java 转发到 `go-media-service :8090`。

---

## 📁 项目结构

```text
src/
├── pages/              # 路由页面（含 admin/ 后台与 MePage 个人中心）
├── components/         # 通用组件（PostCard · CommentList · UserQuickCard ...）
│   ├── layout/
│   └── common/
├── api/                # Axios 接口封装（post · notification · media ...）
├── lib/                # api 实例 / 鉴权拦截器
├── router/             # Vue Router（含权限守卫 + lazy load）
├── store/              # Pinia（auth · draft · notification ...）
├── utils/              # richLink · chunkUpload · notificationRoute · cardTheme ...
├── styles/             # 全局样式 + Tailwind 入口
└── types/              # TypeScript 类型
```

---

## 🎨 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | UI 框架 |
| TypeScript | 5.9 | 类型系统 |
| Vite | 7 | 构建工具 |
| Pinia | 3 | 状态管理 |
| Vue Router | 4 | 路由（含 lazy route） |
| Element Plus | 2.13 | 基础 UI 组件 |
| Tailwind CSS | 4 | 原子化样式 |
| axios | 1.13 | HTTP 客户端 |
| ECharts + vue-echarts | 5.x | 管理后台数据可视化 |
| markdown-it + DOMPurify | — | 富文本渲染 + XSS 防护 |
| SockJS + @stomp/stompjs | — | 实时通知 |
| lucide-vue-next · vue-sonner | — | 图标 · Toast |

---

## 📤 媒体上传链路

前端上传大文件走分片：

```
chunkUpload(file)          (utils/chunkUpload.ts)
  ├─ init    → POST /common/upload/chunk/init  → 得到 uploadId
  ├─ part×N  → POST /common/upload/chunk/part  (3 并发 + 每分片 2 次重试)
  └─ merge   → POST /common/upload/chunk/merge → 返回 accessUrl
```

每个分片请求随 query 携带 `uploadId / chunkIndex / totalChunks / fileName / fileSize / mediaType / module`，body 为 `FormData` 的 `chunk` 字段。Java 层作为薄代理，把分片转给 Go `go-media-service`；Java 只在错误时通过 `MediaClientException` 透传真实的上游错误消息，不再被兜底成 “服务器繁忙”。

文件小于 `CHUNK_SIZE`（2 MB）时直接走 `/common/upload/image` 或 `/common/upload/video`，不切片。

> ℹ️ 若后续改为前端直传 Go 服务，只需拿 `/common/upload/token` 换 Upload JWT，然后把分片请求 baseURL 切到 `go-media-service`。详细契约见 [`../go-media-service/README.md`](../go-media-service/README.md)。

---

## 🧩 主要功能

- ✅ 智能推荐首页 · 板块 / 标签 / 热榜 / 趋势
- ✅ Markdown 帖子编辑（封面图、匿名、位置、富链接）
- ✅ 评论（嵌套、@、匿名、点赞）
- ✅ 个人中心（帖子 / 草稿 / 收藏 / 通知 / 浏览 / 关注）
- ✅ 后台管理（帖子 / 用户 / 举报 / 版主申请 / 缓存）
- ✅ 实时通知：WebSocket Badge + 智能跳转
- ✅ 安全账号通知聚合（设备登录 · 2FA 开关 · 登录失败爆发）
- ✅ 响应式：移动端 Tab 横向滚动 + 骨架屏 + keep-alive

---

## 🎯 设计语言

- **Discourse 变体风格**：克制、干净、现代
- **三段式布局**：左导航 + 中间内容 + 右侧智能推荐
- **卡片式信息流**：摘要、标签、热度、作者
- **双主题卡片**：用户资料 / 头像预览卡支持自定义背景

---

## 🔗 相关

- 主仓库 README：[`../README.md`](../README.md)
- 后端媒体服务：[`../go-media-service/README.md`](../go-media-service/README.md)
- 运维脚本：[`../scripts/README.md`](../scripts/README.md)

## 📄 License

MIT
