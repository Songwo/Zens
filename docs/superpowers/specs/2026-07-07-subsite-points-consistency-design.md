# Zens 生态积分一致性与连通性设计(方案 A:同构直连 + 配置驱动白名单)

日期:2026-07-07
状态:已获用户批准(方案 A;抽奖只扣参与分不返分;cdk 事件回流+统一配置;建账本表;新名优先旧名回落;做积分明细页;go-media-service 不纳入)

## 背景与问题

Zens 生态 = 主站 campus-pulse(Spring Boot 7800)+ 三个用户子站:

| 子站 | 技术栈 | SSO | 积分扣减 | 事件回流 |
|---|---|---|---|---|
| zdc-shop 积分商城 | Next.js | ✅ | ✅ 真实(consume/credit + Redis 幂等 + 自动退款) | ✅ |
| campus-lottery-station 抽奖站 | Go+React | ✅ | ❌ **假扣减** | ✅ 仅开奖中奖事件 |
| cdk-airdrop-station 空投站 | Go | ✅ | ❌ 无 | ❌ 无 |

核心问题(均已在代码中核实):

1. **抽奖站假扣减**:`campus-lottery-station/server/main.go:1361` `joinDraw` 只扣本地 `state.json` 快照余额(SSO 登录时从 token claims 抓的一次性快照,`main.go:1152`),用户主站 `sys_user.points` 一分不动。
2. **主站无积分账本**:流水借用 `level_exp_log`(仅 userId/expDelta/reason/createTime,见 `entity/LevelExpLog.java`),无变动后余额、来源站、订单号;幂等仅靠 Redis 7 天 TTL(`UserPointsServiceImpl.java:26`),Redis 数据丢失后同一 idempotencyKey 可重复扣减。无法对账、无法追溯。
3. **签到加分非原子**:`CheckInServiceImpl.java:83-90` 读-改-写更新 points,与子站并发扣减存在丢失更新竞态。
4. **内部服务白名单硬编码**:`filter/InternalServiceFilter.java:64-67` 构造器写死 `{zdc-shop, campus-lottery-station}`,新增子站必须改 Java 代码。
5. **配置命名四套各自为政**:同一个"主站后端地址",shop 叫 `MAIN_SITE_BACKEND_URL`、lottery 叫 `COMMUNITY_API_BASE_URL`、cdk 叫 `CDK_COMMUNITY_URL`(且指向前端);JWT 密钥 shop 叫 `JWT_SECRET`、lottery 叫 `COMMUNITY_JWT_SECRET`。
6. **商城客户端无超时无重试**:`zdc-shop/src/lib/main-site/client.ts:76` 裸 `fetch`。

## 设计原则

- **主站是积分的唯一事实源**(single source of truth),子站本地余额只是显示缓存。
- **同构复用**:抽奖站/cdk 照搬商城已验证的 HMAC + 幂等 + 补偿模式,不发明新协议。
- **幂等落库**:DB 唯一索引是幂等最终裁判,Redis 降级为快路径。
- **逐站可独立上线验证**,不做大爆炸重构。
- **宁可拒绝服务,不做假扣减**:主站不可达时积分类操作失败,绝不静默降级到本地记账。

---

## §1 主站 · 积分账本表 `sys_point_txn`

新迁移 `src/main/resources/sql/migrations/2026-07-07-add-point-txn-ledger.sql`(遵循 ALTER/CREATE 迁移纪律,不动 schema.sql 初始化流程之外的东西;schema.sql 同步追加建表语句供全新初始化):

```sql
CREATE TABLE IF NOT EXISTS `sys_point_txn` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `user_id`         varchar(64)  NOT NULL,
  `delta`           int          NOT NULL COMMENT '正=入账 负=扣减',
  `balance_after`   int          NOT NULL COMMENT '本笔完成后的余额',
  `source`          varchar(50)  NOT NULL COMMENT 'main-site|zdc-shop|campus-lottery-station|cdk-airdrop',
  `biz_type`        varchar(50)  NOT NULL COMMENT 'checkin|shop.order|shop.refund|lottery.join|lottery.refund|admin.adjust|...',
  `order_id`        varchar(100) DEFAULT NULL COMMENT '子站业务单号',
  `reason`          varchar(200) NOT NULL,
  `idempotency_key` varchar(200) NOT NULL COMMENT '作用域化幂等键',
  `created_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_point_txn_idem` (`idempotency_key`),
  KEY `idx_point_txn_user_time` (`user_id`, `created_at`),
  KEY `idx_point_txn_source_time` (`source`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水账本';
```

