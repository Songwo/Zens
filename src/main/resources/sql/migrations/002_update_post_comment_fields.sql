-- ============================================================
-- Migration 002: 更新 sys_post 和 sys_comment 表，添加新字段
-- 执行时间：2026-06-08
-- 说明：为答案采纳功能添加必要字段
-- ============================================================

USE `campus_pulse`;

-- 检查并添加 has_adopted_answer 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'campus_pulse'
  AND TABLE_NAME = 'sys_post'
  AND COLUMN_NAME = 'has_adopted_answer';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `sys_post` ADD COLUMN `has_adopted_answer` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否有采纳答案 0=否 1=是'' AFTER `comment_count`',
    'SELECT ''Column has_adopted_answer already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @index_exists = 0;
SELECT COUNT(*) INTO @index_exists
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'campus_pulse'
  AND TABLE_NAME = 'sys_post'
  AND INDEX_NAME = 'idx_post_adopted';

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE `sys_post` ADD KEY `idx_post_adopted` (`has_adopted_answer`, `create_time`)',
    'SELECT ''Index idx_post_adopted already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并添加 is_adopted 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'campus_pulse'
  AND TABLE_NAME = 'sys_comment'
  AND COLUMN_NAME = 'is_adopted';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `sys_comment` ADD COLUMN `is_adopted` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否被采纳为最佳答案'' AFTER `collect_count`',
    'SELECT ''Column is_adopted already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @index_exists = 0;
SELECT COUNT(*) INTO @index_exists
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'campus_pulse'
  AND TABLE_NAME = 'sys_comment'
  AND INDEX_NAME = 'idx_comment_adopted';

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE `sys_comment` ADD KEY `idx_comment_adopted` (`post_id`, `is_adopted`)',
    'SELECT ''Index idx_comment_adopted already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '✅ Migration 002 completed successfully' AS status;
