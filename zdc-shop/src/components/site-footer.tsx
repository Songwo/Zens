import Link from "next/link";

export function SiteFooter() {
  return (
    <footer className="mt-32 border-t border-divider">
      <div className="editorial-container flex flex-col gap-6 py-12 text-sm text-muted md:flex-row md:items-center md:justify-between">
        <div className="space-y-1.5">
          <div className="flex items-center gap-2 text-ink">
            <span className="inline-flex h-6 w-6 items-center justify-center rounded-md bg-brand font-mono text-xs font-bold text-ink">
              Z
            </span>
            <span className="font-semibold tracking-tight">zens · store</span>
          </div>
          <p className="text-xs text-muted">
            Zens 社区会员专属积分商城 · 仅限 Zens 账号登录
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-xs">
          <Link
            href={process.env.NEXT_PUBLIC_COMMUNITY_URL || "#"}
            target="_blank"
            rel="noreferrer"
            className="transition-colors hover:text-ink"
          >
            返回 Zens 社区 ↗
          </Link>
          <Link href="/orders" className="transition-colors hover:text-ink">
            我的兑换
          </Link>
          <span className="font-mono text-faint">v0.1.0</span>
        </div>
      </div>
    </footer>
  );
}
