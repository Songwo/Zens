"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

const NEXT_STATUS: Record<string, string> = {
  ACTIVE: "DRAFT",
  DRAFT: "ACTIVE",
  SOLDOUT: "ACTIVE",
};

const LABEL: Record<string, { label: string; cls: string }> = {
  ACTIVE: { label: "在售", cls: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300" },
  DRAFT: { label: "草稿", cls: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300" },
  SOLDOUT: { label: "售罄", cls: "bg-rose-50 text-rose-600 dark:bg-rose-950 dark:text-rose-300" },
};

export function ProductStatusToggle({
  productId,
  status,
}: {
  productId: string;
  status: string;
}) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [current, setCurrent] = useState(status);

  const meta = LABEL[current] || { label: current, cls: "bg-surface-elev text-muted" };

  function handle() {
    const next = NEXT_STATUS[current] || "ACTIVE";
    setCurrent(next);
    fetch("/api/admin/products", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ productId, status: next }),
    })
      .then(async (r) => {
        if (!r.ok) {
          const d = await r.json().catch(() => ({}));
          throw new Error(d?.message || "切换失败");
        }
        toast.success(`已切换为 ${LABEL[next]?.label || next}`);
        startTransition(() => router.refresh());
      })
      .catch((e: Error) => {
        setCurrent(status);
        toast.error("切换失败", { description: e.message });
      });
  }

  return (
    <button
      type="button"
      disabled={pending}
      onClick={handle}
      className={`inline-flex h-8 items-center justify-center rounded-pill px-3 text-xs font-semibold uppercase tracking-widest transition-all hover:opacity-80 disabled:opacity-50 ${meta.cls}`}
      title="点击切换状态"
    >
      {pending ? "…" : meta.label}
    </button>
  );
}
