-- ============================================================
-- 生产库补齐脚本（答案采纳波次）：2026-06-09
-- 合并 migration 001 + 002 + 003 为单份幂等脚本，解决部署采纳功能新代码后
--   "Unknown column 'allow_adoption' in 'field list'" 等整体查询报错。
--
-- 幂等：表用 CREATE IF NOT EXISTS；列/索引先查 information_schema 再 ADD。
--       可安全重复执行；绝不 DROP、不动任何已有数据。
-- 不用 DELIMITER/存储过程，故 mysql 命令行与 Navicat/Workbench 等 GUI 均可直接执行。
--
-- 相比直接跑 001/002/003 的修正：
--   1) 列只在缺失时添加，规避 001 结尾未判存在的 ALTER 与 002 重叠导致的 Duplicate column。
--   2) 板块插入不硬编码 id（交给 AUTO_INCREMENT），规避 003 中 id=11 被占用时的主键冲突。
--   3) 新列一律追加表尾（不用 AFTER），不依赖旧库既有列顺序。
--
-- 用法（在服务器上，换成你的库账号）：
--   mysql -u<user> -p campus_pulse < catch-up-adoption-2026-06-09.sql
-- ============================================================
USE `campus_pulse`;

-- ① 缺失的整张表（采纳/徽章/系列/引用/打赏/搜索/AI 等，新控制器依赖；CREATE IF NOT EXISTS 幂等）
CREATE TABLE IF NOT EXISTS `answer_adoptions` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`          VARCHAR(64)    NOT NULL COMMENT '帖子ID',
  `comment_id`       VARCHAR(64)    NOT NULL COMMENT '被采纳的评论ID',
  `adopted_by`       VARCHAR(64)    NOT NULL COMMENT '采纳者（帖子作者）',
  `adopted_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采纳时间',
  `reputation_granted` INT          NOT NULL DEFAULT 15 COMMENT '授予的声望值',
  `exp_granted`      INT            NOT NULL DEFAULT 20 COMMENT '授予的经验值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adoption_post` (`post_id`),
  KEY `idx_adoption_comment` (`comment_id`),
  KEY `idx_adoption_time` (`adopted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答案采纳表';

CREATE TABLE IF NOT EXISTS `user_badges` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)    NOT NULL COMMENT '用户ID',
  `badge_type`       VARCHAR(50)    NOT NULL COMMENT '徽章类型',
  `badge_category`   VARCHAR(50)    DEFAULT NULL COMMENT '徽章关联分类',
  `badge_name`       VARCHAR(100)   NOT NULL COMMENT '徽章显示名称',
  `badge_desc`       VARCHAR(200)   DEFAULT NULL COMMENT '徽章描述',
  `badge_icon`       VARCHAR(100)   DEFAULT NULL COMMENT '徽章图标',
  `badge_color`      VARCHAR(20)    DEFAULT NULL COMMENT '徽章颜色',
  `earned_at`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  `expiry_at`        DATETIME       DEFAULT NULL COMMENT '过期时间（NULL表示永久）',
  `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=已撤销',
  `grant_reason`     VARCHAR(200)   DEFAULT NULL COMMENT '授予原因',
  `granted_by`       VARCHAR(64)    DEFAULT NULL COMMENT '授予人',
  PRIMARY KEY (`id`),
  KEY `idx_badge_user_status` (`user_id`, `status`, `earned_at`),
  KEY `idx_badge_type_category` (`badge_type`, `badge_category`),
  KEY `idx_badge_expiry` (`expiry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户徽章表';

CREATE TABLE IF NOT EXISTS `post_series` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)    NOT NULL COMMENT '创建者ID',
  `title`            VARCHAR(200)   NOT NULL COMMENT '系列标题',
  `description`      TEXT           DEFAULT NULL COMMENT '系列描述',
  `cover_image`      VARCHAR(500)   DEFAULT NULL COMMENT '封面图',
  `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态 1=发布 0=草稿',
  `post_count`       INT            NOT NULL DEFAULT 0 COMMENT '帖子数量',
  `view_count`       INT            NOT NULL DEFAULT 0 COMMENT '总浏览量',
  `like_count`       INT            NOT NULL DEFAULT 0 COMMENT '总点赞数',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_series_user` (`user_id`, `status`, `created_at`),
  KEY `idx_series_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子系列表';

CREATE TABLE IF NOT EXISTS `post_series_items` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `series_id`        BIGINT         NOT NULL COMMENT '系列ID',
  `post_id`          VARCHAR(64)    NOT NULL COMMENT '帖子ID',
  `order_index`      INT            NOT NULL DEFAULT 0 COMMENT '排序序号',
  `added_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_series_post` (`series_id`, `post_id`),
  KEY `idx_series_order` (`series_id`, `order_index`),
  KEY `idx_post_series` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子系列关联表';

CREATE TABLE IF NOT EXISTS `post_references` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `source_post_id`   VARCHAR(64)    NOT NULL COMMENT '引用来源帖子ID',
  `target_post_id`   VARCHAR(64)    NOT NULL COMMENT '被引用目标帖子ID',
  `context`          VARCHAR(500)   DEFAULT NULL COMMENT '引用上下文',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reference_pair` (`source_post_id`, `target_post_id`),
  KEY `idx_reference_target` (`target_post_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子引用关系表';

CREATE TABLE IF NOT EXISTS `tip_records` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tipper_id`        VARCHAR(64)    NOT NULL COMMENT '打赏者ID',
  `target_type`      VARCHAR(20)    NOT NULL COMMENT '目标类型: post/comment',
  `target_id`        VARCHAR(64)    NOT NULL COMMENT '目标ID',
  `target_author_id` VARCHAR(64)    NOT NULL COMMENT '被打赏者ID',
  `amount`           INT            NOT NULL COMMENT '打赏积分数',
  `message`          VARCHAR(200)   DEFAULT NULL COMMENT '打赏留言',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tip_tipper` (`tipper_id`, `created_at`),
  KEY `idx_tip_target` (`target_type`, `target_id`),
  KEY `idx_tip_author` (`target_author_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打赏记录表';

CREATE TABLE IF NOT EXISTS `ai_qa_cache` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_hash`    VARCHAR(64)    NOT NULL COMMENT '问题hash（去重）',
  `question`         TEXT           NOT NULL COMMENT '原始问题',
  `answer`           TEXT           NOT NULL COMMENT 'AI回答',
  `similar_posts`    JSON           DEFAULT NULL COMMENT '相似帖子列表',
  `hit_count`        INT            NOT NULL DEFAULT 1 COMMENT '命中次数',
  `last_hit_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_hash` (`question_hash`),
  KEY `idx_last_hit` (`last_hit_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答缓存表';

-- ② 缺失的列（先查 information_schema 再 ADD，幂等；不带 AFTER，统一追加表尾）

-- sys_post.has_adopted_answer —— Post.hasAdoptedAnswer
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_post' AND COLUMN_NAME = 'has_adopted_answer');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `sys_post` ADD COLUMN `has_adopted_answer` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否有采纳答案 0=否 1=是''',
  'SELECT ''[skip ] sys_post.has_adopted_answer'' AS log');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- sys_comment.is_adopted —— Comment.isAdopted
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'is_adopted');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `sys_comment` ADD COLUMN `is_adopted` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否被采纳为最佳答案''',
  'SELECT ''[skip ] sys_comment.is_adopted'' AS log');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- sections.allow_adoption —— Section.allowAdoption（本次报错的列）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sections' AND COLUMN_NAME = 'allow_adoption');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `sections` ADD COLUMN `allow_adoption` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否支持答案采纳 0=否 1=是''',
  'SELECT ''[skip ] sections.allow_adoption'' AS log');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ③ 缺失的索引（幂等）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_post' AND INDEX_NAME = 'idx_post_adopted');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `sys_post` ADD KEY `idx_post_adopted` (`has_adopted_answer`, `create_time`)',
  'SELECT ''[skip ] idx_post_adopted'' AS log');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_comment' AND INDEX_NAME = 'idx_comment_adopted');
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `sys_comment` ADD KEY `idx_comment_adopted` (`post_id`, `is_adopted`)',
  'SELECT ''[skip ] idx_comment_adopted'' AS log');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ④ "答疑解惑"问答板块（id 交给 AUTO_INCREMENT，避免主键冲突；仅在不存在时插入）
INSERT INTO `sections` (`name`, `description`, `icon`, `sort_order`, `status`, `allow_adoption`)
SELECT '答疑解惑', '提出问题、解答疑惑，最佳答案可被采纳', 'help', 0, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sections` WHERE `name` = '答疑解惑');

-- 确保该板块已开启采纳
UPDATE `sections` SET `allow_adoption` = 1 WHERE `name` = '答疑解惑';

SELECT '✅ catch-up-adoption-2026-06-09 完成：列/表/板块已就绪' AS status;
