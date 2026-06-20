import { stationSloUrls } from '@/config/stations'

/**
 * 单点登出(SLO)——前端通道(front-channel)实现。
 *
 * 主站登出后,用隐藏 iframe 逐个加载各子站的 GET 登出端点。每个 iframe 在子站
 * 自己的源里执行,从而清掉该子站的会话:
 *   - lottery / shop:服务端清 cookie(响应 Set-Cookie 在子站源内是第一方)
 *   - cdk:登出端点返回一小段 HTML,在 cdk 源内清 localStorage token
 *
 * 尽力而为:某子站不可达或加载失败都不影响主站登出;iframe 数秒后自动移除。
 */
export function triggerSingleLogout(): void {
  if (typeof document === 'undefined') return
  const urls = stationSloUrls()
  for (const url of urls) {
    try {
      const iframe = document.createElement('iframe')
      iframe.style.display = 'none'
      iframe.setAttribute('aria-hidden', 'true')
      iframe.setAttribute('tabindex', '-1')
      iframe.src = url
      document.body.appendChild(iframe)
      window.setTimeout(() => iframe.remove(), 5000)
    } catch {
      // 单个子站失败忽略,不阻断整体登出
    }
  }
}
