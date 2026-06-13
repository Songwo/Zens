-- ============================================================
-- Migration 003: 新增"答疑解惑"板块（答案采纳专用）
-- 执行时间：2026-06-08
-- 说明：答案采纳机制建议在专用问答板块使用，提升问答氛围
-- ============================================================

USE `campus_pulse`;

-- 新增"答疑解惑"板块（如不存在）
INSERT INTO `sections` (`id`, `name`, `description`, `icon`, `sort_order`, `status`)
SELECT 11, '答疑解惑', '提出问题、解答疑惑，最佳答案可被采纳', 'help', 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `sections` WHERE `name` = '答疑解惑'
);

-- 在 sections 表标记哪些板块支持答案采纳（新增字段）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'campus_pulse'
  AND TABLE_NAME = 'sections'
  AND COLUMN_NAME = 'allow_adoption';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE `sections` ADD COLUMN `allow_adoption` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否支持答案采纳 0=否 1=是'' AFTER `status`',
    'SELECT ''Column allow_adoption already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 开启"答疑解惑"板块的答案采纳功能
UPDATE `sections` SET `allow_adoption` = 1 WHERE `name` = '答疑解惑';

SELECT '✅ Migration 003 completed: 答疑解惑板块已就绪' AS status;
