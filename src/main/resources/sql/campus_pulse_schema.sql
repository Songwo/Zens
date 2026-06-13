-- ============================================================
-- Campus Pulse 数据库初始化脚本（单文件版）
-- 说明：
--   1) 仅用于全新初始化（建库 + 建表 + 索引 + 基础种子数据）
--   2) 已合并 migrations 与独立 SQL 脚本中的当前有效结构
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
  `reputation`           int           DEFAULT 0 COMMENT '声望值',
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
  `email_notify_enabled` tinyint       DEFAULT 1 COMMENT '是否开启邮件通知',
  `profile_card_theme`   varchar(32)   DEFAULT 'sunset' COMMENT '个人资料卡片主题',
  `quick_card_theme`     varchar(32)   DEFAULT 'ocean' COMMENT '头像预览卡片主题',
  `profile_card_bg_url`  varchar(500)  DEFAULT NULL COMMENT '个人资料卡片背景图URL',
  `quick_card_bg_url`    varchar(500)  DEFAULT NULL COMMENT '头像预览卡片背景图URL',
  `cover_config`         varchar(255)  DEFAULT NULL COMMENT '封面展示配置(JSON: fit/x/y/height)',
  `badge_text`           varchar(40)   DEFAULT NULL COMMENT '用户徽章文字(管理员授予的纯文字flair)，空表示无',
  `badge_color`          varchar(20)   DEFAULT NULL COMMENT '徽章颜色(纯色样式用,hex 如 #a855f7)，空表示用默认色',
  `badge_style`          varchar(16)   NOT NULL DEFAULT 'solid' COMMENT '徽章样式: solid=纯色 / rainbow=七彩跑马动效',
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
  `post_count`  int           NOT NULL DEFAULT 0 COMMENT '可见帖子数(冗余计数,定时校准)',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`),
  KEY `idx_tag_heat` (`heat`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- ============================================================
-- 3) 用户标签关系表
-- ============================================================
CREATE TABLE `sys_user_tag_relation` (
  `id`          bigint         NOT NULL AUTO_INCREMENT,
  `user_id`     varchar(64)    NOT NULL COMMENT '用户ID',
  `tag_id`      bigint         NOT NULL COMMENT '标签ID',
  `score`       decimal(3,1)   DEFAULT 3.0 COMMENT '兴趣权重(1.0-5.0)',
  `create_time` datetime       DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag_relation` (`user_id`, `tag_id`),
  KEY `idx_user_tag_user_score` (`user_id`, `score`, `create_time`),
  KEY `idx_user_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签关系表';

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
  `status`           tinyint       DEFAULT 1 COMMENT '状态 1正常 0草稿',
  `audit_status`     varchar(20)   DEFAULT 'PENDING' COMMENT '审核/治理状态 DRAFT/PENDING/APPROVED/REJECTED/DELETED',
  `reject_reason`    varchar(500)  DEFAULT NULL COMMENT '审核打回原因',
  `is_pinned`        tinyint(1)    DEFAULT 0 COMMENT '旧版置顶兼容字段',
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
  KEY `idx_post_status_activity` (`status`, `last_activity_at`, `id`),
  KEY `idx_post_status_heat` (`status`, `heat_score`, `id`),
  KEY `idx_post_feed_pin` (`status`, `global_pin`, `category_pin`, `pin_order`, `pin_expire_at`),
  KEY `idx_post_audit_query` (`status`, `audit_status`, `create_time`),
  KEY `idx_post_audit_update` (`audit_status`, `update_time`),
  KEY `idx_post_section_activity` (`section_id`, `status`, `last_activity_at`, `id`),
  FULLTEXT KEY `ft_post_search` (`title`, `content`, `summary`, `tags`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子表';

-- ============================================================
-- 6) 帖子媒体表（配合 Go 媒体服务，覆盖式写入）
-- ============================================================
CREATE TABLE `sys_post_media` (
  `id`               bigint         NOT NULL,
  `post_id`          varchar(64)    NOT NULL COMMENT '所属帖子ID',
  `file_id`          varchar(64)    DEFAULT NULL COMMENT 'Go 侧文件ID',
  `media_type`       varchar(16)    NOT NULL DEFAULT 'image' COMMENT 'image/video',
  `access_url`       varchar(500)   NOT NULL COMMENT '访问URL',
  `cover_url`        varchar(500)   DEFAULT NULL COMMENT '视频封面URL',
  `mime_type`        varchar(128)   DEFAULT NULL,
  `original_name`    varchar(255)   DEFAULT NULL,
  `size_bytes`       bigint         DEFAULT NULL,
  `width`            int            DEFAULT NULL,
  `height`           int            DEFAULT NULL,
  `duration_seconds` int            DEFAULT NULL,
  `sort_order`       int            NOT NULL DEFAULT 0,
  `status`           tinyint        NOT NULL DEFAULT 1 COMMENT '1有效 0已删',
  `create_time`      datetime       DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pm_post` (`post_id`, `sort_order`),
  KEY `idx_pm_file` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子媒体关系表';

