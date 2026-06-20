-- ============================================================
-- SSO 可信(第一方)客户端标记迁移：2026-06-19
-- 给 sys_sso_client 加 trusted：第一方自家子站标记为可信后,
-- 已登录用户进入时自动授权、跳过同意页(OAuth 第一方免同意惯例)。
-- 第三方/未标记客户端(trusted=0)仍走手动同意页,安全不降级。
-- ============================================================
use campus_pulse;

ALTER TABLE `sys_sso_client`
  ADD COLUMN `trusted` tinyint(1) NOT NULL DEFAULT 0
      COMMENT '第一方可信:1 自动授权跳过同意页, 0 需手动同意'
      AFTER `enabled`;
