-- ============================================================
-- Migration: 每日签到
-- 执行时间：2026-06-08
-- 说明：新增用户每日签到表，记录签到日期、连续天数与当日奖励。
--       连续天数与“今日是否已签”均由该表计算，不新增 sys_user 列。
-- ============================================================
use campus_pulse;

CREATE TABLE IF NOT EXISTS `user_check_in` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`          VARCHAR(64)  NOT NULL COMMENT '用户ID',
  `check_in_date`    DATE         NOT NULL COMMENT '签到日期',
  `continuous_days`  INT          NOT NULL DEFAULT 1 COMMENT '截至当日的连续签到天数',
  `reward_points`    INT          NOT NULL DEFAULT 0 COMMENT '当日获得积分',
  `reward_exp`       INT          NOT NULL DEFAULT 0 COMMENT '当日获得经验',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `check_in_date`),
  KEY `idx_user_date` (`user_id`, `check_in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日签到表';
