use campus_pulse;

-- 插入用户数据
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`, `gender`, `role`, `school`, `major`, `grade`, `status`, `create_time`, `update_time`, `last_grade_upgrade`) VALUES
('1', '2021001', '$2a$10$N.zmdr9k7uOCQb376NoUnutj8iAt6ValmpBkJS.uPcaa.gOXJm5kq', '张三', 'https://example.com/avatar1.jpg', 1, 1, '清华大学', '计算机科学与技术', 2021, 1, NOW(), NOW(), NOW()),
('2', '2021002', '$2a$10$N.zmdr9k7uOCQb376NoUnutj8iAt6ValmpBkJS.uPcaa.gOXJm5kq', '李四', 'https://example.com/avatar2.jpg', 2, 1, '北京大学', '软件工程', 2021, 1, NOW(), NOW(), NOW()),
('3', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnutj8iAt6ValmpBkJS.uPcaa.gOXJm5kq', '管理员', 'https://example.com/admin.jpg', 1, 0, '系统管理', '管理', 2020, 1, NOW(), NOW(), NOW());

-- 插入帖子数据
INSERT INTO `sys_post` (`id`, `user_id`, `category_id`, `title`, `content`, `images`, `tags`, `is_anonymous`, `location_name`, `sentiment_score`, `status`, `audit_status`, `view_count`, `like_count`, `collect_count`, `comment_count`, `heat_score`, `create_time`, `update_time`) VALUES
('101', '1', '1', '今天食堂的饭真好吃', '红烧肉不错，推荐大家去尝尝！', '["https://example.com/food.jpg"]', '#食堂 #美食', 0, '第一食堂', 0.8, 1, 'APPROVED', 100, 10, 5, 2, 80.5, NOW(), NOW()),
('102', '2', '2', '图书馆占座太难了', '早上6点去都没位置，太卷了...', NULL, '#图书馆 #学习', 1, '图书馆', -0.2, 1, 'APPROVED', 200, 20, 2, 5, 90.0, NOW(), NOW());

-- 插入评论数据
INSERT INTO `sys_comment` (`id`, `post_id`, `user_id`, `content`, `parent_id`, `reply_user_id`, `is_anonymous`, `like_count`, `create_time`) VALUES
('201', '101', '2', '确实好吃，我也吃了！', '0', NULL, 0, 5, NOW()),
('202', '101', '3', '注意膳食均衡哦', '201', '2', 0, 2, NOW());

-- 插入标签数据
INSERT INTO `sys_tag` (`id`, `name`, `type`, `heat`, `create_time`) VALUES
(1, '食堂', 2, 100, NOW()),
(2, '美食', 2, 80, NOW()),
(3, '图书馆', 2, 200, NOW()),
(4, '学习', 2, 150, NOW());

-- 插入点赞数据
INSERT INTO `sys_post_like` (`id`, `post_id`, `user_id`, `create_time`) VALUES
('301', '101', '2', NOW());

-- 插入收藏数据
INSERT INTO `sys_post_collect` (`id`, `post_id`, `user_id`, `create_time`) VALUES
('401', '102', '1', NOW());

-- 插入用户画像数据
INSERT INTO `sys_user_profile` (`user_id`, `reputation`, `contribution_val`, `active_region`, `preferred_cate_json`, `total_posts`, `total_likes_received`, `last_active_time`) VALUES
('1', 100, 50, '第一食堂', '{"food": 0.8, "study": 0.2}', 1, 10, NOW()),
('2', 90, 30, '图书馆', '{"study": 0.9, "food": 0.1}', 1, 5, NOW());
