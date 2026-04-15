<div align="center">

# 🎓 Campus Pulse

**校园智能内容社区与趋势决策平台**

*A campus-grade intelligent community platform with trend analysis, AI-assisted moderation, and real-time interactions*

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178c6?logo=typescript)](https://www.typescriptlang.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-6%2B-dc382d?logo=redis&logoColor=white)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

[在线演示 Demo](https://zenslt.allinsong.top) · [后端 API 文档](#api-overview) · [快速开始](#quick-start) · [部署指南](#deployment)

</div>

---

## ✨ 项目亮点

Campus Pulse 不是一个普通的论坛系统。它以**行为驱动洞察**为核心，将传统校园社区与 AI 辅助决策、实时热度引擎深度结合，面向高校师生打造的全功能内容治理与趋势分析平台。

| 亮点 | 说明 |
|------|------|
| 🔥 **热度引擎** | 基于浏览/点赞/评论/收藏的多维热度衰减算法，定时批量更新 + Redis 缓存双轨驱动 |
| 🤖 **AI 摘要** | 集成 DeepSeek API 对帖子自动生成摘要，支持作者一键重新生成 |
| 📊 **趋势决策** | 实时板块分布饼图、发帖趋势折线图、热词云、智能话题预测表 |
| 🔐 **多层安全** | JWT + Redis 令牌绑定 + 设备 ID 校验 + 请求签名（SHA-256）+ 二步验证（TOTP） |
| 🗂️ **内容治理** | 多级审核流程（PENDING/APPROVED/REJECTED）、举报异步工作流、版主申请机制 |
| 💬 **实时通知** | WebSocket（STOMP over SockJS）推送点赞/评论/关注/系统通知，前端实时角标更新 |
| 🎨 **个性化** | 用户资料卡片/头像预览卡片双主题系统，支持自定义背景图 |
| 📱 **响应式** | 移动端完整适配，Tab 横向滚动，骨架屏加载，路由组件 keep-alive 缓存 |

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

```
┌──────────────────────────────────────────────────────────────┐
│                      Browser / Client                        │
│  Vue 3 + Vite + Pinia + Element Plus + ECharts               │
└─────────────────────────┬────────────────────────────────────┘
                          │ HTTP / WebSocket
                          ▼
┌──────────────────────────────────────────────────────────────┐
│                    Nginx (反向代理)                           │
│  /api/*  →  Spring Boot :7800                                │
│  /*      →  Vue dist 静态资源                                 │
└─────────────────────────┬────────────────────────────────────┘
                          │
              ┌───────────▼───────────┐
              │   Spring Boot 3.5     │
              │  ┌─────────────────┐  │
              │  │  Security Layer │  │  JWT Filter + Device ID
              │  │  Controller     │  │  REST API + WS Endpoint
              │  │  Service        │  │  Business Logic + AI
              │  │  MyBatis-Plus   │  │  ORM + 动态 SQL
              │  │  Scheduled      │  │  热度衰减 + 趋势统计
              │  └─────────────────┘  │
              └───────┬───────┬───────┘
                      │       │
             ┌────────▼──┐ ┌──▼────────┐
             │  MySQL 8  │ │  Redis 6  │
             │  17 张表  │ │  Token    │
             │  全文索引 │ │  Cache    │
             └───────────┘ └───────────┘
```

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
- JWT access token（7h）+ refresh token（30d），Redis 令牌绑定
- 设备 ID（X-Device-Id）绑定，换设备自动失效
- 变更操作 SHA-256 请求签名防重放
- TOTP 二步验证（Google Authenticator 兼容）
- 慢 SQL 拦截器、请求耗时 Filter

### 📡 实时功能
- WebSocket（STOMP over SockJS）双向通道
- 通知类型：点赞、收藏、评论、@提及、关注、审核结果、系统公告
- 前端 Badge 实时更新，通知跳转智能路由（follow → 用户主页 / 其他 → 帖子详情）

---

## 🗄️ 数据库设计

共 **17 张核心表**，结构文件：[`src/main/resources/sql/campus_pulse_schema.sql`](src/main/resources/sql/campus_pulse_schema.sql)

| 表名 | 说明 |
|------|------|
| `sys_user` | 用户表，含 GitHub 登录、2FA、卡片主题 |
| `sys_post` | 帖子表，全文索引 `ft_post_search`，审核状态，置顶字段 |
| `sys_comment` | 评论表，支持嵌套回复 |
| `sys_post_like` | 点赞表 |
| `sys_post_collect` | 收藏表 |
| `sys_view_log` | 浏览日志，用于热度计算 |
| `sys_tag` | 标签表 |
| `sys_report` | 举报表，异步工作流状态机 |
| `sys_level_exp_log` | 等级经验日志 |
| `notifications` | 通知表 |
| `direct_messages` | 私信表 |
| `sections` | 板块表 |
| `moderator_applications` | 版主申请表 |
| `user_follows` | 关注关系表 |
| `comment_likes` | 评论点赞表 |
| `post_heat_snapshots` | 热度快照表 |
| `trend_stats` | 趋势统计表 |

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
│   │   ├── application.yml          # 开发环境配置
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
└── scripts/
    ├── deploy.sh            # 一键部署脚本
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

复制示例配置：

```bash
cp .env.example .env
```

编辑 `.env`，填入以下必填项：

```env
# 数据库
DB_URL=jdbc:mysql://localhost:3306/campus_pulse?useUnicode=true&characterEncoding=utf8mb4
DB_USERNAME=root
DB_PASSWORD=your_password

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
```

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

**使用部署脚本：**

```bash
bash scripts/deploy.sh
```

### 方式二：脚本一键部署

```bash
# 部署
bash scripts/deploy.sh

# 回滚到上一版本
bash scripts/rollback.sh

# 数据库备份
bash scripts/backup.sh
```

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
| 上传 | `/upload` | 图片/视频上传，MIME 白名单校验 |
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

- [ ] Docker Compose 一键启动
- [ ] 消息已读回执
- [ ] 帖子版本历史（diff 展示）
- [ ] 更丰富的用户等级权益体系
- [ ] 移动端 PWA 支持
- [ ] 管理后台数据大屏

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
