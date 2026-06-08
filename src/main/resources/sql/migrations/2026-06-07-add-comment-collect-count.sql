-- ============================================================
-- 评论收藏数字段迁移：2026-06-07
-- 给 sys_comment 加 collect_count，记录该评论被收藏的次数
-- 实体 Comment.collectCount / CommentCollectServiceImpl 依赖此列；
-- 历史库（部署较早）缺该列会导致评论查询整体报
--   Unknown column 'collect_count' in 'field list' → 评论无法显示
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_comment`
  ADD COLUMN `collect_count` int DEFAULT 0 COMMENT '收藏数'
      AFTER `like_count`;
