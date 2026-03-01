-- ============================================================
-- Campus Pulse 数据库初始化脚本（单文件版）
-- 说明：
--   1) 仅用于全新初始化（建库 + 建表 + 索引 + 基础种子数据）
--   2) 不包含任何迁移、增量、兼容补丁语句
--   3) 直接执行本文件即可完成初始化
-- ============================================================

DROP DATABASE IF EXISTS `campus_pulse`;
CREATE DATABASE `campus_pulse`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `campus_pulse`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1) 用户表
-- ============================================================
CREATE TABLE `sys_user` (
  `id`                   varchar(64)   NOT NULL COMMENT '用户ID',
  `username`             varchar(50)   NOT NULL COMMENT '学号/工号',
  `email`                varchar(100)  DEFAULT NULL COMMENT '邮箱',
  `password`             varchar(100)  NOT NULL COMMENT '加密密码',
  `nickname`             varchar(50)   DEFAULT NULL COMMENT '昵称',
  `avatar`               varchar(255)  DEFAULT NULL COMMENT '头像URL',
  `bio`                  text          DEFAULT NULL COMMENT '个人简介',
  `gender`               tinyint       DEFAULT 0 COMMENT '性别 0未知 1男 2女',
  `school`               varchar(50)   DEFAULT '本校' COMMENT '学校/学院',
  `major`                varchar(50)   DEFAULT NULL COMMENT '专业',
  `enrollment_year`      int           DEFAULT NULL COMMENT '入学年份',
  `level`                int           DEFAULT 1 COMMENT '等级',
  `points`               int           DEFAULT 0 COMMENT '积分',
  `experience`           int           DEFAULT 0 COMMENT '经验值',
  `status`               tinyint       DEFAULT 1 COMMENT '状态 1正常 2封禁',
  `interest_tags`        text          DEFAULT NULL COMMENT '兴趣标签(JSON字符串)',
  `role`                 varchar(50)   DEFAULT 'ROLE_USER' COMMENT '角色',
  `reputation`           int           DEFAULT 100 COMMENT '信誉积分',
  `contribution_val`     int           DEFAULT 0 COMMENT '社区贡献值',
  `active_region`        varchar(100)  DEFAULT NULL COMMENT '常活跃地点',
  `preferred_cate_json`  json          DEFAULT NULL COMMENT '偏好分类权重',
  `total_posts`          int           DEFAULT 0 COMMENT '总发帖数',
  `total_likes_received` int           DEFAULT 0 COMMENT '获得点赞数',
  `last_active_time`     datetime      DEFAULT NULL COMMENT '最后活跃时间',
  `github_id`            varchar(100)  DEFAULT NULL COMMENT 'GitHub用户ID',
  `github_login`         varchar(100)  DEFAULT NULL COMMENT 'GitHub登录名',
  `two_factor_enabled`   tinyint       DEFAULT 0 COMMENT '是否开启谷歌验证器二步验证',
  `two_factor_secret`    varchar(128)  DEFAULT NULL COMMENT '谷歌验证器密钥',
  `email_notify_enabled` tinyint       DEFAULT 1 COMMENT '是否开启邮件通知同步',
  `create_time`          datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time`          datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  UNIQUE KEY `uk_user_github_id` (`github_id`),
  KEY `idx_user_role_status` (`role`, `status`),
  KEY `idx_user_last_active` (`last_active_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2) 标签表
-- ============================================================
CREATE TABLE `sys_tag` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `name`        varchar(32)   NOT NULL COMMENT '标签名',
  `type`        tinyint       DEFAULT 1 COMMENT '1系统 2用户',
  `heat`        int           DEFAULT 0 COMMENT '热度值',
  `heat_score`  decimal(10,2) DEFAULT 0.00 COMMENT '热度分数',
  `use_count`   int           DEFAULT 0 COMMENT '使用次数',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- ============================================================
