import Link from "next/link";
import { ThemeToggle } from "./theme-toggle";
import { BalancePill } from "./balance-pill";
import { UserMenu } from "./user-menu";
import { cn } from "@/lib/utils";

export interface SessionLite {
  userId: string;
  username: string;
  nickname?: string | null;
  avatar?: string | null;
  role: string;
  points?: number | null;
}

export function SiteHeader({ session }: { session: SessionLite | null }) {
  return (
    <header
      className={cn(
        "sticky top-0 z-30 w-full",
        "border-b border-transparent",
        "supports-[backdrop-filter]:backdrop-blur-xl"
      )}
      style={{ backgroundColor: "var(--header-bg)" }}
    >
      <div className="editorial-container relative flex h-16 items-center gap-3">
        <Link
          href="/"
          aria-label="Zens 积分商城"
          className="group flex items-center gap-2.5 text-ink transition-opacity hover:opacity-90"
        >
          <span
            className="inline-flex h-8 w-8 items-center justify-center rounded-xl bg-brand font-mono text-base font-bold text-ink shadow-sm"
            style={{ boxShadow: "0 4px 14px -4px var(--zens-yellow-glow)" }}
          >
            Z
          </span>
          <span className="flex items-baseline gap-1.5 text-[15px] font-semibold tracking-tight">
            <span>zens</span>
            <span className="text-faint">/</span>
            <span className="text-ink-soft">store</span>
          </span>
        </Link>

        <nav className="ml-6 hidden items-center gap-6 md:flex">
          <Link
            href="/"
            className="text-sm font-medium text-muted transition-colors hover:text-ink"
          >
            发现
          </Link>
          <Link
            href="/orders"
            className="text-sm font-medium text-muted transition-colors hover:text-ink"
          >
            我的兑换
          </Link>
          {session?.role === "ROLE_ADMIN" || session?.role === "ROLE_SUPER_ADMIN" ? (
            <Link
              href="/admin"
              className="text-sm font-medium text-muted transition-colors hover:text-ink"
            >
              管理
            </Link>
          ) : null}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {session ? (
            <>
              <BalancePill points={session.points} />
              <UserMenu session={session} />
            </>
          ) : (
            <Link
              href="/login"
              className="btn-brand h-9 px-4 text-sm"
            >
              使用 Zens 账号登录
            </Link>
          )}
          <ThemeToggle />
        </div>
      </div>
      <span className="header-hairline" aria-hidden />
    </header>
  );
}
