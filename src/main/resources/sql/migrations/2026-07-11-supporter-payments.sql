-- Zens 支持者计划与现金支付账本。
-- 现金订单与积分商城订单严格分离；金额统一使用人民币分，避免浮点误差。

CREATE TABLE IF NOT EXISTS `supporter_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL,
  `name` varchar(80) NOT NULL,
  `description` varchar(500) NOT NULL,
  `price_cents` int NOT NULL,
  `currency` char(3) NOT NULL DEFAULT 'CNY',
  `duration_days` int NOT NULL,
  `benefits_json` varchar(2000) NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `sort_weight` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supporter_plan_code` (`code`),
  KEY `idx_supporter_plan_active_sort` (`active`, `sort_weight`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支持者计划';

CREATE TABLE IF NOT EXISTS `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(40) NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `plan_code` varchar(50) NOT NULL,
  `plan_name_snapshot` varchar(80) NOT NULL,
  `amount_cents` int NOT NULL,
  `currency` char(3) NOT NULL,
  `provider` varchar(30) NOT NULL,
  `provider_order_no` varchar(100) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `idempotency_key` varchar(100) NOT NULL,
  `checkout_url` varchar(1000) DEFAULT NULL,
  `failure_reason` varchar(500) DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_order_no` (`order_no`),
  UNIQUE KEY `uk_payment_order_user_idem` (`user_id`, `idempotency_key`),
  KEY `idx_payment_order_user_time` (`user_id`, `created_at`),
  KEY `idx_payment_order_provider_order` (`provider`, `provider_order_no`),
  KEY `idx_payment_order_status_expire` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现金支付订单';

CREATE TABLE IF NOT EXISTS `payment_callback_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider` varchar(30) NOT NULL,
  `event_id` varchar(160) NOT NULL,
  `order_no` varchar(40) DEFAULT NULL,
  `payload_hash` char(64) NOT NULL,
  `signature_valid` tinyint(1) NOT NULL,
  `status` varchar(20) NOT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `received_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_callback_provider_event` (`provider`, `event_id`),
  KEY `idx_payment_callback_order` (`order_no`, `received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调幂等与审计账本';

CREATE TABLE IF NOT EXISTS `supporter_entitlement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `plan_code` varchar(50) NOT NULL,
  `plan_name_snapshot` varchar(80) NOT NULL,
  `source_order_no` varchar(40) NOT NULL,
  `starts_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supporter_entitlement_order` (`source_order_no`),
  KEY `idx_supporter_entitlement_user_expire` (`user_id`, `status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支持者权益期限账本';

INSERT INTO `supporter_plan`
  (`code`, `name`, `description`, `price_cents`, `currency`, `duration_days`, `benefits_json`, `active`, `sort_weight`)
VALUES
  ('supporter_30', 'Zens 支持者', '支持社区持续维护与基础设施成本，获得清晰可见的支持者身份与个人资料装饰。', 900, 'CNY', 30,
   '["30 天支持者身份与到期时间展示","个人资料支持者徽章与专属强调色","本人近 30 天创作数据简报"]', 1, 20),
  ('supporter_plus_30', 'Zens 共建支持者', '在支持者权益之上参与长期共建，获得独立的共建身份和结构化反馈通道。', 1900, 'CNY', 30,
   '["包含 Zens 支持者全部权益","个人资料共建支持者专属徽章","产品共建反馈专属通道","反馈处理状态与官方回复查看"]', 1, 10)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `price_cents` = VALUES(`price_cents`),
  `currency` = VALUES(`currency`),
  `duration_days` = VALUES(`duration_days`),
  `benefits_json` = VALUES(`benefits_json`),
  `sort_weight` = VALUES(`sort_weight`);
