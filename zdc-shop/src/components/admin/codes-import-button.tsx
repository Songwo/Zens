"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

export function CodesImportButton({
  productId,
  productTitle,
}: {
  productId: string;
  productTitle: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [, startTransition] = useTransition();

  const parsed = text
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

  async function handleSubmit() {
    if (parsed.length === 0) {
      toast.error("请贴入至少一行兑换码");
      return;
    }
    setSubmitting(true);
    try {
      const res = await fetch(`/api/admin/products/${productId}/codes`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ codes: parsed }),
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data?.message || "导入失败");
      toast.success(`已导入 ${data.added} 条 (去重前 ${data.requested} 条)`);
      setText("");
      setOpen(false);
      startTransition(() => router.refresh());
    } catch (e) {
      toast.error("导入失败", { description: (e as Error).message });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex h-8 items-center gap-1.5 rounded-pill border border-divider px-3 text-xs font-medium text-muted transition-all hover:border-divider-strong hover:text-ink"
      >
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 5v14M5 12h14" />
        </svg>
        导入兑换码
      </button>
      {open && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-end justify-center sm:items-center"
        >
          <button
            type="button"
            aria-label="关闭"
            onClick={() => setOpen(false)}
            className="absolute inset-0 bg-black/30 backdrop-blur-sm"
          />
          <div className="relative w-full max-w-2xl animate-rise rounded-t-3xl bg-surface p-6 sm:rounded-3xl sm:p-8">
            <p className="eyebrow-brand">import codes · 批量导入</p>
            <h3 className="mt-3 text-xl font-bold text-ink">
              为「{productTitle}」导入兑换码
            </h3>
            <p className="mt-2 text-sm text-muted">
              一行一个,自动去重 + 跳过库内已存在。每条 2~100 字符,单次最多 2000 条。
            </p>

            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={10}
              className={cn(
                "mt-5 block w-full resize-y rounded-2xl border border-divider bg-bg p-4 font-mono text-sm text-ink",
                "focus:border-brand focus:ring-0 focus:outline-none placeholder:text-faint"
              )}
              placeholder={"ZENS-AICALL-A3F2B1\nZENS-AICALL-9C12D8\n…"}
            />

            <div className="mt-3 flex items-center justify-between text-xs text-muted">
              <span>
                已识别 <span className="font-mono text-ink">{parsed.length}</span> 条
              </span>
              <span className="text-faint">已自动剔除空行</span>
            </div>

            <div className="mt-6 flex justify-end gap-2.5">
              <button
                type="button"
                onClick={() => setOpen(false)}
                disabled={submitting}
                className="btn-ghost h-11"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={submitting || parsed.length === 0}
                className="btn-brand h-11 px-6 disabled:opacity-60"
              >
                {submitting ? "导入中…" : `导入 ${parsed.length} 条`}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
