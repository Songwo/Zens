import { ArrowUpRight } from "lucide-react";
import { resolveIcon } from "../lib/icons";
import type { NavItem as NavItemType } from "../types/nav";
import { StatusBadge } from "./StatusBadge";

type NavItemProps = {
  item: NavItemType;
};

export function NavItem({ item }: NavItemProps) {
  const Icon = resolveIcon(item.icon);
  const primaryHref = item.localHref || item.href;

  return (
    <article className="group grid grid-cols-[32px_minmax(0,1fr)] gap-3 border-b border-lineSoft py-4 transition hover:border-amber hover:bg-amber-softer/80 sm:grid-cols-[36px_minmax(0,1fr)_auto]">
      <span className="mt-0.5 flex h-8 w-8 items-center justify-center rounded-nav border border-line bg-surface text-mutedStrong group-hover:border-amber group-hover:text-amber-strong sm:h-9 sm:w-9">
        <Icon className="h-4 w-4" aria-hidden="true" />
      </span>

      <span className="min-w-0">
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <a
            href={primaryHref}
            target={item.external || primaryHref.startsWith("http") ? "_blank" : undefined}
            rel={item.external || primaryHref.startsWith("http") ? "noreferrer" : undefined}
            className="text-sm font-semibold text-ink underline-offset-4 hover:text-amber-strong hover:underline"
          >
            {item.title}
          </a>
          {item.tag ? (
            <span className="rounded-nav border border-line bg-surface px-1.5 py-0.5 text-xs text-muted">{item.tag}</span>
          ) : null}
          {primaryHref.startsWith("http") ? <ArrowUpRight className="h-3.5 w-3.5 text-muted" aria-hidden="true" /> : null}
        </span>
        <span className="mt-1 block text-sm leading-6 text-mutedStrong">{item.description}</span>
        <span className="mt-2 flex flex-wrap gap-2 text-xs text-muted">
          {item.system ? (
            <span className="rounded-nav bg-canvas px-2 py-1">来源：{item.system}</span>
          ) : null}
          {item.localHref ? (
            <a
              href={item.localHref}
              target="_blank"
              rel="noreferrer"
              className="rounded-nav border border-line bg-surface px-2 py-1 hover:border-amber hover:text-ink"
            >
              本地入口
            </a>
          ) : null}
          {item.productionHref || (item.external && item.href.startsWith("http")) ? (
            <a
              href={item.productionHref || item.href}
              target="_blank"
              rel="noreferrer"
              className="rounded-nav border border-line bg-surface px-2 py-1 hover:border-amber hover:text-ink"
            >
              线上入口
            </a>
          ) : null}
        </span>
        {item.integration ? (
          <span className="mt-2 block border-l-2 border-amber/60 pl-3 text-xs leading-5 text-muted">
            {item.integration}
          </span>
        ) : null}
      </span>

      <span className="col-start-2 self-start pt-0.5 sm:col-start-auto">
        <StatusBadge status={item.status} />
      </span>
    </article>
  );
}
