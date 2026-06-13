# 主题追踪 + 邮件摘要 设计文档

- 日期：2026-06-09
- 状态：已批准（方案 A，全部默认值）
- 关联功能线：四大功能之 #2（投票已完成，见 `2026-06-09-poll-design.md`）

## 1. 目标与范围

让用户能"追踪"感兴趣的帖子，在被追踪帖有新评论时获得**即时站内通知**，并按天收到一封**邮件摘要（digest）**聚合所有追踪帖的新动态。对标 Discourse 的 watching/tracking，但 v1 简化为「订阅/不订阅」二态。

**核心拆分**：追踪（订阅关系）与触达（站内通知 + 邮件摘要）是两件事，共用同一张订阅表。
- 站内通知：实时，每条评论触发，不受邮件开关影响。
- 邮件摘要：定时（每天 8:00）聚合，受 `emailNotifyEnabled` 控制。

## 2. 非目标（v1 明确不做，YAGNI）

- 不做 Discourse 的 watching/tracking/muted 四档通知级别，只做订阅/不订阅二态。
- 不做帖子级别的精细静音。
- digest 不做「实时 / 每周」多档，只每天一封（cron 可配）。
- digest 只聚合**评论活动**，不含点赞 / 反应 / 收藏（避免邮件轰炸）。
- 不做邮件内「一键退订」链接（用户可在设置里关 `emailNotifyEnabled`，或在帖子页取消追踪）。

## 3. 复用的现有设施

| 设施 | 位置 | 用途 |
|---|---|---|
| `Notification` + `NotificationService.createNotification(...)` | `entity/Notification.java`、`service/NotificationService.java` | 站内通知入库与推送 |
| `MailServiceImpl.sendHtmlMail(to, subject, html)`（`@Async`） | `service/impl/MailServiceImpl.java` | 发送 HTML 摘要邮件 |
| `User.email` + `User.emailNotifyEnabled` | `entity/User.java` | 邮箱地址 + 邮件开关（**已存在，无需加字段**） |
| `@Scheduled` 定时任务模式 | `scheduled/PostCleanupTask.java` 等 | digest 定时任务模板 |
| 评论通知链路 `sendCommentNotification(postAuthorId, ...)` | `NotificationServiceImpl` | 追踪通知挂载点 |

## 4. 数据模型（1 张新表）

```sql
CREATE TABLE IF NOT EXISTS `post_subscription` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    VARCHAR(64)  NOT NULL COMMENT '订阅用户ID',
  `post_id`    VARCHAR(64)  NOT NULL COMMENT '被订阅帖子ID',
  `source`     VARCHAR(16)  NOT NULL DEFAULT 'manual' COMMENT '订阅来源: auto(评论/发帖自动) / manual(手动追踪)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_user_post` (`user_id`, `post_id`),
  KEY `idx_sub_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子订阅表';
```

- 唯一约束 `(user_id, post_id)`：一人对一帖最多一条订阅记录，重复订阅幂等（INSERT IGNORE / 先查后插）。
- `source` 仅作展示/统计用途，不影响通知逻辑（auto 与 manual 都同等接收通知）。
- 迁移文件：`src/main/resources/sql/migrations/2026-06-09-add-post-subscription.sql`，幂等（CREATE TABLE IF NOT EXISTS），不 DROP、不动既有数据。

## 5. 自动订阅规则

在 `createPost` 成功后：帖子作者自动订阅自己的帖（`source=auto`）。

在评论创建成功后（评论 service 内）：评论者自动订阅所评论的帖（`source=auto`，若已存在则跳过）。

实现策略：自动订阅失败**不阻断主流程**（try/catch 包裹，仅记日志），因为它是增强而非核心。

## 6. 即时站内通知

接入点：评论创建链路（与现有 `sendCommentNotification` 同一处）。

逻辑：有人评论帖子 P 时，查 P 的所有订阅者，对每个订阅者发一条 `type=post_activity` 站内通知，**排除**：
- 评论者本人（自己评论不通知自己）
- 帖子作者（作者已有专门的 `sendCommentNotification`，避免重复）

通知文案：`「{帖子标题}」有新回复`，`relatedId=postId`，点击跳转帖子。

性能：订阅者通常不多；批量查询订阅者后循环发通知。若单帖订阅者极多，可后续异步化（v1 同步即可，复用现有 `createNotification`）。

