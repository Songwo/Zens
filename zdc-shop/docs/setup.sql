-- ─────────────────────────────────────────────────────────────────────────
--  Zens 积分商城子站 · 上线引导 SQL
-- ─────────────────────────────────────────────────────────────────────────
--  这份脚本在主站 MySQL 实例上执行,完成以下三件事:
--    1. 创建子站独立数据库 `zens_shop`
--    2. 在主站 sys_sso_client 注册 zdc-shop 应用
--    3. (开发可选) 给一两个测试账号灌一些初始积分,方便体验兑换
-- ─────────────────────────────────────────────────────────────────────────

-- ────── 1. 子站数据库 ─────────────────────────────────────────────────
CREATE DATABASE IF NOT EXISTS `zens_shop`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- Prisma 会自己在 zens_shop 库里建表(zs_product / zs_order / zs_redemption_code / zs_user_sync)
-- 建库后回到子站执行:  cd zdc-shop && npm run db:push && npm run db:seed

-- ────── 2. 主站注册 SSO 应用 ─────────────────────────────────────────
--  默认走主站当前数据库(campus_pulse)
USE `campus_pulse`;

INSERT IGNORE INTO `sys_sso_client` (
  `id`, `client_id`, `client_name`, `client_secret`,
  `redirect_uri`, `description`, `logo_url`, `enabled`,
  `create_time`, `update_time`
) VALUES (
  REPLACE(UUID(), '-', ''),
  'zdc-shop',
  'Zens 积分商城',
  -- 此 secret 在当前 SSO 流程里不参与签名(用 jwt.secret),先随便填一个随机串
  -- 后续如果接 PKCE/OAuth2 再使用
  REPLACE(UUID(), '-', ''),
  'http://localhost:3000/login/callback',
  '使用 Zens 社区积分兑换虚拟物品的子站',
  'http://localhost:3000/favicon.svg',
  1,
  NOW(),
  NOW()
);

-- 生产域名 (注意: 当前 SsoClientService.validateClient 是精确匹配 redirect_uri,
-- 若需要同时支持 dev + prod 两个 URI, 需要改主站 service 允许多个,或者上线时再
-- UPDATE 一次)。先放生产 URI 的引导 SQL 在这里:
-- UPDATE sys_sso_client SET redirect_uri = 'https://shop.allinsong.top/login/callback'
-- WHERE client_id = 'zdc-shop';

-- ────── 3. (DEV 可选) 给测试账号灌点积分 ────────────────────────────
--  sys_user.points 在主站现有业务里没有写入逻辑,新账号 points = 0 / NULL。
--  开发联调时给指定用户灌一些积分,方便走通兑换流程。
--
--  替换 <YOUR_USER_ID> 为你的 sys_user.id (字符串 ID,可在 sys_user 表查)
-- UPDATE sys_user SET points = 2000 WHERE id = '<YOUR_USER_ID>';
--
--  或按用户名:
-- UPDATE sys_user SET points = 2000 WHERE username = 'song';
