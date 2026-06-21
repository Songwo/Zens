# Zens 项目清单与 Docker 部署说明

本文档是当前仓库的总入口，方便快速分清主社区项目、子站项目、端口、配置文件和 Docker 部署方式。

## 1. 项目清单

| 项目 | 目录 | 技术栈 | 默认端口 | 说明 |
| --- | --- | --- | --- | --- |
| Zens 主社区后端 | `./` | Spring Boot 3 / MyBatis-Plus / MySQL / Redis | `7800` | 主账号、帖子、评论、积分、SSO、内部 API |
| Zens 主社区前端 | `./web` | Vue 3 / Vite / Element Plus / Tailwind | `5173` | 主社区 Web、后台管理、星港 |
| 积分商城 | `./zdc-shop` | Next.js 15 / Prisma / MySQL | `3000` | 使用主站 SSO 登录，强一致扣减/退回主站积分 |
| 抽奖站 | `./campus-lottery-station` | React / Vite / Go | `8093` | 读取主站帖子评论，开奖并可由机器人回写原帖 |
| CDK 空投站 | `./cdk-airdrop-station` | React / Vite / Go / PostgreSQL | `8088` | 活动、CDK、领取、风控、后台运营 |
| 媒体服务 | `./go-media-service` | Go / SQLite | `8090` | 独立媒体上传与管理服务 |
| 导航站 | `./zens-nav` | React / Vite | `4173` preview | 静态导航入口站 |

## 2. 统一部署前置

生产环境至少准备：

1. Docker 与 Docker Compose。
2. 主站可访问域名，例如 `https://allinsong.top`。
3. 主站后端 API 地址，例如 `https://allinsong.top`。如果你有独立 API 域名，也可以改成自己的地址。
4. 主站 `JWT_SECRET`，所有 SSO 子站必须与主站完全一致。
5. 根目录 `deploy-subsites.sh` 会自动检查 MySQL、创建缺失数据库，并在主站 `sys_sso_client` 中注册/修复三个第一方 SSO 应用。

建议域名：

| 子站 | 推荐域名 | 本机反代目标 |
| --- | --- | --- |
| 积分商城 | `https://shop.allinsong.top` | `http://127.0.0.1:3000` |
| 抽奖站 | `https://lottery.allinsong.top` | `http://127.0.0.1:8093` |
| CDK 空投站 | `https://cdk.allinsong.top` | `http://127.0.0.1:8088` |

## 3. 一键部署三个子站（推荐）

当主站已经在服务器上正常运行时，直接在仓库根目录执行：

```bash
cd /www/wwwroot/campus-pulse
chmod +x deploy-subsites.sh

MAIN_ENV_FILE=.env.production \
MAIN_SITE_BACKEND_URL=https://allinsong.top \
bash deploy-subsites.sh
```

脚本会自动完成：

1. 读取主站 `.env` / `.env.local` / `.env.production`，也支持 `MAIN_ENV_FILE` 指定。
2. 从主站 `DB_URL` 解析 MySQL 连接信息。
3. 创建缺失的 `zens_shop` 数据库。
4. 创建/修复主站 `sys_sso_client` 表、`trusted` 字段和三个 SSO 客户端：
   - `zdc-shop`
   - `campus-lottery-station`
   - `cdk-airdrop`
5. 生成并备份三个子站的生产 `.env` 文件。
6. 执行三个子站的 `docker compose up -d --build --remove-orphans`。
7. 检查本机健康接口并输出宝塔/Nginx 反代目标。

常用覆盖项：

```bash
# 指定域名 / 端口
SHOP_DOMAIN=shop.allinsong.top \
LOTTERY_DOMAIN=lottery.allinsong.top \
CDK_DOMAIN=cdk.allinsong.top \
SHOP_PORT=3000 \
LOTTERY_PORT=8093 \
CDK_AIRDROP_PORT=8088 \
bash deploy-subsites.sh

# 首次部署后初始化积分商城种子数据
RUN_SHOP_SEED=true bash deploy-subsites.sh

# 只写配置和启动容器，不修数据库/SSO
SKIP_DB=true bash deploy-subsites.sh
```

> 脚本只部署子站，不会重启主站。旧 `.env` 会自动备份为 `.env.bak.YYYYmmddHHMMSS`。

## 4. 积分商城 Docker 部署

配置文件：

- 示例：`zdc-shop/.env.example`
- 生产：`zdc-shop/.env`
- Docker：`zdc-shop/Dockerfile`
- Compose：`zdc-shop/docker-compose.yml`

最小部署：

```bash
cd zdc-shop
cp .env.example .env
nano .env
docker compose up -d --build
docker compose logs -f --tail=100
```

必须修改：

```env
DATABASE_URL="mysql://root:你的密码@host.docker.internal:3306/zens_shop"
JWT_SECRET="必须等于主站 JWT_SECRET"
SHOP_SERVICE_SECRET="必须等于主站 SHOP_SERVICE_SECRET"
MAIN_SITE_BACKEND_URL="https://allinsong.top"
NEXT_PUBLIC_COMMUNITY_URL="https://allinsong.top"
NEXT_PUBLIC_SITE_URL="https://shop.allinsong.top"
SESSION_PASSWORD="至少 32 位随机字符串"
R2_ACCOUNT_ID="你的 Cloudflare R2 Account ID"
R2_ACCESS_KEY_ID="你的 R2 Access Key"
R2_SECRET_ACCESS_KEY="你的 R2 Secret Key"
R2_BUCKET="zdc-shop-assets"
R2_PUBLIC_BASE_URL="https://cdn.allinsong.top"
```