`idempotency_key` 存作用域化组合键,与现 Redis 键语义对齐:
- consume:`consume:{userId}:{clientKey}`
- credit:`credit:{userId}:{clientKey}`
- 站内赚分:`earn:{bizType}:{userId}:{bizKey}`(如 `earn:checkin:{userId}:{date}`)

对应 entity `PointTxn` + mapper `PointTxnMapper`(MyBatis-Plus,与现有 entity 风格一致)。

## §2 主站 · `UserPointsService` 重构为唯一收口

`UserPointsServiceImpl` 改造:

- `consume` / `credit` 加 `@Transactional(rollbackFor = Exception.class)`,事务内:
  1. 原子 UPDATE(consume 保留 `points >= amount` 守卫;credit 用 `COALESCE(points,0)+amount`);
  2. 查回最新 `points` 作为 `balance_after`(同事务内 `SELECT points FROM sys_user WHERE id=?`);
  3. INSERT `sys_point_txn`。
- INSERT 撞 `uk_point_txn_idem`(DuplicateKeyException)→ 事务回滚后查旧流水,按幂等重放语义返回上次结果(响应体从旧流水重建)。
- Redis 幂等缓存保留为快路径(命中则不进 DB),写路径成功后回写,TTL 7 天不变。**语义变化:Redis 丢失不再导致重复扣减,DB 唯一索引兜底。**
- 新增接口方法 `earn(String userId, int amount, String bizType, String bizKey, String reason)`:source=main-site,内部生成幂等键 `earn:{bizType}:{userId}:{bizKey}`,同一事务模式。供签到及未来站内加分场景使用。
- **不再写 `level_exp_log`**:`writeExpLog` 调用从 consume/credit 中移除(积分≠经验的历史借用,账本表取代其审计职能)。经验/等级体系(`LevelServiceImpl.addExperience`)不动。
- `getPoints` 不变。
- `EcosystemMetrics.recordPointsMutation` 埋点保留,新增 `earn` 操作类型。

**签到改造**:`CheckInServiceImpl.java:83-91` 的读-改-写替换为 `userPointsService.earn(userId, rewardPoints, "checkin", today.toString(), "每日签到")`——原子、并发安全、幂等(同一天重复请求天然防重)。签到的经验部分(`LevelService`)照旧。返回给前端的 `totalPoints` 改用 earn 返回的 `balance_after`。

## §3 主站 · 内部服务白名单配置化

`InternalServiceFilter` 的硬编码 map 改为 `@ConfigurationProperties(prefix = "internal.service")` 配置类 `InternalServiceProperties`(`List<Client> clients`,Client={id, secret}):

```yaml
internal:
  service:
    clients:
      - id: zdc-shop
        secret: ${SHOP_SERVICE_SECRET:dev-shop-service-secret-CHANGE_ME_at_least_16_chars}
      - id: campus-lottery-station
        secret: ${LOTTERY_SERVICE_SECRET:dev-lottery-service-secret-CHANGE_ME_at_least_16_chars}
      - id: cdk-airdrop
        secret: ${CDK_SERVICE_SECRET:dev-cdk-service-secret-CHANGE_ME_at_least_16_chars}
```

- 环境变量名不变,现有部署无感;旧 `internal.service.shop-secret`/`lottery-secret` 键从 yml 移除(它们只被 Filter 构造器读取,无其他引用)。
- 加子站从此只改 yml/env,不改 Java。
- 启动时校验:id 重复或 secret 为空 → 启动失败(fail-fast)。
- HMAC 算法、±60s 时窗、nonce 防重、常量时间比较全部不动。

## §4 抽奖站 · 真实扣减(核心)

**Go 侧内部 API 客户端补全**(`campus-lottery-station/server/main.go`,现有 `signedInternalHeaders`/`postSignedInternal` 之上):

- 泛化出 `callSignedInternal(method, path, body) (status int, respBody []byte, err error)`,支持 GET/POST 并返回响应体(现 `postSignedInternal` 只回 retryable/err,不够)。
- 新增 `consumeMainSitePoints(userID string, amount int, reason, orderID, idemKey string) (pointsAfter int, err error)` 调 `POST /api/internal/user/{id}/points/consume`;错误区分 `INSUFFICIENT_POINTS` / 网络错 / 其他业务错(解析主站 Result JSON 的 message 前缀码)。
- 新增 `creditMainSitePoints(...)` 同构,调 credit,用于补偿。
- 新增 `fetchMainSitePoints(userID)` 调 `GET /api/internal/user/{id}/points`,刷新显示缓存。