-- ============================================================
-- 7) 媒体文件元数据表（Cloudflare R2）
-- ============================================================
CREATE TABLE `sys_media_file` (
  `id`               varchar(64)   NOT NULL,
  `file_key`         varchar(500)  NOT NULL COMMENT 'R2 object key',
  `original_name`    varchar(255)  DEFAULT NULL,
  `mime_type`        varchar(128)  DEFAULT NULL,
  `media_type`       varchar(16)   NOT NULL COMMENT 'image/video',
  `size_bytes`       bigint        NOT NULL,
  `sha256`           varchar(64)   DEFAULT NULL,
  `width`            int           DEFAULT NULL,
  `height`           int           DEFAULT NULL,
  `duration_seconds` int           DEFAULT NULL,
  `access_url`       varchar(500)  NOT NULL,
  `cover_url`        varchar(500)  DEFAULT NULL,
  `uploader_id`      varchar(64)   DEFAULT NULL,
  `biz_type`         varchar(32)   DEFAULT NULL,
  `biz_id`           varchar(64)   DEFAULT NULL,
  `status`           tinyint       NOT NULL DEFAULT 1 COMMENT '1=有效 0=已删',
  `create_time`      datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_key` (`file_key`),
  KEY `idx_sha256` (`sha256`),
  KEY `idx_uploader_time` (`uploader_id`, `create_time`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体文件元数据(R2)';

-- ============================================================
-- 8) 帖子点赞表
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
-- 9) 帖子收藏表
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
-- 10) 评论表
-- ============================================================
CREATE TABLE `sys_comment` (
  `id`               varchar(64)    NOT NULL,
  `post_id`          varchar(64)    NOT NULL COMMENT '帖子ID',
  `user_id`          varchar(64)    NOT NULL COMMENT '评论用户ID',
  `content`          varchar(1000)  NOT NULL COMMENT '评论内容',
  `parent_id`        varchar(64)    DEFAULT '0' COMMENT '父评论ID，0表示顶层评论',
  `reply_to_user_id` varchar(64)    DEFAULT NULL COMMENT '被回复用户ID(兼容字段)',
  `reply_user_id`    varchar(64)    DEFAULT NULL COMMENT '被回复用户ID',
  `is_anonymous`     tinyint(1)     DEFAULT 0 COMMENT '是否匿名',
  `like_count`       int            DEFAULT 0 COMMENT '点赞数',
  `collect_count`    int            DEFAULT 0 COMMENT '收藏数',
  `audit_status`     varchar(16)    NOT NULL DEFAULT 'APPROVED' COMMENT '审核/删除状态 APPROVED/DELETED',
  `create_time`      datetime       DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间(软删 3 天倒计时基准)',
  `edit_time`        datetime       DEFAULT NULL COMMENT '最后编辑时间(为空表示未编辑过;与软删倒计时无关)',
  PRIMARY KEY (`id`),
  KEY `idx_comment_post_time` (`post_id`, `create_time`),
  KEY `idx_comment_parent` (`parent_id`),
  KEY `idx_comment_post_parent_time` (`post_id`, `parent_id`, `create_time`),
  KEY `idx_comment_user_time` (`user_id`, `create_time`),
  KEY `idx_comment_reply_user_time` (`reply_user_id`, `create_time`),
  KEY `idx_comment_audit_update` (`audit_status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============================================================
-- 11) 评论点赞表
-- ============================================================
CREATE TABLE `comment_likes` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `comment_id`  varchar(64)   NOT NULL,
  `user_id`     varchar(64)   NOT NULL,
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_like_user_comment` (`user_id`, `comment_id`),
  KEY `idx_comment_like_comment` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论点赞表';

-- ============================================================
-- 12) 评论收藏表
-- ============================================================
CREATE TABLE `comment_collects` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `comment_id`  varchar(64)   NOT NULL,
  `user_id`     varchar(64)   NOT NULL,
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_collect_user_comment` (`user_id`, `comment_id`),
  KEY `idx_comment_collect_comment` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论收藏表';