-- 3) 用户-标签关联表
-- ============================================================
CREATE TABLE `sys_user_tag_relation` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `user_id`     varchar(64)   NOT NULL,
  `tag_id`      bigint        NOT NULL,
  `score`       decimal(5,2)  DEFAULT 1.00 COMMENT '兴趣权重',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
  KEY `idx_user_tag_user` (`user_id`),
  KEY `idx_user_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签兴趣关联表';

-- ============================================================
-- 4) 板块表
-- ============================================================
CREATE TABLE `sections` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `name`        varchar(50)   NOT NULL COMMENT '板块名称',
  `description` varchar(200)  DEFAULT NULL COMMENT '板块描述',
  `icon`        varchar(100)  DEFAULT NULL COMMENT '图标标识',
  `sort_order`  int           DEFAULT 0 COMMENT '排序',
  `status`      tinyint       DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_name` (`name`),
  KEY `idx_section_sort` (`sort_order`),
  KEY `idx_section_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块表';

-- ============================================================
-- 5) 帖子表
-- ============================================================
CREATE TABLE `sys_post` (
  `id`               varchar(64)   NOT NULL,
  `user_id`          varchar(64)   NOT NULL COMMENT '作者ID',
  `section_id`       bigint        NOT NULL COMMENT '板块ID',
  `title`            varchar(100)  NOT NULL COMMENT '标题',
  `content`          text          DEFAULT NULL COMMENT '正文(Markdown)',
  `summary`          varchar(500)  DEFAULT NULL COMMENT '摘要',
  `cover_image`      varchar(500)  DEFAULT NULL COMMENT '封面图',
  `images`           json          DEFAULT NULL COMMENT '图片列表',
  `tags`             varchar(255)  DEFAULT NULL COMMENT '标签(逗号分隔)',
  `is_anonymous`     tinyint(1)    DEFAULT 0 COMMENT '是否匿名',
  `location_name`    varchar(100)  DEFAULT NULL COMMENT '位置名称',
  `sentiment_score`  decimal(5,2)  DEFAULT 0.00 COMMENT '情感分数',
  `status`           tinyint       DEFAULT 1 COMMENT '状态 1正常 0删除',
  `audit_status`     varchar(20)   DEFAULT 'PENDING' COMMENT '审核状态',
  `is_pinned`        tinyint       DEFAULT 0 COMMENT '旧置顶字段(兼容)',
  `global_pin`       tinyint(1)    DEFAULT 0 COMMENT '全局置顶',
  `category_pin`     tinyint(1)    DEFAULT 0 COMMENT '板块置顶',
  `pin_order`        int           DEFAULT 0 COMMENT '置顶排序',
  `pin_expire_at`    datetime      DEFAULT NULL COMMENT '置顶过期时间',
  `is_featured`      tinyint       DEFAULT 0 COMMENT '是否精华',
  `view_count`       int           DEFAULT 0 COMMENT '浏览数',
  `like_count`       int           DEFAULT 0 COMMENT '点赞数',
  `collect_count`    int           DEFAULT 0 COMMENT '收藏数',
  `comment_count`    int           DEFAULT 0 COMMENT '评论数',
  `heat_score`       double        DEFAULT 0 COMMENT '热度值',
  `last_reply_at`    datetime      DEFAULT NULL COMMENT '最后回复时间',
  `last_activity_at` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `create_time`      datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_post_user` (`user_id`),
  KEY `idx_post_section` (`section_id`),
  KEY `idx_post_status_create` (`status`, `create_time`),
  KEY `idx_post_section_status_time` (`section_id`, `status`, `create_time`),
  KEY `idx_post_feed_new` (`status`, `section_id`, `last_activity_at`, `id`),
  KEY `idx_post_feed_hot` (`status`, `section_id`, `heat_score`, `id`),
  KEY `idx_post_feed_pin` (`status`, `global_pin`, `category_pin`, `pin_order`, `pin_expire_at`),
  KEY `idx_post_audit_query` (`status`, `audit_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子表';

-- ============================================================
-- 6) 帖子点赞表
-- ============================================================
CREATE TABLE `sys_post_like` (
  `id`          varchar(64)   NOT NULL,
  `post_id`     varchar(64)   NOT NULL,
  `user_id`     varchar(64)   NOT NULL,
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_like_user_post` (`user_id`, `post_id`),
  KEY `idx_post_like_post_time` (`post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞表';

-- ============================================================
-- 7) 帖子收藏表
-- ============================================================
CREATE TABLE `sys_post_collect` (
  `id`          varchar(64)   NOT NULL,
  `post_id`     varchar(64)   NOT NULL,
  `user_id`     varchar(64)   NOT NULL,
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_collect_user_post` (`user_id`, `post_id`),
  KEY `idx_post_collect_post_time` (`post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏表';

-- ============================================================
-- 8) 评论表
-- ============================================================
CREATE TABLE `sys_comment` (
  `id`               varchar(64)    NOT NULL,
  `post_id`          varchar(64)    NOT NULL COMMENT '帖子ID',
  `user_id`          varchar(64)    NOT NULL COMMENT '评论用户ID',
  `content`          varchar(1000)  NOT NULL COMMENT '评论内容',
  `parent_id`        varchar(64)    DEFAULT '0' COMMENT '父评论ID',
  `reply_user_id`    varchar(64)    DEFAULT NULL COMMENT '被回复用户ID',
  `reply_to_user_id` varchar(64)    DEFAULT NULL COMMENT '兼容字段',
  `is_anonymous`     tinyint(1)     DEFAULT 0 COMMENT '是否匿名',
  `like_count`       int            DEFAULT 0 COMMENT '点赞数',
  `create_time`      datetime       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_time` (`post_id`, `create_time`),
  KEY `idx_comment_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============================================================
-- 9) 浏览日志表
-- ============================================================
CREATE TABLE `sys_view_log` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `post_id`     varchar(64)   NOT NULL,
  `user_id`     varchar(64)   DEFAULT NULL,
  `ip`          varchar(50)   DEFAULT NULL,
  `device`      varchar(50)   DEFAULT NULL,
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_view_log_create_time` (`create_time`),
  KEY `idx_view_log_post` (`post_id`),
  KEY `idx_view_log_user_time` (`user_id`, `create_time`),
  KEY `idx_view_log_post_time` (`post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览日志表';

-- ============================================================
-- 10) 等级经验日志表
-- ============================================================
CREATE TABLE `sys_level_exp_log` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `user_id`     varchar(64)   NOT NULL,
  `exp_delta`   int           NOT NULL,
  `reason`      varchar(100)  DEFAULT NULL,
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_level_exp_user_time` (`user_id`, `create_time`),
  KEY `idx_level_exp_reason` (`reason`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级经验日志表';

-- ============================================================
-- 11) 通知表
-- ============================================================
CREATE TABLE `notifications` (
  `id`              bigint        NOT NULL AUTO_INCREMENT,
  `user_id`         varchar(64)   NOT NULL COMMENT '接收者ID',
  `type`            varchar(20)   NOT NULL COMMENT '通知类型',
  `title`           varchar(100)  DEFAULT NULL,
  `content`         varchar(500)  DEFAULT NULL,
  `related_id`      varchar(64)   DEFAULT NULL COMMENT '关联资源ID',
  `related_user_id` varchar(64)   DEFAULT NULL COMMENT '触发者ID',
  `is_read`         tinyint       DEFAULT 0,
  `created_at`      datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notification_user` (`user_id`),
  KEY `idx_notification_user_read` (`user_id`, `is_read`),
  KEY `idx_notification_user_read_time` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

-- ============================================================
-- 12) 私信表
-- ============================================================
CREATE TABLE `direct_messages` (
  `id`              bigint         NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(130)   NOT NULL COMMENT '会话ID(双方ID排序拼接)',
  `sender_id`       varchar(64)    NOT NULL,
  `receiver_id`     varchar(64)    NOT NULL,
  `content`         varchar(1000)  NOT NULL,
  `is_read`         tinyint        DEFAULT 0,
  `created_at`      datetime       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_dm_conversation_time` (`conversation_id`, `created_at`),
  KEY `idx_dm_receiver_read_time` (`receiver_id`, `is_read`, `created_at`),
  KEY `idx_dm_sender_receiver_time` (`sender_id`, `receiver_id`, `created_at`),
  KEY `idx_dm_receiver_sender_time` (`receiver_id`, `sender_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息表';

-- ============================================================
-- 13) 举报表
-- ============================================================
CREATE TABLE `sys_report` (
  `id`          varchar(64)   NOT NULL,
  `target_type` varchar(20)   NOT NULL COMMENT 'post/comment',
  `target_id`   varchar(64)   NOT NULL,
  `reason`      varchar(100)  DEFAULT NULL,
  `details`     text          DEFAULT NULL,
  `reporter_id` varchar(64)   NOT NULL,
  `status`      int           DEFAULT 0 COMMENT '0待处理 1已处理 2已忽略',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_status_time` (`status`, `create_time`),
  KEY `idx_report_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

-- ============================================================
-- 14) 关注表
-- ============================================================
CREATE TABLE `follows` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `follower_id`  varchar(64)  NOT NULL,
  `followee_id`  varchar(64)  NOT NULL,
  `created_at`   datetime     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_pair` (`follower_id`, `followee_id`),
  KEY `idx_follow_follower` (`follower_id`),
  KEY `idx_follow_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- ============================================================
-- 15) 发展历程表
-- ============================================================
CREATE TABLE `sys_changelog` (
  `id`         bigint        NOT NULL AUTO_INCREMENT,
  `version`    varchar(20)   NOT NULL,
  `title`      varchar(200)  NOT NULL,
  `content`    text          DEFAULT NULL,
  `timestamp`  varchar(50)   DEFAULT NULL,
  `status`     tinyint       DEFAULT 1 COMMENT '1发布 0草稿',
  `sort_order` int           DEFAULT 0,
  `created_at` datetime      DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_changelog_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展历程表';

-- ============================================================
-- 16) 版主申请表
-- ============================================================
CREATE TABLE `moderator_applications` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `user_id`     varchar(64)   NOT NULL,
  `section_id`  bigint        NOT NULL,
  `reason`      text          DEFAULT NULL,
  `status`      tinyint       DEFAULT 0 COMMENT '0待审核 1通过 2拒绝',
  `review_note` varchar(500)  DEFAULT NULL,
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  `reviewed_at` datetime      DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_moderator_user` (`user_id`),
  KEY `idx_moderator_status` (`status`),
  KEY `idx_moderator_section` (`section_id`),
  KEY `idx_moderator_status_time` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版主申请表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 初始化完成（16 张核心表）
-- ============================================================
