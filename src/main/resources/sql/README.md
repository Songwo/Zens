# Campus Pulse SQL 说明

## 单一事实源
本目录中唯一有效的初始化脚本是 `campus_pulse_schema.sql`。

它已经合并了当前后端代码所依赖的完整表结构、字段、索引和约束。后续数据库结构变更必须先改代码，再同步回写这个文件，禁止继续维护零散 migration SQL。

## 适用场景
- 全新初始化数据库
- 校验本地库结构是否与当前代码一致
- 作为生产库升级时的结构对照基线

## 环境要求
- MySQL 8.0+
- 字符集 `utf8mb4`
- 存储引擎 `InnoDB`

## 初始化方式
在项目根目录执行：

```bash
mysql -u root -p < src/main/resources/sql/campus_pulse_schema.sql
```

脚本会执行以下动作：
- 重建 `campus_pulse` 数据库
- 创建当前版本所需的 34 张核心表
- 创建帖子全文索引、热榜/评论/浏览日志相关索引
- 生成与当前实体类一致的字段结构

## 当前结构同步点
当前脚本已同步以下近期结构调整：
- `sys_user` 包含 `points`、`experience`、`reputation`、GitHub 登录、二步验证、邮件通知、用户卡片主题与背景图字段
- `sys_post` 包含 `summary`、`images`、`audit_status`、`reject_reason`、`is_pinned`、`global_pin`、`category_pin`、`pin_order`、`pin_expire_at`、`is_featured`、`last_activity_at`
- `sys_post` 已包含全文索引 `ft_post_search(title, content, summary, tags)`
- `sys_comment` 同时保留 `reply_user_id` 与 `reply_to_user_id`，并包含评论软删除字段 `audit_status/update_time`
- `comment_collects`、`short_links` 已合并自历史 migrations
- `sys_media_file`、`sys_post_media`、`invite_codes`、`sys_sso_client`、`sys_changelog`、`sys_user_tag_relation` 已统一进入主 schema
- 关注关系表名为当前实体和 Mapper 使用的 `follows`
- `sys_view_log` 已补充用户维度和帖子维度组合索引
- `sys_report.status` 已覆盖异步流程状态：`0/1/2/3/10/11`
- `sys_tag` 仅保留代码实际使用的字段：`id/name/type/heat/create_time`
- 自动运营的 `ops_content_plan`、`ops_draft`、`ops_approval`、`ops_job_run`、`ops_metric_snapshot` 已纳入初始化基线

## 已有数据库升级建议
已有运行中的数据库不要直接重跑初始化脚本。请先备份，再按 `campus_pulse_schema.sql` 与线上库结构做差异比对，生成一次性升级脚本执行。

历史 `migrations` 目录下的评论软删除、评论收藏、短链接 SQL 已合并进主 schema，不再作为单独事实源维护。

## 维护要求
- 每次后端实体字段变更后，必须同步检查 `campus_pulse_schema.sql`
- 每次新增查询热点后，必须同步评估是否补索引
- 若 README 与脚本不一致，以脚本和实体类为准，并立即修正 README
