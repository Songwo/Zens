-- ============================================================
-- 生产库一次性补齐脚本：2026-06-07
-- 幂等补全：缺失的表(CREATE IF NOT EXISTS)与缺失的列(判存在再 ADD)。
-- 已存在的表/列自动跳过,可安全重复执行;绝不 DROP、不动任何已有数据。
-- 解决部署新代码后 "Unknown column 'xxx'" / "Table doesn't exist" 导致查询整体报错。
--
-- 经与服务器结构比对(mysqldump --no-data),该库实际只缺：
--   列: sys_comment.audit_status、sys_comment.update_time
--   表: comment_collects、short_links
-- 其余列/表服务器已有,跑本脚本对应行会显示 [skip] 或被 IF NOT EXISTS 跳过。
--
-- 覆盖列(逐列判存在,缺则补)：
--   sys_comment: collect_count / edit_time / reply_to_user_id / reply_user_id
--                / is_anonymous / audit_status / update_time
--   sys_user:    cover_config / badge_text / badge_color / badge_style
-- 覆盖表(缺则建)：comment_collects(评论收藏) / short_links(短链接)
--
-- 用法（在服务器上，改成你的库账号）：
--   mysql -u<user> -p campus_pulse < catch-up-pending-2026-06-07.sql
-- 必须用 mysql 命令行客户端执行（脚本含 DELIMITER）。
--
-- 注：列位置(AFTER)对按列名查询无影响,故新列统一追加到表尾,
--     不依赖既有列顺序,避免「被引用列在旧库不存在」的连锁失败。
-- ============================================================
USE `campus_pulse`;

-- ① 先建缺失的整张表（CREATE IF NOT EXISTS 幂等：已存在则跳过,只建空表不动数据）
-- comment_collects：评论收藏功能(CommentCollectService)依赖；缺它收藏评论报 Table doesn't exist
CREATE TABLE IF NOT EXISTS `comment_collects` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `comment_id`  varchar(64)   NOT NULL,
  `user_id`     varchar(64)   NOT NULL,
  `created_at`  datetime      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_collect_user_comment` (`user_id`, `comment_id`),
  KEY `idx_comment_collect_comment` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论收藏表';

-- short_links：短链接功能(ShortLinkController/Service)依赖；缺它分享/解析短链报 Table doesn't exist
CREATE TABLE IF NOT EXISTS `short_links` (
  `code`        varchar(24)   NOT NULL COMMENT '短链接随机码',
  `target_type` varchar(20)   NOT NULL COMMENT 'comment/post',
  `post_id`     varchar(64)   DEFAULT NULL COMMENT '内部帖子ID',
  `comment_id`  varchar(64)   DEFAULT NULL COMMENT '内部评论ID',
  `creator_id`  varchar(64)   DEFAULT NULL COMMENT '创建人ID',
  `create_time` datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`code`),
  KEY `idx_short_link_target` (`target_type`, `post_id`, `comment_id`),
  KEY `idx_short_link_creator_time` (`creator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短链接映射表';

-- ② 再补缺失的列（information_schema 判存在再 ADD,幂等）
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

CALL __add_col_if_missing('sys_comment', 'collect_count',
    'ALTER TABLE `sys_comment` ADD COLUMN `collect_count` int DEFAULT 0 COMMENT ''收藏数''');
CALL __add_col_if_missing('sys_comment', 'edit_time',
    'ALTER TABLE `sys_comment` ADD COLUMN `edit_time` datetime DEFAULT NULL COMMENT ''最后编辑时间(为空表示未编辑过;与软删倒计时无关)''');
CALL __add_col_if_missing('sys_comment', 'reply_to_user_id',
    'ALTER TABLE `sys_comment` ADD COLUMN `reply_to_user_id` varchar(64) DEFAULT NULL COMMENT ''被回复用户ID(兼容字段)''');
CALL __add_col_if_missing('sys_comment', 'reply_user_id',
    'ALTER TABLE `sys_comment` ADD COLUMN `reply_user_id` varchar(64) DEFAULT NULL COMMENT ''被回复用户ID''');
CALL __add_col_if_missing('sys_comment', 'is_anonymous',
    'ALTER TABLE `sys_comment` ADD COLUMN `is_anonymous` tinyint(1) DEFAULT 0 COMMENT ''是否匿名''');
CALL __add_col_if_missing('sys_comment', 'audit_status',
    'ALTER TABLE `sys_comment` ADD COLUMN `audit_status` varchar(16) NOT NULL DEFAULT ''APPROVED'' COMMENT ''审核/删除状态 APPROVED/DELETED''');
CALL __add_col_if_missing('sys_comment', 'update_time',
    'ALTER TABLE `sys_comment` ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''最后更新时间(软删 3 天倒计时基准)''');
CALL __add_col_if_missing('sys_user', 'cover_config',
    'ALTER TABLE `sys_user` ADD COLUMN `cover_config` varchar(255) DEFAULT NULL COMMENT ''封面展示配置(JSON: fit/x/y/height)''');
CALL __add_col_if_missing('sys_user', 'badge_text',
    'ALTER TABLE `sys_user` ADD COLUMN `badge_text` varchar(40) DEFAULT NULL COMMENT ''用户徽章文字(管理员授予的纯文字flair)，空表示无''');
CALL __add_col_if_missing('sys_user', 'badge_color',
    'ALTER TABLE `sys_user` ADD COLUMN `badge_color` varchar(20) DEFAULT NULL COMMENT ''徽章颜色(纯色样式用,hex 如 #a855f7)，空表示用默认色''');
CALL __add_col_if_missing('sys_user', 'badge_style',
    'ALTER TABLE `sys_user` ADD COLUMN `badge_style` varchar(16) NOT NULL DEFAULT ''solid'' COMMENT ''徽章样式: solid=纯色 / rainbow=七彩跑马动效''');

DROP PROCEDURE IF EXISTS __add_col_if_missing;
