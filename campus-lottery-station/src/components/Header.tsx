import { ArrowUpRight, History, LogIn, LogOut, ScrollText } from "lucide-react";
import type { CurrentUser } from "../types/lottery";

type HeaderProps = {
  user: CurrentUser | null;
  communityBaseUrl: string;
  logoUrl: string;
  ssoStartUrl: string;
  onLogout: () => void;
};

export function Header({ user, communityBaseUrl, logoUrl, ssoStartUrl, onLogout }: HeaderProps) {
  return (
    <header className="sticky top-0 z-20 border-b border-line bg-white">
      <div className="mx-auto grid h-auto min-h-16 w-full max-w-7xl grid-cols-1 items-center gap-3 px-4 py-3 sm:px-6 lg:grid-cols-[minmax(220px,1fr)_auto_auto] lg:px-8">
        <a href="/" className="flex min-w-0 items-center gap-3" aria-label="Zens Lottery 首页">
          <span className="grid h-10 w-10 shrink-0 place-items-center overflow-hidden rounded-lg border border-amber/25 bg-white">
            <img
              className="h-full w-full object-contain p-1.5"
              src={logoUrl || "/logo.png"}
              alt="Zens"
              onError={(event) => {
                const image = event.currentTarget;
                if (!image.src.endsWith("/logo.png")) {
                  image.src = "/logo.png";
                }
              }}
            />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-base font-semibold text-ink">Zens Lottery</span>
            <span className="block truncate text-sm text-muted">Zens 抽奖</span>
          </span>
        </a>

        <nav className="flex flex-wrap items-center gap-1 text-sm text-muted" aria-label="页面导航">
          <a className="nav-link" href="#history">
            <History className="h-4 w-4" />
            抽奖记录
          </a>
          <a className="nav-link" href="#rules">
            <ScrollText className="h-4 w-4" />
            规则说明
          </a>
          <a className="nav-link" href={communityBaseUrl || "/"}>
            <ArrowUpRight className="h-4 w-4" />
            返回社区
          </a>
        </nav>

        {user ? (
          <div className="flex min-w-0 flex-wrap items-center gap-2 lg:justify-self-end">
            <span className="inline-flex min-h-10 min-w-0 items-center gap-2 rounded-lg bg-cream px-3 text-sm font-medium text-amber-ink">
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-amber text-xs font-semibold text-white">
                {isImageAvatar(user.avatar) ? (
                  <img className="h-full w-full rounded-md object-cover" src={user.avatar} alt="" />
                ) : (
                  user.avatar || user.displayName.slice(0, 1)
                )}
              </span>
              <span className="min-w-0 truncate">{user.displayName}</span>
              <span className="shrink-0 text-xs text-muted">Lv.{user.level} · {user.points} 积分</span>
            </span>
            <button className="btn-secondary min-h-10 px-3" type="button" onClick={onLogout}>
              <LogOut className="h-4 w-4" />
              退出
            </button>
          </div>
        ) : (
          <a className="btn-primary justify-center lg:justify-self-end" href={ssoStartUrl || "/api/auth/sso/start"}>
            <LogIn className="h-4 w-4" />
            登录 / 连接社区账号
          </a>
        )}
      </div>
    </header>
  );
}

function isImageAvatar(value?: string) {
  return Boolean(value && /^(https?:|\/|data:image\/)/i.test(value));
}