-- ============================================================
-- 13) 短链接映射表
-- ============================================================
CREATE TABLE `short_links` (
  `code`        varchar(24)   NOT NULL COMMENT '短链接随机码',
  `target_type` varchar(20)   NOT NULL COMMENT 'comment/post',
  `post_id`     varchar(64)   DEFAULT NULL COMMENT '内部帖子ID',
  `comment_id`  varchar(64)   DEFAULT NULL COMMENT '内部评论ID',
  `creator_id`  varchar(64)   DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`code`),
  KEY `idx_short_link_target` (`target_type`, `post_id`, `comment_id`),
  KEY `idx_short_link_creator_time` (`creator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短链接映射表';

-- ============================================================
-- 14) 浏览日志表
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
  KEY `idx_view_log_post_time` (`post_id`, `create_time`),
  KEY `idx_view_log_user_post_time` (`user_id`, `post_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览日志表';

-- ============================================================
-- 15) 等级经验日志表
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
-- 16) 通知表
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
  KEY `idx_notification_user_read_time` (`user_id`, `is_read`, `created_at`),
  KEY `idx_notification_user_type_time` (`user_id`, `type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';

-- ============================================================
-- 17) 私信表
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
  KEY `idx_dm_receiver_sender_time` (`receiver_id`, `sender_id`, `created_at`),
  KEY `idx_dm_conversation_read_time` (`conversation_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息表';

-- ============================================================
-- 18) 举报表
-- ============================================================
CREATE TABLE `sys_report` (
  `id`          varchar(64)   NOT NULL,
  `target_type` varchar(20)   NOT NULL COMMENT 'post/comment',
  `target_id`   varchar(64)   NOT NULL,
  `reason`      varchar(100)  DEFAULT NULL,
  `details`     text          DEFAULT NULL,
  `reporter_id` varchar(64)   NOT NULL,
  `status`      int           DEFAULT 0 COMMENT '0待处理 1已处理 2已忽略 3打回修改 10排队中 11处理中',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_status_time` (`status`, `create_time`),
  KEY `idx_report_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

-- ============================================================
-- 19) 版主申请表
-- ============================================================
CREATE TABLE `moderator_applications` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `user_id`     varchar(64)   NOT NULL COMMENT '申请人ID',
  `section_id`  bigint        NOT NULL COMMENT '申请板块ID',
  `reason`      varchar(500)  DEFAULT NULL COMMENT '申请理由',
  `status`      tinyint       DEFAULT 0 COMMENT '0待审核 1通过 2拒绝',
  `review_note` varchar(500)  DEFAULT NULL COMMENT '审核备注',
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  `reviewed_at` datetime      DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_moderator_user_section` (`user_id`, `section_id`),
  KEY `idx_moderator_status` (`status`),
  KEY `idx_moderator_section` (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版主申请表';

-- ============================================================
-- 20) 用户关注表
-- ============================================================
CREATE TABLE `follows` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `follower_id` varchar(64)   NOT NULL COMMENT '关注者ID',
  `followee_id` varchar(64)   NOT NULL COMMENT '被关注者ID',
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_pair` (`follower_id`, `followee_id`),
  KEY `idx_follow_followee` (`followee_id`),
  KEY `idx_follow_follower_time` (`follower_id`, `created_at`),
  KEY `idx_follow_followee_time` (`followee_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注表';

-- ============================================================
-- 21) 热度快照表
-- ============================================================
CREATE TABLE `post_heat_snapshots` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `post_id`    varchar(64)  NOT NULL,
  `heat_score` double       NOT NULL DEFAULT 0,
  `snap_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_heat_snap_post_time` (`post_id`, `snap_time`),
  KEY `idx_heat_snap_time` (`snap_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子热度快照表';

-- ============================================================
-- 22) 趋势统计表
-- ============================================================
CREATE TABLE `trend_stats` (
  `id`            bigint        NOT NULL AUTO_INCREMENT,
  `stat_type`     varchar(20)   NOT NULL COMMENT '统计维度 section/tag/keyword',
  `stat_key`      varchar(100)  NOT NULL COMMENT '维度值',
  `section_id`    bigint        DEFAULT NULL COMMENT '关联板块ID',
  `post_count`    int           DEFAULT 0,
  `view_count`    int           DEFAULT 0,
  `like_count`    int           DEFAULT 0,
  `comment_count` int           DEFAULT 0,
  `heat_score`    double        DEFAULT 0,
  `stat_date`     date          NOT NULL COMMENT '统计日期',
  `created_at`    datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trend_type_key_date` (`stat_type`, `stat_key`, `stat_date`),
  KEY `idx_trend_date` (`stat_date`),
  KEY `idx_trend_section_date` (`section_id`, `stat_date`),
  KEY `idx_trend_heat` (`heat_score`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='趋势统计表';

-- ============================================================
-- 23) 邀请码表
-- ============================================================
CREATE TABLE `invite_codes` (
  `id`              varchar(64)   NOT NULL COMMENT '主键',
  `code`            varchar(32)   NOT NULL COMMENT '邀请码',
  `creator_id`      varchar(64)   DEFAULT NULL COMMENT '创建人ID',
  `used_by_user_id` varchar(64)   DEFAULT NULL COMMENT '使用者ID',
  `status`          tinyint       DEFAULT 0 COMMENT '0=未用 1=已用 2=禁用',
  `max_uses`        int           DEFAULT 1 COMMENT '最大使用次数(0=不限)',
  `used_count`      int           DEFAULT 0 COMMENT '已使用次数',
  `expire_time`     datetime      DEFAULT NULL COMMENT '过期时间(null=永不过期)',
  `remark`          varchar(200)  DEFAULT NULL COMMENT '备注',
  `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         tinyint       DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`code`),
  KEY `idx_invite_status` (`status`, `expire_time`),
  KEY `idx_invite_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码表';

-- ============================================================
-- 24) SSO 应用注册表
-- ============================================================
CREATE TABLE `sys_sso_client` (
  `id`            varchar(64)   NOT NULL,
  `client_id`     varchar(100)  NOT NULL COMMENT '应用标识（唯一）',
  `client_name`   varchar(200)  NOT NULL COMMENT '应用名称',
  `client_secret` varchar(200)  NOT NULL COMMENT '应用密钥',
  `redirect_uri`  varchar(500)  NOT NULL COMMENT '回调地址',
  `description`   varchar(500)  DEFAULT NULL COMMENT '应用描述',
  `logo_url`      varchar(500)  DEFAULT NULL COMMENT '应用Logo URL',
  `enabled`       tinyint(1)    DEFAULT 1 COMMENT '是否启用 1:是 0:否',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SSO 应用注册表';

-- ============================================================
-- 25) 发展历程 / 版本日志表
-- ============================================================
CREATE TABLE `sys_changelog` (
  `id`         bigint        NOT NULL AUTO_INCREMENT,
  `version`    varchar(50)   DEFAULT NULL COMMENT '版本号',
  `title`      varchar(100)  NOT NULL COMMENT '标题',
  `content`    text          DEFAULT NULL COMMENT '内容',
  `timestamp`  varchar(32)   DEFAULT NULL COMMENT '时间标签',
  `status`     tinyint       DEFAULT 1 COMMENT '状态 1发布 0隐藏',
  `sort_order` int           DEFAULT 0 COMMENT '排序',
  `created_at` datetime      DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_changelog_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展历程/版本日志表';

-- ============================================================
-- 基础种子数据
-- ============================================================
INSERT INTO `sections` (`id`, `name`, `description`, `icon`, `sort_order`, `status`) VALUES
(1,  '校园生活',   '记录校园日常、生活点滴',           'school',       1,  1),
(2,  '学习交流',   '课程讨论、学习资源分享',           'book',         2,  1),
(3,  '社团活动',   '各类社团、课外活动信息',           'group',        3,  1),
(4,  '二手交易',   '校园二手物品买卖',                 'swap',         4,  1),
(5,  '求职就业',   '实习招聘、求职经验分享',           'work',         5,  1),
(6,  '情感树洞',   '倾诉心声、情感交流',               'favorite',     6,  1),
(7,  '美食推荐',   '食堂、周边美食点评',               'restaurant',   7,  1),
(8,  '竞赛资讯',   '各类竞赛报名、经验分享',           'emoji_events', 8,  1),
(9,  '失物招领',   '校园内失物招领信息',               'search',       9,  1),
(10, '考研专区',   '考研备考、经验交流',               'psychology',   10, 1);

SET FOREIGN_KEY_CHECKS = 1;
