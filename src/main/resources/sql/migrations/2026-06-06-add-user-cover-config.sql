-- ============================================================
-- 用户封面展示配置字段迁移：2026-06-06
-- 给 sys_user 加 cover_config(JSON: fit/x/y/height),供资料卡封面自助调节
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_user`
  ADD COLUMN `cover_config` varchar(255) DEFAULT NULL
      COMMENT '封面展示配置(JSON: fit/x/y/height)'
      AFTER `quick_card_bg_url`;
