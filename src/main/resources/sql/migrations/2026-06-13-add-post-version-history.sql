-- 帖子版本历史：记录每次正文类编辑前的快照，用于前端 diff 展示

use campus_pulse;

CREATE TABLE IF NOT EXISTS `post_version_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id` VARCHAR(64) NOT NULL COMMENT '帖子ID',
    `version_no` INT NOT NULL COMMENT '版本号',
    `editor_id` VARCHAR(64) NOT NULL COMMENT '编辑用户ID',
    `editor_name` VARCHAR(128) DEFAULT NULL COMMENT '编辑用户展示名',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '编辑前标题',
    `content` LONGTEXT DEFAULT NULL COMMENT '编辑前正文',
    `tags` VARCHAR(512) DEFAULT NULL COMMENT '编辑前标签',
    `section_id` BIGINT DEFAULT NULL COMMENT '编辑前板块ID',
    `cover_image` VARCHAR(1024) DEFAULT NULL COMMENT '编辑前封面',
    `change_summary` VARCHAR(512) DEFAULT NULL COMMENT '变更摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_version` (`post_id`, `version_no`),
    KEY `idx_post_created` (`post_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子版本历史';
