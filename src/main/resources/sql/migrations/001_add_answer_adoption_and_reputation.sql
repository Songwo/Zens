-- ============================================================
-- Migration 001: 答案采纳机制 + 信誉分系统
-- 执行时间：2026-06-08
-- 说明：新增答案采纳、用户徽章、信誉分增强等功能
-- ============================================================

-- 1) 答案采纳表
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

-- 2) 用户徽章表（结构化存储，支持过期、分类）
CREATE TABLE IF NOT EXISTS `user_badges` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)    NOT NULL COMMENT '用户ID',
  `badge_type`       VARCHAR(50)    NOT NULL COMMENT '徽章类型: expert/contributor/moderator/early_bird/quality_answer',
  `badge_category`   VARCHAR(50)    DEFAULT NULL COMMENT '徽章关联分类（如板块ID或标签名）',
  `badge_name`       VARCHAR(100)   NOT NULL COMMENT '徽章显示名称',
  `badge_desc`       VARCHAR(200)   DEFAULT NULL COMMENT '徽章描述',
  `badge_icon`       VARCHAR(100)   DEFAULT NULL COMMENT '徽章图标',
  `badge_color`      VARCHAR(20)    DEFAULT NULL COMMENT '徽章颜色',
  `earned_at`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获得时间',
  `expiry_at`        DATETIME       DEFAULT NULL COMMENT '过期时间（NULL表示永久）',
  `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态 1=有效 0=已撤销',
  `grant_reason`     VARCHAR(200)   DEFAULT NULL COMMENT '授予原因',
  `granted_by`       VARCHAR(64)    DEFAULT NULL COMMENT '授予人（管理员/系统）',
  PRIMARY KEY (`id`),
  KEY `idx_badge_user_status` (`user_id`, `status`, `earned_at`),
  KEY `idx_badge_type_category` (`badge_type`, `badge_category`),
  KEY `idx_badge_expiry` (`expiry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户徽章表';

-- 3) 帖子系列表
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

-- 4) 帖子系列关联表
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

-- 5) 帖子引用表
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

-- 6) 打赏记录表
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

-- 7) 搜索历史表
CREATE TABLE IF NOT EXISTS `search_history` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)    NOT NULL COMMENT '用户ID',
  `keyword`          VARCHAR(200)   NOT NULL COMMENT '搜索关键词',
  `result_count`     INT            NOT NULL DEFAULT 0 COMMENT '结果数量',
  `clicked_post_id`  VARCHAR(64)    DEFAULT NULL COMMENT '点击的帖子ID',
  `search_filters`   JSON           DEFAULT NULL COMMENT '搜索过滤条件',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_search_user_time` (`user_id`, `created_at`),
  KEY `idx_search_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索历史表';

-- 8) 热门搜索词统计表
CREATE TABLE IF NOT EXISTS `hot_search_keywords` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `keyword`          VARCHAR(200)   NOT NULL COMMENT '关键词',
  `search_count`     INT            NOT NULL DEFAULT 1 COMMENT '搜索次数',
  `click_count`      INT            NOT NULL DEFAULT 0 COMMENT '点击次数',
  `stat_date`        DATE           NOT NULL COMMENT '统计日期',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword_date` (`keyword`, `stat_date`),
  KEY `idx_hot_search_count` (`search_count`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热门搜索词统计表';

-- 9) 用户行为分析表（用于个人数据面板）
CREATE TABLE IF NOT EXISTS `user_activity_stats` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)    NOT NULL COMMENT '用户ID',
  `stat_date`        DATE           NOT NULL COMMENT '统计日期',
  `post_count`       INT            NOT NULL DEFAULT 0 COMMENT '发帖数',
  `comment_count`    INT            NOT NULL DEFAULT 0 COMMENT '评论数',
  `like_received`    INT            NOT NULL DEFAULT 0 COMMENT '获赞数',
  `collect_received` INT            NOT NULL DEFAULT 0 COMMENT '收藏数',
  `view_received`    INT            NOT NULL DEFAULT 0 COMMENT '浏览数',
  `active_hours`     JSON           DEFAULT NULL COMMENT '活跃时段分布 {hour: count}',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_stat_date` (`user_id`, `stat_date`),
  KEY `idx_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户活动统计表';

-- 10) 快捷键配置表
CREATE TABLE IF NOT EXISTS `user_shortcuts` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)    NOT NULL COMMENT '用户ID',
  `action`           VARCHAR(50)    NOT NULL COMMENT '快捷键动作: compose/search/help/next/prev',
  `shortcut_key`     VARCHAR(50)    NOT NULL COMMENT '快捷键组合',
  `enabled`          TINYINT        NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_action` (`user_id`, `action`),
  KEY `idx_user_enabled` (`user_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户快捷键配置表';

-- 11) AI问答缓存表（避免重复调用API）
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

-- 12) 新增字段：帖子表增加"有采纳答案"标记
ALTER TABLE `sys_post`
ADD COLUMN `has_adopted_answer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否有采纳答案 0=否 1=是' AFTER `comment_count`,
ADD KEY `idx_post_adopted` (`has_adopted_answer`, `create_time`);

-- 13) 新增字段：评论表增加"被采纳"标记
ALTER TABLE `sys_comment`
ADD COLUMN `is_adopted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否被采纳为最佳答案' AFTER `collect_count`,
ADD KEY `idx_comment_adopted` (`post_id`, `is_adopted`);

-- 完成
