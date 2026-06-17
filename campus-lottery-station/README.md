# Campus Pulse Lottery

Zens 社区抽奖子站。它作为主站的 SSO 子系统运行，用主站账号登录，读取主站帖子和评论，按楼层/去重/排除楼主等规则生成参与名单，并支持由抽奖机器人把中奖名单回写到原帖。

## 已对接能力

- 主站 SSO：跳转到主站 `/sso/authorize`，回调接收 `sso_token`，验证通过后写入本站 HttpOnly 会话。
- 主站帖子读取：`GET /post/{postId}`。
- 主站评论读取：`GET /comment/post/{postId}`，会展开子回复，按创建时间升序重新计算楼层。
- 抽奖限制：同步评论、预览名单、开奖均要求登录；官方发布结果要求管理员或版主。
- 参与过滤：匿名评论、已删除/驳回评论会被过滤；支持排除发帖人、用户去重、截止楼层。
- 结果发布：配置机器人 token 后，通过 `POST /comment/create` 在原帖发布中奖名单。

## 本地运行

主站后端默认端口是 `7800`，主站前端默认端口通常是 `5173` 或同源部署后的站点地址。

```bash
cd campus-lottery-station
npm install
npm run build

cd server
go run .
```

访问：

```text
http://localhost:8093
```

本地没有配置主站 SSO 时，也可以用演示登录接口调试：

```bash
curl -X POST http://localhost:8093/api/auth/dev-login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"displayName\":\"社区运营管理员\",\"level\":6,\"points\":999}"
```

## 主站对接配置

先在主站后台进入 SSO 应用管理，新增应用：

```text
client_id: campus-lottery-station
client_name: Zens 抽奖站
redirect_uri: http://localhost:8093/api/auth/sso/callback
```

然后给抽奖站配置环境变量。Windows PowerShell 示例：

```powershell
$env:LOTTERY_ADDR=":8093"
$env:LOTTERY_PUBLIC_URL="http://localhost:8093"
$env:LOTTERY_LOGO_URL="/logo.png"
$env:LOTTERY_DATA="./data/state.json"
$env:LOTTERY_SESSION_SECRET="replace-with-a-long-random-string"

$env:COMMUNITY_BASE_URL="http://localhost:5173"
$env:COMMUNITY_API_BASE_URL="http://localhost:7800"
$env:COMMUNITY_SSO_AUTHORIZE_URL="http://localhost:5173/sso/authorize"
$env:COMMUNITY_JWT_SECRET="和主站 JWT_SECRET 保持一致"

$env:SSO_CLIENT_ID="campus-lottery-station"
$env:SSO_CLIENT_SECRET="主站 SSO 应用密钥"

go run .
```

Linux/macOS 示例：

```bash
export LOTTERY_ADDR=:8093
export LOTTERY_PUBLIC_URL=http://localhost:8093
export LOTTERY_LOGO_URL=/logo.png
export LOTTERY_DATA=./data/state.json
export LOTTERY_SESSION_SECRET=replace-with-a-long-random-string

export COMMUNITY_BASE_URL=http://localhost:5173
export COMMUNITY_API_BASE_URL=http://localhost:7800
export COMMUNITY_SSO_AUTHORIZE_URL=http://localhost:5173/sso/authorize
export COMMUNITY_JWT_SECRET='和主站 JWT_SECRET 保持一致'

export SSO_CLIENT_ID=campus-lottery-station
export SSO_CLIENT_SECRET='主站 SSO 应用密钥'

go run .
```

## 机器人发布配置

如果只需要开奖和导出文本，可以不配置机器人。若要把中奖名单发布回原帖，需要二选一：

```bash
export LOTTERY_BOT_ACCESS_TOKEN='主站机器人 accessToken'
```

或配置机器人账号密码，让抽奖站自动登录主站：

```bash
export LOTTERY_BOT_USERNAME=zens-lottery-bot
export LOTTERY_BOT_PASSWORD='机器人密码'
```

机器人账号需要能调用主站 `POST /comment/create`。

## 快速部署

### Docker + 宝塔 + Cloudflare 子域名

如果你已经用宝塔管理 Nginx，推荐直接用 Docker 跑抽奖站，再让宝塔反代到本机端口。抽奖站镜像会在构建时完成前端打包，并把静态文件打进 Go 二进制。

1. Cloudflare 解析子域名

在 Cloudflare DNS 添加：

```text
Type: A
Name: lottery
Content: 你的服务器公网 IP
Proxy status: Proxied
```

SSL/TLS 建议设置为 `Full (strict)`。宝塔站点可以使用 Cloudflare Origin Certificate 或宝塔自己的 Let's Encrypt 证书。

2. 服务器拉代码

```bash
cd /www/wwwroot
git clone -b feat/trust-level-and-optimizations https://github.com/Songwo/Zens.git zens
cd /www/wwwroot/zens/campus-lottery-station
```

如果服务器已有仓库：

```bash
cd /www/wwwroot/zens
git pull origin feat/trust-level-and-optimizations
cd campus-lottery-station
```

3. 创建 `.env`

```bash
cp .env.example .env
nano .env
```

最少需要改这些值：

```bash
LOTTERY_PUBLIC_URL=https://lottery.example.com
LOTTERY_LOGO_URL=https://zens.example.com/logo.png
LOTTERY_DATA=/app/data/state.json
LOTTERY_SESSION_SECRET=replace-with-a-long-random-string

COMMUNITY_BASE_URL=https://zens.example.com
COMMUNITY_API_BASE_URL=https://api.zens.example.com
COMMUNITY_SSO_AUTHORIZE_URL=https://zens.example.com/sso/authorize
COMMUNITY_JWT_SECRET=copy-main-site-jwt-secret

SSO_CLIENT_ID=campus-lottery-station
SSO_CLIENT_SECRET=copy-main-site-sso-client-secret
```