说明：

- 容器启动时会执行 `npx prisma db push --accept-data-loss` 自动建表。
- 生产更严格时，可改成 Prisma migration 流程。
- `NEXT_PUBLIC_*` 已同时作为 build args 与 runtime env 注入，改域名后需要重新 `docker compose up -d --build`。

## 5. 抽奖站 Docker 部署

配置文件：

- 示例：`campus-lottery-station/.env.example`
- 生产：`campus-lottery-station/.env`
- Docker：`campus-lottery-station/Dockerfile`
- Compose：`campus-lottery-station/docker-compose.yml`

最小部署：

```bash
cd campus-lottery-station
cp .env.example .env
nano .env
docker compose up -d --build
docker compose logs -f --tail=100
```

必须修改：

```env
LOTTERY_PUBLIC_URL=https://lottery.allinsong.top
LOTTERY_LOGO_URL=https://allinsong.top/logo.png
LOTTERY_SESSION_SECRET=至少 32 位随机字符串
LOTTERY_SERVICE_SECRET=必须等于主站 LOTTERY_SERVICE_SECRET
COMMUNITY_BASE_URL=https://allinsong.top
COMMUNITY_API_BASE_URL=https://allinsong.top
COMMUNITY_SSO_AUTHORIZE_URL=https://allinsong.top/sso/authorize
COMMUNITY_JWT_SECRET=必须等于主站 JWT_SECRET
SSO_CLIENT_ID=campus-lottery-station
SSO_CLIENT_SECRET=主站 SSO 应用密钥
LOTTERY_BOT_ACCESS_TOKEN=
LOTTERY_BOT_USERNAME=zens-lottery-bot
LOTTERY_BOT_PASSWORD=机器人账号密码
```

说明：

- 只开奖和导出结果时，机器人凭据可以先不填。
- 要发布中奖名单到原帖，必须配置 `LOTTERY_BOT_ACCESS_TOKEN`，或配置机器人账号密码。
- 运行数据保存在 Docker volume `zens-lottery-data`。

## 6. CDK 空投站 Docker 部署

配置文件：

- 示例：`cdk-airdrop-station/.env.example`
- 生产：`cdk-airdrop-station/.env`
- Docker：`cdk-airdrop-station/Dockerfile`
- Compose：`cdk-airdrop-station/docker-compose.yml`

最小部署：

```bash
cd cdk-airdrop-station
cp .env.example .env
nano .env
docker compose up -d --build
docker compose logs -f --tail=100
```

必须修改：

```env
POSTGRES_PASSWORD=强密码
CDK_POSTGRES_DSN=postgres://cdk_airdrop:强密码@postgres:5432/cdk_airdrop?sslmode=disable
CDK_COMMUNITY_URL=https://allinsong.top
CDK_COMMUNITY_CLIENT_ID=cdk-airdrop
CDK_COMMUNITY_JWT_SECRET=必须等于主站 JWT_SECRET
HCAPTCHA_SECRET=生产 hCaptcha secret
HCAPTCHA_SITE_KEY=生产 hCaptcha site key
VITE_HCAPTCHA_SITE_KEY=生产 hCaptcha site key
```

说明：

- Dockerfile 已自包含构建 React 前端和 Go 后端，不需要手动 `npm run build`。
- PostgreSQL 会由 compose 自动启动，数据保存在 volume `postgres_data`。
- 如需 Redis/RabbitMQ 增强：

```bash
docker compose --profile cache up -d --build
```

## 7. 宝塔与 Cloudflare 反代

Cloudflare DNS 分别添加：

```text
shop     A  服务器公网 IP  Proxied
lottery  A  服务器公网 IP  Proxied
cdk      A  服务器公网 IP  Proxied
```

宝塔站点反代：

| 域名 | 目标 URL |
| --- | --- |
| `shop.allinsong.top` | `http://127.0.0.1:3000` |
| `lottery.allinsong.top` | `http://127.0.0.1:8093` |
| `cdk.allinsong.top` | `http://127.0.0.1:8088` |

Nginx 手动反代示例：

```nginx
location / {
    proxy_pass http://127.0.0.1:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 8. 常用检查命令

```bash
curl http://127.0.0.1:3000/api/auth/community-config
curl http://127.0.0.1:8093/api/health
curl http://127.0.0.1:8088/health
docker compose ps
docker compose logs -f --tail=100
```

SSO 登录失败优先检查：

1. 子站 `.env` 的 JWT secret 是否和主站 `JWT_SECRET` 完全一致。
2. 主站后台 SSO 应用是否启用。
3. 主站 SSO 应用的 `redirectUri` 是否包含当前子站真实回调地址。
4. Cloudflare/宝塔是否把 Host 和 `X-Forwarded-Proto` 传给容器。
