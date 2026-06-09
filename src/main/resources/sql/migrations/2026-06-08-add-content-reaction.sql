-- ============================================================
-- Migration: 内容表情反应（Reaction）
-- 执行时间：2026-06-08
-- 说明：在不改动现有点赞(post_like)的前提下，新增表情反应。
--       每个用户对每个目标(帖子/评论)最多一个表情，可切换或取消。
--       👍 仍是原有点赞，不进入此表。表情集：love/haha/wow/celebrate。
-- ============================================================
use campus_pulse;

CREATE TABLE IF NOT EXISTS `content_reaction` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `target_type`    VARCHAR(16)  NOT NULL COMMENT '目标类型: post/comment',
  `target_id`      VARCHAR(64)  NOT NULL COMMENT '目标ID',
  `user_id`        VARCHAR(64)  NOT NULL COMMENT '用户ID',
  `reaction_type`  VARCHAR(16)  NOT NULL COMMENT '表情类型: love/haha/wow/celebrate',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_target_user` (`target_type`, `target_id`, `user_id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容表情反应表';
