# Campus Pulse 前端项目

基于 Vue 3 + TypeScript + Vite 构建的现代化校园论坛前端应用。

## 🚀 快速开始

### 安装依赖
```bash
npm install
```

### 开发环境
```bash
npm run dev
```

访问：http://localhost:5173

### 生产构建
```bash
npm run build
```

### 预览生产构建
```bash
npm run preview
```

## 📁 项目结构

```
src/
├── pages/              # 页面组件
├── components/         # 可复用组件
│   ├── layout/        # 布局组件
│   └── common/        # 通用组件
├── api/               # API 接口
├── store/             # Pinia 状态管理
├── router/            # 路由配置
├── styles/            # 全局样式
├── utils/             # 工具函数
└── types/             # TypeScript 类型定义
```

## 🎨 技术栈

- **框架**: Vue 3 + TypeScript
- **构建工具**: Vite
- **路由**: Vue Router 4
- **状态管理**: Pinia
- **样式**: Tailwind CSS 4.x
- **图标**: lucide-vue-next
- **HTTP 客户端**: axios
- **通知**: vue-sonner
- **Markdown**: markdown-it

## 📝 主要功能

- ✅ 智能推荐首页
- ✅ 板块/标签浏览
- ✅ 帖子发布（Markdown 编辑器）
- ✅ 帖子详情（评论、点赞、收藏）
- ✅ 全局搜索
- ✅ 个人中心
- ✅ 用户认证（登录/注册）
- ✅ 后台管理

## 🎯 设计特点

- **Discourse 变体风格**: 技术论坛风格，克制、干净、现代
- **三段式布局**: 左侧导航 + 中间内容 + 右侧智能推荐
- **卡片式设计**: 带摘要、标签、热度信息的帖子卡片
- **智能推荐**: 右侧栏展示推荐理由、热门内容、热门标签
- **响应式设计**: 适配移动端和桌面端

## 📄 License

MIT
