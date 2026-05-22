"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

type State =
  | { phase: "loading" }
  | { phase: "success"; username: string; redirectTo: string }
  | { phase: "error"; message: string };

export function CallbackClient() {
  const router = useRouter();
  const search = useSearchParams();
  const [state, setState] = useState<State>({ phase: "loading" });

  useEffect(() => {
    const ssoToken = search.get("sso_token");
    let returnUrl = "";
    try {
      returnUrl = sessionStorage.getItem("zs_return_url") || "";
    } catch {
      returnUrl = "";
    }
    if (!returnUrl || !returnUrl.startsWith("/")) returnUrl = "/";

    if (!ssoToken) {
      setState({ phase: "error", message: "缺少 sso_token 参数" });
      return;
    }

    let aborted = false;
    fetch("/api/auth/community-login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ssoToken }),
      credentials: "include",
    })
      .then(async (res) => {
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
          throw new Error(data?.message || data?.error || `登录失败 (${res.status})`);
        }
        return data as { username: string; redirectTo?: string };
      })
      .then((data) => {
        if (aborted) return;
        try {
          sessionStorage.removeItem("zs_return_url");
        } catch {
          /* ignore */
        }
        const next = data.redirectTo && data.redirectTo.startsWith("/")
          ? data.redirectTo
          : returnUrl;
        setState({ phase: "success", username: data.username, redirectTo: next });
        setTimeout(() => {
          router.replace(next);
          router.refresh();
        }, 600);
      })
      .catch((err: Error) => {
        if (aborted) return;
        setState({ phase: "error", message: err.message });
      });

    return () => {
      aborted = true;
    };
  }, [router, search]);

  if (state.phase === "loading") {
    return (
      <div className="space-y-4 text-center">
        <div className="mx-auto h-12 w-12 animate-spin rounded-full border-2 border-divider border-t-brand" />
        <p className="text-sm text-muted">正在校验 Zens 社区授权…</p>
      </div>
    );
  }

  if (state.phase === "success") {
    return (
      <div className="space-y-4 text-center">
        <div
          className="mx-auto inline-flex h-12 w-12 items-center justify-center rounded-full bg-brand-soft text-brand"
          style={{ boxShadow: "0 8px 24px -8px var(--zens-yellow-glow)" }}
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 6 9 17l-5-5" />
          </svg>
        </div>
        <div>
          <p className="text-lg font-semibold text-ink">
            欢迎回来，{state.username}
          </p>
          <p className="mt-1 text-sm text-muted">正在带你回到商城…</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-center">
      <div className="mx-auto inline-flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-rose-500 dark:bg-rose-950">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M18 6 6 18M6 6l12 12" />
        </svg>
      </div>
      <div>
        <p className="text-lg font-semibold text-ink">登录失败</p>
        <p className="mt-1 text-sm text-muted">{state.message}</p>
      </div>
      <a href="/login" className="btn-ghost h-10 inline-flex">
        重新登录
      </a>
    </div>
  );
}
