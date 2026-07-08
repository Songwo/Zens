-- ============================================================
-- 迁移脚本：2026-07-06 帖子恢复状态快照 + 评论内容扩容
-- 幂等：判存在再 ADD / 判类型再 MODIFY，可安全重复执行；绝不 DROP、不动已有数据。
--
-- 背景：
--   1) 回收站恢复漏洞修复 —— sys_post 增加删除前状态快照两列，
--      恢复时还原删前状态，堵住 REJECTED/PENDING/DRAFT 帖删除→恢复被"洗白"成已发布。
--   2) sys_comment.content 由 varchar(1000) 扩为 TEXT ——
--      后端 @Size(max=2000) + HtmlUtils.htmlEscape 实体膨胀（如 < → &lt;）
--      会超出 1000 列宽导致 SQL 截断报错。
--
-- 用法（必须用 mysql 命令行客户端执行，脚本含 DELIMITER）：
--   mysql -u<user> -p campus_pulse < 2026-07-06-post-restore-and-comment-fix.sql
-- ============================================================
USE `campus_pulse`;

DROP PROCEDURE IF EXISTS __add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE __add_col_if_missing(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_ddl VARCHAR(2000))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col
    ) THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('[added] ', p_table, '.', p_col) AS migration_log;
    ELSE
        SELECT CONCAT('[skip ] ', p_table, '.', p_col, ' already exists') AS migration_log;
    END IF;
END$$
DELIMITER ;

-- ① sys_post 删除前状态快照（软删时写入，恢复时还原并清空）
CALL __add_col_if_missing('sys_post', 'pre_delete_status',
    'ALTER TABLE `sys_post` ADD COLUMN `pre_delete_status` tinyint DEFAULT NULL COMMENT ''软删前 status 快照(恢复用)''');
CALL __add_col_if_missing('sys_post', 'pre_delete_audit_status',
    'ALTER TABLE `sys_post` ADD COLUMN `pre_delete_audit_status` varchar(20) DEFAULT NULL COMMENT ''软删前 audit_status 快照(恢复用)''');

DROP PROCEDURE IF EXISTS __add_col_if_missing;

-- ② sys_comment.content 扩容为 TEXT（判当前类型，仅 varchar 时才 MODIFY）
DROP PROCEDURE IF EXISTS __widen_comment_content;
DELIMITER $$
CREATE PROCEDURE __widen_comment_content()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_comment'
          AND COLUMN_NAME = 'content' AND DATA_TYPE = 'varchar'
    ) THEN
        ALTER TABLE `sys_comment` MODIFY COLUMN `content` text NOT NULL COMMENT '评论内容';
        SELECT '[added] sys_comment.content widened to TEXT' AS migration_log;
    ELSE
        SELECT '[skip ] sys_comment.content already TEXT' AS migration_log;
    END IF;
END$$
DELIMITER ;
CALL __widen_comment_content();
DROP PROCEDURE IF EXISTS __widen_comment_content;
