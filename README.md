<div align="center">

# Campus Pulse

**智能内容社区与趋势分析平台**

*An intelligent community platform with trend analysis, AI-assisted moderation, and real-time interactions*

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178c6?logo=typescript)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-6%2B-dc382d?logo=redis&logoColor=white)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

[在线预览](https://allinsong.top) · [后端 API 文档](#api-overview) · [快速开始](#quick-start) · [部署指南](#deployment)

</div>

---

## ✨ 项目亮点

Campus Pulse 不是一个普通的论坛系统。它以**行为驱动洞察**为核心，将内容社区、AI 辅助决策与实时热度引擎深度结合，适用于开发者交流、内容协作与趋势洞察等场景，强调可扩展的内容治理与分析能力。

| 亮点 | 说明 |
|------|------|
| 🔥 **热度引擎** | 基于浏览/点赞/评论/收藏的多维热度衰减算法，定时批量更新 + Redis 缓存双轨驱动 |
| 🤖 **AI 摘要** | 集成 DeepSeek API 对帖子自动生成摘要，支持作者一键重新生成 |
| 📊 **趋势决策** | 实时板块分布饼图、发帖趋势折线图、热词云、智能话题预测表 |
| 🔐 **多层安全** | JWT + Redis 令牌绑定 + 设备 ID 校验 + 请求签名（SHA-256）+ 二步验证（TOTP） |
| 🗂️ **内容治理** | 多级审核流程（PENDING/APPROVED/REJECTED）、举报异步工作流、版主申请机制 |
| 💬 **实时通知** | WebSocket（STOMP over SockJS）推送点赞/评论/关注/系统通知，前端实时角标更新 |
| 🎨 **个性化** | 用户资料卡片/头像预览卡片双主题系统，支持自定义背景图 |
| 📱 **响应式 + PWA** | 移动端完整适配，Tab 横向滚动，骨架屏加载，keep-alive 缓存，PWA 离线可安装 |
| 🛡️ **信任等级** | TL0-TL4 五级信任体系，行为驱动升降级，按等级解锁权限与举报加权 |
| 🪐 **子站生态** | SSO 统一登录 + HMAC 内部 API 串联积分商城 / 抽奖站 / CDK 空投站 / 导航站，积分双向流转、事件账本回流 |

---

## 🖼️ 界面预览

> 首页信息流 · 帖子详情 · 趋势分析 · 管理后台

```
┌─────────────────────────────────────────────────────┐
│  🏠 首页        热门话题 · 推荐帖子 · 右侧热榜        │
│  📰 板块        分区浏览 · 版主标识 · 置顶帖          │
│  🔍 搜索        全文检索 · 标签筛选 · 排序切换        │
│  🔥 热榜        时间维度切换 · 热度排名               │
│  📈 趋势        ECharts 图表 · 智能预测 · 实时指标    │
│  👤 个人中心    帖子/草稿/收藏/通知/浏览历史/关注      │
│  ⚙️  管理后台   用户/帖子/举报/申请/缓存管理           │
└─────────────────────────────────────────────────────┘
```

---

## 🏗️ 系统架构

Campus Pulse 采用 **Java 主应用 + Go 媒体服务** 双后端拆分，Java 专注业务编排，Go 专注高并发媒体上传与治理；两端通过 Upload JWT / Service Token 解耦通信。

```
┌──────────────────────────────────────────────────────────────┐
│                      Browser / Client                        │
│  Vue 3 + Vite + Pinia + Element Plus + ECharts               │
└─────────────────────────┬────────────────────────────────────┘
                          │ HTTP / WebSocket
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                    Nginx (反向代理)                           │
│  /api/*              →  Spring Boot :7800                    │
│  /api/media/* · /file→  Go media service :8090               │
│  /*                  →  Vue dist 静态资源                     │
└─────────┬─────────────────────────────┬──────────────────────┘
          │                             │
┌─────────▼───────────┐       ┌─────────▼──────────────────────┐
│   Spring Boot 3.5   │       │   Go Media Service (Gin 1.10)  │
│  ┌───────────────┐  │       │  ┌───────────────────────────┐ │
│  │ Security      │  │       │  │ Upload / Chunk / Merge    │ │
│  │ Controller    │  │ HTTP  │  │ File access · Admin API   │ │
│  │ Service       │──┼──────►│  │ Panel (HTML)              │ │
│  │ MyBatis-Plus  │  │       │  │ Prometheus · pprof        │ │
│  │ Scheduled     │  │       │  └───────────────────────────┘ │
│  └───────────────┘  │       │   SQLite(WAL) + 本地磁盘存储    │
└──────┬──────┬───────┘       │   可切 OSS / COS / S3           │
       │      │               └────────────────────────────────┘
┌──────▼──┐ ┌─▼────────┐
│ MySQL 8 │ │ Redis 6  │
│ 25 张表 │ │ Token    │
│ 全文索引│ │ Cache    │
└─────────┘ └──────────┘
```

> 📎 媒体子系统的详细文档见 [`go-media-service/README.md`](go-media-service/README.md)。

---

## 🧩 核心功能模块

### 📝 内容系统
- **帖子**：Markdown 编辑、封面图、多图上传、匿名发布、位置标注
- **草稿**：本地 + 服务端双端草稿保存，支持断点续写
- **评论**：无限层级嵌套、@回复、匿名评论、点赞
- **富链接**：GitHub 仓库/Issue/PR/Commit 自动解析为卡片，外部链接预览卡
- **引用合并**：连续 Markdown 引用块自动合并为单一 blockquote

### 🔥 热度与趋势
- 热度公式：`score = (views×w1 + likes×w2 + comments×w3 + collects×w4) × decay(t)`
- 定时任务每 15 分钟批量写回 MySQL，Redis 热榜 TTL 抖动防缓存雪崩
- 趋势统计表 `trend_stats` 按天聚合，支持板块/标签/关键词三维度
- 智能预测：对比最近 7 天 vs 前 7 天增长率，输出结构化话题预测

### 👮 内容治理
- **审核流程**：发帖 → PENDING → APPROVED/REJECTED（含打回原因通知作者）
- **举报工作流**：异步处理队列，状态机（待处理/排队/处理中/已处理/忽略/打回）
- **版主系统**：用户申请 → 管理员审核 → 版主获得板块管理权限
- **管理后台**：帖子/用户/举报/版主申请/缓存全功能管理界面

### 🔐 认证与安全
- 密码登录 / 邮箱 OTP 登录 / GitHub OAuth2 登录
- JWT access token（默认 2h）+ refresh token（默认 14d），Redis 令牌绑定，可通过环境变量覆盖
- 设备 ID（X-Device-Id）绑定，默认允许多设备并存，可通过 `AUTH_SESSION_SINGLE_DEVICE` 切换成单设备策略
- 变更操作 SHA-256 请求签名防重放，前端在 token 刷新后会自动重签名重试
- TOTP 二步验证（Google Authenticator 兼容）
- 慢 SQL 拦截器、请求耗时 Filter

### 📡 实时功能
- WebSocket（STOMP over SockJS）双向通道
- 通知类型：点赞、收藏、评论、@提及、关注、审核结果、系统公告
- 前端 Badge 实时更新，通知跳转智能路由（follow → 用户主页 / 其他 → 帖子详情）

### 🪐 子站生态（Zens 一站式）
Campus Pulse 作为 **Zens 主站**，对外提供 SSO 统一登录与 HMAC 签名的内部 API，串联多个独立子站，形成积分驱动的一站式生态：

- **SSO 统一登录**：OAuth2 授权码风格流程，`sys_sso_client` 注册应用，`trusted` 标记第一方应用自动授权跳过同意页，支持单点登出（SLO）
- **积分商城（zdc-shop）**：Next.js 子站，全量融入主站登录与积分体系，下单/退款双向增减 `sys_user.points`
- **抽奖站（campus-lottery-station）**：SSO 登录 + 开奖事件回流主站，帖子可挂载抽奖规则
- **CDK 空投站（cdk-airdrop-station）**：SSO 登录领取兑换码
- **导航站（zens-nav）**：生态入口聚合
- **事件账本**：`sys_subsite_event` 统一记录子站事件（积分变动、开奖、兑换），幂等去重 + 多维索引，主站可审计与回流
- **内部 API 安全**：`InternalServiceFilter` 多密钥白名单 + HMAC 签名校验，子站与主站间服务端通信不走用户 token

---

## 🗄️ 数据库设计

共 **28 张核心表**，结构文件：[`src/main/resources/sql/campus_pulse_schema.sql`](src/main/resources/sql/campus_pulse_schema.sql)

| 表名 | 说明 |
|------|------|
| `sys_user` | 用户表，含 GitHub 登录、2FA、卡片主题 |
| `sys_tag` | 标签表 |
| `sys_user_tag_relation` | 用户标签兴趣关系表 |
| `sections` | 板块表 |
| `sys_post` | 帖子表，全文索引 `ft_post_search`，审核状态，置顶字段 |
| `sys_post_media` | 帖子媒体关系表 |
| `sys_media_file` | 媒体文件元数据表 |
| `sys_post_like` | 点赞表 |
| `sys_post_collect` | 收藏表 |
| `sys_comment` | 评论表，支持嵌套回复、软删除 |
| `comment_likes` | 评论点赞表 |
| `comment_collects` | 评论收藏表 |
| `short_links` | 短链接映射表 |
| `sys_view_log` | 浏览日志，用于热度计算 |
| `sys_level_exp_log` | 等级经验日志 |
| `notifications` | 通知表 |
| `direct_messages` | 私信表 |
| `sys_report` | 举报表，异步工作流状态机 |
| `moderator_applications` | 版主申请表 |
| `follows` | 用户关注关系表 |
| `post_heat_snapshots` | 热度快照表 |
| `trend_stats` | 趋势统计表 |
| `invite_codes` | 邀请码表 |
| `sys_sso_client` | SSO 应用注册表，含第一方可信（`trusted`）自动授权 |
| `sys_subsite_event` | 子站事件账本，子站行为（开奖/兑换等）回流主站，幂等 `event_id` |
| `sys_trust_event` | 信任等级变更事件日志，记录升降级原因 |
| `sys_changelog` | 发展历程 / 版本日志表 |

---

## 📦 技术栈

### 后端

| 依赖 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行时 |
| Spring Boot | 3.5 | 主框架 |
| Spring Security | 6.x | 认证授权 |
| MyBatis-Plus | 3.5.7 | ORM |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.0+ | 缓存 / Token 存储 |
| JJWT | 0.12 | JWT 生成与验证 |
| WebSocket + STOMP | — | 实时推送 |
| DeepSeek API | — | AI 摘要生成 |
| Actuator + Prometheus | — | 监控指标暴露 |
| HikariCP | — | 连接池 |

### 媒体子系统（Go）

| 依赖 | 版本 | 用途 |
|------|------|------|
| Go | 1.22 | 运行时 |
| Gin | 1.10 | HTTP 框架 |
| modernc.org/sqlite | 1.35 | 纯 Go SQLite 驱动（无 CGO） |
| zerolog | 1.33 | 结构化日志 |
| golang-jwt/jwt v5 | 5.2 | Upload / Admin JWT |
| prometheus/client_golang | 1.20 | `/metrics` 暴露 |
| gopsutil | 4.25 | 系统资源监控 |
| golang.org/x/time | 0.10 | 令牌桶限流 |

### 前端

| 依赖 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | UI 框架 |
| TypeScript | 5.9 | 类型系统 |
| Vite | 7 | 构建工具 |
| Pinia | 3 | 状态管理 |
| Element Plus | 2.13 | UI 组件库 |
| Vue Router | 4 | 路由 |
| Axios | 1.13 | HTTP 客户端 |
| ECharts + vue-echarts | 5.x | 数据可视化图表 |
| markdown-it | — | Markdown 渲染 |
| DOMPurify | — | XSS 防护 |
| SockJS + @stomp/stompjs | — | WebSocket 客户端 |

---

## 📁 目录结构

```text
campus-pulse/
├── src/
│   ├── main/java/com/campus/trend/campus_pulse/
│   │   ├── config/          # Security、Redis、Async、WebSocket、MyBatis 配置
│   │   ├── controller/      # REST 控制器（Auth/Post/Comment/User/Trend/Report...）
│   │   ├── dto/             # 请求 DTO 与响应 VO
│   │   ├── entity/          # 数据实体（MyBatis-Plus）
│   │   ├── filter/          # JWT 过滤器、请求耗时过滤器
│   │   ├── mapper/          # MyBatis-Plus Mapper 接口
│   │   ├── scheduled/       # 定时任务（热度衰减、趋势统计）
│   │   ├── service/         # 业务接口
│   │   │   └── impl/        # 业务实现
│   │   └── utils/           # JWT、权限、IP、时间范围工具
│   ├── main/resources/
│   │   ├── application.yml          # 基础配置 / 本地开发默认配置
│   │   ├── application-prod.yml     # 生产环境配置
│   │   └── sql/
│   │       └── campus_pulse_schema.sql  # 唯一数据库初始化脚本
│   └── test/java/                   # Controller 集成测试
├── web/                             # Vue 3 前端
│   ├── src/
│   │   ├── api/             # Axios 接口封装
│   │   ├── components/      # 通用组件（PostCard、CommentList、UserQuickCard...）
│   │   ├── composables/     # 组合式函数
│   │   ├── layouts/         # MainLayout、AdminLayout
│   │   ├── pages/           # 页面组件
│   │   │   └── admin/       # 管理后台页面
│   │   ├── router/          # 路由配置（含权限守卫）
│   │   ├── store/           # Pinia Store
│   │   ├── types/           # TypeScript 类型定义
│   │   └── utils/           # 工具函数（richLink、notificationRoute、cardTheme...）
│   └── vite.config.ts
├── go-media-service/                 # 独立 Go 媒体服务（见子目录 README）
│   ├── cmd/media-service/            # 进程入口
│   ├── internal/
│   │   ├── api/                      # upload · file · admin · health
│   │   ├── service/                  # 上传 / 合并 / 秒传 / 配置 / 审计
│   │   ├── repository/sqlite.go      # SQLite(WAL) 元数据 CRUD
│   │   ├── storage/                  # 存储驱动（local → OSS/COS/S3）
│   │   └── middleware/ · metrics/ · panel/
│   ├── web/ (templates + static)     # 内嵌管理面板
│   ├── deployments/ (nginx · systemd)
│   └── config.yaml · Dockerfile · docker-compose.yml
├── zdc-shop/                         # 积分商城子站（Next.js + Prisma）
├── campus-lottery-station/           # 抽奖站子站（React + Go）
├── cdk-airdrop-station/              # CDK 空投站（React + Go + PostgreSQL）
├── docs/
│   └── PROJECTS_AND_DOCKER_DEPLOY.md # 项目清单与 Docker 部署总览
├── deploy-subsites.sh                # 子站 Docker 一键部署脚本（不重启主站）
└── scripts/
    ├── deploy.sh            # 主站 Jar 部署脚本
    ├── rollback.sh          # 回滚脚本
    └── backup.sh            # 数据库备份脚本
```

---

## 🚀 快速开始 {#quick-start}

### 环境要求

| 工具 | 最低版本 |
|------|----------|
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |

### 1. 克隆项目

```bash
git clone https://github.com/your-username/campus-pulse.git
cd campus-pulse
```

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/sql/campus_pulse_schema.sql
```

> 脚本会自动创建 `campus_pulse` 数据库、所有表、索引及基础板块种子数据。

### 3. 配置环境变量

复制本地开发示例配置：

```bash
cp .env.example .env.local
```

编辑 `.env.local`，填入以下必填项：

```env
# 数据库
DB_URL=jdbc:mysql://localhost:3306/campus_pulse?useUnicode=true&characterEncoding=utf8mb4
DB_USERNAME=root
DB_PASSWORD=123456

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT 密钥（至少 64 字符的随机字符串）
JWT_SECRET=your_jwt_secret_at_least_64_characters_long

# DeepSeek API（AI 摘要功能，可选）
DEEPSEEK_API_KEY=sk-xxxx

# 邮件服务（OTP 登录，可选）
MAIL_HOST=smtp.example.com
MAIL_USERNAME=noreply@example.com
MAIL_PASSWORD=your_mail_password

# GitHub OAuth（第三方登录，可选）
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret

# Cloudflare Turnstile（登录人机验证，生产建议启用）
TURNSTILE_ENABLED=false
TURNSTILE_SITE_KEY=REPLACE_WITH_TURNSTILE_SITE_KEY
TURNSTILE_SECRET_KEY=REPLACE_WITH_TURNSTILE_SECRET_KEY
TURNSTILE_ALLOW_LOCAL_PREVIEW_SKIP=false
```

生产环境请单独使用 `.env.production`。`application-prod.yml` 现在只会读取 `.env.prod` / `.env.production`，本地默认不会再误吃生产数据库账号。

### 4. 启动后端

```bash
# 开发模式
mvn spring-boot:run

# 或先构建再运行
mvn clean package -DskipTests
java -jar target/campus-pulse-0.0.1-SNAPSHOT.jar
```

后端默认运行在：**http://localhost:7800**

### 5. 启动前端

```bash
cd web
npm install
npm run dev
```

前端开发服务运行在：**http://localhost:5173**（已配置 `/api` 代理至后端 7800 端口）

### 6. 启动媒体服务（Go）

```bash
cd go-media-service
go mod tidy
go run ./cmd/media-service
```

媒体服务运行在：**http://localhost:8090**，管理面板 `/panel/login`（默认 `admin` / `admin123456`）。配置与 API 参考：[`go-media-service/README.md`](go-media-service/README.md)。

---

## 🏭 生产部署 {#deployment}

### 方式一：手动部署（推荐）

**构建前端：**

```bash
cd web
npm run build
# dist/ 目录即为静态产物
```

**构建后端：**

```bash
mvn clean package -DskipTests -P prod
```

**Nginx 配置示例：**

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态资源
    root /var/www/campus-pulse/dist;
    index index.html;

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:7800/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 上传文件代理
    location /uploads/ {
        proxy_pass http://127.0.0.1:7800/uploads/;
    }

    # WebSocket 代理
    location /api/ws {
        proxy_pass http://127.0.0.1:7800/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Vue Router history 模式回退
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**使用主站 Jar 部署脚本：**

```bash
bash scripts/deploy.sh target/campus-pulse-0.0.1-SNAPSHOT.jar
```

### 方式二：主站脚本运维

```bash
# 部署主站 Jar
bash scripts/deploy.sh target/campus-pulse-0.0.1-SNAPSHOT.jar

# 回滚到上一版本
bash scripts/rollback.sh

# 数据库备份
bash scripts/backup.sh
```

### 方式三：子站 Docker 一键部署

主站已经在线时，子站可以用根目录脚本统一部署。该脚本只读取主站配置，不会重启主站；它会自动补齐 `zens_shop` 数据库、主站 `sys_sso_client` SSO 应用记录，生成三个子站 `.env`，并重建积分商城 / 抽奖站 / CDK 空投站容器。

```bash
cd /www/wwwroot/campus-pulse
chmod +x deploy-subsites.sh

# 读取生产配置，部署三个子站
MAIN_ENV_FILE=.env.production \
MAIN_SITE_BACKEND_URL=https://allinsong.top \
bash deploy-subsites.sh
```

默认域名与宝塔反代目标：

| 子站 | 默认域名 | 本机反代目标 |
|------|----------|--------------|
| 积分商城 | `https://shop.allinsong.top` | `http://127.0.0.1:3000` |
| 抽奖站 | `https://lottery.allinsong.top` | `http://127.0.0.1:8093` |
| CDK 空投站 | `https://cdk.allinsong.top` | `http://127.0.0.1:8088` |

常用覆盖项：

```bash
# 指定子域名 / 端口
SHOP_DOMAIN=shop.allinsong.top \
LOTTERY_DOMAIN=lottery.allinsong.top \
CDK_DOMAIN=cdk.allinsong.top \
SHOP_PORT=3000 \
LOTTERY_PORT=8093 \
CDK_AIRDROP_PORT=8088 \
bash deploy-subsites.sh

# 首次部署后顺便初始化积分商城种子数据
RUN_SHOP_SEED=true bash deploy-subsites.sh

# 只生成配置并启动容器，不修数据库/SSO
SKIP_DB=true bash deploy-subsites.sh
```

更多子站清单、环境变量和 Docker 说明见 [`docs/PROJECTS_AND_DOCKER_DEPLOY.md`](docs/PROJECTS_AND_DOCKER_DEPLOY.md)。

---

## 🔌 API Overview {#api-overview}

| 模块 | 前缀 | 说明 |
|------|------|------|
| 认证 | `/auth` | 登录/注册/OTP/GitHub/2FA/Token 刷新 |
| 帖子 | `/post` | CRUD、搜索、点赞、收藏、审核、置顶 |
| 评论 | `/comment` | 树形评论、点赞 |
| 用户 | `/user` | 个人资料、关注/粉丝、偏好设置 |
| 通知 | `/notification` | 列表、已读、批量操作 |
| 举报 | `/report` | 提交举报、工作流处理 |
| 趋势 | `/trend-stat` | 发帖趋势、板块分布、热词云、预测 |
| 热榜 | `/heat-rank` | Top N 热帖，支持时间维度 |
| 版主申请 | `/moderator-application` | 申请、审核 |
| 上传（兼容） | `/common/upload/*` | Java 代理层：图片 / 视频 / 分片，透传 Go |
| 媒体（Go 直达） | `/api/upload/*` · `/api/file/:id` · `/api/admin/*` | 走 `go-media-service` :8090（见子 README） |
| 统计 | `/stats` | 站点总览数据 |
| WebSocket | `/ws` | STOMP 实时通知 |

---

## 🧪 测试

```bash
# 运行核心接口测试
mvn test -Dtest=PostControllerValidationTest,AuthControllerValidationTest,UploadControllerValidationTest,NotificationControllerBatchTest

# 编译检查（不运行测试）
mvn -q -DskipTests compile

# 前端类型检查 + 构建验证
cd web && npm run build:check
```

---

## ⚙️ 关键配置说明

### 后端性能配置（`application.yml`）

```yaml
server:
  port: 7800
  compression:
    enabled: true          # 开启 Gzip 压缩
  tomcat:
    threads:
      max: 200             # 最大线程数
    max-connections: 8192  # 最大连接数
```

### Redis 缓存策略

- Token 存储：`auth:access:{userId}:{sessionId}` / `auth:refresh:{userId}:{sessionId}`
- 热榜缓存：TTL 随机抖动 ±30s，防缓存雪崩
- 空值缓存：搜索结果空列表缓存 60s，防缓存穿透

### JWT 配置

- Access Token 有效期：7 小时（记住我模式）
- Refresh Token 有效期：30 天
- 滑动续期：access token 剩余时间 < 2 分钟时自动在响应头续期
- 响应头：`Authorization`、`X-Access-Token`、`X-Access-Token-Expires-In`

### Cloudflare Turnstile 人机验证

登录接口 `POST /api/auth/login` 会强制走 Cloudflare Turnstile 校验，防御撞库与机器人注册。实现链路：前端 `LoginWizard` 挂载 Turnstile widget → 提交时携带 `cfTurnstileResponse` → 后端 `TurnstileServiceImpl` 调用 `siteverify` 校验 → 通过后再走正常登录逻辑。

**后端配置（`application.yml` 下的 `cloudflare.turnstile`）**

| 键 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `enabled` | `TURNSTILE_ENABLED` | `true` | 关掉则直接跳过校验，仅建议本地调试使用 |
| `site-key` | `TURNSTILE_SITE_KEY` | 占位示例 | 公钥，给前端 widget 用 |
| `secret-key` | `TURNSTILE_SECRET_KEY` | 占位示例 | 私钥，后端调 `siteverify` 用；⚠️ 不要泄漏 |
| `allow-local-preview-skip` | `TURNSTILE_ALLOW_LOCAL_PREVIEW_SKIP` | `true` | 允许本地预览（localhost / 127.0.0.1 / 内网 Referer）跳过校验，便于联调；生产必须关 |
| `verify-url` | `TURNSTILE_VERIFY_URL` | `https://challenges.cloudflare.com/turnstile/v0/siteverify` | 官方校验地址，一般不改 |
| `connect-timeout-seconds` / `read-timeout-seconds` | 同名变量 | `5` / `8` | 调 `siteverify` 超时 |
| `proxy-host` / `proxy-port` | 同名变量 | 空 | 出网需要代理时配置 |

**前端配置（`web/.env.*`）**

```env
VITE_TURNSTILE_ENABLED=true
VITE_TURNSTILE_SITE_KEY=0x4AAAA...   # 与后端 site-key 对应，可以不同环境配不同 key
```

`VITE_TURNSTILE_ENABLED` 为假值时前端不会渲染 widget；为真但未填 `VITE_TURNSTILE_SITE_KEY` 时，登录按钮会提示"人机验证暂不可用"。

**快速获取 Key**：登录 Cloudflare Dashboard → Turnstile → Add site → 选 Managed / Invisible → 填入部署域名（生产域 + 预览域），拿到 Site Key 与 Secret Key。本地调试域建议选 Invisible 以减少 UI 干扰。

**常见失败码**（`TurnstileServiceImpl.resolveFailureMessage`）：

- `invalid-input-secret` / `missing-input-secret`：Secret Key 没配或配错 → 提示"人机验证配置错误"
- `timeout-or-duplicate`：token 已使用或超过 5 分钟 → 提示"人机验证已过期"
- `missing-input-response` / `invalid-input-response`：前端没拿到或传错了 token → 提示"人机验证无效"

**临时关闭**：生产临时排障时把 `TURNSTILE_ENABLED=false` 即可放行所有登录请求，恢复后记得改回。

---

## 🔒 安全说明

- JWT 令牌与 Redis 强绑定，截获的 token 在原设备登出后立即失效
- `X-Device-Id` 设备绑定，同一 token 无法在不同设备使用
- 变更操作（POST/PUT/DELETE）附带 `X-Request-Signature`（SHA-256 HMAC），防重放攻击
- 上传接口：MIME 类型白名单 + 扩展名一致性校验
- 全局异常处理器，不向外泄露堆栈信息
- 慢 SQL 拦截器（> 500ms 告警），请求耗时 Filter 记录

---

## 📊 监控

```bash
# 健康检查
GET /actuator/health

# Prometheus 指标（需配置 Prometheus + Grafana）
GET /actuator/prometheus
```

---

## 🗺️ Roadmap

- [x] 消息已读回执
- [x] 帖子版本历史（diff 展示）
- [x] 更丰富的用户等级权益体系（信任等级 TL0-TL4）
- [x] 移动端 PWA 支持
- [x] 子站生态接入（积分商城 / 抽奖站 / CDK 空投站 / 导航站）
- [x] 子站 Docker 一键部署（商城 / 抽奖 / CDK）
- [ ] Docker Compose 全栈一键启动
- [ ] 管理后台数据大屏
- [ ] 元宇宙空间 / 积分福利体系深化

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。贡献前请：

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交变更：`git commit -m 'feat: add your feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

**数据库变更约定**：每次修改实体字段/索引，必须同步回写 `campus_pulse_schema.sql`，不允许新增散落 migration 文件。

---

## 📄 License

MIT License © 2026 Campus Pulse Contributors

---

<div align="center">

如果这个项目对你有帮助，欢迎点个 ⭐ Star 支持！

</div>
