-- 仅用于已经执行过原始 2026-07-11-supporter-payments.sql 的运行中数据库。
-- 全新数据库只执行 ../campus_pulse_schema.sql，不要再执行 07-11/07-12，避免重复建表/加列。
-- 执行前确认历史非空 provider_order_no 没有重复值；若有，需人工对账后再建立唯一索引。

ALTER TABLE `payment_order`
  ADD COLUMN `duration_days_snapshot` int NULL AFTER `plan_name_snapshot`;

UPDATE `payment_order` po
LEFT JOIN `supporter_plan` sp ON sp.`code` = po.`plan_code`
SET po.`duration_days_snapshot` = sp.`duration_days`
WHERE po.`duration_days_snapshot` IS NULL;

-- 无法从方案回填的历史订单采用当前既有支持者周期，避免留下可变/空快照。
UPDATE `payment_order`
SET `duration_days_snapshot` = 30
WHERE `duration_days_snapshot` IS NULL OR `duration_days_snapshot` <= 0;

ALTER TABLE `payment_order`
  MODIFY COLUMN `duration_days_snapshot` int NOT NULL,
  DROP INDEX `idx_payment_order_provider_order`,
  ADD UNIQUE KEY `uk_payment_order_provider_order` (`provider`, `provider_order_no`),
  ADD CONSTRAINT `chk_payment_provider_order_no_nonblank`
    CHECK (`provider_order_no` IS NULL OR CHAR_LENGTH(TRIM(`provider_order_no`)) > 0),
  ADD CONSTRAINT `chk_payment_duration_days_positive`
    CHECK (`duration_days_snapshot` > 0);
