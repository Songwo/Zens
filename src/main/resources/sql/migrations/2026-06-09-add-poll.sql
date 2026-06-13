-- ============================================================
-- Migration: 帖子投票（Poll，标准版）
-- 执行时间：2026-06-09
-- 说明：发帖时可附带 1 个投票（一帖 0..1）。支持单选/多选 + 截止时间。
--       投票数据独立三表，按 post_id 关联；不进入帖子详情缓存。
-- 幂等：全部 CREATE TABLE IF NOT EXISTS；不 DROP、不动任何既有数据，可安全重复执行。
-- ============================================================
use campus_pulse;

CREATE TABLE IF NOT EXISTS `poll` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`      VARCHAR(64)  NOT NULL COMMENT '关联帖子ID（1:1）',
  `title`        VARCHAR(200) DEFAULT NULL COMMENT '投票问题，空则前端用帖子标题兜底',
  `multi_choice` TINYINT      NOT NULL DEFAULT 0 COMMENT '0单选 1多选',
  `max_choices`  INT          NOT NULL DEFAULT 1 COMMENT '多选时最多可选项数；单选恒为1；0=不限',
  `deadline`     DATETIME     DEFAULT NULL COMMENT '截止时间，空=不限期',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1进行中 0已关闭',
  `voter_count`  INT          NOT NULL DEFAULT 0 COMMENT '参与投票的去重人数（冗余缓存）',
  `created_by`   VARCHAR(64)  NOT NULL COMMENT '创建者（帖子作者）',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_poll_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子投票表';

CREATE TABLE IF NOT EXISTS `poll_option` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `poll_id`      BIGINT       NOT NULL COMMENT '所属投票ID',
  `option_text`  VARCHAR(200) NOT NULL COMMENT '选项文本',
  `option_order` INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `vote_count`   INT          NOT NULL DEFAULT 0 COMMENT '该项票数（冗余缓存）',
  PRIMARY KEY (`id`),
  KEY `idx_option_poll` (`poll_id`, `option_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投票选项表';

CREATE TABLE IF NOT EXISTS `poll_vote` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `poll_id`     BIGINT      NOT NULL COMMENT '投票ID',
  `option_id`   BIGINT      NOT NULL COMMENT '选项ID',
  `user_id`     VARCHAR(64) NOT NULL COMMENT '投票用户ID',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vote` (`poll_id`, `user_id`, `option_id`),
  KEY `idx_vote_user` (`poll_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投票记录表';

SELECT '✅ 2026-06-09-add-poll 完成：poll / poll_option / poll_vote 已就绪' AS status;
