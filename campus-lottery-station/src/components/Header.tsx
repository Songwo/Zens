import { ArrowUpRight, History, LogIn, ScrollText } from "lucide-react";

export function Header() {
  return (
    <header className="sticky top-0 z-20 border-b border-line bg-white">
      <div className="mx-auto grid h-auto min-h-16 w-full max-w-7xl grid-cols-1 items-center gap-3 px-4 py-3 sm:px-6 lg:grid-cols-[minmax(220px,1fr)_auto_auto] lg:px-8">
        <a href="/" className="flex min-w-0 items-center gap-3" aria-label="Zens Lottery 首页">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg border border-amber/35 bg-amber-soft text-sm font-semibold text-amber-ink">
            Z
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
          <a className="nav-link" href="/community">
            <ArrowUpRight className="h-4 w-4" />
            返回社区
          </a>
        </nav>

        <a className="btn-primary justify-center lg:justify-self-end" href="/api/auth/sso/start">
          <LogIn className="h-4 w-4" />
          登录 / 连接社区账号
        </a>
      </div>
    </header>
  );
}
