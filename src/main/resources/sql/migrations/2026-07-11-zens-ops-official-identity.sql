-- 统一官方运营账号的公开身份，并修复早期运营发布造成的双重审核状态。
UPDATE `sys_user`
SET `nickname` = 'Zens运营',
    `badge_text` = COALESCE(NULLIF(`badge_text`, ''), '官方运营'),
    `badge_color` = COALESCE(NULLIF(`badge_color`, ''), '#D97706'),
    `update_time` = NOW()
WHERE `username` = 'zens_ops';

UPDATE `sys_post` p
JOIN `sys_user` u ON u.`id` = p.`user_id`
SET p.`audit_status` = 'APPROVED',
    p.`reject_reason` = NULL,
    p.`update_time` = NOW()
WHERE u.`username` = 'zens_ops'
  AND p.`status` = 1
  AND p.`audit_status` = 'PENDING';
