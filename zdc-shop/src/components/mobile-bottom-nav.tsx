"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const items = [
  {
    href: "/",
    label: "发现",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
        <path d="M3 11.5 12 4l9 7.5" />
        <path d="M5 10v9a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1v-9" />
      </svg>
    ),
  },
  {
    href: "/orders",
    label: "兑换",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
        <path d="M4 7h13l2 11H6L4 7Z" />
        <path d="M8 7V5a4 4 0 0 1 8 0v2" />
      </svg>
    ),
  },
  {
    href: "/me",
    label: "我的",
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5">
        <circle cx="12" cy="8" r="3.5" />
        <path d="M5 20a7 7 0 0 1 14 0" />
      </svg>
    ),
  },
];

export function MobileBottomNav() {
  const pathname = usePathname();

  // 不在登录回调页显示
  if (pathname?.startsWith("/login")) return null;

  return (
    <nav aria-label="底部导航" className="md:hidden">
      <div className="mobile-nav">
        {items.map((item) => {
          const active =
            item.href === "/"
              ? pathname === "/"
              : pathname?.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "mobile-nav__item",
                active && "mobile-nav__item--active"
              )}
              aria-current={active ? "page" : undefined}
            >
              {item.icon}
              <span className="leading-none">{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
