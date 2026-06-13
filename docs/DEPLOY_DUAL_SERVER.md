# Campus Pulse 双服务器 + Cloudflare 小白部署手册

> 目标读者：第一次搞生产部署，跟着抄命令就能跑起来。  
> 架构目标：A 服务器（腾讯云 4核4G）跑主应用；前端走 Cloudflare Pages；媒体文件走 Cloudflare R2；B 服务器（华为云 2核2G）做监控 + 备份 + 灾备静态页。

---

## 目录

- [0. 架构总览](#0-架构总览)
- [1. 开始之前：你需要准备什么](#1-开始之前你需要准备什么)
- [2. Cloudflare 基础配置](#2-cloudflare-基础配置)
- [3. A 服务器：主应用部署](#3-a-服务器主应用部署)
- [4. 前端部署到 Cloudflare Pages](#4-前端部署到-cloudflare-pages)
- [5. Cloudflare 加速：Cache Rules + R2](#5-cloudflare-加速cache-rules--r2)
- [6. B 服务器：监控 + 备份 + 灾备页](#6-b-服务器监控--备份--灾备页)
- [7. 上线验证清单](#7-上线验证清单)
- [8. 日常维护手册](#8-日常维护手册)
- [9. 常见问题 FAQ](#9-常见问题-faq)

---

## 0. 架构总览

```
              ┌──────────────────┐
              │   用户浏览器      │
              └────────┬─────────┘
                       │
              ┌────────▼─────────┐
              │   Cloudflare     │
              │  (DNS + CDN +    │
              │   Pages + R2)    │
              └────────┬─────────┘
         ┌─────────────┼─────────────┐
         │             │             │
    前端静态       /api/*        媒体资源
         │             │             │
    ┌────▼────┐   ┌────▼────┐   ┌────▼────┐
    │ CF Pages│   │ A 服务器 │   │ R2 存储 │
    │ (vue)   │   │ (Spring │   │ (图/视  │
    │         │   │  Boot + │   │  频)   │
    │         │   │  MySQL +│   │         │
    │         │   │  Redis) │   │         │
    └─────────┘   └────┬────┘   └─────────┘
                       │ (监控抓取 + 备份拉取)
                  ┌────▼─────┐
                  │ B 服务器  │
                  │ Prometheus│
                  │ + Grafana │
                  │ + Backup  │
                  │ + 维护页   │
                  └──────────┘
```

**数据流**：
- 用户打开网页 → CF Pages 秒出前端（全球边缘缓存）
- 前端调 API → 走 `api.你的域名.com` → CF 边缘 → 回源到 A 服务器 7800 端口
- 前端上传图片 → 找 A 要 presigned URL → 浏览器直传 R2 → 回调 A 记录元数据
- B 定时抓 A 的 `/actuator/prometheus`，夜里从 A 拉 MySQL dump

---

## 1. 开始之前：你需要准备什么

### 1.1 账号与资源

| 项目 | 说明 |
|---|---|
| Cloudflare 账号 | 免费版够用，需要能添加域名 |
| 域名 | 需要能把 NS 改到 Cloudflare（大多数注册商都行） |
| GitHub 账号 | 用于 CF Pages 自动部署（可选） |
| A 服务器 | 腾讯云 4核4G，Ubuntu 22.04 LTS（推荐） |
| B 服务器 | 华为云 2核2G，Ubuntu 22.04 LTS |
| Cloudflare R2 | 已经在用，桶名 `campus-pulse-media`，自定义域名 `media.你的域名.com` |
| DeepSeek API Key | 用于 AI 摘要（可选） |
| SMTP 邮箱 | OTP 登录用，QQ 邮箱/163 都行 |

### 1.2 域名规划（举例）

假设你的域名是 `example.com`，这么分：

| 二级域名 | 用途 | 指向 |
|---|---|---|
| `example.com` | 主站前端 | Cloudflare Pages |
| `www.example.com` | 主站前端（别名） | Cloudflare Pages |
| `api.example.com` | 后端 API | A 服务器公网 IP |
| `media.example.com` | R2 媒体资源 | Cloudflare R2 |
| `monitor.example.com` | Grafana 监控（可选） | B 服务器公网 IP |

本文后续都用 `example.com` 作占位符，替换成你自己的。

### 1.3 记下这些 IP 和端口

开工前在本地建个文本文件记下来，后面到处要填：

```
A 服务器公网 IP：xx.xx.xx.xx
A 服务器 SSH 端口：22（默认，建议改）
A 服务器 root 密码：********

B 服务器公网 IP：yy.yy.yy.yy
B 服务器 SSH 端口：22
B 服务器 root 密码：********

MySQL root 密码：自己设一个强密码
MySQL campus_pulse 用户密码：自己设一个强密码
Redis 密码：自己设一个强密码

JWT_SECRET：至少 64 位随机字符串
```

生成强密码的命令（Git Bash 或 Linux 终端）：

```bash
openssl rand -base64 48
```

---

## 2. Cloudflare 基础配置

### 2.1 把域名接入 Cloudflare

1. 登录 <https://dash.cloudflare.com>
2. 右上角 **"Add a Site"** → 输入 `example.com` → **Continue**
3. 选 **Free** 套餐 → **Continue**
4. Cloudflare 会扫现有 DNS 记录，下一步给你两个 NS 地址，类似：
   ```
   alice.ns.cloudflare.com
   bob.ns.cloudflare.com
   ```
5. 去你买域名的地方（阿里云/腾讯云/Namesilo 等），改 DNS 为上面两个 NS
6. 等 5 分钟到 24 小时（通常半小时内），Cloudflare 会显示 **Active**

### 2.2 添加 DNS 记录

进入 Cloudflare → 你的域名 → **DNS → Records**，加这几条：

| Type | Name | Content | Proxy 状态 |
|---|---|---|---|
| A | `api` | A 服务器公网 IP | 🟠 Proxied（橙色云朵） |
| A | `monitor` | B 服务器公网 IP | 🟠 Proxied |
| CNAME | `@` | （后面配 Pages 时自动生成） | 🟠 Proxied |
| CNAME | `www` | `example.com` | 🟠 Proxied |

> ⚠️ `api.example.com` 必须开橙色云朵，否则没有 CDN 加速和 HTTPS 证书。

### 2.3 SSL/TLS 设置

**SSL/TLS → Overview** → 模式选 **Full (strict)**  
（要求 A 服务器上有有效证书，我们后面用 CF 签发的 Origin Certificate）

**SSL/TLS → Edge Certificates** → 开启：
- Always Use HTTPS：**On**
- Automatic HTTPS Rewrites：**On**
- Minimum TLS Version：**TLS 1.2**

### 2.4 给 A 服务器签一张 Origin Certificate

**SSL/TLS → Origin Server → Create Certificate**

- Hostnames 填：`*.example.com, example.com`
- 有效期：15 年
- 点 **Create**

屏幕上会显示两段内容，**只显示一次，现在就复制保存**：

- **Origin Certificate**（PEM 格式）→ 保存成 `origin.pem`
- **Private Key** → 保存成 `origin.key`

> 这两个文件稍后要传到 A 服务器 `/etc/nginx/ssl/` 下。

---

## 3. A 服务器：主应用部署

> 以下所有命令假设你已经 SSH 登录 A 服务器，用 `root` 或 `sudo` 执行。

### 3.1 基础环境准备

```bash
# 更新系统
apt update && apt upgrade -y

# 装常用工具
apt install -y curl wget git vim unzip htop ufw

# 设置时区（中国）
timedatectl set-timezone Asia/Shanghai

# 开启防火墙，放通必要端口
ufw allow 22/tcp         # SSH（如果你改了 SSH 端口，换成你的）
ufw allow 80/tcp         # HTTP
ufw allow 443/tcp        # HTTPS
ufw --force enable
ufw status
```

### 3.2 安装 JDK 17

```bash
apt install -y openjdk-17-jdk
java -version
# 应该看到 openjdk version "17.x.x"
```

### 3.3 安装 MySQL 8

```bash
apt install -y mysql-server
systemctl enable --now mysql
systemctl status mysql  # 看到 active (running) 就对了

# 安全初始化（跟着向导走）
mysql_secure_installation
# - VALIDATE PASSWORD plugin：y
# - 密码强度：2（STRONG）
# - 设置 root 密码：填你准备好的强密码
# - 删除匿名用户：y
# - 禁止 root 远程登录：y
# - 删除 test 库：y
# - 刷新权限：y
```

**创建数据库和用户：**

```bash
mysql -u root -p
```

在 MySQL 里执行（把 `你的强密码` 换成真实密码）：

```sql
CREATE DATABASE campus_pulse DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'campus'@'localhost' IDENTIFIED BY '你的强密码';
GRANT ALL PRIVILEGES ON campus_pulse.* TO 'campus'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**导入表结构**（先从本地传 SQL 文件上来）：

本地 Windows（Git Bash）：

```bash
scp "D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse/src/main/resources/sql/campus_pulse_schema.sql" root@A服务器IP:/tmp/
```

回到 A 服务器：

```bash
mysql -u campus -p campus_pulse < /tmp/campus_pulse_schema.sql
# 验证
mysql -u campus -p -e "USE campus_pulse; SHOW TABLES;"
# 应该看到 25 张表
```

### 3.4 安装 Redis 6+

```bash
apt install -y redis-server

# 改配置：加密码 + 只监听本机
vim /etc/redis/redis.conf
```

找到并修改这几行（vim 里用 `/关键词` 搜索）：

```conf
bind 127.0.0.1 ::1
requirepass 你的Redis强密码
maxmemory 1gb
maxmemory-policy allkeys-lru
```

保存退出（`Esc` → `:wq`），重启：

```bash
systemctl enable --now redis-server
systemctl restart redis-server

# 验证
redis-cli -a 你的Redis强密码 ping
# 应该返回 PONG
```

### 3.5 安装 Nginx

```bash
apt install -y nginx
systemctl enable --now nginx

# 放证书
mkdir -p /etc/nginx/ssl
# 把前面下载的 origin.pem 和 origin.key 上传到 /etc/nginx/ssl/
# 本地 Git Bash：
# scp origin.pem origin.key root@A服务器IP:/etc/nginx/ssl/

chmod 600 /etc/nginx/ssl/origin.key
```

**写 Nginx 配置**：

```bash
vim /etc/nginx/sites-available/campus-pulse
```

粘贴（把 `example.com` 换成你的域名）：

```nginx
# API 反向代理（只处理 api.example.com）
server {
    listen 80;
    listen [::]:80;
    server_name api.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name api.example.com;

    ssl_certificate     /etc/nginx/ssl/origin.pem;
    ssl_certificate_key /etc/nginx/ssl/origin.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    client_max_body_size 64m;

    # 真实客户端 IP 从 Cloudflare 头拿
    set_real_ip_from 173.245.48.0/20;
    set_real_ip_from 103.21.244.0/22;
    set_real_ip_from 103.22.200.0/22;
    set_real_ip_from 103.31.4.0/22;
    set_real_ip_from 141.101.64.0/18;
    set_real_ip_from 108.162.192.0/18;
    set_real_ip_from 190.93.240.0/20;
    set_real_ip_from 188.114.96.0/20;
    set_real_ip_from 197.234.240.0/22;
    set_real_ip_from 198.41.128.0/17;
    set_real_ip_from 162.158.0.0/15;
    set_real_ip_from 104.16.0.0/13;
    set_real_ip_from 104.24.0.0/14;
    set_real_ip_from 172.64.0.0/13;
    set_real_ip_from 131.0.72.0/22;
    real_ip_header CF-Connecting-IP;

    # API 反向代理
    location / {
        proxy_pass http://127.0.0.1:7800;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;

        # WebSocket 支持
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

启用并测试：

```bash
ln -s /etc/nginx/sites-available/campus-pulse /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t    # 测试语法
systemctl reload nginx
```

### 3.6 部署 Spring Boot jar

**在本地 Windows 打包：**

```bash
cd "D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse"
mvn clean package -DskipTests -P prod
# 产物在 target/campus-pulse-0.0.1-SNAPSHOT.jar
```

**上传到 A 服务器：**

```bash
# 本地 Git Bash
scp target/campus-pulse-0.0.1-SNAPSHOT.jar root@A服务器IP:/opt/campus-pulse/campus-pulse.jar
```

**A 服务器建目录和配置文件：**

```bash
mkdir -p /opt/campus-pulse/log
cd /opt/campus-pulse
vim .env.prod
```

贴进去（把占位值换成真实的）：

```env
# 数据库
DB_URL=jdbc:mysql://127.0.0.1:3306/campus_pulse?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
DB_USERNAME=campus
DB_PASSWORD=你的数据库密码

# Redis
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=你的Redis密码

# JWT（openssl rand -base64 48 生成）
JWT_SECRET=填一个至少64字符的随机字符串

# 前端来源域名（CF Pages 配好后填）
ALLOWED_ORIGIN_PATTERNS=https://example.com,https://www.example.com

# R2
R2_ENABLED=true
R2_ENDPOINT=https://你的R2账号ID.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=你的R2_AK
R2_SECRET_ACCESS_KEY=你的R2_SK
R2_BUCKET=campus-pulse-media
R2_PUBLIC_BASE_URL=https://media.example.com

# 头像前缀
AVATAR_BASE_URL=https://media.example.com

# Cloudflare Turnstile
TURNSTILE_ENABLED=true
TURNSTILE_SITE_KEY=填你的_Turnstile_site_key
TURNSTILE_SECRET_KEY=填你的_Turnstile_secret_key
TURNSTILE_ALLOW_LOCAL_PREVIEW_SKIP=false

# DeepSeek AI（可选）
DEEPSEEK_API_KEY=sk-xxxx

# 邮件（OTP 登录）
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=你的邮箱@qq.com
MAIL_PASSWORD=授权码（不是邮箱密码）

# GitHub OAuth（可选）
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GITHUB_REDIRECT_URI=https://api.example.com/auth/github/callback

# 日志
LOG_FILE=./log/logfile.log

# Spring profile
SPRING_PROFILES_ACTIVE=prod
```

**保护好密钥文件：**

```bash
chmod 600 /opt/campus-pulse/.env.prod
```

### 3.7 配置 systemd 开机自启

```bash
vim /etc/systemd/system/campus-pulse.service
```

粘贴：

```ini
[Unit]
Description=Campus Pulse Spring Boot
After=network.target mysql.service redis-server.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/campus-pulse
EnvironmentFile=/opt/campus-pulse/.env.prod
Environment=SPRING_PROFILES_ACTIVE=prod
ExecStart=/usr/bin/java -Xms1g -Xmx2g -jar /opt/campus-pulse/campus-pulse.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/campus-pulse/log/stdout.log
StandardError=append:/opt/campus-pulse/log/stderr.log

[Install]
WantedBy=multi-user.target
```

启动并开机自启：

```bash
systemctl daemon-reload
systemctl enable --now campus-pulse
systemctl status campus-pulse

# 跟日志
journalctl -u campus-pulse -f
# 或看文件
tail -f /opt/campus-pulse/log/logfile.log
```

**验证**：

```bash
curl http://127.0.0.1:7800/actuator/health
# 预期返回 {"status":"UP"}

curl https://api.example.com/actuator/health
# 经过 Cloudflare 的预期也是 {"status":"UP"}
```

### 3.8 部署脚本（后续更新用）

项目自带 `scripts/deploy.sh`，以后更新 jar 只要：

```bash
# 本地打包
mvn clean package -DskipTests -P prod

# 上传
scp target/campus-pulse-0.0.1-SNAPSHOT.jar root@A服务器IP:/tmp/new.jar

# A 服务器上执行（systemd 模式）
SERVICE_NAME=campus-pulse DEPLOY_DIR=/opt/campus-pulse APP_NAME=campus-pulse \
  bash /opt/campus-pulse/scripts/deploy.sh /tmp/new.jar
```

（先把 `scripts/` 目录拷到 `/opt/campus-pulse/scripts/`）

---

## 4. 前端部署到 Cloudflare Pages

### 4.1 准备前端构建

**在本地 Windows 先改环境变量：**

```bash
cd "D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse/web"

# 新建或修改 .env.production
```

`web/.env.production` 内容：

```env
VITE_API_BASE_URL=https://api.example.com
VITE_WS_URL=wss://api.example.com/ws
VITE_TURNSTILE_ENABLED=true
VITE_TURNSTILE_SITE_KEY=填你的_Turnstile_site_key
```

> 注意：`VITE_API_BASE_URL` 必须指向 `api.example.com`，因为 CF Pages 上没有你的后端。

### 4.2 方式 A：把代码推到 GitHub，自动部署（推荐）

1. 把项目推到 GitHub（私有仓库也行）
2. Cloudflare Dashboard → **Workers & Pages → Create → Pages → Connect to Git**
3. 授权 GitHub，选你的仓库
4. 构建配置：
   - **Production branch**：`main`
   - **Framework preset**：`Vue`
   - **Build command**：`cd web && npm install && npm run build`
   - **Build output directory**：`web/dist`
   - **Root directory**：`/`（保持默认）
5. **Environment variables (Production)** 里加：
   - `VITE_API_BASE_URL` = `https://api.example.com`
   - `VITE_WS_URL` = `wss://api.example.com/ws`
   - `VITE_TURNSTILE_ENABLED` = `true`
   - `VITE_TURNSTILE_SITE_KEY` = `填你的 key`
   - `NODE_VERSION` = `20`
6. **Save and Deploy** → 等 2-3 分钟看到 "Success"

### 4.3 方式 B：本地构建直接上传

```bash
cd "D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse/web"
npm install
npm run build
# 产物在 web/dist/
```

Cloudflare Dashboard → **Workers & Pages → Create → Pages → Upload assets**  
→ 项目名填 `campus-pulse` → 上传 `web/dist` 整个目录 → Deploy。

### 4.4 绑定自定义域名

Pages 项目页 → **Custom domains → Set up a custom domain**

- 输入 `example.com` → Continue → Cloudflare 自动加 CNAME
- 再加一次：`www.example.com`

几分钟后打开 <https://example.com> 应该能看到前端。

### 4.5 后续每次更新前端

**方式 A（Git 自动）**：推代码到 `main`，Cloudflare 自动重新构建。

**方式 B（手动）**：

```bash
cd web
npm run build
# 再 Upload assets
```

### 4.6 A 服务器上的前端怎么办？

**不用删**。你的 DNS 里 `example.com` 已经 CNAME 到 CF Pages，根本不走 A 服务器。A 服务器 Nginx 只配了 `api.example.com`，没有 `example.com` 的 server block，就不会响应。

如果你想做兜底：`/var/www/campus-pulse-fallback/` 放一份旧 dist，Nginx 配一个 `example.com` 的 server 指向它，CF Pages 全挂时手动切 DNS 回 A 即可。**不急着做**。

---

## 5. Cloudflare 加速：Cache Rules + R2

### 5.1 缓存 GET 接口

Cloudflare Dashboard → 你的域名 → **Caching → Cache Rules → Create rule**

**规则 1：热榜和趋势接口缓存 60 秒**

- Rule name：`API hot endpoints cache`
- If incoming requests match：
  - Hostname **equals** `api.example.com`
  - AND URI Path **starts with** `/heat-rank/`
  - OR URI Path **starts with** `/trend-stat/`
  - OR URI Path **starts with** `/stats/`
  - OR URI Path **starts with** `/section/list`
- Then：
  - Cache eligibility：**Eligible for cache**
  - Edge TTL：**Override origin → 60 seconds**
  - Cache key：忽略 cookie（默认就行）
- **Deploy**

**规则 2：R2 媒体缓存 1 年**

- Rule name：`R2 media long cache`
- Hostname **equals** `media.example.com`
- Then：
  - Edge TTL：**Override origin → 1 year**
  - Browser TTL：**Override origin → 1 month**
- **Deploy**

### 5.2 R2 自定义域名（如果还没配）

Cloudflare Dashboard → **R2 → 你的桶 → Settings → Custom Domains → Connect Domain**

- 填 `media.example.com` → Cloudflare 自动加 CNAME
- 等 1-2 分钟 HTTPS 生效

### 5.3 Turnstile 配置

**Cloudflare Dashboard → Turnstile → Add Site**

- Domain：`example.com`、`www.example.com`、`api.example.com`（全加）
- Widget Mode：**Managed**（推荐）
- Get：
  - **Site Key** → 给前端 `VITE_TURNSTILE_SITE_KEY` 和后端 `TURNSTILE_SITE_KEY` 用
  - **Secret Key** → 给后端 `TURNSTILE_SECRET_KEY` 用

### 5.4 可选：Argo Smart Routing（付费）

`$5/月` 起，跨地域访问明显提速。**Traffic → Argo → Enable**。暂时可以不开，后面流量上来再说。

---

## 6. B 服务器：监控 + 备份 + 灾备页

> B 服务器只有 2核2G，所以我们只部署轻量服务：Prometheus + Grafana + 备份脚本 + 一个静态 Nginx。

### 6.1 基础环境

```bash
# SSH 到 B 服务器
apt update && apt upgrade -y
apt install -y curl wget git vim ufw

timedatectl set-timezone Asia/Shanghai

ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

### 6.2 装 Docker + Docker Compose

```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
# 验证
docker --version
docker compose version
```

### 6.3 部署 Prometheus + Grafana

```bash
mkdir -p /opt/monitor/prometheus
mkdir -p /opt/monitor/grafana-data
cd /opt/monitor
```

**Prometheus 配置**：

```bash
vim prometheus/prometheus.yml
```

粘贴：

```yaml
global:
  scrape_interval: 30s
  evaluation_interval: 30s

scrape_configs:
  - job_name: 'campus-pulse'
    metrics_path: /actuator/prometheus
    scheme: https
    static_configs:
      - targets: ['api.example.com']
    # A 服务器 Spring Boot 需要开放 prometheus 端点（默认已开）
```

**docker-compose.yml**：

```bash
vim /opt/monitor/docker-compose.yml
```

粘贴：

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    restart: unless-stopped
    ports:
      - "127.0.0.1:9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'

  grafana:
    image: grafana/grafana-oss:latest
    restart: unless-stopped
    ports:
      - "127.0.0.1:3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=你设一个强密码
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./grafana-data:/var/lib/grafana

volumes:
  prometheus-data:
```

启动：

```bash
cd /opt/monitor
docker compose up -d
docker compose ps
# 应该看到 prometheus、grafana 都是 running
```

### 6.4 给 Grafana 配 HTTPS 访问

**先在 A 服务器一样的流程给 B 申请一张 CF Origin Certificate**（SSL/TLS → Origin Server → Create Certificate，Hostnames 填 `*.example.com`）。

B 服务器装 Nginx：

```bash
apt install -y nginx
mkdir -p /etc/nginx/ssl
# 把 origin.pem / origin.key 上传到 /etc/nginx/ssl/
chmod 600 /etc/nginx/ssl/origin.key

vim /etc/nginx/sites-available/monitor
```

```nginx
server {
    listen 80;
    server_name monitor.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name monitor.example.com;

    ssl_certificate     /etc/nginx/ssl/origin.pem;
    ssl_certificate_key /etc/nginx/ssl/origin.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

```bash
ln -s /etc/nginx/sites-available/monitor /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx
```

**访问** <https://monitor.example.com>，登录 `admin` / 你设的密码 → **Connections → Data sources → Add → Prometheus** → URL 填 `http://prometheus:9090` → Save & Test。

导一个 Spring Boot 监控看板：**Dashboards → Import → ID 填 12900 → Load → 选 Prometheus 数据源 → Import**。

### 6.5 每日 MySQL 备份（从 A 拉到 B）

**先在 A 服务器搞一个只读备份账号**：

A 服务器：

```bash
mysql -u root -p
```

```sql
CREATE USER 'backup'@'B服务器公网IP' IDENTIFIED BY '备份专用强密码';
GRANT SELECT, LOCK TABLES, SHOW VIEW, PROCESS, RELOAD, REPLICATION CLIENT ON *.* TO 'backup'@'B服务器公网IP';
FLUSH PRIVILEGES;
EXIT;
```

A 服务器 MySQL 允许远程连接（仅限 B）：

```bash
vim /etc/mysql/mysql.conf.d/mysqld.cnf
```

找到 `bind-address`，改成：

```conf
bind-address = 0.0.0.0
```

```bash
systemctl restart mysql
```

A 服务器防火墙放通 B 的 IP：

```bash
ufw allow from B服务器公网IP to any port 3306
```

> 安全提示：生产环境建议用 SSH 隧道或 VPN 隧道传数据，不要裸开 3306。这里为简单起见用 IP 白名单。

**B 服务器写备份脚本**：

```bash
apt install -y mysql-client
mkdir -p /opt/backup/mysql
vim /opt/backup/backup-a-mysql.sh
```

```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR=/opt/backup/mysql
RETAIN_DAYS=14

A_HOST=A服务器公网IP
A_PORT=3306
A_USER=backup
A_PASS='备份专用强密码'
A_DB=campus_pulse

TS=$(date +%Y%m%d_%H%M%S)
OUT="${BACKUP_DIR}/campus_pulse_${TS}.sql.gz"

mkdir -p "${BACKUP_DIR}"

MYSQL_PWD="${A_PASS}" mysqldump \
  -h"${A_HOST}" -P"${A_PORT}" -u"${A_USER}" \
  --single-transaction --set-gtid-purged=OFF --routines --triggers --events \
  "${A_DB}" | gzip > "${OUT}"

echo "[$(date)] backup done: ${OUT}"

# 清理过期
find "${BACKUP_DIR}" -type f -name "*.sql.gz" -mtime +${RETAIN_DAYS} -delete
```

```bash
chmod 700 /opt/backup/backup-a-mysql.sh
chmod 600 /opt/backup/backup-a-mysql.sh   # 里面有密码，只给 root 读

# 测试一次
/opt/backup/backup-a-mysql.sh
ls -lh /opt/backup/mysql/
```

**加 crontab，每天凌晨 3 点跑**：

```bash
crontab -e
```

加一行：

```cron
0 3 * * * /opt/backup/backup-a-mysql.sh >> /opt/backup/backup.log 2>&1
```

### 6.6 灾备静态页（A 挂了能显示的维护页）

B 服务器：

```bash
mkdir -p /var/www/maintenance
vim /var/www/maintenance/index.html
```

```html
<!doctype html>
<html lang="zh">
<head>
<meta charset="utf-8" />
<title>Campus Pulse · 维护中</title>
<style>
  body { font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; background: #f5f7fa; color: #334; }
  .box { text-align: center; padding: 2rem; }
  h1 { font-size: 2rem; margin: 0 0 1rem; }
  p { color: #666; }
</style>
</head>
<body>
  <div class="box">
    <h1>🛠 站点正在维护</h1>
    <p>我们正在升级系统，请稍后再试。</p>
  </div>
</body>
</html>
```

Nginx 加一个 server block（复用 443 的 SSL）：

```bash
vim /etc/nginx/sites-available/maintenance
```

```nginx
server {
    listen 443 ssl http2;
    server_name backup.example.com;

    ssl_certificate     /etc/nginx/ssl/origin.pem;
    ssl_certificate_key /etc/nginx/ssl/origin.key;

    root /var/www/maintenance;
    index index.html;
}
```

```bash
ln -s /etc/nginx/sites-available/maintenance /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

然后在 Cloudflare DNS 加：`backup.example.com` → A → B 服务器 IP → Proxied。

**Cloudflare Load Balancer**（付费功能，$5/月）才能自动切换。预算有限的话，A 挂掉手动改 DNS：把 `example.com` 临时指到 `backup.example.com`。

---

## 7. 上线验证清单

按顺序一个个测：

- [ ] `https://example.com` 能打开，看到登录页
- [ ] `https://api.example.com/actuator/health` 返回 `{"status":"UP"}`
- [ ] `https://media.example.com/` 能访问（即使是空桶也有响应头）
- [ ] 注册一个账号 → Turnstile 校验弹出 → 完成注册
- [ ] 登录后发一条带图片的帖子 → 图片成功上传（走 R2 直传）
- [ ] 图片 URL 是 `https://media.example.com/xxxx`，能打开
- [ ] 刷新热榜页面 → 第二次刷新 F12 看 `cf-cache-status: HIT`
- [ ] WebSocket 通知能实时推送（点赞自己的帖子看 badge 跳不跳）
- [ ] `https://monitor.example.com` 能进 Grafana，看到 Spring Boot 指标有数据
- [ ] B 服务器 `ls /opt/backup/mysql/` 有今天的 `.sql.gz`（明天早上看）

---

## 8. 日常维护手册

### 8.1 查看 A 服务器后端日志

```bash
# 实时日志
journalctl -u campus-pulse -f

# 最近 500 行
journalctl -u campus-pulse -n 500

# 业务日志文件
tail -f /opt/campus-pulse/log/logfile.log
```

### 8.2 重启服务

```bash
# 后端
systemctl restart campus-pulse

# Nginx
systemctl reload nginx   # 改配置后
nginx -t                 # 改前先测语法

# MySQL
systemctl restart mysql

# Redis
systemctl restart redis-server
```

### 8.3 更新后端代码

```bash
# 本地
cd "D:/2026毕业设计/DaiMa/campus-pulse(back)/campus-pulse"
mvn clean package -DskipTests -P prod
scp target/campus-pulse-0.0.1-SNAPSHOT.jar root@A服务器IP:/tmp/new.jar

# A 服务器
cp /tmp/new.jar /opt/campus-pulse/campus-pulse.jar
systemctl restart campus-pulse
journalctl -u campus-pulse -f   # 看启动成功
```

### 8.4 更新前端

**方式 A（Git）**：`git push` 就完事，Cloudflare Pages 自动构建。

**方式 B（手动）**：`npm run build` → Cloudflare Pages → Upload assets。

### 8.5 备份恢复

**从 B 恢复数据到 A**：

```bash
# B 服务器：下最新的备份
scp /opt/backup/mysql/campus_pulse_20260421_030000.sql.gz root@A服务器IP:/tmp/

# A 服务器：恢复
cd /tmp
gunzip campus_pulse_20260421_030000.sql.gz
mysql -u root -p campus_pulse < campus_pulse_20260421_030000.sql
```

### 8.6 Cloudflare 紧急清缓存

Cloudflare Dashboard → 你的域名 → **Caching → Configuration → Purge Everything**（慎用）  
或 **Purge by URL** 只清某几个接口。

---

## 9. 常见问题 FAQ

**Q1：前端访问 API 跨域失败**  
A：检查 `.env.prod` 里 `ALLOWED_ORIGIN_PATTERNS` 是否包含前端域名（`https://example.com`，不要带末尾斜杠）。改完 `systemctl restart campus-pulse`。

**Q2：上传图片提示"人机验证不通过"**  
A：上传接口不走 Turnstile，登录才走。检查是不是在登录时出问题。看 A 服务器日志 `grep -i turnstile /opt/campus-pulse/log/logfile.log`。

**Q3：CF Pages 构建失败**  
A：最常见是 Node 版本。Environment variables 加 `NODE_VERSION=20`。再看构建日志具体错误。

**Q4：Grafana 抓不到 Prometheus 数据**  
A：`curl https://api.example.com/actuator/prometheus` 在 B 上能不能访问？不能的话可能是 A 的防火墙/CF WAF 挡了。Spring Boot 的 `/actuator/prometheus` 默认不鉴权，流量出口只能信任。生产建议在 A 的 Nginx 上给 `/actuator/prometheus` 加 Basic Auth，Prometheus 配置里加 `basic_auth:`。

**Q5：A 服务器内存报警**  
A：JVM 启动参数 `-Xmx2g` 在 4G 机器上稍紧。可以降到 `-Xms512m -Xmx1536m`。修改 `/etc/systemd/system/campus-pulse.service` 的 `ExecStart` 后 `systemctl daemon-reload && systemctl restart campus-pulse`。

**Q6：R2 直传失败，浏览器 CORS 报错**  
A：R2 桶也要配 CORS。Cloudflare Dashboard → R2 → 你的桶 → **Settings → CORS Policy**：

```json
[
  {
    "AllowedOrigins": ["https://example.com", "https://www.example.com"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

**Q7：怎么看缓存命中率**  
A：F12 → Network → 看请求响应头 `cf-cache-status`：
- `HIT` = 命中缓存
- `MISS` = 未命中，从源站拿
- `DYNAMIC` = 不可缓存（POST/PUT 等）
- `BYPASS` = 匹配了 bypass 规则

**Q8：怎么提前预热缓存**  
A：部署完新版本后，用 `curl` 手动请求一次热门接口：
```bash
curl -s "https://api.example.com/heat-rank/list?limit=50" > /dev/null
curl -s "https://api.example.com/trend-stat/daily?days=7" > /dev/null
```

**Q9：A 挂了怎么快速切到维护页**  
A：Cloudflare DNS → 把 `example.com` 记录的 Content 临时改成 B 服务器 IP → Proxy 保持开。几分钟生效。修完 A 再切回去。

**Q10：证书什么时候过期**  
A：CF Origin Certificate 有效期 15 年。Edge 证书 CF 自动续。监控里可以加个证书到期检查，或者每年 4 月检查一次。

---

## 附录：目录结构速查

```
A 服务器
/opt/campus-pulse/
├── campus-pulse.jar       # 后端 jar
├── .env.prod              # 环境变量（600 权限）
├── log/
│   ├── logfile.log        # 业务日志
│   ├── stdout.log
│   └── stderr.log
└── scripts/               # 从项目拷过来的 deploy.sh 等

/etc/nginx/
├── sites-available/campus-pulse
├── sites-enabled/campus-pulse
└── ssl/
    ├── origin.pem
    └── origin.key

/etc/systemd/system/campus-pulse.service

B 服务器
/opt/monitor/
├── docker-compose.yml
├── prometheus/
│   └── prometheus.yml
└── grafana-data/

/opt/backup/
├── backup-a-mysql.sh
├── backup.log
└── mysql/
    └── campus_pulse_YYYYMMDD_HHMMSS.sql.gz

/var/www/maintenance/
└── index.html

/etc/nginx/sites-available/
├── monitor
└── maintenance
```

---

**部署完成后，发个朋友圈吧 🎉**
