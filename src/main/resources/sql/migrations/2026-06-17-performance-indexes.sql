-- ============================================================
-- Campus Pulse 性能优化索引脚本
-- 执行时间: 2026-06-17
-- 说明: 添加高频查询场景的组合索引，优化列表页和热点数据访问
--
-- IDEA / MySQL 兼容说明：
--   1) 不使用 DROP INDEX IF EXISTS，兼容更多 MySQL 版本。
--   2) 使用 information_schema 判断，重复执行不会报错。
--   3) 表不存在时自动跳过，避免增量环境执行中断。
-- ============================================================

USE `campus_pulse`;

SET @schema_name := DATABASE();

-- ============================================================
-- 1. 用户表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'status')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'last_active_time')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'id')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_user_last_active_status'),
        'CREATE INDEX `idx_user_last_active_status` ON `sys_user` (`status`, `last_active_time` DESC, `id`)',
        'SELECT ''skip idx_user_last_active_status''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'status')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'level')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'experience')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'id')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_user_level_exp'),
        'CREATE INDEX `idx_user_level_exp` ON `sys_user` (`status`, `level` DESC, `experience` DESC, `id`)',
        'SELECT ''skip idx_user_level_exp''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 评论表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'post_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'audit_status')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'like_count')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND INDEX_NAME = 'idx_comment_post_hot'),
        'CREATE INDEX `idx_comment_post_hot` ON `sys_comment` (`post_id`, `audit_status`, `like_count` DESC, `create_time` DESC)',
        'SELECT ''skip idx_comment_post_hot''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'user_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'audit_status')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_comment' AND INDEX_NAME = 'idx_comment_user_time'),
        'CREATE INDEX `idx_comment_user_time` ON `sys_comment` (`user_id`, `audit_status`, `create_time` DESC)',
        'SELECT ''skip idx_comment_user_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 通知表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'user_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'is_read')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND INDEX_NAME = 'idx_notification_user_unread'),
        'CREATE INDEX `idx_notification_user_unread` ON `sys_notification` (`user_id`, `is_read`, `create_time` DESC)',
        'SELECT ''skip idx_notification_user_unread''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'user_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'type')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'is_read')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_notification' AND INDEX_NAME = 'idx_notification_user_type'),
        'CREATE INDEX `idx_notification_user_type` ON `sys_notification` (`user_id`, `type`, `is_read`, `create_time` DESC)',
        'SELECT ''skip idx_notification_user_type''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 4. 帖子点赞表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like' AND COLUMN_NAME = 'post_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like' AND INDEX_NAME = 'idx_post_like_post_time'),
        'CREATE INDEX `idx_post_like_post_time` ON `sys_post_like` (`post_id`, `create_time` DESC)',
        'SELECT ''skip idx_post_like_post_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like' AND COLUMN_NAME = 'user_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_like' AND INDEX_NAME = 'idx_post_like_user_time'),
        'CREATE INDEX `idx_post_like_user_time` ON `sys_post_like` (`user_id`, `create_time` DESC)',
        'SELECT ''skip idx_post_like_user_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 5. 帖子收藏表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_collect')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_collect' AND COLUMN_NAME = 'user_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_collect' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_post_collect' AND INDEX_NAME = 'idx_post_collect_user_time'),
        'CREATE INDEX `idx_post_collect_user_time` ON `sys_post_collect` (`user_id`, `create_time` DESC)',
        'SELECT ''skip idx_post_collect_user_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 6. 关注表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow' AND COLUMN_NAME = 'followee_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow' AND INDEX_NAME = 'idx_follow_followee_time'),
        'CREATE INDEX `idx_follow_followee_time` ON `sys_follow` (`followee_id`, `create_time` DESC)',
        'SELECT ''skip idx_follow_followee_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow' AND COLUMN_NAME = 'follower_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_follow' AND INDEX_NAME = 'idx_follow_follower_time'),
        'CREATE INDEX `idx_follow_follower_time` ON `sys_follow` (`follower_id`, `create_time` DESC)',
        'SELECT ''skip idx_follow_follower_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 7. 标签表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_tag')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_tag' AND COLUMN_NAME = 'heat')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_tag' AND COLUMN_NAME = 'post_count')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_tag' AND COLUMN_NAME = 'id')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_tag' AND INDEX_NAME = 'idx_tag_heat_post_count'),
        'CREATE INDEX `idx_tag_heat_post_count` ON `sys_tag` (`heat` DESC, `post_count` DESC, `id`)',
        'SELECT ''skip idx_tag_heat_post_count''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 8. 浏览记录表索引优化（如果存在）
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log' AND COLUMN_NAME = 'user_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log' AND INDEX_NAME = 'idx_view_log_user_time'),
        'CREATE INDEX `idx_view_log_user_time` ON `sys_view_log` (`user_id`, `create_time` DESC)',
        'SELECT ''skip idx_view_log_user_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log' AND COLUMN_NAME = 'post_id')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_view_log' AND INDEX_NAME = 'idx_view_log_post_time'),
        'CREATE INDEX `idx_view_log_post_time` ON `sys_view_log` (`post_id`, `create_time` DESC)',
        'SELECT ''skip idx_view_log_post_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 9. 举报表索引优化
-- ============================================================

SET @ddl := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_report')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_report' AND COLUMN_NAME = 'status')
        AND EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_report' AND COLUMN_NAME = 'create_time')
        AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'sys_report' AND INDEX_NAME = 'idx_report_status_time'),
        'CREATE INDEX `idx_report_status_time` ON `sys_report` (`status`, `create_time` DESC)',
        'SELECT ''skip idx_report_status_time''')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 索引验证查询
-- ============================================================

SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    INDEX_TYPE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND INDEX_NAME IN (
    'idx_user_last_active_status',
    'idx_user_level_exp',
    'idx_comment_post_hot',
    'idx_comment_user_time',
    'idx_notification_user_unread',
    'idx_notification_user_type',
    'idx_post_like_post_time',
    'idx_post_like_user_time',
    'idx_post_collect_user_time',
    'idx_follow_followee_time',
    'idx_follow_follower_time',
    'idx_tag_heat_post_count',
    'idx_view_log_user_time',
    'idx_view_log_post_time',
    'idx_report_status_time'
  )
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT '性能优化索引脚本执行完成' AS message;
