/*
 Navicat Premium Data Transfer
 数据库名称: campus_pulse
 目标版本: MySQL 8.4
 描述: 校园智能内容与趋势分析平台 - 增强版 V2.0
*/

CREATE DATABASE IF NOT EXISTS `campus_pulse` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `campus_pulse`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =================================================================
-- 1. 用户基础表 (sys_user)
-- 只存核心认证和基础静态信息
-- =================================================================
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
                            `id` varchar(64) NOT NULL COMMENT '主键ID',
                            `username` varchar(50) NOT NULL COMMENT '学号/工号',
                            `password` varchar(100) NOT NULL COMMENT '加密密码',
                            `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                            `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
                            `gender` tinyint DEFAULT 0 COMMENT '性别 0:未知 1:男 2:女 (用于人口统计分析)',
                            `role` tinyint DEFAULT 1 COMMENT '角色 0:管理员 1:学生 2:老师',

    -- 学院专业信息 (做圈子推荐的基础)
                            `school` varchar(50) DEFAULT '本校' COMMENT '学院/校区',
                            `major` varchar(50) DEFAULT NULL COMMENT '专业',
                            `grade` int DEFAULT NULL COMMENT '入学年份 (如: 2023)',

                            `status` tinyint DEFAULT 1 COMMENT '状态 1:正常 0:封禁 (风控基础)',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            `last_grade_upgrade` datetime,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础表';


