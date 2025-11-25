/*
 Navicat Premium Data Transfer
 Source Database       : campus_pulse
 Target Server Type    : MySQL
 Target Server Version : 8.4
 File Encoding         : 65001

 Date: 2025-11-24
*/

SET NAMES utf8;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户表 (sys_user)
-- 用于存储学生和管理员信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `username` varchar(50) NOT NULL COMMENT '用户名/学号',
                            `password` varchar(100) NOT NULL COMMENT '密码',
                            `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                            `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
                            `role` tinyint DEFAULT 1 COMMENT '角色 0:管理员 1:学生',
                            `major` varchar(50) DEFAULT NULL COMMENT '专业 (如: 计算机)',
                            `grade` int DEFAULT NULL COMMENT '年级 (如: 2021)',
                            `interest_tags` varchar(500) DEFAULT NULL COMMENT '兴趣标签JSON (用于冷启动推荐，如 ["考研","篮球"])',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 2. 内容分类表 (sys_category)
-- 四大板块：资讯、学术、生活、话题
-- ----------------------------
DROP TABLE IF EXISTS `sys_category`;
CREATE TABLE `sys_category` (
                                `id` int NOT NULL AUTO_INCREMENT,
                                `name` varchar(20) NOT NULL COMMENT '分类名称',
                                `code` varchar(20) NOT NULL COMMENT '分类编码 (news, study, life, trends)',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块分类表';

-- 插入初始化数据
INSERT INTO `sys_category` (`name`, `code`) VALUES ('校园资讯', 'news');
INSERT INTO `sys_category` (`name`, `code`) VALUES ('学术资源', 'study');
INSERT INTO `sys_category` (`name`, `code`) VALUES ('生活互助', 'life');
INSERT INTO `sys_category` (`name`, `code`) VALUES ('热门话题', 'trends');

-- ----------------------------
-- 3. 帖子/内容主表 (sys_post)
-- 核心表：所有的内容都存在这里
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post` (
                            `id` bigint NOT NULL AUTO_INCREMENT,
                            `user_id` bigint NOT NULL COMMENT '发布者ID',
                            `category_id` int NOT NULL COMMENT '所属板块ID',
                            `title` varchar(100) NOT NULL COMMENT '标题',
                            `content` text COMMENT '内容详情 (HTML或Markdown)',
                            `images` varchar(1000) DEFAULT NULL COMMENT '图片链接JSON',
                            `tags` varchar(255) DEFAULT NULL COMMENT '标签 (用于推荐，如: #二手 #教材)',
                            `view_count` int DEFAULT 0 COMMENT '浏览量',
                            `like_count` int DEFAULT 0 COMMENT '点赞量',
                            `heat_score` double DEFAULT 0 COMMENT '热度值 (算法计算结果)',
                            `status` tinyint DEFAULT 1 COMMENT '状态 1:正常 0:下架',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            KEY `idx_heat` (`heat_score`) USING BTREE COMMENT '用于热搜榜排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子内容表';

-- ----------------------------
-- 4. 用户交互记录表 (sys_interaction)
-- 推荐系统的灵魂：记录谁看了什么
-- ----------------------------
DROP TABLE IF EXISTS `sys_interaction`;
CREATE TABLE `sys_interaction` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `user_id` bigint NOT NULL,
                                   `post_id` bigint NOT NULL,
                                   `type` tinyint NOT NULL COMMENT '交互类型 1:浏览 2:点赞 3:收藏 4:评论',
                                   `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_user_post` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交互行为表';

-- ----------------------------
-- 5. 趋势统计缓存表 (sys_trend_stat)
-- 用于 ECharts 图表展示，定时任务每天算完存这里
-- ----------------------------
DROP TABLE IF EXISTS `sys_trend_stat`;
CREATE TABLE `sys_trend_stat` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `stat_date` date NOT NULL COMMENT '统计日期',
                                  `type` varchar(20) NOT NULL COMMENT '统计类型 (keyword_cloud, category_pie, daily_traffic)',
                                  `data_json` text NOT NULL COMMENT '统计结果JSON数据 (直接给前端ECharts用)',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='趋势分析缓存表';

-- ----------------------------
-- 模拟数据
-- ----------------------------
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `major`) VALUES ('admin', '123456', '系统管理员', 0, '管理处');
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `major`) VALUES ('student1', '123456', '张三', 1, '计算机科学');

INSERT INTO `sys_post` (`user_id`, `category_id`, `title`, `content`, `tags`, `view_count`, `heat_score`) VALUES
                                                                                                              (1, 1, '关于2024年秋季运动会报名的通知', '详情请见...', '#活动 #运动会', 1200, 98.5),
                                                                                                              (2, 3, '【出】九成新考研英语红宝书', '低价出，食堂面交', '#二手 #考研 #书籍', 56, 12.0),
                                                                                                              (2, 4, '吐槽一下今天的食堂阿姨手抖', '肉全是土豆...', '#吐槽 #食堂', 340, 45.2),
                                                                                                              (1, 2, 'Java高并发编程学习笔记分享', '纯干货...', '#学习 #Java #笔记', 890, 88.0);

SET FOREIGN_KEY_CHECKS = 1;