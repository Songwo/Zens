-- ============================================================
-- 用户徽章颜色与样式字段迁移：2026-06-07
-- 给 sys_user 加 badge_color(纯色样式的自定义颜色) 与 badge_style(solid/rainbow)
-- 配合 badge_text 一起决定徽章外观；rainbow=七彩跑马动效，忽略 badge_color
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_user`
  ADD COLUMN `badge_color` varchar(20) DEFAULT NULL
      COMMENT '徽章颜色(纯色样式用,hex 如 #a855f7)，空表示用默认色'
      AFTER `badge_text`,
  ADD COLUMN `badge_style` varchar(16) NOT NULL DEFAULT 'solid'
      COMMENT '徽章样式: solid=纯色 / rainbow=七彩跑马动效'
      AFTER `badge_color`;