**joinDraw 改造**(`main.go:1335`):

```
costPoints == 0            → 现有流程不变(不调主站)
costPoints > 0 且未配密钥   → 拒绝参与:"本站未接入主站积分,暂不能参与付费抽奖"(绝不本地假扣)
costPoints > 0 且已配密钥   →
  1. 预检 canJoin(用本地缓存余额做友好提示,非权威)
  2. 调 consumeMainSitePoints(幂等键 lottery:join:{drawId}:{userId},重复点击安全)
     - INSUFFICIENT_POINTS → 400 "主站积分不足"
     - 网络错/5xx → 502 "主站暂时不可达,请稍后重试"(幂等键保证重试安全)
  3. 扣成功 → store.update 写参与记录 + 回写本地缓存 Points = pointsAfter
  4. store.update 失败 → 调 creditMainSitePoints 补偿退回(幂等键 lottery:refund:{drawId}:{userId},重试×3 指数退避);补偿也失败 → 记 ERROR 日志含全部上下文,靠幂等键人工/后续对账恢复
```

**显示缓存刷新**:`/api/me` 支持 `?refresh=1`(或登录后首次)经 `fetchMainSitePoints` 拉主站真值回写本地;consume 成功的 `pointsAfter` 即时回写。前端参与抽奖成功后刷新余额显示。

**canJoin 语义**(`main.go:778`):本地余额检查降级为预检提示,权威判定以主站 consume 结果为准。

## §5 cdk 空投站 · 事件回流 + 接入白名单

- 配置新增(`internal/config/config.go`):`CDK_SERVICE_ID`(默认 `cdk-airdrop`)、`CDK_SERVICE_SECRET`、`MAIN_SITE_API_URL`。
- 新增 `internal/mainsite/client.go`:HMAC 签名 + `POST /api/internal/subsite/events`,实现与抽奖站逐字节对齐(METHOD\nPATH\nTS\nNONCE\nsha256Hex(BODY),hex 小写),含指数退避×3(5xx/网络错重试,4xx 不重试)。
- 领取成功钩子:`ClaimNodeReward`/CDK 兑换成功后,若领取者有 `CommunityUserID`,**异步**(goroutine)回流:
  - eventId=`cdk:claim:{claimId}`(sanitize 到主站 `^[a-z0-9._:-]+$` 8-120 字符)
  - source=`cdk-airdrop`,eventType=`cdk.claim.success`,notifyUser=true
  - title/content 含项目名与奖励简述
- 未配密钥 → 跳过回流只记一行日志(优雅降级);回流失败不阻断领取。
- 主站侧:§3 白名单加入 `cdk-airdrop` 后即可通过 `InternalServiceFilter`;`SubsiteEventServiceImpl` 校验 source==serviceId 已就绪,无需改动。

## §6 商城 · 客户端韧性

`zdc-shop/src/lib/main-site/client.ts` 的 `call()`:

- `fetch` 加 `signal: AbortSignal.timeout(8000)`。
- 网络错误(fetch reject/timeout)与 5xx:自动重试 1 次,200ms 退避,重试前重新生成签名头(timestamp/nonce 不能复用);4xx 与业务错不重试。幂等键保证重试安全。
- 业务流程(PENDING 建单 → 扣分 → 发货 → 失败自动退款 → 事件回流)已达标,不动。

## §7 全生态 · 统一配置命名(新名优先 + 旧名回落)

| 统一新名 | 语义 | shop 旧名 | lottery 旧名 | cdk 旧名 |
|---|---|---|---|---|
| `MAIN_SITE_API_URL` | 主站后端 base | `MAIN_SITE_BACKEND_URL` | `COMMUNITY_API_BASE_URL` | (新增) |
| `MAIN_SITE_WEB_URL` | 主站前端 base(SSO 跳转) | `NEXT_PUBLIC_COMMUNITY_URL`* | `COMMUNITY_BASE_URL` | `CDK_COMMUNITY_URL` |
| `MAIN_SITE_JWT_SECRET` | SSO 验签密钥 | `JWT_SECRET` | `COMMUNITY_JWT_SECRET` | `CDK_COMMUNITY_JWT_SECRET` |
| `{SHOP,LOTTERY,CDK}_SERVICE_ID` | HMAC 服务标识 | `SHOP_SERVICE_ID` | `LOTTERY_SERVICE_ID` | (新增) |
| `{SHOP,LOTTERY,CDK}_SERVICE_SECRET` | HMAC 密钥 | `SHOP_SERVICE_SECRET` | `LOTTERY_SERVICE_SECRET` | (新增) |

