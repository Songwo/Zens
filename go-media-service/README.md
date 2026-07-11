<div align="center">

# 🎬 Campus Pulse · Media Service

**高并发、可治理的媒体上传微服务**

*A Go-powered media ingestion microservice for Campus Pulse — chunked upload, resumable sessions, SHA-256 de-duplication, admin console, and first-class observability.*

[![Go](https://img.shields.io/badge/Go-1.22-00ADD8?logo=go&logoColor=white)](https://go.dev/)
[![Gin](https://img.shields.io/badge/Gin-v1.10-00ACD7?logo=gin)](https://gin-gonic.com/)
[![SQLite](https://img.shields.io/badge/SQLite-WAL-003B57?logo=sqlite&logoColor=white)](https://www.sqlite.org/)
[![Prometheus](https://img.shields.io/badge/Prometheus-metrics-E6522C?logo=prometheus&logoColor=white)](https://prometheus.io/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](../LICENSE)

[快速上手](#-快速上手) · [架构总览](#-架构总览) · [API 参考](#-api-参考) · [性能与并发](#-性能与并发) · [运维手册](#-运维手册)

</div>

---

## ✨ 服务定位

`go-media-service` 是 Campus Pulse 平台独立部署的媒体子系统，专职处理**图片 / 视频上传、分片续传、文件元数据管理、存储驱动抽象与管理面板**。它与 Java 主应用（端口 `7800`）解耦，通过 HTTP 协议以两类凭据接入：

- 🔑 **Upload JWT**（Java 签发）→ 前端直传 / Java 代理分片上传
- 🛂 **Service Token**（`X-Service-Token`）→ Java Admin 管道，用于后台治理

| 🎯 设计目标 | 🛠 实现方式 |
|------------|------------|
| 不污染 Java 主库 | 独立 SQLite 元数据 + 可插拔存储驱动，未来切 OSS/COS/S3 只改 `internal/storage/` |
| 大文件不拖垮主进程 | Gin `MultipartReader` 流式解析 + `io.LimitReader` 边读边写，不整块入内存 |
| 千并发不丢分片 | 外键先建父后建子 + 每连接一致的 `PRAGMA foreign_keys/busy_timeout` + WAL |
| 上传失败可观测 | 5xx `AppError` 自动把 cause 打进结构化日志，Prometheus 暴露 QPS/延迟/活跃上传 |
| 管理员零前端依赖 | 服务内嵌 HTML 模板 + 原生 JS 面板，免 Node 打包 |

---

## 🏗 架构总览

### 全链路调用图

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Browser · Vue 3 前端                           │
│  chunkUpload.ts  切片 → 并发 3 worker → 失败重试 → 合并                │
└───────────────┬──────────────────────────────────────────────────────┘
                │ multipart/form-data · 带 uploadId / chunkIndex / meta
                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Java Spring Boot :7800 (代理)                     │
│  ChunkUploadController  →  MediaClient (RestTemplate + Upload JWT)   │
│  GlobalExceptionHandler.MediaClientException 透传上游错误             │
└───────────────┬──────────────────────────────────────────────────────┘
                │ JWT Bearer (Upload) · X-Service-Token (Admin)
                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    Go Media Service :8090 (本服务)                    │
│                                                                      │
│   ┌──── middleware ────┐  RequestContext · Recovery · BodyLimit      │
│   │                    │  AccessLog · UploadAuth · UploadRateLimit   │
│   │                    │  AdminAPIAuth · PanelAuth                   │
│   └────────────────────┘                                             │
│   ┌──── api ──────────┐  upload · file · admin · health              │
│   │                   │  + panel (HTML 模板)                         │
│   └───────────────────┘                                              │
│   ┌──── service ──────┐  UploadService (instant + chunk + merge)     │
│   │                   │  FileService · AdminService                  │
│   │                   │  AuthService · RuntimeConfigService          │
│   │                   │  MaintenanceService (定期清理过期分片)       │
│   └───────────────────┘                                              │
│   ┌──── storage ──────┐  Local (./uploads + chunks + tmp)            │
│   │                   │  Driver 接口 → 可插 OSS / COS / S3          │
│   └───────────────────┘                                              │
│   ┌──── repository ───┐  SQLite + WAL + foreign_keys=ON (DSN)        │
│   │                   │  media_files · upload_tasks · upload_chunks  │
│   │                   │  runtime_configs · audit_logs                │
│   └───────────────────┘                                              │
│                                                                      │
│   /metrics · /debug/pprof · /panel · /health                         │
└──────────────────────────────────────────────────────────────────────┘
```

### 分片上传时序

```
前端                Java                    Go
 │  init (meta)      │                       │
 ├──────────────────►│   gen uploadId (UP-*) │   ← 本地生成，不落 Go
 │◄──────────────────┤                       │
 │                                           │
 │  part × N (并发 3) via RestTemplate       │
 ├───────────────────┼──────────────────────►│  SaveChunk:
 │                                           │    1. load or build task
 │                                           │    2. SaveTask (upsert)      ← FK 必须先父后子
 │                                           │    3. storage.SaveChunk
 │                                           │    4. SaveChunk metadata
 │                                           │    5. 更新 task 累积进度
 │◄──────────────────┼───────────────────────┤
 │                                           │
 │  merge                                    │
 ├───────────────────┼──────────────────────►│  MergeChunks:
 │                                           │    concat + sha256 + 落盘
 │                                           │    写入 media_files
 │                                           │    删除临时分片
 │◄──────────────────┼───────────────────────┤  return accessUrl
```

---

## 🧩 核心特性

### ✅ 已实现

| 领域 | 能力 |
|------|------|
| **上传通道** | 图片 / 视频 / 多文件 / 分片 / 合并 / 取消 / 状态查询 |
| **秒传** | `sha256 + size + media_type` 命中 → 直接复用已有文件 ID |
| **存储** | 本地磁盘，按 `media_type` + 日期分目录（`2006/01/02`） |
| **元数据** | SQLite + WAL，外键约束兜底数据一致性 |
| **鉴权** | Upload JWT（签发方 Java）/ Admin JWT（Cookie）/ Service Token（Header） |
| **限流** | 单 IP 令牌桶 + 单用户令牌桶（`golang.org/x/time/rate`） |
| **并发控制** | 全局上传 CAS 限流 + 合并任务独立池 |
| **可观测性** | `/metrics`（Prometheus）、`/debug/pprof`、结构化 zerolog |
| **治理后台** | `/panel` 原生 HTML 面板 + `/api/admin/*` RESTful 接口 |
| **运行时配置** | `PUT /api/admin/config` 热更新，持久化在 `runtime_configs` 表 |
| **审计** | `audit_logs` 记录删除等高危动作 |
| **清理任务** | `MaintenanceService` 按 `chunk_expire_hours` 回收过期分片 |

### 🗺 路线图

- [ ] 图片缩略图（imageproxy 风格）
- [ ] 视频封面帧提取（`ffmpeg` 外部依赖）+ 时长采集
- [ ] OSS / COS / S3 存储驱动
- [ ] 审核流水与细粒度操作日志页
- [ ] CRDB / MySQL 元数据后端（仅替换 `internal/repository/`）

---

## 🚀 快速上手

### 环境要求

| 组件 | 最低版本 |
|------|----------|
| Go | 1.22 |
| Docker (可选) | 24+ |
| OS | Linux / macOS / Windows 10+ |

> ℹ️ 默认使用 `modernc.org/sqlite`，**无需 CGO**，跨平台构建开箱即用。

### 本地启动（源码）

```powershell
cd go-media-service
go mod tidy
go run ./cmd/media-service
```

入口（启动前必须注入 `.env.example` 中列出的四项敏感环境变量）：

- 服务：<http://localhost:8090>
- 健康检查：<http://localhost:8090/health>
- 管理面板：<http://localhost:8090/panel/login>
- 面板用户名默认是 `admin`；密码、两类 JWT 密钥和 Service Token 没有默认值，缺失时进程会拒绝启动。

### 一键脚本

```powershell
# 启动 + 热重载（若已安装 air）
cd go-media-service
.\scripts\dev.ps1

# 烟雾测试（健康 + 上传基础用例）
.\scripts\smoke.ps1
```

### Docker

```powershell
cd go-media-service
# 先在当前 shell 或不纳入 Git 的 .env 中设置全部必需敏感变量
docker compose up -d --build
```

---

## 🔌 API 参考

所有响应统一结构：

```json
{
  "code": 0,
  "message": "ok",
  "requestId": "req_xxxxxxxx",
  "data": { /* ... */ }
}
```

分页响应：

```json
{
  "code": 0,
  "message": "ok",
  "requestId": "req_xxxxxxxx",
  "data": {
    "items": [],
    "pagination": { "page": 1, "pageSize": 20, "total": 100, "totalPage": 5 }
  }
}
```

### 🔹 Upload（需要 Upload JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/upload/image` | 单图上传，multipart `file` |
| `POST` | `/api/upload/video` | 单视频上传，multipart `file` |
| `POST` | `/api/upload/files` | 多文件批量，受 `max_batch_files` 限制 |
| `POST` | `/api/upload/chunk` | 分片上传（多部分 form） |
| `POST` | `/api/upload/merge` | 合并分片（JSON `{taskId}`） |
| `GET`  | `/api/upload/status/:taskId` | 查询上传进度 / 已到分片索引 |
| `DELETE` | `/api/upload/task/:taskId` | 取消上传（清理分片） |

分片上传请求示例（**注意：元数据走 query，分片体走 multipart**）：

```http
POST /api/upload/chunk?taskId=UP-abc&chunkIndex=0&totalChunks=32&fileName=a.mp4&fileSize=104857600&mediaType=video
Authorization: Bearer <upload-jwt>
Content-Type: multipart/form-data; boundary=----x

------x
Content-Disposition: form-data; name="chunk"; filename="chunk-0"
Content-Type: application/octet-stream

<binary bytes>
------x--
```

### 🔹 File（公开 / 登录复用）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET`  | `/api/file/:id` | 下载 / 预览单文件（前端直链） |
| `GET`  | `/api/file/list` | 文件列表（需 Service Token） |
| `GET`  | `/api/file/detail/:id` | 元数据详情（需 Service Token） |
| `DELETE` | `/api/file/:id` | 软删 + 物理删除（需 Service Token） |
| `POST` | `/api/file/batch-delete` | 批量删除（需 Service Token） |

### 🔹 Admin（Service Token / Admin JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/admin/dashboard` | 面板总览（上传数、失败率、磁盘占用） |
| `GET` | `/api/admin/uploads` | 分页上传记录 |
| `GET` | `/api/admin/files` | 分页文件列表 |
| `GET` | `/api/admin/files/:id` | 文件详情 |
| `DELETE` | `/api/admin/files/:id` | 删除文件 |
| `POST` | `/api/admin/files/batch-delete` | 批量删除 |
| `GET` | `/api/admin/tasks` | 上传任务列表 |
| `GET` | `/api/admin/stats` | 汇总统计（近 7/30 天） |
| `GET` | `/api/admin/system` | 进程 / 磁盘 / 负载监控 |
| `GET` | `/api/admin/config` | 读取运行时配置 |
| `PUT` | `/api/admin/config` | 热更新运行时配置 |

### 🔹 Observability

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/health` | 进程 + DB + 存储健康 |
| `GET` | `/metrics` | Prometheus 指标 |
| `GET` | `/debug/pprof/*` | 性能剖析（默认仅内网开启） |

---

## ⚙ 配置

主配置：`go-media-service/config.yaml`（纯 YAML）；敏感项通过环境变量覆盖。

### YAML 结构（节选）

```yaml
server:
  port: 8090
  public_base_url: http://localhost:8090
  trust_proxy: false
  enable_pprof: true
  enable_metrics: true

database:
  driver: sqlite
  dsn: ./data/media.db              # 运行期自动追加 _pragma=foreign_keys(1)&journal_mode(WAL)&busy_timeout(5000)

storage:
  driver: local
  root_dir: ./uploads
  temp_dir: ./tmp
  date_path_layout: "2006/01/02"

upload:
  max_image_size_mb: 15
  max_video_size_mb: 512
  chunk_size_mb: 8
  max_chunk_count: 4096
  chunk_expire_hours: 48
  merge_concurrency: 2
  global_upload_concurrency: 64

auth:
  upload: { enabled: true, jwt_secret: "" } # MEDIA_UPLOAD_JWT_SECRET
  admin:  { username: admin, password: "", jwt_secret: "", service_tokens: [] }

security:
  per_ip_rps: 20
  per_user_rps: 15
  max_request_body_mb: 1024
```

### 🌿 可热更新（`PUT /api/admin/config`）

- `upload_enabled`、`max_batch_files`
- `max_image_size_mb`、`max_video_size_mb`、`chunk_size_mb`、`max_chunk_count`、`chunk_expire_hours`
- `global_upload_concurrency`、`merge_concurrency`
- `per_ip_rps`、`per_user_rps`、`public_file_access`
- `allowed_image_exts`、`allowed_video_exts`、`allowed_image_mimes`、`allowed_video_mimes`
- `max_multipart_memory_mb`

### 🔒 需要重启的配置

`server.*` · `database.*` · `storage.*` · `auth.*` · `panel.*`

### 环境变量速查

| 变量 | 默认 | 作用 |
|------|------|------|
| `MEDIA_SERVER_PORT` | `8090` | 监听端口 |
| `MEDIA_PUBLIC_BASE_URL` | `http://localhost:8090` | 拼 `accessUrl` 时使用 |
| `MEDIA_DATABASE_DSN` | `./data/media.db` | SQLite DSN |
| `MEDIA_STORAGE_ROOT_DIR` | `./uploads` | 本地存储根 |
| `MEDIA_UPLOAD_JWT_SECRET` | 必填，至少 32 字符 | 与 Java 约定的 Upload JWT 密钥 |
| `MEDIA_ADMIN_PASSWORD` | 必填，至少 16 字符 | 面板登录密码 |
| `MEDIA_ADMIN_JWT_SECRET` | 必填，至少 32 字符 | 面板会话 JWT 签名密钥 |
| `MEDIA_ADMIN_SERVICE_TOKENS` | 必填，每项至少 32 字符 | 逗号分隔的 Java 后台 Service Token，可并行轮换 |
| `MEDIA_PER_IP_RPS` / `MEDIA_PER_USER_RPS` | `20` / `15` | 限流阈值 |
| `MEDIA_UPLOAD_MAX_CONCURRENCY` | `64` | 全局并发上传上限 |

---

## 🚄 性能与并发

### 流式处理

- `Gin.MultipartReader` 逐 part 处理，大文件不会驻留在内存中。
- 写盘路径使用 `io.LimitReader`，防止超大 body 打穿内存。
- `SHA256` 在写盘同侧计算（单次 I/O），避免二次读文件。

### 数据库一致性（近期修复）

过去分片上传偶发 `save chunk metadata failed`。根因是：

1. `upload_chunks.task_id → upload_tasks(id)` 有外键；
2. Java `init` 只生成本地 `uploadId`，不在 Go 预建任务；
3. Go `SaveChunk` 原本先写 `upload_chunks` 再写 `upload_tasks`，多 worker 并发下首个分片必触外键失败。

修复要点：

- ✅ `SaveChunk` 改为**先 `SaveTask` upsert，再写分片元数据**（`internal/service/upload.go`）。
- ✅ DSN 自动附加 `_pragma=foreign_keys(1)&journal_mode(WAL)&synchronous(NORMAL)&busy_timeout(5000)`，保证**连接池内每条连接一致**（`cmd/media-service/main.go`）。
- ✅ 5xx `AppError` 自动把底层 `cause` 输出到日志（`internal/api/response.go`），问题现场不再静默。

### 并发旋钮

| 参数 | 默认 | 建议 |
|------|------|------|
| `upload.global_upload_concurrency` | 64 | CPU × 8 起步，结合磁盘 IOPS 调 |
| `upload.merge_concurrency` | 2 | 大视频合并吃磁盘带宽，别超过 `CPU/2` |
| `security.per_ip_rps` | 20 | 前台直传场景可下调至 5-10 |
| `security.per_user_rps` | 15 | 与 `per_ip_rps` 联动 |
| `upload.chunk_size_mb` | 8 | 移动端建议 2-4，服务器之间可上到 16 |

### 基准压测

```bash
# 健康检查极限 QPS
hey -n 5000 -c 100 http://127.0.0.1:8090/health

# 图片上传（自定义 multipart 用 vegeta / k6 / 自写）
k6 run scripts/k6-upload-image.js   # 可自行补齐
```

重点观察：`/metrics` 中的 `media_upload_*` 系列、`go_goroutines`、`process_resident_memory_bytes`，以及磁盘 `iostat`。

---

## 🛡 安全

已内建：

- ✅ 文件名清洗（`utils.SafeFileName`）与路径穿越防护
- ✅ MIME 嗅探 + 扩展名白名单双重校验
- ✅ 单文件大小上限（图片 / 视频独立）
- ✅ Body 大小硬上限（`security.max_request_body_mb`）
- ✅ 请求 ID 串联日志（`X-Request-Id`）
- ✅ `X-Service-Token` 多 token 轮换；可选 Java 出口 IP 白名单
- ✅ 删除审计落表
- ✅ Admin 面板独立 JWT（Cookie `media_panel_token`，HttpOnly）

上线再做：

- 🔐 Nginx / Caddy HTTPS
- 🔐 WAF / CDN 前置限速
- 🔐 `/panel` 仅内网/堡垒机
- 🔐 Secrets 用 Vault / 云厂商 KMS 注入，不入库

服务会对 Upload JWT、管理员密码、管理员 JWT 和每一个 Service Token 做启动期校验。生产值应写入权限为 `0600` 的环境文件（systemd 示例使用 `/etc/zens/go-media-service.env`）或容器 Secret；任何一项为空或长度不足都会 fail-fast，仓库中的 `config.yaml` 不再包含可用凭据。

如果历史版本曾使用过仓库内的固定值，应将它们视为已经泄露：上线本改动前先轮换 Java 与媒体服务共享的 Upload JWT、管理员密码、管理员 JWT 和全部 Service Token，旧值不得继续保留在生产环境。

---

## 🗄 存储与元数据

### 本地存储布局

```
uploads/
├── images/2026/04/17/file_20260417_abc.jpg
├── videos/2026/04/17/file_20260417_xyz.mp4
└── chunks/<taskId>/0001.part
tmp/
└── <taskId>/...
```

### 表结构（SQLite）

- `media_files` — 文件元数据，含 `sha256`、`size_bytes`、`access_url`、软删字段
- `upload_tasks` — 上传任务，状态机 `uploading / success / failed / canceled / merging`
- `upload_chunks` — 分片索引，`UNIQUE(task_id, chunk_index)` + FK 到 `upload_tasks`
- `runtime_configs` — 热更新配置快照
- `audit_logs` — 删除 / 危险操作审计

迁移到 MySQL / PostgreSQL 时仅替换 `internal/repository/`，上层接口不变。

---

## 🧰 运维手册

### Docker Compose

```powershell
cd go-media-service
docker compose up -d --build
```

推荐卷映射：

```yaml
volumes:
  - ./data:/app/data         # SQLite 文件
  - ./uploads:/app/uploads   # 媒体主体
  - ./tmp:/app/tmp           # 分片缓冲
  - ./logs:/app/logs         # 访问 / 错误日志
```

### Nginx 反代关键项

```nginx
client_max_body_size        1024m;
proxy_request_buffering     off;
proxy_buffering             off;
proxy_read_timeout          600s;
proxy_send_timeout          600s;
keepalive_timeout           65;
```

完整示例见 `deployments/nginx/media-service.conf`。

### systemd

```ini
[Unit]
Description=Campus Pulse media service
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/campus-pulse/go-media-service
ExecStart=/opt/campus-pulse/go-media-service/media-service
Restart=on-failure
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

完整单元文件：`deployments/systemd/go-media-service.service`。

### Linux 内核调优建议

```bash
ulimit -n 65535
sysctl -w fs.file-max=2097152
sysctl -w net.core.somaxconn=4096
sysctl -w net.ipv4.tcp_max_syn_backlog=4096
sysctl -w net.ipv4.ip_local_port_range="10240 65535"
```

---

## 📁 目录速查

```
go-media-service/
├── cmd/media-service/       # 程序入口 + DSN 注入
├── internal/
│   ├── api/                 # upload · file · admin · health + response.go
│   ├── config/              # YAML + env 合并
│   ├── constants/
│   ├── metrics/             # Prometheus collector
│   ├── middleware/          # auth / ratelimit / recovery / request
│   ├── model/
│   ├── panel/               # 原生 HTML 管理面板
│   ├── repository/sqlite.go # 建表 + CRUD + PRAGMA 引导
│   ├── router/              # 路由拼装
│   ├── service/             # 业务编排（upload / file / admin / maintenance ...）
│   ├── storage/             # Driver 接口 + 本地实现
│   └── utils/               # ID、文件名、env 解析
├── web/
│   ├── static/ (panel.css / panel.js)
│   └── templates/ (*.html)
├── deployments/ (nginx + systemd 模版)
├── scripts/ (dev.ps1 / dev.sh / smoke.ps1)
├── config.yaml · Dockerfile · docker-compose.yml · go.mod
└── data/ · uploads/ · tmp/ · logs/  (运行时生成)
```

---

## 🤝 与 Java 主工程的契约

1. **Upload JWT**：Java `MediaJwtIssuer` 使用 `auth.upload.jwt_secret` 签发，`iss=campus-pulse-java`、`aud=campus-pulse-media`，`sub=userId`，`biz=bizType`。
2. **Service Token**：Java 后台 `X-Service-Token: <auth.admin.service_tokens[*]>` 调 `/api/admin/*` 与 `/api/file/list|detail|delete`。
3. **媒体响应**：Go 返回 `{ fileId, accessUrl, coverUrl, mediaType, sizeBytes, sha256, width, height, durationSec, instantUpload }`，Java 写入 `post_media` 关联表。
4. **错误传递**：Go 非 0 `code` + HTTP 4xx/5xx → Java `MediaClient.toClientException` 解析后抛 `MediaClientException`，`GlobalExceptionHandler` 透传上游消息给前端，**不再被兜底 5xx 吞掉**。

---

## 📄 License

MIT License © 2026 Campus Pulse Contributors

<div align="center">

如果这个服务帮到你，欢迎给主仓库点个 ⭐。<br>
问题 / 建议请到 [../README.md](../README.md#-贡献) 提 Issue。

</div>
