import { ArrowUpRight } from "lucide-react";

const links = [
  { label: "社区首页", href: "#社区首页" },
  { label: "子站系统", href: "#子站系统" },
  { label: "服务联动", href: "#服务联动" },
  { label: "运营治理", href: "#运营与治理" },
  { label: "规则文档", href: "#规则文档" },
];

export function Header() {
  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-4 sm:px-7 lg:flex-row lg:items-center lg:justify-between lg:px-8">
        <a href="/" className="flex min-w-0 items-center gap-3" aria-label="Zens Nav 首页">
          <img src="/assets/logo-horizontal.png" alt="Zens" className="h-9 w-auto" />
          <span className="h-8 w-px bg-line" aria-hidden="true" />
          <span className="min-w-0">
            <span className="block text-base font-semibold leading-5 text-ink">Zens Nav</span>
            <span className="block text-xs leading-5 text-muted">开发者社区服务导航</span>
          </span>
        </a>

        <nav className="hidden items-center gap-6 text-sm text-mutedStrong lg:flex" aria-label="主导航">
          {links.map((link) => (
            <a key={link.href} href={link.href} className="border-b border-transparent py-1 hover:border-amber hover:text-ink">
              {link.label}
            </a>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <a
            href="https://allinsong.top/auth/login"
            className="inline-flex h-9 items-center justify-center rounded-nav border border-line px-3 text-sm text-mutedStrong hover:border-amber hover:bg-amber-softer hover:text-ink"
          >
            登录 / 注册
          </a>
          <a
            href="https://allinsong.top"
            className="inline-flex h-9 items-center justify-center gap-1.5 rounded-nav border border-amber bg-amber px-3 text-sm font-medium text-amber-ink hover:bg-[#E9AA00]"
            target="_blank"
            rel="noreferrer"
          >
            返回社区
            <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
          </a>
        </div>
      </div>
    </header>
  );
}
