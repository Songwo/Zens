/**
 * Zens 子站入口地址集中配置。
 *
 * 取自 VITE_STATION_*_URL 环境变量(见 .env.development / .env.production),
 * 本地缺省回落到 dev 端口,保证零配置可跑。所有星港/启动器/深链都应经此取址,
 * 不再在组件或数据里硬编码 localhost。
 *
 * 生产地址须与主站 SsoController 各 preset 注册的 redirect_uri 域名一致,
 * 否则一站式深链会因 redirect_uri 不匹配被拒。
 */

export type StationKey = 'shop' | 'lottery' | 'cdk' | 'media'

const DEFAULT_PUBLIC_URL: Record<StationKey, string> = {
  shop: 'https://shop.allinsong.top',
  lottery: 'https://lottery.allinsong.top',
  cdk: 'https://cdk.allinsong.top',
  media: 'https://media.allinsong.top',
}

const ENV_KEY: Record<StationKey, string> = {
  shop: 'VITE_STATION_SHOP_URL',
  lottery: 'VITE_STATION_LOTTERY_URL',
  cdk: 'VITE_STATION_CDK_URL',
  media: 'VITE_STATION_MEDIA_URL',
}

/** 子站 SSO 回调路径:必须与该子站在主站注册的 redirect_uri 末段一致。 */
export const STATION_SSO_CALLBACK: Record<StationKey, string> = {
  shop: '/login/callback',
  lottery: '/api/auth/sso/callback',
  cdk: '/login/callback',
  media: '', // 媒体服务无 SSO
}

/** 子站前端通道(front-channel)登出端点:主站登出时用隐藏 iframe 逐个加载,清各站会话。 */
export const STATION_SLO_PATH: Record<StationKey, string> = {
  shop: '/api/auth/slo',
  lottery: '/api/auth/sso/logout',
  cdk: '/api/auth/community-logout',
  media: '', // 媒体服务无 SSO 会话
}

function trimSlash(url: string): string {
  return url.replace(/\/+$/, '')
}

/** 子站根地址(无尾斜杠)。 */
export function stationBase(key: StationKey): string {
  const fromEnv = (import.meta.env[ENV_KEY[key]] as string | undefined)?.trim()
  return trimSlash(fromEnv || DEFAULT_PUBLIC_URL[key])
}

/** 拼子站某路径的完整地址。 */
export function stationUrl(key: StationKey, path = ''): string {
  if (!path) return stationBase(key)
  const p = path.startsWith('/') ? path : `/${path}`
  return stationBase(key) + p
}

/** 子站 SSO 回调完整地址(用于一站式深链的 redirect_uri)。 */
export function stationSsoCallback(key: StationKey): string {
  const cb = STATION_SSO_CALLBACK[key]
  return cb ? stationUrl(key, cb) : ''
}

/** 所有有 SSO 会话的子站的登出 URL,用于主站单点登出(SLO)。 */
export function stationSloUrls(): string[] {
  return (Object.keys(STATION_SLO_PATH) as StationKey[])
    .filter((key) => STATION_SLO_PATH[key])
    .map((key) => stationUrl(key, STATION_SLO_PATH[key]))
}
