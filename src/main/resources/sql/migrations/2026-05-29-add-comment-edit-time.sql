-- ============================================================
-- 评论编辑时间字段迁移：2026-05-29
-- 给 sys_comment 加 edit_time，用于记录作者/版主/管理员的内容修改时间
-- 注意：不复用 update_time（其为软删 3 天倒计时基准），故单独加列
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_comment`
  ADD COLUMN `edit_time` datetime DEFAULT NULL
      COMMENT '最后编辑时间(为空表示未编辑过；与软删倒计时无关)'
      AFTER `update_time`;
