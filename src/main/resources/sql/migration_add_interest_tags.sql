-- 迁移：为 sys_user 表添加 interest_tags 字段
ALTER TABLE sys_user ADD COLUMN interest_tags TEXT COMMENT '兴趣标签' AFTER status;
