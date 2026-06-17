-- ============================================================
-- 2026-06-16 增量迁移：发展历程扩展为社区路线图
--
-- 特点：
--   1) 只新增字段和索引，不删除已有数据
--   2) 使用 information_schema 判断，重复执行不报错
--   3) 补入默认 v1.0.0/v1.1.0/v1.2.0 路线图，可在后台继续编辑
-- ============================================================

USE `campus_pulse`;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `stage_no` VARCHAR(16) DEFAULT NULL COMMENT ''路线图阶段编号，如 01'' AFTER `content`',
        'SELECT ''stage_no already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'stage_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `stage_label` VARCHAR(32) DEFAULT NULL COMMENT ''路线图阶段标签，如 已上线/建设中/下一阶段'' AFTER `stage_no`',
        'SELECT ''stage_label already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'stage_label'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `roadmap_status` VARCHAR(32) DEFAULT ''released'' COMMENT ''上线状态 released/building/planned'' AFTER `stage_label`',
        'SELECT ''roadmap_status already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'roadmap_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `highlights` VARCHAR(255) DEFAULT NULL COMMENT ''路线图高亮信息，如 发帖/评论/标签'' AFTER `roadmap_status`',
        'SELECT ''highlights already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'highlights'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `action_path` VARCHAR(255) DEFAULT NULL COMMENT ''详情或后续升级入口'' AFTER `highlights`',
        'SELECT ''action_path already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'action_path'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `upgrade_enabled` TINYINT DEFAULT 0 COMMENT ''是否开放在线升级入口 1是 0否'' AFTER `action_path`',
        'SELECT ''upgrade_enabled already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'upgrade_enabled'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_changelog` ADD COLUMN `upgrade_url` VARCHAR(500) DEFAULT NULL COMMENT ''在线升级地址或说明链接'' AFTER `upgrade_enabled`',
        'SELECT ''upgrade_url already exists''')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND COLUMN_NAME = 'upgrade_url'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX `idx_changelog_roadmap_status` ON `sys_changelog` (`roadmap_status`, `status`, `sort_order`)',
        'SELECT ''idx_changelog_roadmap_status already exists''')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_changelog'
      AND INDEX_NAME = 'idx_changelog_roadmap_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `sys_changelog`
(`version`, `title`, `content`, `stage_no`, `stage_label`, `roadmap_status`, `highlights`, `timestamp`, `status`, `sort_order`)
SELECT 'v1.0.0', '基础社区功能', '已完成发帖、评论、标签分类、个人主页和后台管理。', '01', '已上线', 'released', '发帖/评论/标签', '已上线', 1, 300
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_changelog` WHERE `version` = 'v1.0.0');

INSERT INTO `sys_changelog`
(`version`, `title`, `content`, `stage_no`, `stage_label`, `roadmap_status`, `highlights`, `timestamp`, `status`, `sort_order`)
SELECT 'v1.1.0', '用户体验优化', '正在优化错误提示、内容推荐、项目展示和消息通知。', '02', '建设中', 'building', '推荐/通知/展示', '建设中', 1, 200
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_changelog` WHERE `version` = 'v1.1.0');

INSERT INTO `sys_changelog`
(`version`, `title`, `content`, `stage_no`, `stage_label`, `roadmap_status`, `highlights`, `timestamp`, `status`, `sort_order`)
SELECT 'v1.2.0', '内容生态增强', '计划加入积分等级、问答专区、创作者激励和专栏系统。', '03', '下一阶段', 'planned', '积分/问答/专栏', '下一阶段', 1, 100
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_changelog` WHERE `version` = 'v1.2.0');

UPDATE `sys_changelog`
SET `stage_no` = COALESCE(`stage_no`, '01'),
    `stage_label` = COALESCE(`stage_label`, '已上线'),
    `roadmap_status` = COALESCE(`roadmap_status`, 'released'),
    `highlights` = COALESCE(`highlights`, '发帖/评论/标签'),
    `timestamp` = COALESCE(`timestamp`, '已上线'),
    `sort_order` = CASE WHEN `sort_order` = 0 THEN 300 ELSE `sort_order` END
WHERE `version` = 'v1.0.0';

UPDATE `sys_changelog`
SET `stage_no` = COALESCE(`stage_no`, '02'),
    `stage_label` = COALESCE(`stage_label`, '建设中'),
    `roadmap_status` = COALESCE(`roadmap_status`, 'building'),
    `highlights` = COALESCE(`highlights`, '推荐/通知/展示'),
    `timestamp` = COALESCE(`timestamp`, '建设中'),
    `sort_order` = CASE WHEN `sort_order` = 0 THEN 200 ELSE `sort_order` END
WHERE `version` = 'v1.1.0';

UPDATE `sys_changelog`
SET `stage_no` = COALESCE(`stage_no`, '03'),
    `stage_label` = COALESCE(`stage_label`, '下一阶段'),
    `roadmap_status` = COALESCE(`roadmap_status`, 'planned'),
    `highlights` = COALESCE(`highlights`, '积分/问答/专栏'),
    `timestamp` = COALESCE(`timestamp`, '下一阶段'),
    `sort_order` = CASE WHEN `sort_order` = 0 THEN 100 ELSE `sort_order` END
WHERE `version` = 'v1.2.0';

-- ============================================================
-- 迁移完成
-- 校验：
--   DESC sys_changelog;
--   SELECT version, stage_no, stage_label, roadmap_status, status, sort_order FROM sys_changelog ORDER BY sort_order DESC;
-- ============================================================
