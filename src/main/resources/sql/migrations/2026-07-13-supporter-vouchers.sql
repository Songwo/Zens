-- 支持者公益站真实兑换码库存与发放账本。
-- 兑换码只保存 AES-256-GCM 密文和带密钥 HMAC-SHA256 指纹，明文不落库。
CREATE TABLE IF NOT EXISTS `supporter_voucher_grant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `source_order_no` varchar(40) NOT NULL,
  `plan_code` varchar(50) NOT NULL,
  `quota` int NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `voucher_code_id` bigint DEFAULT NULL,
  `redemption_url_snapshot` varchar(500) NOT NULL,
  `granted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `issued_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supporter_voucher_grant_order` (`source_order_no`),
  UNIQUE KEY `uk_supporter_voucher_grant_code` (`voucher_code_id`),
  KEY `idx_supporter_voucher_grant_user_time` (`user_id`, `granted_at`),
  KEY `idx_supporter_voucher_grant_pending` (`quota`, `status`, `granted_at`, `id`),
  CONSTRAINT `chk_supporter_voucher_grant_quota` CHECK (`quota` IN (30, 50)),
  CONSTRAINT `chk_supporter_voucher_grant_status` CHECK (`status` IN ('PENDING', 'ISSUED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支持者公益站额度发放账本';

CREATE TABLE IF NOT EXISTS `supporter_voucher_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quota` int NOT NULL,
  `code_ciphertext` varchar(1000) NOT NULL,
  `code_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'AVAILABLE',
  `assigned_grant_id` bigint DEFAULT NULL,
  `imported_by` varchar(64) NOT NULL,
  `imported_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `assigned_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supporter_voucher_code_hash` (`code_hash`),
  UNIQUE KEY `uk_supporter_voucher_code_grant` (`assigned_grant_id`),
  KEY `idx_supporter_voucher_code_fifo` (`quota`, `status`, `id`),
  CONSTRAINT `chk_supporter_voucher_code_quota` CHECK (`quota` IN (30, 50)),
  CONSTRAINT `chk_supporter_voucher_code_status` CHECK (`status` IN ('AVAILABLE', 'ASSIGNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支持者公益站预生成兑换码加密库存';

-- 为迁移前已完成的正式支持者订单补建待发权益；没有真实库存时保持 PENDING。
INSERT IGNORE INTO `supporter_voucher_grant`
(`user_id`, `source_order_no`, `plan_code`, `quota`, `status`, `redemption_url_snapshot`, `granted_at`)
SELECT `user_id`, `order_no`, `plan_code`,
       CASE `plan_code` WHEN 'supporter_30' THEN 30 ELSE 50 END,
       'PENDING', 'https://pip.kdns.fr', COALESCE(`paid_at`, `updated_at`)
FROM `payment_order`
WHERE `status` = 'PAID'
  AND `plan_code` IN ('supporter_30', 'supporter_plus_30')
  AND `duration_days_snapshot` = 30;

UPDATE `supporter_plan`
SET `benefits_json` = '["30 天支持者身份与到期时间展示","个人资料支持者徽章与专属强调色","本人近 30 天创作数据简报","每 30 天 30 公益站额度，以兑换码形式发放（缺货自动排队补发）"]'
WHERE `code` = 'supporter_30';

UPDATE `supporter_plan`
SET `benefits_json` = '["包含 Zens 支持者全部权益","个人资料共建支持者专属徽章","产品共建反馈专属通道","反馈处理状态与官方回复查看","每 30 天 50 公益站额度，以兑换码形式发放（缺货自动排队补发）"]'
WHERE `code` = 'supporter_plus_30';
