CREATE TABLE IF NOT EXISTS `supporter_feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` VARCHAR(64) NOT NULL,
  `subject` VARCHAR(100) NOT NULL,
  `content` VARCHAR(2000) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  `admin_reply` VARCHAR(2000) NULL,
  `replied_by` VARCHAR(64) NULL,
  `replied_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_supporter_feedback_user_created` (`user_id`, `created_at`),
  KEY `idx_supporter_feedback_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Zens 共建支持者反馈通道';
