-- ============================================================
-- 用户徽章文字字段迁移：2026-06-07
-- 给 sys_user 加 badge_text，管理员授予的纯文字徽章(flair，如「你可以访问L站」)
-- 跟随用户在帖子/评论/私信/资料卡处实时显示；空表示无徽章。业务层限 20 字。
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_user`
  ADD COLUMN `badge_text` varchar(40) DEFAULT NULL
      COMMENT '用户徽章文字(管理员授予的纯文字flair)，空表示无'
      AFTER `cover_config`;
