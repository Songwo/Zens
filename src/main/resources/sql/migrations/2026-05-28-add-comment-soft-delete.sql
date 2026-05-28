-- ============================================================
-- 评论软删除字段迁移：2026-05-28
-- 给 sys_comment 加 audit_status + update_time，对齐 Post 软删模式
-- ============================================================

ALTER TABLE `sys_comment`
  ADD COLUMN `audit_status` varchar(16) NOT NULL DEFAULT 'APPROVED'
      COMMENT '审核/删除状态: APPROVED|DELETED'
      AFTER `like_count`,
  ADD COLUMN `update_time` datetime DEFAULT CURRENT_TIMESTAMP
      ON UPDATE CURRENT_TIMESTAMP
      COMMENT '最后更新时间(软删 3 天倒计时基准)'
      AFTER `create_time`,
  ADD INDEX `idx_comment_audit_update` (`audit_status`, `update_time`);
