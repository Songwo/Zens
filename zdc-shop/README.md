# Zens 积分商城 · zdc-shop

> Zens 社区会员专属积分商城子站，基于 Next.js 15 + Tailwind v3 + Prisma + MySQL。
> 视觉走 Editorial / 杂志式风格，杜绝传统卡片网格，仅靠分割线与留白节奏组织内容。
> 仅允许带 `sso:true` 且 `client_id=zdc-shop` 的 Zens 主站 SSO JWT 登录。

---

## ✨ 设计语言

- **品牌色**：Zens-Yellow `#F4B400`（亮模式）/ `#FFCB33`（暗模式）
- **明暗双主题**：默认跟随系统，可手动切换（`next-themes`）
- **无卡片**：内容块之间仅用 `1px var(--divider)` 横线 + 48~64px 垂直间距
- **Hover 反馈**：左侧 3px 黄色立柱 transition-in + 标题轻微位移
- **字体**：Inter（正文）+ JetBrains Mono（数字）
- **响应式**：移动端胶囊浮动导航替代传统底栏

---

## 🏗️ 技术栈

| 角色 | 技术 |
| ---- | ---- |
| 框架 | Next.js 15 (App Router) + React 18 |
| 样式 | Tailwind v3 + CSS custom property tokens |
| 状态 | SWR (客户端缓存) |
| ORM | Prisma 5 + MySQL 8 |
| 会话 | iron-session (HTTP-only encrypted cookie) |
| SSO 验签 | jose (HS256/HS512 共享 `jwt.secret`) |
| Toast | sonner |
| 部署 | Node 20 + Next.js standalone + Docker |

---

## 🚀 本地开发

### 1. 依赖
```powershell
cd zdc-shop
npm install
```

### 2. 环境变量
```powershell
copy .env.example .env.local
# 编辑 .env.local：
# - DATABASE_URL 指向主站同实例的 zens_shop 库
# - JWT_SECRET 与主站 application.yml 的 jwt.secret 完全一致
# - SHOP_SERVICE_SECRET 与主站 SHOP_SERVICE_SECRET 完全一致
# - SESSION_PASSWORD 任意 32+ 字符随机串
```

### 3. 数据库
```powershell
# 在主站 MySQL 上手动建库
mysql -u root -p -e "CREATE DATABASE zens_shop DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 用 Prisma 推 schema
npm run db:push

# 灌示例商品 + 兑换码
npm run db:seed
```

### 4. 启动
```powershell
npm run dev          # http://localhost:3000
```

### 5. 主站侧准备 (一次性)
- 在主站 `sys_sso_client` 表里 INSERT 一行 `client_id=zdc-shop`，`redirect_uri=http://localhost:3000/login/callback`
- 在主站 `application.yml` 增加：
  ```yaml
  internal:
    service:
      shop-secret: ${SHOP_SERVICE_SECRET:dev-shop-secret-change-me}
  ```
- 启动新增的 `InternalUserController` / `InternalServiceFilter`

---

## 📂 目录结构

```
zdc-shop/
├── prisma/
│   ├── schema.prisma            # zs_product / zs_order / zs_redemption_code / zs_user_sync
│   └── seed.ts                  # 6 个示例商品 + 各 20 条兑换码
├── public/
│   └── favicon.svg
└── src/
    ├── app/
    │   ├── layout.tsx           # ThemeProvider, fonts, Toaster
    │   ├── globals.css          # 全部设计 token + Editorial 组件类
    │   ├── page.tsx             # Editorial 首页
    │   ├── (shop)/
    │   │   ├── products/[slug]/page.tsx
    │   │   └── orders/page.tsx
    │   ├── (auth)/
    │   │   ├── login/page.tsx
    │   │   └── login/callback/page.tsx
    │   ├── admin/
    │   └── api/
    │       ├── auth/community-config/route.ts
    │       ├── auth/community-login/route.ts
    │       ├── auth/me/route.ts
    │       ├── auth/logout/route.ts
    │       ├── products/
    │       ├── orders/
    │       └── admin/
    ├── components/
    │   ├── site-header.tsx
    │   ├── site-footer.tsx
    │   ├── mobile-bottom-nav.tsx
    │   ├── theme-provider.tsx
    │   ├── theme-toggle.tsx
    │   ├── balance-pill.tsx
    │   ├── user-menu.tsx
    │   └── editorial/
    └── lib/
        ├── utils.ts
        ├── auth/session.ts
        ├── main-site/           # HMAC 调主站 (TODO task #3)
        └── db.ts                # Prisma singleton (TODO task #4)
```

---

## 🔐 SSO 流程

