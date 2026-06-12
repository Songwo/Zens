-- ============================================================
-- Migration: 帖子订阅（主题追踪 + 邮件摘要基础表）
-- 执行时间：2026-06-09
-- 说明：用户可"追踪"帖子；被追踪帖有新评论时发即时站内通知，
--       并按天聚合成邮件摘要（digest）。
--       评论者/发帖人自动订阅(source=auto)，帖子页可手动追踪(source=manual)。
-- 幂等：CREATE TABLE IF NOT EXISTS；不 DROP、不动任何既有数据，可安全重复执行。
-- ============================================================
use campus_pulse;

CREATE TABLE IF NOT EXISTS `post_subscription` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    VARCHAR(64)  NOT NULL COMMENT '订阅用户ID',
  `post_id`    VARCHAR(64)  NOT NULL COMMENT '被订阅帖子ID',
  `source`     VARCHAR(16)  NOT NULL DEFAULT 'manual' COMMENT '订阅来源: auto(评论/发帖自动) / manual(手动追踪)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sub_user_post` (`user_id`, `post_id`),
  KEY `idx_sub_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子订阅表';

SELECT '✅ 2026-06-09-add-post-subscription 完成：post_subscription 已就绪' AS status;
