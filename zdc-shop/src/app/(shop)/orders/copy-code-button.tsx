"use client";

import { useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

export function CopyCodeButton({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      toast.success("兑换码已复制", { description: code });
      setTimeout(() => setCopied(false), 1600);
    } catch {
      toast.error("复制失败,请手动选中文本复制");
    }
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={cn(
        "inline-flex h-8 items-center gap-1.5 rounded-pill border border-divider px-3 text-xs font-medium transition-all",
        copied
          ? "border-brand bg-brand-soft text-brand"
          : "text-muted hover:border-divider-strong hover:text-ink"
      )}
    >
      {copied ? (
        <>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M20 6 9 17l-5-5" />
          </svg>
          已复制
        </>
      ) : (
        <>
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
            <rect x="9" y="9" width="11" height="11" rx="2" />
            <path d="M5 15V5a2 2 0 0 1 2-2h10" />
          </svg>
          复制兑换码
        </>
      )}
    </button>
  );
}