```
1. 浏览器 → /login → 点 [使用 Zens 账号继续]
2. fetch /api/auth/community-config → { communityUrl, clientId }
3. window.location = `${communityUrl}/sso/authorize?client_id=zdc-shop&redirect_uri=...`
4. 主站若未登录,跳 /auth/login → 登录后回到 /sso/authorize 页面
5. 用户在主站确认授权 → 主站 POST /sso/authorize → 拿到 ssoToken
6. 主站前端 redirect 到 shop.allinsong.top/login/callback?sso_token=...
7. 子站 POST /api/auth/community-login { ssoToken }
   - jose.jwtVerify(token, JWT_SECRET)
   - assert claims.sso === true && client_id === 'zdc-shop'
   - upsert UserSync + iron-session.save({ userId, username, role, ... })
8. 完成,跳回 sessionStorage.zs_return_url
```

---

## 🤝 与主站的依赖

| 主站资源 | 用途 |
| -------- | ---- |
| `application.yml` 中的 `jwt.secret` | 子站验签 ssoToken |
| `application.yml` 中的 `internal.service.shop-secret` | s2s HMAC 共享密钥 |
| `sys_sso_client` 表 | 注册 `zdc-shop` 应用 |
| `sys_user.points` 字段 | 强一致的积分余额 |
| `POST /sso/authorize` | 颁发 ssoToken |
| `GET /api/internal/user/{userId}/points` | 查余额 (HMAC) |
| `POST /api/internal/user/{userId}/points/consume` | 原子扣减 (HMAC + 幂等) |

⚠️ **`sys_user.points` 当前没有任何业务写入逻辑**——只有此商城做消费动作。如果需要让用户先有积分，可让主站补一个签到 / 发帖奖励的逻辑（不在本子站范围）。

---

## 🖼️ 图片存储 · Cloudflare R2 小白教程

子站的商品封面、未来其他用户上传图都走 **Cloudflare R2** 对象存储 (S3 兼容)。
浏览器直接 PUT 到 R2,Next.js 服务器不经手文件本身,只下发一次性签名链接。

> R2 优点:全球 CDN、出站流量免费、价格便宜、和 Cloudflare DNS / Pages 协同好。

### 整体流程

```
1. 浏览器选图
2. POST /api/admin/upload { filename, contentType, size }
3. 子站后端 (requireAdmin) → @aws-sdk/s3-request-presigner 签一个 60s PUT URL
4. 浏览器 fetch(uploadUrl, { method: "PUT", body: file })  ← 直连 R2,不经过子站
5. 浏览器把 publicUrl 写回表单 → 走商品保存接口落 DB
6. 前台展示时,浏览器 GET https://cdn.zens.community/<key> ← 走 Cloudflare CDN
```

### Step 1 · 注册 Cloudflare 账号并开通 R2

