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
  ('supporter_30', 'Zens 支持者', '支持社区持续维护、内容整理与基础设施成本。不会获得审核优待或推荐排名。', 900, 'CNY', 30,
   '["30 天支持者身份记录","支持者专属月度运营简报","新功能内测邀请（按批次开放）"]', 1, 20),
  ('supporter_plus_30', 'Zens 共建支持者', '为社区长期建设提供更多支持，并参与公开的产品反馈与共建讨论。', 1900, 'CNY', 30,
   '["包含 Zens 支持者全部内容","共建反馈议题优先收集","支持者名单展示可由本人随时选择隐藏"]', 1, 10)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `price_cents` = VALUES(`price_cents`),
  `currency` = VALUES(`currency`),
  `duration_days` = VALUES(`duration_days`),
  `benefits_json` = VALUES(`benefits_json`),
  `sort_weight` = VALUES(`sort_weight`);
