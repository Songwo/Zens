-- Add configurable lottery-post rules to the main post table.
ALTER TABLE `sys_post`
  ADD COLUMN `post_type` varchar(32) NOT NULL DEFAULT 'NORMAL' COMMENT '帖子类型 NORMAL/LOTTERY' AFTER `is_anonymous`,
  ADD COLUMN `comment_deadline` datetime DEFAULT NULL COMMENT '抽奖帖评论截止时间' AFTER `post_type`,
  ADD COLUMN `comment_once_per_user` tinyint(1) NOT NULL DEFAULT 0 COMMENT '抽奖帖是否限制每人评论一次' AFTER `comment_deadline`,
  ADD KEY `idx_post_type_deadline` (`post_type`, `comment_deadline`);
