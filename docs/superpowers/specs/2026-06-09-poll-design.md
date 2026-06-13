# 投票 / Poll 功能设计（标准版）

- **日期**：2026-06-09
- **状态**：设计已通过用户口头确认（"A"），待写实现计划（writing-plans）
- **位置**：4 个新功能中的第 1 个（投票 → 主题追踪+邮件摘要 → @提及自动补全 → PWA）
- **适用项目**：campus-pulse（Spring Boot + MyBatis-Plus + MySQL + Redis / Vue3 + Element Plus）

---

## 1. 背景与目标

社区目前没有"帖子内投票"（Discourse 招牌功能）。本期新增**标准版**投票：发帖时可附带 1 个投票，支持单选/多选、可设截止时间，投票后或截止后以**条形图 + 百分比**展示结果。目标是提升互动与答辩演示价值，同时保持数据模型干净、计票并发安全。

## 2. 范围

**做（标准版）**
- 发帖时**可选**附带 **1 个**投票（一帖 0..1）。
- 单选 / 多选可切换；多选可设"最多可选几项"。
- 可设截止时间（可空 = 不限期，靠作者手动关闭）。
- 结果展示：每项票数 + 百分比条形图 + 参与人数 + 状态（进行中/已截止/已关闭）。

**不做（Non-goals，留进阶版）**
- 改票（投出后不可更改）。
- 公开"谁投了哪项"的投票人名单（仅展示票数）。
- 一帖多个投票。
- 编辑旧帖时新增/修改投票（仅新发帖时创建）。
- 匿名/实名可配、按等级/角色限制投票资格。

## 3. 关键决策（已确认）

| 维度 | 决策 |
|---|---|
| 接入方式 | 独立三表，按 `post_id` 关联；**A 方案** |
| 投票数据传输 | **单独接口**拉取，**不**进 `PostResp` / 帖子详情缓存（票数实时、避免缓存陈旧） |
| 创建时机 | 发帖时通过 `CreatePostRequest.poll`（可选）原子创建 |
| 投票人可见性 | 仅显示**票数**，不公开投票人 |
| 改票 | 不允许 |
| 结果可见性 | 未投票者看不到结果；**投票后**或**已截止/已关闭**或**作者**可见 |
| 关闭权限 | 作者 / 该板块版主 / 管理员可提前关闭 |
| 截止处理 | 读取或投票时若已过 `deadline`，自动置 `status=0` 并拒绝新投票 |
| 投票前提 | 必须登录；一人一票（单选）/ 一人最多 N 项（多选） |
| 配色 | 中性/主色，**不**用采纳的黄色主题（黄色仅限问答状态） |

## 4. 数据模型（3 张表）

### `poll`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AI PK | |
| `post_id` | VARCHAR(64) NOT NULL，UNIQUE | 1:1 关联帖子 |
| `title` | VARCHAR(200) NULL | 投票问题，空则前端用帖子标题兜底 |
| `multi_choice` | TINYINT NOT NULL DEFAULT 0 | 0 单选 / 1 多选 |
| `max_choices` | INT NOT NULL DEFAULT 1 | 多选时最多可选项数；单选恒为 1；0 视为不限 |
| `deadline` | DATETIME NULL | 截止时间，空 = 不限期 |
| `status` | TINYINT NOT NULL DEFAULT 1 | 1 进行中 / 0 已关闭 |
| `voter_count` | INT NOT NULL DEFAULT 0 | 参与投票的**去重人数**（冗余缓存） |
| `created_by` | VARCHAR(64) NOT NULL | |
| `created_at` | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | |

### `poll_option`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AI PK | |
| `poll_id` | BIGINT NOT NULL | |
| `option_text` | VARCHAR(200) NOT NULL | |
| `option_order` | INT NOT NULL DEFAULT 0 | 展示顺序 |
| `vote_count` | INT NOT NULL DEFAULT 0 | 该项票数（冗余缓存） |

索引：`KEY idx_option_poll (poll_id, option_order)`

### `poll_vote`
| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AI PK | |
| `poll_id` | BIGINT NOT NULL | |
| `option_id` | BIGINT NOT NULL | |
| `user_id` | VARCHAR(64) NOT NULL | |
| `created_at` | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | |

约束：`UNIQUE KEY uk_vote (poll_id, user_id, option_id)`（挡重复投同一项 + 并发竞态）；`KEY idx_vote_user (poll_id, user_id)`（查"我投了哪些"）。

