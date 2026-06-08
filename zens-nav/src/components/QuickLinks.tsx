import { ArrowUpRight } from "lucide-react";
import { resolveIcon } from "../lib/icons";
import type { NavItem } from "../types/nav";

type QuickLinksProps = {
  items: NavItem[];
};

export function QuickLinks({ items }: QuickLinksProps) {
  return (
    <section id="工具服务" className="mx-auto max-w-7xl px-5 py-8 sm:px-7 lg:px-8" aria-labelledby="quick-title">
      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-normal text-amber-strong">Quick Access</p>
          <h2 id="quick-title" className="text-2xl font-semibold text-ink">
            常用入口
          </h2>
        </div>
        <p className="max-w-xl text-sm leading-6 text-muted">把社区成员最常访问的入口放在这里，减少跨子站寻找成本。</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {items.map((item) => {
          const Icon = resolveIcon(item.icon);

          return (
            <a
              key={item.id}
              href={item.localHref || item.href}
              target={item.external ? "_blank" : undefined}
              rel={item.external ? "noreferrer" : undefined}
              className="group flex min-h-[92px] items-start gap-3 rounded-nav border border-line bg-surface p-4 transition hover:-translate-y-0.5 hover:border-amber hover:bg-amber-softer"
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-nav border border-amber/50 bg-amber-softer text-amber-strong">
                <Icon className="h-4 w-4" aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex items-center gap-2 text-sm font-semibold text-ink">
                  {item.title}
                  <ArrowUpRight className="h-3.5 w-3.5 text-muted transition group-hover:text-amber-strong" aria-hidden="true" />
                </span>
                <span className="mt-1 block text-sm leading-6 text-muted">{item.description}</span>
                {item.system ? (
                  <span className="mt-2 block text-xs text-muted">来源：{item.system}</span>
                ) : null}
              </span>
            </a>
          );
        })}
      </div>
    </section>
  );
}
