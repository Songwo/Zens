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
- 创建当前版本所需的 16 张核心表
- 创建帖子全文索引、热榜/评论/浏览日志相关索引
- 生成与当前实体类一致的字段结构

## 当前结构同步点
当前脚本已同步以下近期结构调整：
- `sys_user.experience` 为经验值正式字段，已移除历史错误字段名 `experience1`
- `sys_user` 包含 GitHub 登录、二步验证、邮件通知、用户卡片主题与背景图字段
- `sys_post` 包含 `summary`、`images`、`audit_status`、`global_pin`、`category_pin`、`pin_order`、`pin_expire_at`、`is_featured`、`last_activity_at`
- `sys_post` 已包含全文索引 `ft_post_search(title, content, summary, tags)`
- `sys_comment` 同时保留 `reply_user_id` 与 `reply_to_user_id`，与当前实体保持一致
- `sys_view_log` 已补充用户维度和帖子维度组合索引
- `sys_report.status` 已覆盖异步流程状态：`0/1/2/3/10/11`
- `sys_tag` 仅保留代码实际使用的字段：`id/name/type/heat/create_time`

## 已有数据库升级建议
已有运行中的数据库不要直接重跑初始化脚本。请先备份，再按需补齐以下差异。

```sql
ALTER TABLE `sys_user`
  CHANGE COLUMN `experience1` `experience` INT DEFAULT 0 COMMENT '经验值';

ALTER TABLE `sys_user`
  ADD COLUMN IF NOT EXISTS `github_id` VARCHAR(100) DEFAULT NULL COMMENT 'GitHub用户ID',
  ADD COLUMN IF NOT EXISTS `github_login` VARCHAR(100) DEFAULT NULL COMMENT 'GitHub登录名',
  ADD COLUMN IF NOT EXISTS `two_factor_enabled` TINYINT DEFAULT 0 COMMENT '是否开启谷歌验证器二步验证',
  ADD COLUMN IF NOT EXISTS `two_factor_secret` VARCHAR(128) DEFAULT NULL COMMENT '谷歌验证器密钥',
  ADD COLUMN IF NOT EXISTS `email_notify_enabled` TINYINT DEFAULT 1 COMMENT '是否开启邮件通知同步',
  ADD COLUMN IF NOT EXISTS `profile_card_theme` VARCHAR(32) DEFAULT 'sunset' COMMENT '个人资料卡片背景主题',
  ADD COLUMN IF NOT EXISTS `quick_card_theme` VARCHAR(32) DEFAULT 'ocean' COMMENT '头像预览卡片背景主题',
  ADD COLUMN IF NOT EXISTS `profile_card_bg_url` VARCHAR(500) DEFAULT NULL COMMENT '个人资料卡片背景图URL',
  ADD COLUMN IF NOT EXISTS `quick_card_bg_url` VARCHAR(500) DEFAULT NULL COMMENT '头像预览卡片背景图URL';

ALTER TABLE `sys_tag`
  DROP COLUMN IF EXISTS `heat_score`,
  DROP COLUMN IF EXISTS `use_count`,
  ADD INDEX `idx_tag_heat` (`heat`, `create_time`);

ALTER TABLE `sys_post`
  ADD COLUMN IF NOT EXISTS `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '审核打回原因',
  ADD COLUMN IF NOT EXISTS `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  ADD COLUMN IF NOT EXISTS `images` JSON DEFAULT NULL COMMENT '图片列表',
  ADD COLUMN IF NOT EXISTS `audit_status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '审核状态',
  ADD COLUMN IF NOT EXISTS `global_pin` TINYINT(1) DEFAULT 0 COMMENT '全局置顶',
  ADD COLUMN IF NOT EXISTS `category_pin` TINYINT(1) DEFAULT 0 COMMENT '板块置顶',
  ADD COLUMN IF NOT EXISTS `pin_order` INT DEFAULT 0 COMMENT '置顶排序',
  ADD COLUMN IF NOT EXISTS `pin_expire_at` DATETIME DEFAULT NULL COMMENT '置顶过期时间',
  ADD COLUMN IF NOT EXISTS `is_featured` TINYINT DEFAULT 0 COMMENT '是否精华',
  ADD COLUMN IF NOT EXISTS `last_activity_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  ADD INDEX `idx_post_status_activity` (`status`, `last_activity_at`, `id`),
  ADD INDEX `idx_post_status_heat` (`status`, `heat_score`, `id`),
  ADD INDEX `idx_post_section_activity` (`section_id`, `status`, `last_activity_at`, `id`),
  ADD FULLTEXT KEY `ft_post_search` (`title`, `content`, `summary`, `tags`);

ALTER TABLE `sys_comment`
  ADD COLUMN IF NOT EXISTS `reply_to_user_id` VARCHAR(64) DEFAULT NULL COMMENT '兼容字段',
  ADD INDEX `idx_comment_post_parent_time` (`post_id`, `parent_id`, `create_time`),
  ADD INDEX `idx_comment_user_time` (`user_id`, `create_time`),
  ADD INDEX `idx_comment_reply_user_time` (`reply_user_id`, `create_time`);

ALTER TABLE `sys_view_log`
  ADD INDEX `idx_view_log_user_post_time` (`user_id`, `post_id`, `create_time`);

ALTER TABLE `notifications`
  ADD INDEX `idx_notification_user_type_time` (`user_id`, `type`, `created_at`);

ALTER TABLE `sys_report`
  MODIFY COLUMN `status` INT DEFAULT 0 COMMENT '0待处理 1已处理 2已忽略 3打回修改 10排队中 11处理中';
```

## 维护要求
- 每次后端实体字段变更后，必须同步检查 `campus_pulse_schema.sql`
- 每次新增查询热点后，必须同步评估是否补索引
- 若 README 与脚本不一致，以脚本和实体类为准，并立即修正 README
