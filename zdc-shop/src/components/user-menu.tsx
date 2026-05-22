"use client";

import Link from "next/link";
import { useRef, useState, useEffect } from "react";
import { cn } from "@/lib/utils";
import type { SessionLite } from "./site-header";

export function UserMenu({ session }: { session: SessionLite }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const initial = (session.nickname || session.username || "?").trim().slice(0, 1).toUpperCase();

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    }
    function onEsc(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    if (open) {
      document.addEventListener("mousedown", onClick);
      document.addEventListener("keydown", onEsc);
    }
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onEsc);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex h-9 w-9 items-center justify-center overflow-hidden rounded-pill border border-divider bg-surface-elev text-sm font-semibold text-ink-soft transition-colors hover:border-divider-strong"
      >
        {session.avatar ? (
          <img src={session.avatar} alt="" className="h-full w-full object-cover" />
        ) : (
          <span>{initial}</span>
        )}
      </button>
      <div
        role="menu"
        className={cn(
          "absolute right-0 top-[calc(100%+8px)] z-40 w-56 origin-top-right rounded-2xl border border-divider bg-surface p-1.5 text-sm shadow-pop transition-all",
          open ? "scale-100 opacity-100" : "pointer-events-none scale-95 opacity-0"
        )}
        style={{ boxShadow: "var(--shadow-pop)" }}
      >
        <div className="px-3 py-2.5">
          <div className="text-[13px] font-semibold text-ink">
            {session.nickname || session.username}
          </div>
          <div className="text-xs text-muted">@{session.username}</div>
        </div>
        <hr className="my-1 border-divider" />
        <Link
          href="/orders"
          role="menuitem"
          className="block rounded-xl px-3 py-2 text-ink-soft transition-colors hover:bg-surface-elev hover:text-ink"
          onClick={() => setOpen(false)}
        >
          我的兑换记录
        </Link>
        <Link
          href={process.env.NEXT_PUBLIC_COMMUNITY_URL || "#"}
          target="_blank"
          rel="noreferrer"
          role="menuitem"
          className="block rounded-xl px-3 py-2 text-ink-soft transition-colors hover:bg-surface-elev hover:text-ink"
          onClick={() => setOpen(false)}
        >
          返回 Zens 社区 ↗
        </Link>
        <hr className="my-1 border-divider" />
        <form action="/api/auth/logout" method="POST" className="contents">
          <button
            type="submit"
            role="menuitem"
            className="block w-full rounded-xl px-3 py-2 text-left text-ink-soft transition-colors hover:bg-surface-elev hover:text-ink"
          >
            退出登录
          </button>
        </form>
      </div>
    </div>
  );
}