## 7. 邮件摘要（digest，方案 A）

新增 `scheduled/PostDigestTask.java`：

- `@Scheduled(cron = "${campus.digest.cron:0 0 8 * * ?}")` —— 默认每天 8:00，cron 可配。
- 流程：
  1. 计算时间窗口：过去 24h（`now - 24h` ~ `now`）。
  2. 找出窗口内有新评论的帖子集合。
  3. 对这些帖子的订阅者，聚合出「每个用户 → 他订阅且有新评论的帖子列表（含新增评论数）」。
  4. 过滤：用户 `emailNotifyEnabled = 1` 且 `email` 非空。
  5. 每个符合条件的用户组装一封 HTML 邮件：标题「你追踪的 N 个主题有新回复」，正文列出帖子标题 + 新增评论数 + 链接（`{siteUrl}/t/{shortId}` 或 `/t/{postId}`）。统计某帖新增评论数时**排除该用户自己发的评论**；若某帖在窗口内的新评论全部由该用户本人发出，则该帖不计入其 digest；若聚合后该用户无任何可提醒的帖子，则不给他发邮件。
  6. 调 `mailService.sendHtmlMail(...)`。
- 站点 URL 取 `campus.site.url`（application.yml 已有 `${SITE_URL:https://allinsong.top}`）。
- 失败隔离：单个用户邮件失败不影响其他用户（循环内 try/catch）。
- 任务整体 try/catch + 日志，遵循现有定时任务风格。

## 8. 后端接口（`SubscriptionController`，`/subscription`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/subscription/{postId}` | 手动订阅（`source=manual`），幂等 |
| DELETE | `/subscription/{postId}` | 取消订阅 |
| GET | `/subscription/{postId}/status` | 返回 `{ subscribed: boolean }` |
| GET | `/subscription/my` | 我追踪的帖子列表（分页，给"我的追踪"页备用，前端 v1 可不接） |

- 服务层 `PostSubscriptionService`：`subscribe(userId, postId, source)`、`unsubscribe(userId, postId)`、`isSubscribed(userId, postId)`、`listSubscribers(postId)`、`listByUser(userId, page, size)`。
- 复用 MyBatis-Plus BaseMapper 风格（参考 `AnswerAdoptionMapper`）。

## 9. 前端

- `api/subscription.ts`：`subscribe(postId)` / `unsubscribe(postId)` / `getStatus(postId)`。
- `PostDetailPage.vue`：在帖子操作区或作者信息区加「追踪 / 已追踪」按钮（图标 + 文案切换），进入帖子时拉 `getStatus`，点击切换。
  - 登录校验：未登录点击提示登录。
  - 评论成功后，前端把追踪状态置为已追踪（与后端自动订阅一致），避免再请求一次。
- 视觉：中性/品牌色，不用黄色 accept 主题（追踪不是问答状态）。

## 10. 测试与验收

- 后端 `mvn -q compile` 通过。
- 前端 `npm run build` 通过。
- 手测闭环（部署后）：
  1. 用户 B 评论用户 A 的帖 → A 收到评论通知；B 自动订阅该帖。
  2. 用户 C 也评论该帖 → A 和 B 都收到 `post_activity` 通知（C 不收到自己的）。
  3. 帖子页「追踪」按钮可手动订阅 / 取消，状态正确。
  4. （可临时把 cron 设为近几分钟验证）digest 任务对开了 `emailNotifyEnabled` 的订阅者发出聚合邮件。

## 11. 部署注意

1. **先跑迁移** `2026-06-09-add-post-subscription.sql`。
2. 后端重打 jar + 前端 `npm run build` 部署。
3. 订阅 / 通知数据不进帖子详情缓存，无需清 `post:detail:cache:*`。
4. digest 依赖邮件配置（`spring.mail.*`），生产需确保 `MAIL_USERNAME/MAIL_PASSWORD` 已注入，否则邮件发送失败（仅日志报错，不影响站内通知）。

## 12. 已知权衡

- 即时通知 v1 同步发送，单帖订阅者极多时有延迟；可后续异步化。
- digest 用「过去 24h 有新评论」近似，未持久化「上次 digest 时间」做精确去重；每天固定窗口足够毕设场景，避免引入额外状态表。