\* Next.js `NEXT_PUBLIC_*` 前缀是构建期注入约束,shop 前端继续用 `NEXT_PUBLIC_COMMUNITY_URL`,服务端代码统一读 `MAIN_SITE_WEB_URL ?? NEXT_PUBLIC_COMMUNITY_URL`。

实现方式:各站配置加载处实现「新名优先、旧名回落」helper(Go:`envFirst("MAIN_SITE_API_URL", "COMMUNITY_API_BASE_URL")`;TS:`process.env.MAIN_SITE_API_URL ?? process.env.MAIN_SITE_BACKEND_URL`)。旧名标注 deprecated 但长期兼容。

三站 `.env.example` 重写为统一模板结构(分区:主站连接 / 服务身份 / 本站会话 / 站点元信息 / 其他),并新增 `docs/ecosystem-config.md`:一页对照表,列出每个变量在每个站的名字、与主站哪个变量必须一致、泄露时的轮换步骤。

## §8 主站 · 积分明细页

- 后端:`GET /user/points/transactions?page=&pageSize=`(登录态,`UserPointsController` 或挂现有 user controller),读 `sys_point_txn` 按 userId 分页倒序;响应含 delta、balanceAfter、source、bizType、reason、createdAt。附 `GET /user/points/summary`(当前余额 + 本月收支合计)。
- 前端(web/):个人中心新增「积分明细」入口与页面。Editorial 无卡片风格:大留白 + 分割线列表;顶部当前余额大数字;每行 = 时间 / 来源站徽标(主站|商城|抽奖|CDK)/ 事由 / 右侧带符号 delta(正 = Zens-Yellow 强调,负 = 中性灰);沿用现有无限滚动模式。

## §9 错误处理汇总

| 场景 | 行为 |
|---|---|
| 子站→主站网络错/5xx | 重试(shop 1 次 / lottery、cdk 3 次指数退避),重试仍败:积分类操作对用户报失败;事件类只记日志 |
| INSUFFICIENT_POINTS | 不重试,用户可见"积分不足" |
| 幂等键重复(DB 撞唯一索引) | 返回首次结果,不报错 |
| 抽奖本地写失败(已扣分) | credit 补偿退回,补偿失败记 ERROR 日志待对账 |
| 主站 Redis 丢失 | 幂等由 DB 唯一索引兜底,无重复扣减 |
| 子站未配 HMAC 密钥 | 积分类:拒绝操作;事件类:跳过回流,均明示不静默 |

## §10 测试与验收

- 主站 `mvn test`:
  - consume/credit/earn 幂等重放(同 key 二次调用返回首次结果、账本仅一条);
  - 并发扣减守卫(余额 10 并发扣 8+8,恰一笔成功);
  - 签到重复请求同一天只加一次分;
  - 账本 balance_after 与 sys_user.points 一致性。
- 抽奖站 `go test`(httptest 模拟主站):joinDraw 扣减成功 / INSUFFICIENT_POINTS / 主站不可达 / 本地写失败触发补偿四条路径;未配密钥拒绝付费抽奖。
- cdk `go test`:领取成功触发回流(mock 主站断言签名头齐全)、未配密钥跳过。
- 端到端 runbook(docs 内):本地起主站+三子站,签到→商城兑换→抽奖参与→领 CDK,核对 `sys_point_txn` 流水、`sys_user.points` 余额、站内通知三者闭环。

## 明确不做(YAGNI)

- MQ 异步记账、transfer 聚合 API(方案 B/C 已否)。
- go-media-service 改动。
- 中奖返积分、未中奖退分、CDK 积分消耗。
- SSO 授权流程改动(direct-token 流保持)。
- 旧环境变量删除(仅 deprecate)。

## 实施顺序(每步可独立验证)

1. 主站:账本表迁移 + UserPointsService 重构 + 签到改造(§1§2)
2. 主站:白名单配置化(§3)
3. 抽奖站:真实扣减(§4)
4. cdk:事件回流(§5)
5. 商城:客户端韧性(§6)
6. 配置统一 + 文档(§7)
7. 积分明细页(§8)
