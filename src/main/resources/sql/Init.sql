/*
 Navicat Premium Data Transfer

 数据库名称: campus_pulse (校园脉动)
 目标版本: MySQL 8.4
 文件编码: 65001

 Date: 2025-11-24
*/

-- 确保使用正确的数据库
use campus_pulse;

-- 设定字符集为 utf8mb4，以支持更广泛的 Unicode 字符（包括表情符号）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户表 (sys_user)
-- 用于存储学生和管理员信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
                            `id` varchar(255) NOT NULL COMMENT '主键ID（应用生成）',
                            `username` varchar(50) NOT NULL COMMENT '用户名/学号',
                            `password` varchar(100) NOT NULL COMMENT '密码',
                            `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                            `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
                            `role` tinyint DEFAULT 1 COMMENT '角色 0:管理员 1:学生',
                            `major` varchar(50) DEFAULT NULL COMMENT '专业 (如: 计算机)',
                            `grade` int DEFAULT NULL COMMENT '年级 (如: 2021)',
                            `interest_tags` varchar(500) DEFAULT NULL COMMENT '兴趣标签JSON (如 ["考研","篮球"])',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 模拟数据：
INSERT INTO `sys_user` (`id`,`username`, `password`, `nickname`, `role`, `major`, `grade`, `interest_tags`) VALUES
                                                                                                                ('223333','admin', '$2a$10$T.sP90W3zQ7Z6oQ.tF9pIu9.mH1.yH0.tF9pIu9.mH1.yH0', '系统管理员', 0, '管理处', 2020, '["公告","行政"]'),
                                                                                                                ('333444','student1', '$2a$10$T.sP90W3zQ7Z6oQ.tF9pIu9.mH1.yH0.tF9pIu9.mH1.yH0', '张三-计算机', 1, '计算机科学', 2023, '["Java","算法","篮球"]'),
                                                                                                                ('444555','student2', '$2a$10$T.sP90W3zQ7Z6oQ.tF9pIu9.mH1.yH0.tF9pIu9.mH1.yH0', '李四-经管', 1, '经济管理', 2024, '["投资","兼职","实习"]'),
                                                                                                                ('666777','student3', '$2a$10$T.sP90W3zQ7Z6oQ.tF9pIu9.mH1.yH0.tF9pIu9.mH1.yH0', '王五-设计', 1, '艺术设计', 2022, '["摄影","短视频","展览"]');