**计票策略**：`vote_count` / `voter_count` 为冗余缓存，在投票事务内 `UPDATE ... SET vote_count = vote_count + 1` 递增；可由 `poll_vote` 重算校正。百分比 = `option.vote_count / poll.voter_count`（多选时各项之和可 >100%，符合"多少比例的人选了此项"语义）。

## 5. 后端

- **实体 / Mapper**：`Poll` / `PollOption` / `PollVote`（MyBatis-Plus，沿用现有风格与命名）。
- **Service**：`PollService` / `PollServiceImpl`
  - `createPoll(postId, createdBy, dto)`：校验选项 2–10、非空、多选 maxChoices∈[1, 选项数]。
  - `getPollByPost(postId, currentUserId)`：返回投票 DTO（见下）；读取时若过期自动关闭。
  - `vote(pollId, optionIds, userId)`：`@Transactional`；校验登录/开放/未过期/选项归属/未投过/数量合法；写 `poll_vote` + 递增计数；唯一约束兜底并发。
  - `closePoll(pollId, operator)`：作者/版主/管理员，置 `status=0`。
- **Controller**：`PollController`
  - `GET /poll/by-post/{postId}` → `Result<PollResp>`（无则 data=null）
  - `POST /poll/vote`（body `{ pollId, optionIds[] }`）→ `Result<PollResp>`
  - `POST /poll/{pollId}/close` → `Result<?>`
- **创建集成**：`CreatePostRequest` 增加可选字段
  `poll?: { title?, multiChoice, maxChoices?, deadline?, options: string[] }`；
  帖子创建成功后于同一事务内创建投票（poll 为空则跳过）。
- **DTO**：`PollResp { id, postId, title, multiChoice, maxChoices, deadline, status, voterCount, options: [{id, text, voteCount, percentage}], myVotedOptionIds: number[], canVote, showResults, closed }`。

## 6. 前端

- **`PostComposerModal.vue`**：新增可折叠"投票"面板——问题输入、选项列表（2–10，可增删，按输入顺序）、单/多选开关、（多选）最多可选、截止时间选择器。提交时把 poll 草稿并入 `postApi.create` 载荷。前端校验：≥2 个非空选项、≤10、多选 maxChoices 合法。
- **`PollCard.vue`**（props: `postId`）：挂载时 `pollApi.getByPost(postId)`；
  - 可投票：单选 `el-radio` / 多选 `el-checkbox`（限制 maxChoices）+ "投票"按钮；
  - 已投/已截止/已关闭/作者：结果条形图（每项 文本 + 百分比 + 票数）+ "X 人参与" + 状态标签；
  - 作者额外显示"关闭投票"按钮。
  - 渲染位置：`PostDetailPage.vue` 正文下方（标签区与"采纳入口"附近）。
- **`pollApi`**：新增 api 模块；**类型**：`Poll` / `PollOption` / `PollResp` 加入 `types/index.ts`。

## 7. 迁移

- 新建 `src/main/resources/sql/migrations/2026-06-09-add-poll.sql`。
- **幂等、无 DROP**：三表用 `CREATE TABLE IF NOT EXISTS`；遵守改库纪律（不重跑 schema，只新增）。
- 部署/本地启动按既有流程先跑该迁移再起服务。

## 8. 缓存

投票数据只走 `GET /poll/by-post/{postId}` 专用接口，**绝不**塞进 `PostResp` 或 `post:detail:cache:*`。票数变动频繁，单独接口保证实时、并彻底规避帖子详情缓存陈旧问题。前端可对 GET 结果做短 TTL（如 0 缓存/即时），投票成功后用返回的最新 DTO 就地刷新。

## 9. 测试要点

- 单选：一人一票；重复投票被唯一约束/服务层拦截。
- 多选：不超过 `maxChoices`；同一项不可重复。
- 截止：过期后投票被拒、对所有人显示结果并标"已截止"。
- 关闭：仅作者/版主/管理员可关闭；关闭后禁投。
- 可见性：未投票的非作者看不到结果。
- 计数：`vote_count` / `voter_count` 与 `poll_vote` 实际行数一致。
- `npm run build:check` 不新增类型错误。

## 10. 验收标准

1. 发帖时可添加单选/多选投票并设截止时间，发布后帖子详情出现投票卡片。
2. 登录用户可投票，未登录引导登录；投票后立即看到条形图结果与"X 人参与"。
3. 截止时间到或作者关闭后，自动停投并对所有人展示结果。
4. 重复投票、超额多选、越权关闭均被正确拒绝。
5. 投票数据不进帖子详情缓存，刷新页面票数实时准确。
