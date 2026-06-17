-- ============================================================
-- 2026-06-14 增量迁移：信任等级 + 阅读时长 + 自治 Flag + 搜索 + 帖子阅读热度
-- 对应代码改动：TrustLevelService、SearchService、OneboxService、ViewLog heartbeat
--
-- 特点：纯 ALTER/CREATE 语句，无存储过程、无 DELIMITER，
--       兼容 DataGrip / Navicat / mysql CLI，可逐条执行。
-- 幂等设计：每条都先查 information_schema，已存在则跳过，重复执行不报错。
-- 不会 DROP 任何表、不会删除任何数据。
-- ============================================================

use campus_pulse;

-- ------------------------------------------------------------
-- 1) sys_user 新增 5 个字段（信任等级体系 + 阅读时长统计）
-- ------------------------------------------------------------
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'trust_level') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `trust_level` TINYINT NOT NULL DEFAULT 0 COMMENT ''信任等级 0-4: TL0新人/TL1基础/TL2成员/TL3常客/TL4领袖'' AFTER `contribution_val`',
    'SELECT ''sys_user.trust_level 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'silenced_until') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `silenced_until` DATETIME DEFAULT NULL COMMENT ''禁言截止时间，NULL=未禁言'' AFTER `trust_level`',
    'SELECT ''sys_user.silenced_until 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'read_time_sec') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `read_time_sec` INT NOT NULL DEFAULT 0 COMMENT ''累计阅读时长(秒)，用于 TL 计算'' AFTER `silenced_until`',
    'SELECT ''sys_user.read_time_sec 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'days_visited') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `days_visited` INT NOT NULL DEFAULT 0 COMMENT ''累计访问天数(sys_view_log 去重日期)'' AFTER `read_time_sec`',
    'SELECT ''sys_user.days_visited 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'likes_given') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `likes_given` INT NOT NULL DEFAULT 0 COMMENT ''发出点赞总数'' AFTER `days_visited`',
    'SELECT ''sys_user.likes_given 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2) sys_view_log 新增阅读时长字段（阅读时长心跳用）
-- ------------------------------------------------------------
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_view_log' AND COLUMN_NAME = 'duration_ms') = 0,
    'ALTER TABLE `sys_view_log` ADD COLUMN `duration_ms` INT NOT NULL DEFAULT 0 COMMENT ''本次浏览停留毫秒数'' AFTER `device`',
    'SELECT ''sys_view_log.duration_ms 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3) sys_report 新增自治 Flag 加权字段
-- ------------------------------------------------------------
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_report' AND COLUMN_NAME = 'reporter_trust_level') = 0,
    'ALTER TABLE `sys_report` ADD COLUMN `reporter_trust_level` TINYINT DEFAULT 0 COMMENT ''举报人当时的信任等级，用于加权'' AFTER `reporter_id`',
    'SELECT ''sys_report.reporter_trust_level 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_report' AND COLUMN_NAME = 'flag_weight') = 0,
    'ALTER TABLE `sys_report` ADD COLUMN `flag_weight` INT DEFAULT 1 COMMENT ''flag 权重=举报人 TL 映射值'' AFTER `reporter_trust_level`',
    'SELECT ''sys_report.flag_weight 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4) sys_post 新增平均阅读时长字段（热度公式加权用）
-- ------------------------------------------------------------
SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_post' AND COLUMN_NAME = 'avg_dwell_sec') = 0,
    'ALTER TABLE `sys_post` ADD COLUMN `avg_dwell_sec` INT DEFAULT 0 COMMENT ''平均阅读时长(秒)，热度加权用'' AFTER `heat_score`',
    'SELECT ''sys_post.avg_dwell_sec 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 5) 新增索引（存在性判断避免重复创建报错）
-- ------------------------------------------------------------
SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_user_trust_level') = 0,
    'CREATE INDEX `idx_user_trust_level` ON `sys_user` (`trust_level`, `status`)',
    'SELECT ''索引 idx_user_trust_level 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_report' AND INDEX_NAME = 'idx_report_target_weight') = 0,
    'CREATE INDEX `idx_report_target_weight` ON `sys_report` (`target_type`, `target_id`, `flag_weight`)',
    'SELECT ''索引 idx_report_target_weight 已存在，跳过'' AS msg');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