4. 启动容器

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f --tail=100
```

容器只监听服务器本机：

```text
127.0.0.1:8093 -> container:8093
```

运行数据保存在 Docker volume `zens-lottery-data` 中，更新镜像不会清空抽奖数据。

本机检查：

```bash
curl http://127.0.0.1:8093/api/health
```

5. 宝塔反向代理

宝塔面板添加站点：

```text
域名：lottery.example.com
PHP：纯静态
```

进入站点设置 -> 反向代理 -> 添加反向代理：

```text
代理名称：zens-lottery
目标 URL：http://127.0.0.1:8093
发送域名：$host
```

如果需要手动 Nginx 配置，可用：

```nginx
location / {
    proxy_pass http://127.0.0.1:8093;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

6. 主站 SSO 应用配置

主站后台 SSO 应用必须配置：

```text
clientId: campus-lottery-station
clientSecret: 和 .env 的 SSO_CLIENT_SECRET 一致
redirectUri: https://lottery.example.com/api/auth/sso/callback
logoUrl: https://zens.example.com/logo.png
```

7. 更新部署

```bash
cd /www/wwwroot/zens
git pull origin feat/trust-level-and-optimizations
cd campus-lottery-station
docker compose up -d --build
docker image prune -f
```

常用排查：

```bash
docker compose logs -f --tail=100
docker compose restart
docker compose down
curl http://127.0.0.1:8093/api/bootstrap
```

登录后提示 `invalid-token` 时，优先检查 `.env` 里的 `COMMUNITY_JWT_SECRET` 是否和主站后端 `JWT_SECRET` 完全一致。授权回调失败时，检查主站 SSO 应用的 `redirectUri`。

### systemd 裸机部署

1. 准备服务器

```bash
sudo apt update
sudo apt install -y git nginx
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
wget https://go.dev/dl/go1.22.3.linux-amd64.tar.gz
sudo tar -C /usr/local -xzf go1.22.3.linux-amd64.tar.gz
echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bashrc
source ~/.bashrc
```

2. 拉代码并构建

```bash
git clone <your-repo-url> campus-pulse
cd campus-pulse/campus-lottery-station
npm ci
npm run build
cd server
go build -o zens-lottery .
```

3. 创建环境变量文件

```bash
sudo mkdir -p /opt/zens-lottery/data
sudo tee /opt/zens-lottery/.env >/dev/null <<'EOF'
LOTTERY_ADDR=:8093
LOTTERY_PUBLIC_URL=https://lottery.example.com
LOTTERY_LOGO_URL=https://zens.example.com/logo.png
LOTTERY_DATA=/opt/zens-lottery/data/state.json
LOTTERY_SESSION_SECRET=replace-with-a-strong-random-string
LOTTERY_ALLOW_DEMO_SSO_FALLBACK=false
LOTTERY_COMMENT_MAX_PAGES=50

COMMUNITY_BASE_URL=https://zens.example.com
COMMUNITY_API_BASE_URL=https://api.zens.example.com
COMMUNITY_SSO_AUTHORIZE_URL=https://zens.example.com/sso/authorize
COMMUNITY_JWT_SECRET=copy-main-site-jwt-secret

SSO_CLIENT_ID=campus-lottery-station
SSO_CLIENT_SECRET=copy-main-site-sso-client-secret

LOTTERY_BOT_ACCESS_TOKEN=
LOTTERY_BOT_USERNAME=zens-lottery-bot
LOTTERY_BOT_PASSWORD=
EOF
```

4. 安装二进制并创建 systemd 服务

```bash
sudo cp zens-lottery /opt/zens-lottery/zens-lottery
sudo tee /etc/systemd/system/zens-lottery.service >/dev/null <<'EOF'
[Unit]
Description=Zens Lottery Station
After=network.target

[Service]
WorkingDirectory=/opt/zens-lottery
EnvironmentFile=/opt/zens-lottery/.env
ExecStart=/opt/zens-lottery/zens-lottery
Restart=always
RestartSec=3
User=www-data
Group=www-data

[Install]
WantedBy=multi-user.target
EOF

sudo chown -R www-data:www-data /opt/zens-lottery
sudo systemctl daemon-reload
sudo systemctl enable --now zens-lottery
sudo systemctl status zens-lottery
```

5. 配置 Nginx 反代

```nginx
server {
    listen 80;
    server_name lottery.example.com;

    location / {
        proxy_pass http://127.0.0.1:8093;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

启用配置后：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

6. 上线检查

```bash
curl https://lottery.example.com/api/health
```

浏览器打开 `https://lottery.example.com`，点击“登录 / 连接社区账号”，授权后回到抽奖站。粘贴主站帖子链接，确认能同步评论和生成参与名单。

## 常见问题

- 登录后回到抽奖站提示 `invalid-token`：检查 `COMMUNITY_JWT_SECRET` 是否和主站 `JWT_SECRET` 一致。
- 主站授权页提示回调不匹配：检查主站 SSO 应用里的 `redirect_uri` 是否包含抽奖站回调地址。
- 能开奖但不能发布原帖评论：检查 `LOTTERY_BOT_ACCESS_TOKEN` 或机器人账号密码是否配置，并确认机器人有评论权限。
- 评论很多时同步失败：设置 `LOTTERY_COMMENT_MAX_PAGES`，或在页面输入截止楼层分批开奖。