-- ----------------------------
-- 2. 内容分类表 (sys_category)
-- 四大板块：资讯、学术、生活、话题
-- ----------------------------
DROP TABLE IF EXISTS `sys_category`;
CREATE TABLE `sys_category` (
                                `id` varchar(255) NOT NULL,
                                `name` varchar(20) NOT NULL COMMENT '分类名称',
                                `code` varchar(20) NOT NULL COMMENT '分类编码 (news, study, life, trends)',
                                PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块分类表';

-- 插入初始化数据
INSERT INTO `sys_category` (`id`,`name`, `code`) VALUES
                                                     ('123','校园资讯', 'news'),
                                                     ('124','学术资源', 'study'),
                                                     ('125','生活互助', 'life'),
                                                     ('126','热门话题', 'trends');


-- ----------------------------
-- 3. 帖子/内容主表 (sys_post)
-- 核心表：所有的内容都存在这里
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post` (
                            `id` varchar(255) NOT NULL ,
                            `user_id` varchar(255) NOT NULL COMMENT '发布者ID',
                            `category_id` varchar(255) NOT NULL COMMENT '所属板块ID',
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

-- 模拟数据：
INSERT INTO `sys_post` (`id`,`user_id`, `category_id`, `title`, `content`, `tags`, `view_count`, `like_count`, `heat_score`) VALUES
                                                                                                                                 ('1','223333', '123', '关于2024年秋季运动会报名的通知', '详情请见体育部公告...', '#活动 #运动会', 1200, 80, 98.5),
                                                                                                                                 ('2','333444', '125', '【出】九成新考研英语红宝书', '低价出，食堂面交，可小刀。', '#二手 #考研 #书籍', 56, 10, 12.0),
                                                                                                                                 ('3','333444', '126', '吐槽一下今天的食堂阿姨手抖', '肉全是土豆...呜呜呜', '#吐槽 #食堂', 340, 45, 45.2),
                                                                                                                                 ('4','223333', '124', 'Java高并发编程学习笔记分享', '纯干货，PDF文档，需要的留邮箱。', '#学习 #Java #笔记', 890, 150, 88.0),
                                                                                                                                 ('5','444555', '126', '校园周边最好吃的奶茶店是哪家？', '急需一杯续命！', '#美食 #讨论 #奶茶', 620, 75, 55.5),
                                                                                                                                 ('6','666777', '125', '求租：学校附近两室一厅的房源', '希望能长租，价格美丽。', '#租房 #生活', 150, 5, 18.0),
                                                                                                                                 ('7','444555', '124', '分享：如何高效利用学校的学术资源', '包含了数据库和文献查找技巧。', '#学术 #资源 #技巧', 400, 60, 65.0),
                                                                                                                                 ('8','333444', '123', '本周科技讲座预告：AI与未来生活', '主讲人是XXX教授，不要错过！', '#讲座 #科技 #AI', 2000, 120, 105.0);


-- ----------------------------
-- 4. 用户交互记录表 (sys_interaction)
-- 推荐系统的灵魂：记录谁看了什么（浏览、点赞、收藏、评论）
-- ----------------------------
DROP TABLE IF EXISTS `sys_interaction`;
CREATE TABLE `sys_interaction` (
                                   `id` varchar(255) NOT NULL COMMENT '主键ID（应用生成）' ,
                                   `user_id` varchar(255) NOT NULL,
                                   `post_id` varchar(255) NOT NULL,
                                   `type` tinyint NOT NULL COMMENT '交互类型 1:浏览 2:点赞 3:收藏 4:评论',
                                   `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_user_post` (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交互行为表';

-- 模拟数据：
INSERT INTO `sys_interaction` (`id`, `user_id`, `post_id`, `type`) VALUES
                                                                       ('i001', '333444', '1', 1), -- student1 浏览了 运动会通知
                                                                       ('i002', '444555', '1', 1), -- student2 浏览了 运动会通知
                                                                       ('i003', '666777', '4', 2), -- student3 点赞了 Java笔记
                                                                       ('i004', '333444', '2', 3), -- student1 收藏了 红宝书
                                                                       ('i005', '444555', '3', 4), -- student2 评论了 食堂吐槽
                                                                       ('i006', '223333', '5', 1), -- admin 浏览了 奶茶店讨论
                                                                       ('i007', '666777', '8', 2), -- student3 点赞了 AI讲座
                                                                       ('i008', '444555', '7', 1); -- student2 浏览了 学术资源分享


-- ----------------------------
-- 5. 趋势统计缓存表 (sys_trend_stat)
-- 用于 ECharts 图表展示，定时任务每天算完存这里
-- ----------------------------
DROP TABLE IF EXISTS `sys_trend_stat`;
CREATE TABLE `sys_trend_stat` (
                                  `id` varchar(255) NOT NULL COMMENT '主键ID（应用生成）',
                                  `stat_date` date NOT NULL COMMENT '统计日期',
                                  `type` varchar(20) NOT NULL COMMENT '统计类型 (keyword_cloud, category_pie, daily_traffic)',
                                  `data_json` text NOT NULL COMMENT '统计结果JSON数据 (直接给前端ECharts用)',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='趋势分析缓存表';

-- 模拟数据：
INSERT INTO `sys_trend_stat` (`id`, `stat_date`, `type`, `data_json`) VALUES
                                                                          ('s001', '2025-11-23', 'daily_traffic', '{"views": 5200, "posts": 45, "interactions": 1200}'),
                                                                          ('s002', '2025-11-23', 'category_pie', '[{"name":"资讯", "value": 300}, {"name":"学术", "value": 200}, {"name":"生活", "value": 150}, {"name":"话题", "value": 350}]');


SET FOREIGN_KEY_CHECKS = 1;