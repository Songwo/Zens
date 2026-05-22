"use client";

import { useState } from "react";

export function SSOLoginButton({ from }: { from: string }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch("/api/auth/community-config", { cache: "no-store" });
      if (!res.ok) throw new Error("无法获取 SSO 配置");
      const cfg = (await res.json()) as { communityUrl: string; clientId: string };
      if (!cfg.communityUrl || !cfg.clientId) {
        throw new Error("社区 SSO 未就绪，请联系管理员");
      }

      const origin = window.location.origin;
      const callbackUrl = `${origin}/login/callback`;
      try {
        sessionStorage.setItem("zs_return_url", from || "/");
      } catch {
        /* ignore */
      }

      const url =
        `${cfg.communityUrl.replace(/\/$/, "")}/sso/authorize` +
        `?client_id=${encodeURIComponent(cfg.clientId)}` +
        `&redirect_uri=${encodeURIComponent(callbackUrl)}`;
      window.location.href = url;
    } catch (e) {
      setError((e as Error).message);
      setLoading(false);
    }
  }

  return (
    <div className="space-y-3">
      <button
        type="button"
        onClick={handleClick}
        disabled={loading}
        className="btn-brand h-12 w-full text-base disabled:cursor-wait disabled:opacity-70"
      >
        {loading ? (
          <span className="inline-flex items-center gap-2">
            <Spinner />
            正在跳转 Zens…
          </span>
        ) : (
          <>
            使用 Zens 社区账号继续 →
          </>
        )}
      </button>
      {error && (
        <p className="text-sm text-rose-500" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

function Spinner() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="18"
      height="18"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.5"
      strokeLinecap="round"
      className="animate-spin"
      aria-hidden
    >
      <path d="M21 12a9 9 0 1 1-6.2-8.55" />
    </svg>
  );
}
