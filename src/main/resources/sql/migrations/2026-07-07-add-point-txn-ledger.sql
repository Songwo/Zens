-- 积分流水账本:所有积分变动(站内签到/子站消费/退款)的唯一审计源。
-- 幂等最终裁判 = uk_point_txn_idem 唯一索引(Redis 幂等缓存降级为快路径)。
-- 已有库执行本文件即可;全新初始化走 campus_pulse_schema.sql(已同步含本表)。
CREATE TABLE IF NOT EXISTS `sys_point_txn` (
  `id`              bigint       NOT NULL AUTO_INCREMENT,
  `user_id`         varchar(64)  NOT NULL COMMENT '主站用户ID',
  `delta`           int          NOT NULL COMMENT '积分变动,正=入账 负=扣减',
  `balance_after`   int          NOT NULL COMMENT '本笔完成后的余额',
  `source`          varchar(50)  NOT NULL COMMENT '来源: main-site/zdc-shop/campus-lottery-station/cdk-airdrop',
  `biz_type`        varchar(50)  NOT NULL COMMENT '业务类型: checkin/shop.order/shop.refund/lottery.join/lottery.refund/admin.adjust',
  `order_id`        varchar(100) DEFAULT NULL COMMENT '子站业务单号',
  `reason`          varchar(200) NOT NULL COMMENT '变动事由',
  `idempotency_key` varchar(200) NOT NULL COMMENT '作用域化幂等键,如 consume:{userId}:{clientKey}',
  `created_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_point_txn_idem` (`idempotency_key`),
  KEY `idx_point_txn_user_time` (`user_id`, `created_at`),
  KEY `idx_point_txn_source_time` (`source`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水账本';
