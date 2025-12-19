-- =================================================================
-- 推荐系统测试数据脚本
-- 包含：用户、帖子、标签关联、浏览记录（用于协同过滤）
-- =================================================================

-- 1. 准备测试用户
-- test_u1: 技术宅，喜欢 Java, AI
-- test_u2: 考研党，关注 考研, 学习
-- test_u3: 生活派，关注 恋爱, 美食, 二手
-- test_u4: 模拟用户A (用于产生协同过滤数据)
-- test_u5: 模拟用户B (用于产生协同过滤数据)
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `school`, `status`) VALUES
('test_u1', 'tech_user', '123456', '极客阿强', '计算机学院', 1),
('test_u2', 'study_user', '123456', '考研上岸', '数理学院', 1),
('test_u3', 'life_user', '123456', '生活也是学问', '文法学院', 1),
('test_u4', 'cf_user_a', '123456', '路人甲', '电信学院', 1),
('test_u5', 'cf_user_b', '123456', '路人乙', '外语学院', 1);

-- 2. 准备标签 (确保存在)
INSERT IGNORE INTO `sys_tag` (`name`, `type`, `heat`) VALUES
('Java', 1, 500), ('Spring', 1, 400), ('AI', 1, 600),
('考研', 1, 800), ('英语', 1, 300), ('高数', 1, 250),
('恋爱', 1, 900), ('吐槽', 1, 750), ('二手', 1, 200),
('求职', 1, 450);

-- 获取标签ID (假设自增ID)
-- 这里为了脚本可执行性，我们在 sys_user_tag_relation 中会先查询 tag_id 或者手动指定
-- 假设:
-- Java(id=?) 对应 test_u1
-- 考研(id=?) 对应 test_u2

-- 3. 准备测试帖子 (由于ID是UUID/String，我们手动指定方便关联)

-- [技术类帖子]
INSERT INTO `sys_post` (`id`, `user_id`, `category_id`, `title`, `content`, `tags`, `heat_score`, `view_count`, `like_count`) VALUES
('test_p1', 'test_u1', 'c4', 'Java后端学习路线2026版', '详细的Java学习路线图...', '#Java #Spring #求职', 100.0, 500, 50),
('test_p2', 'test_u1', 'c4', 'SpringBoot3.0新特性解析', 'SpringBoot3.0带来了什么...', '#Java #Spring', 80.0, 300, 30),
('test_p3', 'test_u1', 'c4', 'AI大模型在编程中的应用', '如何使用ChatGPT辅助编程...', '#AI #Java', 120.0, 800, 100),
('test_p4', 'test_u1', 'c4', '我的秋招面试经历(字节/阿里)', '分享一下面试题...', '#求职 #Java', 90.0, 400, 45);

-- [考研学习类帖子]
INSERT INTO `sys_post` (`id`, `user_id`, `category_id`, `title`, `content`, `tags`, `heat_score`, `view_count`, `like_count`) VALUES
('test_p5', 'test_u2', 'c4', '2026考研数学复习规划', '张宇还是汤家凤？...', '#考研 #高数', 150.0, 1000, 120),
('test_p6', 'test_u2', 'c4', '英语一85分经验分享', '单词怎么背...', '#考研 #英语', 130.0, 900, 95),
('test_p7', 'test_u2', 'c4', '图书馆占座大赏', '今天的图书馆...', '#考研 #吐槽', 60.0, 200, 10);

-- [生活情感类帖子]
INSERT INTO `sys_post` (`id`, `user_id`, `category_id`, `title`, `content`, `tags`, `heat_score`, `view_count`, `like_count`) VALUES
('test_p8', 'test_u3', 'c3', '在食堂遇到了crush', '那个穿白衣服的男生...', '#恋爱 #吐槽', 200.0, 2000, 300),
('test_p9', 'test_u3', 'c5', '出闲置：iPad Air 5', '考研结束了，出个板子...', '#二手 #考研', 70.0, 150, 5),
('test_p10', 'test_u3', 'c2', '二食堂的猪脚饭太好吃了', '强烈推荐...', '#吐槽', 50.0, 100, 20);

-- 4. 建立用户画像 (用户-标签关联)

-- test_u1 (极客阿强) 关注 Java, AI
INSERT INTO `sys_user_tag_relation` (`user_id`, `tag_id`, `score`) 
SELECT 'test_u1', id, 5.0 FROM `sys_tag` WHERE name IN ('Java', 'AI');

-- test_u2 (考研上岸) 关注 考研
INSERT INTO `sys_user_tag_relation` (`user_id`, `tag_id`, `score`) 
SELECT 'test_u2', id, 5.0 FROM `sys_tag` WHERE name IN ('考研');

-- test_u3 (生活派) 关注 恋爱
INSERT INTO `sys_user_tag_relation` (`user_id`, `tag_id`, `score`) 
SELECT 'test_u3', id, 5.0 FROM `sys_tag` WHERE name IN ('恋爱');


-- 5. 构造协同过滤数据 (浏览日志)
-- 场景：很多人看了 "Java后端学习路线" (p1) 的人，也看了 "SpringBoot特性" (p2) 和 "AI大模型" (p3)
-- 目的是：当新用户看 p1 时，推荐 p2, p3

-- 5组数据：同时看了 p1 和 p2
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_1'), ('test_p2', 'cf_user_1');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_2'), ('test_p2', 'cf_user_2');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_3'), ('test_p2', 'cf_user_3');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_4'), ('test_p2', 'cf_user_4');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_5'), ('test_p2', 'cf_user_5');

-- 3组数据：同时看了 p1 和 p3
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_6'), ('test_p3', 'cf_user_6');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_7'), ('test_p3', 'cf_user_7');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p1', 'cf_user_8'), ('test_p3', 'cf_user_8');

-- 场景2：考研圈子
-- 看了 p5(数学) 的人也看了 p6(英语)
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p5', 'cf_stu_1'), ('test_p6', 'cf_stu_1');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p5', 'cf_stu_2'), ('test_p6', 'cf_stu_2');
INSERT INTO `sys_view_log` (`post_id`, `user_id`) VALUES ('test_p5', 'cf_stu_3'), ('test_p6', 'cf_stu_3');

-- 6. 构造互动数据 (点赞)
INSERT INTO `sys_post_like` (`id`, `post_id`, `user_id`) VALUES
('like_1', 'test_p1', 'test_u2'),
('like_2', 'test_p1', 'test_u3'),
('like_3', 'test_p8', 'test_u1');

-- 提示信息
SELECT 'Test data inserted successfully. Use test_u1/u2/u3 to verify recommendations.' as result;
