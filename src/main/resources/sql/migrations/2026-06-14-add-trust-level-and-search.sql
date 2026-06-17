-- ============================================================
-- 2026-06-14 增量迁移：信任等级 + 阅读时长 + 自治 Flag + 搜索 + 帖子阅读热度
-- 对应代码改动：TrustLevelService、SearchService、OneboxService、ViewLog heartbeat
--
-- 特点：全部用 IF NOT EXISTS 做幂等，可重复执行不报错。
-- 不会 DROP 任何表、不会删除任何数据。只 ADD COLUMN / CREATE TABLE IF NOT EXISTS。
-- ============================================================

-- ------------------------------------------------------------
-- 1) sys_user 新增 5 个字段（信任等级体系 + 阅读时长统计）
-- ------------------------------------------------------------
-- trust_level        信任等级 0-4: TL0新人/TL1基础/TL2成员/TL3常客/TL4领袖
-- silenced_until     禁言截止时间，NULL=未禁言
-- read_time_sec      累计阅读时长（秒），用于 TL 计算
-- days_visited       累计访问天数（sys_view_log 去重日期）
-- likes_given        发出点赞总数
-- 注：MySQL 8.0.29+ 才支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS。
--     为兼容更早版本，这里用存储过程 + information_schema 探测列是否存在。

use campus_pulse;

DROP PROCEDURE IF EXISTS cp_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE cp_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- sys_user 信任等级字段
CALL cp_add_column_if_missing('sys_user', 'trust_level',
    "TINYINT NOT NULL DEFAULT 0 COMMENT '信任等级 0-4: TL0新人/TL1基础/TL2成员/TL3常客/TL4领袖' AFTER contribution_val");
CALL cp_add_column_if_missing('sys_user', 'silenced_until',
    "DATETIME DEFAULT NULL COMMENT '禁言截止时间，NULL=未禁言' AFTER trust_level");
CALL cp_add_column_if_missing('sys_user', 'read_time_sec',
    "INT NOT NULL DEFAULT 0 COMMENT '累计阅读时长(秒)，用于 TL 计算' AFTER silenced_until");
CALL cp_add_column_if_missing('sys_user', 'days_visited',
    "INT NOT NULL DEFAULT 0 COMMENT '累计访问天数(sys_view_log 去重日期)' AFTER read_time_sec");
CALL cp_add_column_if_missing('sys_user', 'likes_given',
    "INT NOT NULL DEFAULT 0 COMMENT '发出点赞总数' AFTER days_visited");

-- ------------------------------------------------------------
-- 2) sys_view_log 新增阅读时长字段（阅读时长心跳用）
-- ------------------------------------------------------------
CALL cp_add_column_if_missing('sys_view_log', 'duration_ms',
    "INT NOT NULL DEFAULT 0 COMMENT '本次浏览停留毫秒数' AFTER device");

-- ------------------------------------------------------------
-- 3) sys_report 新增自治 Flag 加权字段
-- ------------------------------------------------------------
CALL cp_add_column_if_missing('sys_report', 'reporter_trust_level',
    "TINYINT DEFAULT 0 COMMENT '举报人当时的信任等级，用于加权' AFTER reporter_id");
CALL cp_add_column_if_missing('sys_report', 'flag_weight',
    "INT DEFAULT 1 COMMENT 'flag 权重=举报人 TL 映射值' AFTER reporter_trust_level");

-- ------------------------------------------------------------
-- 4) sys_post 新增平均阅读时长字段（热度公式加权用）
-- ------------------------------------------------------------
CALL cp_add_column_if_missing('sys_post', 'avg_dwell_sec',
    "INT DEFAULT 0 COMMENT '平均阅读时长(秒)，热度加权用' AFTER heat_score");

-- ------------------------------------------------------------
-- 5) 新增索引（同样做存在性判断，避免重复创建报错）
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS cp_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE cp_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_cols TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table
          AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL cp_add_index_if_missing('sys_user', 'idx_user_trust_level', '`trust_level`, `status`');
CALL cp_add_index_if_missing('sys_report', 'idx_report_target_weight', '`target_type`, `target_id`, `flag_weight`');

-- ------------------------------------------------------------
-- 6) 新建信任等级变更日志表（TL 晋升/降级审计）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_trust_event` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`      VARCHAR(64)   NOT NULL,
  `old_level`    TINYINT       NOT NULL,
  `new_level`    TINYINT       NOT NULL,
  `reason`       VARCHAR(200)  NOT NULL COMMENT '晋升/降级原因',
  `metrics_json` TEXT          DEFAULT NULL COMMENT '触发时的指标快照',
  `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_trust_event_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信任等级变更日志';

-- ------------------------------------------------------------
-- 清理临时存储过程
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS cp_add_column_if_missing;
DROP PROCEDURE IF EXISTS cp_add_index_if_missing;

-- ============================================================
-- 迁移完成
-- 执行后可校验：
--   DESC sys_user;          -- 应看到 trust_level 等 5 个新字段
--   DESC sys_view_log;      -- 应看到 duration_ms
--   DESC sys_report;        -- 应看到 reporter_trust_level, flag_weight
--   DESC sys_post;          -- 应看到 avg_dwell_sec
--   SHOW TABLES LIKE 'sys_trust_event';  -- 应存在
--
-- 老用户初始 trust_level 全是 0（TL0），启动后端后调一次：
--   POST /api/trust-level/recalculate  （管理员触发全量重算）
-- 或等每日 02:30 定时任务自动重算。
-- ============================================================