1. 打开 [dash.cloudflare.com](https://dash.cloudflare.com),用邮箱注册一个账号 (免费)
2. 左侧栏选 **R2 Object Storage** → 第一次进会要你 **添加付款方式**
   - R2 有 10GB / 月免费额度,只是要绑卡防滥用
   - 不放心可以用一张额度低的副卡

### Step 2 · 创建 Bucket

1. R2 控制台 → **Create bucket**
2. **Bucket name**:`zdc-shop-assets` (可自定义,要全小写,后面要填到 `R2_BUCKET`)
3. **Location**:留 *Automatic*,Cloudflare 会就近选
4. **Default Storage Class**:`Standard`
5. 创建完先不要开 *Allow Access*,我们走自定义域名

### Step 3 · 绑定自定义域名 (推荐: cdn.zens.community)

> 自定义域名 = 走 Cloudflare CDN,稳定 + 可缓存 + URL 好看。
> 替代方案是 R2 自带的 `pub-xxx.r2.dev`,有限速,不推荐生产。

**前提**:`zens.community` (或你自己的域名) 必须在这个 Cloudflare 账号下托管 DNS。
如果还没托管:

1. Cloudflare 主控台 → **Add a site** → 填 `zens.community`
2. 按提示去你的注册商 (阿里云/Namecheap/Cloudflare Registrar 等) 把 NS 改成 Cloudflare 给的两条
3. 等 NS 生效 (5 分钟 ~ 24 小时)

域名托管 OK 之后:

1. R2 控制台 → 你的 bucket → **Settings** → **Public Access** 区域 → **Custom Domains** → **Connect Domain**
2. 输入 `cdn.zens.community` → Continue
3. Cloudflare 会自动给你建一条 CNAME → 直接 Save 即可
4. 等 1~2 分钟,状态变成 **Active**
5. 浏览器访问 `https://cdn.zens.community/` 应该出现 R2 默认的 403 或空响应 = 域名 OK

> 这一步对应 `.env.local` 里的 `R2_PUBLIC_BASE_URL=https://cdn.zens.community`

### Step 4 · 创建 R2 API Token (Access Key)

1. R2 控制台 → 顶部 **Manage R2 API Tokens** (或 *Account Home* → API → R2 Tokens)
2. **Create API token**
3. 配置:
   - **Token name**:`zdc-shop-upload` (随意)
   - **Permissions**:选 **Object Read & Write**
   - **Specify bucket**:选你刚建的 `zdc-shop-assets` (只允许操作这一个,最小权限)
   - **TTL**:留默认 *Forever* 或自己设一个到期时间
4. 创建后页面会**一次性**展示:
   - **Access Key ID** → 填到 `R2_ACCESS_KEY_ID`
   - **Secret Access Key** → 填到 `R2_SECRET_ACCESS_KEY`
   - **Endpoint** 形如 `https://<accountId>.r2.cloudflarestorage.com` → `<accountId>` 填到 `R2_ACCOUNT_ID`

> ⚠️ Secret Access Key **只显示这一次**,关掉就再也看不到了,务必先复制保存好。

### Step 5 · 配置 CORS (关键!)

浏览器直传 R2 受同源策略限制,必须给 bucket 加一条 CORS 规则,否则前端会报
`CORS error: No 'Access-Control-Allow-Origin' header`。

1. R2 控制台 → 你的 bucket → **Settings** → **CORS Policy** → **Add CORS policy**
2. 粘贴:

```json
[
  {
    "AllowedOrigins": [
      "http://localhost:3000",
      "https://shop.allinsong.top"
    ],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["content-type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

要点:
- `AllowedOrigins` 把所有会发起上传的前端域名都加进去 (本地、预发布、生产)
- `AllowedMethods` 至少要 `PUT` (上传) + `GET`/`HEAD` (浏览器优化预检)
- 切勿写 `"*"` 配合 credentials,不安全

### Step 6 · 填 .env.local

```bash
# .env.local
R2_ACCOUNT_ID="你的 cloudflare account id"
R2_ACCESS_KEY_ID="刚才创建 token 拿到的 Access Key ID"
R2_SECRET_ACCESS_KEY="刚才创建 token 拿到的 Secret Access Key"
R2_BUCKET="zdc-shop-assets"
R2_PUBLIC_BASE_URL="https://cdn.zens.community"
```

> ⚠️ 改完 env 必须重启 `npm run dev`,Next.js 启动时才会读 `.env.local`。

### Step 7 · 验证一次端到端上传

1. `npm run dev` 起服务
2. 登录管理员账号 → 后台 → 新建商品
3. 点封面区域选一张图 (≤ 5MB,JPG/PNG/WebP/GIF/SVG)
4. 控制台 (F12 Network) 会依次看到:
   - **POST /api/admin/upload** 200 → 返回 `{uploadUrl, publicUrl, key, ...}`
   - **PUT https://`<accountId>`.r2.cloudflarestorage.com/...** 200 → 上传成功
5. 表单里的 `coverUrl` 自动填入 `https://cdn.zens.community/products/2026/05/<key>.jpg`
6. 提交商品 → 前台访问 → 图正常显示

### 常见问题排查

| 现象 | 原因 | 解决 |
| ---- | ---- | ---- |
| `CORS error: blocked by CORS policy` | bucket CORS 没配或域名不在 AllowedOrigins | 回 Step 5 检查,加上当前页面的 origin |
| `PUT 403 SignatureDoesNotMatch` | 浏览器实际 PUT 用的 Content-Type 和签发时不一致 | 不要手动改 fetch 的 headers,组件默认就对 |
| `POST /api/admin/upload 500 PRESIGN_FAILED` | env 缺失或 R2 token 失效 | 看服务端日志,核对 R2_ACCOUNT_ID / Access Key |
| 上传 OK 但 `https://cdn...` 显示 404 | 自定义域名还没生效或 bucket key 拼错 | 等 1 分钟,或直接用 R2 控制台 Object 列表验证 key |
| 上传 OK 但前端访问被拦 | 没在 `next.config.ts` 加 host | 已自动从 `R2_PUBLIC_BASE_URL` 提取,如果改了域名需重启 dev |
| `403 Forbidden` 访问 publicUrl | bucket 没绑 Public Access (custom domain) | 回 Step 3 把自定义域名 *Connect* 一遍 |

### 安全注意

- R2 token 选 **Object Read & Write** + **指定单个 bucket**,不要给 Admin Read & Write
- `R2_SECRET_ACCESS_KEY` 是高危机密,**绝不**进 git;`.env.local` 已经在 `.gitignore`
- presigned URL 默认 60s 过期 ([src/lib/r2.ts](src/lib/r2.ts)),签发接口本身受 `requireAdmin` 保护,普通用户拿不到
- 5MB 上限 + 类型白名单在服务端校验 ([src/app/api/admin/upload/route.ts](src/app/api/admin/upload/route.ts)),不要在客户端再放宽

---

## 📦 部署

```bash
docker build -t zdc-shop .
docker run -d --name zdc-shop \
  -p 3000:3000 \
  --env-file .env.production \
  zdc-shop
```

外部 nginx 加：
```nginx
server {
  listen 443 ssl http2;
  server_name shop.allinsong.top;
  ssl_certificate /etc/letsencrypt/.../fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/.../privkey.pem;

  location / {
    proxy_pass http://127.0.0.1:3000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }
}
```

---

## 📝 License

MIT © 2026 Zens / SongWo
