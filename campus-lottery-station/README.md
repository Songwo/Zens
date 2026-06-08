# Campus Pulse Lottery

社区抽奖福利站，作为 Campus Pulse 主社区的独立 SSO 子站示例。项目默认使用 Go 标准库后端和内嵌静态前端，不依赖 npm 包，适合快速演示“主站账号登录、参与抽奖、管理员开奖、结果留痕”的完整闭环。

## 功能

- 社区 SSO 登录入口：`/api/auth/sso/start`
- 演示登录入口：无需主站也可以本地体验
- 抽奖活动列表、活动详情、规则、参与记录
- 等级门槛、积分消耗、人数限制、重复参与限制
- 管理员创建抽奖、立即开奖
- 中奖名单、社区公告文案导出和运营审计
- JSON 本地持久化：`server/data/state.json`

## 启动

```bash
cd campus-lottery-station/server
go run .
```

默认访问：

```text
http://localhost:8093
```

演示账号：

- 普通用户：点击“演示登录”，输入 `demo`
- 管理员：点击“演示登录”，输入 `admin`

## SSO 接入

默认配置在 `.env.example` 中。正式接入时，先在主社区 SSO client 管理中注册：

```text
client_id: campus-lottery-station
redirect_uri: http://localhost:8093/api/auth/sso/callback
```

然后配置环境变量：

```bash
set LOTTERY_PUBLIC_URL=http://localhost:8093
set COMMUNITY_BASE_URL=http://localhost:8080
set COMMUNITY_SSO_AUTHORIZE_URL=http://localhost:8080/api/sso/authorize
set COMMUNITY_SSO_TOKEN_URL=http://localhost:8080/api/sso/token
set SSO_CLIENT_ID=campus-lottery-station
set SSO_CLIENT_SECRET=你的主站客户端密钥
go run .
```

当前后端已经预留授权码换票逻辑。如果主站 token 响应字段和本站假设不一致，只需要调整 `exchangeSSOCode` 中的解析结构。

## API 摘要

- `GET /api/bootstrap`：页面初始化数据
- `POST /api/auth/dev-login`：本地演示登录
- `POST /api/auth/logout`：退出
- `GET /api/auth/sso/start`：跳转主站授权
- `GET /api/auth/sso/callback`：SSO 回调
- `GET /api/lotteries`：抽奖列表
- `POST /api/lotteries/{id}/join`：参与抽奖
- `POST /api/lotteries/{id}/draw`：管理员开奖
- `POST /api/admin/lotteries`：管理员创建抽奖
- `GET /api/admin/lotteries/{id}/announcement`：管理员导出社区公告文案
- `GET /api/admin/audit`：管理员审计记录
