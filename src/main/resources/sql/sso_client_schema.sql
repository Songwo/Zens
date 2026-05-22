-- SSO 应用注册表
CREATE TABLE IF NOT EXISTS `sys_sso_client` (
  `id`            VARCHAR(64)   NOT NULL,
  `client_id`     VARCHAR(100)  NOT NULL COMMENT '应用标识（唯一）',
  `client_name`   VARCHAR(200)  NOT NULL COMMENT '应用名称',
  `client_secret` VARCHAR(200)  NOT NULL COMMENT '应用密钥',
  `redirect_uri`  VARCHAR(500)  NOT NULL COMMENT '回调地址',
  `description`   VARCHAR(500)  DEFAULT NULL COMMENT '应用描述',
  `logo_url`      VARCHAR(500)  DEFAULT NULL COMMENT '应用Logo URL',
  `enabled`       TINYINT(1)    DEFAULT 1 COMMENT '是否启用 1:是 0:否',
  `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SSO 应用注册表';
