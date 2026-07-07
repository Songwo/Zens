# Zens 生态统一配置对照表

四个组件:主站 campus-pulse + 三子站 zdc-shop(积分商城)/campus-lottery-station(抽奖站)/cdk-airdrop-station(空投站)。

本文档是配置真相源:改任意一处共享密钥/地址,先看这里影响哪些站。

## 命名原则

- 新名统一以 `MAIN_SITE_*` 开头指代"主站",各站服务身份用 `{SHOP,LOTTERY,CDK}_SERVICE_{ID,SECRET}`。
- 旧名长期兼容(新名优先、旧名回落),现有部署无需立即改环境变量。
- 前端可见的 `NEXT_PUBLIC_*` / `VITE_*` 前缀变量是构建期注入约束,不在本次统一改名范围,但值必须与对应 `MAIN_SITE_WEB_URL` 一致。

## 对照表

| 统一新名 | 语义 | 主站 | zdc-shop | campus-lottery-station | cdk-airdrop-station |
|---|---|---|---|---|---|
| `MAIN_SITE_API_URL` | 主站后端地址,子站服务端调用 `/api/internal/**` | — | `MAIN_SITE_API_URL`(旧:`MAIN_SITE_BACKEND_URL`) | `MAIN_SITE_API_URL`(旧:`COMMUNITY_API_BASE_URL`) | `MAIN_SITE_API_URL`(新增,原无) |
| `MAIN_SITE_WEB_URL` | 主站前端地址,SSO 跳转目标 | — | `MAIN_SITE_WEB_URL`/`NEXT_PUBLIC_COMMUNITY_URL`(前端必须用 `NEXT_PUBLIC_` 前缀) | `MAIN_SITE_WEB_URL`(旧:`COMMUNITY_BASE_URL`) | `MAIN_SITE_WEB_URL`(旧:`CDK_COMMUNITY_URL`) |
| `MAIN_SITE_JWT_SECRET` | SSO Token 验签密钥,三站必须与主站 `JWT_SECRET` 完全一致 | `JWT_SECRET` | `MAIN_SITE_JWT_SECRET`(旧:`JWT_SECRET`) | `MAIN_SITE_JWT_SECRET`(旧:`COMMUNITY_JWT_SECRET`) | `MAIN_SITE_JWT_SECRET`(旧:`CDK_COMMUNITY_JWT_SECRET`) |
| `{SITE}_SERVICE_ID` | HMAC 内部 API 的调用方标识(`X-Service-Id`) | `internal.service.clients[].id`(白名单) | `SHOP_SERVICE_ID=zdc-shop` | `LOTTERY_SERVICE_ID=campus-lottery-station` | `CDK_SERVICE_ID=cdk-airdrop` |
| `{SITE}_SERVICE_SECRET` | HMAC 共享密钥,必须与主站白名单同名条目完全一致 | `SHOP_SERVICE_SECRET`/`LOTTERY_SERVICE_SECRET`/`CDK_SERVICE_SECRET`(经 `internal.service.clients` 注入) | `SHOP_SERVICE_SECRET` | `LOTTERY_SERVICE_SECRET` | `CDK_SERVICE_SECRET` |

## 主站内部服务白名单(`internal.service.clients`)

配置于 `src/main/resources/application.yml` / `application-prod.yml`,新增子站只需加一项,无需改 Java 代码:

```yaml
internal:
  service:
    clients:
      - id: zdc-shop
        secret: ${SHOP_SERVICE_SECRET:...}
      - id: campus-lottery-station
        secret: ${LOTTERY_SERVICE_SECRET:...}
      - id: cdk-airdrop
        secret: ${CDK_SERVICE_SECRET:...}
```

启动时校验:id 重复或 secret 空白直接 fail-fast(`InternalServiceProperties`)。

## 密钥泄露轮换步骤

1. **`MAIN_SITE_JWT_SECRET`(原 `JWT_SECRET`)泄露**:影响全部三站的 SSO。主站生成新密钥 → 同步更新四处配置(主站 + 三子站)→ 依次重启,重启窗口内正在登录的用户需要重新走 SSO。
2. **某站 `{SITE}_SERVICE_SECRET` 泄露**:只影响该站的内部 API 调用。只需更新主站白名单该条目 + 该子站配置,其余站不受影响(这是白名单配置化的意义所在)。
3. 轮换后用 `docs/` 里的端到端 runbook 走一遍签到→兑换→抽奖→领 CDK,确认账本 (`sys_point_txn`) 与各站功能都正常。

## 各站 `.env.example` 位置

- 主站:`.env.example`(根目录)
- zdc-shop:`zdc-shop/.env.example`
- campus-lottery-station:`campus-lottery-station/server/.env.example`
- cdk-airdrop-station:`cdk-airdrop-station/.env.example`

## 相关文档

- 积分一致性设计:`docs/superpowers/specs/2026-07-07-subsite-points-consistency-design.md`
