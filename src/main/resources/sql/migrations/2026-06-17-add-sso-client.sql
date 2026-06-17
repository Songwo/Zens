-- ============================================================
-- Campus Pulse SSO 应用注册表
-- 说明：修复增量环境缺少 sys_sso_client 导致后台应用管理报错的问题。
-- 重复执行安全：表已存在时跳过。
-- ============================================================

USE `campus_pulse`;

CREATE TABLE IF NOT EXISTS `sys_sso_client` (
  `id`            varchar(64)   NOT NULL,
  `client_id`     varchar(100)  NOT NULL COMMENT '应用标识（唯一）',
  `client_name`   varchar(200)  NOT NULL COMMENT '应用名称',
  `client_secret` varchar(200)  NOT NULL COMMENT '应用密钥',
  `redirect_uri`  varchar(500)  NOT NULL COMMENT '回调地址',
  `description`   varchar(500)  DEFAULT NULL COMMENT '应用描述',
  `logo_url`      varchar(500)  DEFAULT NULL COMMENT '应用Logo URL',
  `enabled`       tinyint(1)    DEFAULT 1 COMMENT '是否启用 1:是 0:否',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_id` (`client_id`),
  KEY `idx_sso_client_enabled` (`enabled`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SSO 应用注册表';