-- =================================================================
-- 2. 用户画像扩展表 (sys_user_profile)
-- 【核心】专门服务于推荐系统和趋势分析
-- 这里的字段通常由定时任务异步计算更新，而不是用户手动填写的
-- =================================================================
DROP TABLE IF EXISTS `sys_user_profile`;
CREATE TABLE `sys_user_profile` (
                                    `user_id` varchar(64) NOT NULL COMMENT '关联用户ID',

    -- 1. 价值分析
                                    `reputation` int DEFAULT 100 COMMENT '信誉积分 (发广告扣分，优质贴加分)',
                                    `contribution_val` int DEFAULT 0 COMMENT '社区贡献值 (发帖/互动产生的价值)',

    -- 2. 行为特征 (用于协同过滤推荐)
                                    `active_region` varchar(100) DEFAULT NULL COMMENT '常活跃地点 (通过发帖定位分析)',
                                    `preferred_cate_json` json DEFAULT NULL COMMENT '偏好分类权重 {"news":0.8, "life":0.2}',

    -- 3. 统计特征
                                    `total_posts` int DEFAULT 0,
                                    `total_likes_received` int DEFAULT 0 COMMENT '获得的点赞数',
                                    `last_active_time` datetime DEFAULT NULL COMMENT '最后活跃时间 (筛选僵尸粉)',

                                    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户动态画像表';


-- =================================================================
-- 3. 标签定义表 (sys_tag)
-- 统一管理所有标签，防止“Java”和“java”重复
-- =================================================================
DROP TABLE IF EXISTS `sys_tag`;
CREATE TABLE `sys_tag` (
                           `id` bigint AUTO_INCREMENT COMMENT '自增ID',
                           `name` varchar(32) NOT NULL COMMENT '标签名',
                           `type` tinyint DEFAULT 1 COMMENT '类型 1:系统预设 2:用户生成',
                           `heat` int DEFAULT 0 COMMENT '全站热度 (引用次数)',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局标签池';

-- 初始化一些基础标签
INSERT INTO `sys_tag` (`name`, `type`, `heat`) VALUES
                                                   ('考研', 1, 100), ('实习', 1, 80), ('二次元', 1, 50), ('恋爱', 1, 90), ('Java', 1, 60);


-- =================================================================
-- 4. 用户-标签关联表 (sys_user_tag_relation)
-- 【核心】替代原来的 interest_tags 字符串
-- 记录用户对某个标签的感兴趣程度
-- =================================================================
DROP TABLE IF EXISTS `sys_user_tag_relation`;
CREATE TABLE `sys_user_tag_relation` (
                                         `id` bigint AUTO_INCREMENT,
                                         `user_id` varchar(64) NOT NULL,
                                         `tag_id` bigint NOT NULL,

    -- 这个权重非常重要！
    -- 初始时：用户选了标签，权重为 1.0
    -- 后续：用户经常看“考研”帖子，系统自动把“考研”标签的权重由 1.0 加到 5.0
                                         `score` decimal(5,2) DEFAULT 1.00 COMMENT '兴趣权重',

                                         `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                         PRIMARY KEY (`id`),
                                         UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`) -- 防止重复打标
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户兴趣标签关联';



-- =================================================================
-- 2. 内容分类表 (sys_category)
-- =================================================================
DROP TABLE IF EXISTS `sys_category`;
CREATE TABLE `sys_category` (
                                `id` varchar(64) NOT NULL,
                                `name` varchar(32) NOT NULL COMMENT '分类名称',
                                `code` varchar(32) NOT NULL COMMENT '编码 (news, lost_found, confess, study)',
                                `icon` varchar(255) DEFAULT NULL COMMENT '分类图标',
                                `sort` int DEFAULT 0 COMMENT '排序权重',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容板块表';

INSERT INTO `sys_category` (`id`, `name`, `code`, `sort`) VALUES
                                                              ('c1', '校园头条', 'news', 1),
                                                              ('c2', '失物招领', 'lost_found', 2),
                                                              ('c3', '表白墙', 'confess', 3),
                                                              ('c4', '学术交流', 'study', 4),
                                                              ('c5', '二手市场', 'market', 5);


-- =================================================================
-- 3. 帖子主表 (sys_post)
-- 核心增强：匿名、位置、情感分析、审核状态
-- =================================================================
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post` (
                            `id` varchar(64) NOT NULL,
                            `user_id` varchar(64) NOT NULL COMMENT '发帖人ID',
                            `category_id` varchar(64) NOT NULL COMMENT '分类ID',
                            `title` varchar(100) NOT NULL COMMENT '标题',
                            `content` text COMMENT '正文内容',
                            `images` json DEFAULT NULL COMMENT '图片列表 (JSON数组)',
                            `tags` varchar(255) DEFAULT NULL COMMENT '标签 (如: #期末 #高数)',

    -- [新增] 校园特色与分析字段
                            `is_anonymous` tinyint(1) DEFAULT 0 COMMENT '是否匿名 1:是 0:否',
                            `location_name` varchar(100) DEFAULT NULL COMMENT '地理位置 (如: 第二图书馆)',
                            `sentiment_score` decimal(5,2) DEFAULT 0.00 COMMENT '情感分数 (-1.0消极 ~ 1.0积极)',

    -- [新增] 审核与状态
                            `status` tinyint DEFAULT 1 COMMENT '状态 1:正常 0:删除',
                            `audit_status` varchar(20) DEFAULT 'PENDING' COMMENT '审核状态: PENDING/APPROVED/REJECTED',

    -- [优化] 冗余计数 (为了列表页查询性能，通过定时任务或事件同步)
                            `view_count` int DEFAULT 0 COMMENT '浏览数',
                            `like_count` int DEFAULT 0 COMMENT '点赞数',
                            `collect_count` int DEFAULT 0 COMMENT '收藏数',
                            `comment_count` int DEFAULT 0 COMMENT '评论数',
                            `heat_score` double DEFAULT 0 COMMENT '算法计算的热度值',

                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            KEY `idx_user` (`user_id`),
                            KEY `idx_heat` (`heat_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子内容表';

-- 模拟数据
INSERT INTO `sys_post` (`id`, `user_id`, `category_id`, `title`, `content`, `is_anonymous`, `location_name`, `sentiment_score`, `like_count`, `heat_score`) VALUES
                                                                                                                                                                ('p001', 'u1002', 'c3', '图书馆那个穿白衬衫的男生', '有人认识吗？在三楼A区，很帅！', 1, '图书馆', 0.85, 20, 88.5),
                                                                                                                                                                ('p002', 'u1003', 'c1', '关于五一放假的通知', '具体安排如下...', 0, '行政楼', 0.00, 150, 200.0);


-- =================================================================
-- 4. 帖子点赞关联表 (sys_post_like)
-- 解决“谁点赞了谁”的问题，专门存关系
-- =================================================================
DROP TABLE IF EXISTS `sys_post_like`;
CREATE TABLE `sys_post_like` (
                                 `id` varchar(64) NOT NULL,
                                 `post_id` varchar(64) NOT NULL COMMENT '帖子ID',
                                 `user_id` varchar(64) NOT NULL COMMENT '点赞人ID',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`id`),
    -- 联合唯一索引：确保一个用户对一个帖子只能点赞一次
                                 UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
                                 KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞记录表';

-- 模拟数据：用户 u1002 点赞了 p001
INSERT INTO `sys_post_like` (`id`, `post_id`, `user_id`) VALUES ('l1', 'p001', 'u1002');


-- =================================================================
-- 5. 帖子收藏关联表 (sys_post_collect)
-- 解决“谁收藏了谁”的问题
-- =================================================================
DROP TABLE IF EXISTS `sys_post_collect`;
CREATE TABLE `sys_post_collect` (
                                    `id` varchar(64) NOT NULL,
                                    `post_id` varchar(64) NOT NULL,
                                    `user_id` varchar(64) NOT NULL,
                                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏记录表';


-- =================================================================
-- 6. 评论表 (sys_comment)
-- [新增] 社区互动的核心
-- =================================================================
DROP TABLE IF EXISTS `sys_comment`;
CREATE TABLE `sys_comment` (
                               `id` varchar(64) NOT NULL,
                               `post_id` varchar(64) NOT NULL COMMENT '所属帖子',
                               `user_id` varchar(64) NOT NULL COMMENT '评论人',
                               `content` varchar(1000) NOT NULL COMMENT '评论内容',
                               `parent_id` varchar(64) DEFAULT '0' COMMENT '父评论ID (0为根评论)',
                               `reply_user_id` varchar(64) DEFAULT NULL COMMENT '被回复的人ID',
                               `is_anonymous` tinyint(1) DEFAULT 0 COMMENT '是否匿名评论',
                               `like_count` int DEFAULT 0 COMMENT '评论点赞数',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (`id`),
                               KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

INSERT INTO `sys_comment` (`id`, `post_id`, `user_id`, `content`, `is_anonymous`) VALUES
    ('cm1', 'p001', 'u1003', '我也看到了！确实很帅！', 1);


-- =================================================================
-- 7. 浏览日志表 (sys_view_log)
-- [数据分析专用] 记录每一次点击，用于生成热度图、词云时效性权重
-- 该表数据量会很大，生产环境通常定期归档或直接写ES/HBase
-- =================================================================
DROP TABLE IF EXISTS `sys_view_log`;
CREATE TABLE `sys_view_log` (
                                `id` bigint AUTO_INCREMENT NOT NULL, -- 使用自增ID节省空间
                                `post_id` varchar(64) NOT NULL,
                                `user_id` varchar(64) DEFAULT NULL COMMENT '可为空，统计游客访问',
                                `ip` varchar(50) DEFAULT NULL COMMENT '访问IP',
                                `device` varchar(50) DEFAULT NULL COMMENT '设备类型 (Mobile/PC)',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (`id`),
                                KEY `idx_create_time` (`create_time`), -- 用于按时间统计热度
                                KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览行为日志表';


-- =================================================================
-- 8. 趋势统计缓存表 (sys_trend_stat)
-- 保持不变，用于存储定时任务算好的 JSON 结果
-- =================================================================
DROP TABLE IF EXISTS `sys_trend_stat`;
CREATE TABLE `sys_trend_stat` (
                                  `id` varchar(64) NOT NULL,
                                  `stat_date` date NOT NULL,
                                  `type` varchar(32) NOT NULL COMMENT 'keyword_cloud, category_pie, heat_rank',
                                  `data_json` longtext NOT NULL,
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据看板缓存表';

SET FOREIGN_KEY_CHECKS = 1;