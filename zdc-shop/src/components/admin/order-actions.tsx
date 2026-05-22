"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

export function OrderActions({
  orderId,
  status,
  fulfillment,
}: {
  orderId: string;
  status: string;
  fulfillment: "CODE" | "SOFT";
}) {
  const router = useRouter();
  const [, startTransition] = useTransition();
  const [pending, setPending] = useState<"refund" | "deliver" | null>(null);

  async function refund() {
    if (!confirm("确认退款?\n会把积分原路退回主站,并把订单标记为 REFUNDED。")) return;
    setPending("refund");
    try {
      const res = await fetch(`/api/admin/orders/${orderId}/refund`, {
        method: "POST",
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data?.message || "退款失败");
      toast.success(`已退款 ${data.refundedPoints} pts`);
      startTransition(() => router.refresh());
    } catch (e) {
      toast.error("退款失败", { description: (e as Error).message });
    } finally {
      setPending(null);
    }
  }

  async function deliver(force = false) {
    if (status === "FAILED" && !force) {
      if (!confirm("此订单已 FAILED。\n强制改成 DELIVERED 表示你已在外部补偿用户,操作不可逆。继续?")) return;
    }
    setPending("deliver");
    try {
      const res = await fetch(`/api/admin/orders/${orderId}/deliver`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ force: status === "FAILED" }),
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data?.message || "操作失败");
      toast.success("已标记为已发放");
      startTransition(() => router.refresh());
    } catch (e) {
      toast.error("操作失败", { description: (e as Error).message });
    } finally {
      setPending(null);
    }
  }

  const showRefund = status === "DELIVERED" || status === "PENDING";
  const showDeliver =
    (status === "PENDING" && fulfillment === "SOFT") || status === "FAILED";

  if (!showRefund && !showDeliver) return null;

  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {showDeliver && (
        <button
          type="button"
          onClick={() => deliver()}
          disabled={pending !== null}
          className={cn(
            "inline-flex h-7 items-center gap-1 rounded-pill border border-divider px-2.5 text-[11px] font-medium text-emerald-700 transition-colors hover:border-emerald-300 hover:bg-emerald-50",
            "dark:text-emerald-300 dark:hover:bg-emerald-950"
          )}
        >
          {pending === "deliver" ? "…" : "标记已发放"}
        </button>
      )}
      {showRefund && (
        <button
          type="button"
          onClick={refund}
          disabled={pending !== null}
          className={cn(
            "inline-flex h-7 items-center gap-1 rounded-pill border border-divider px-2.5 text-[11px] font-medium text-rose-500 transition-colors hover:border-rose-300 hover:bg-rose-50",
            "dark:hover:bg-rose-950"
          )}
        >
          {pending === "refund" ? "…" : "退款"}
        </button>
      )}
    </div>
  );
}
