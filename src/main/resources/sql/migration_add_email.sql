-- 为 sys_user 表添加 email 字段
-- 执行时间: 2025-12-11
-- 说明: 支持邮箱验证码注册功能

ALTER TABLE `sys_user` 
ADD COLUMN `email` VARCHAR(100) NULL COMMENT '邮箱地址' AFTER `username`,
ADD UNIQUE INDEX `uk_email` (`email`);
