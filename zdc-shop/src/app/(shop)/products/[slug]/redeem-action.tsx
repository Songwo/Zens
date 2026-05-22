"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { toast } from "sonner";
import { cn, formatPoints } from "@/lib/utils";

interface Props {
  productSlug: string;
  productTitle: string;
  pricePoints: number;
  isAuthed: boolean;
  userPoints: number | null;
  userOrderCount: number;
  limitPerUser: number;
  stock: number;
  codeRemain: number | null;
  fulfillment: "CODE" | "SOFT";
}

type Phase = "idle" | "confirm" | "submitting";

export function RedeemAction(props: Props) {
  const router = useRouter();
  const [phase, setPhase] = useState<Phase>("idle");
  const [pending, startTransition] = useTransition();

  const insufficientBalance =
    props.userPoints !== null && props.userPoints < props.pricePoints;
  const reachedLimit = props.userOrderCount >= props.limitPerUser;
  const outOfStock =
    props.stock === 0 ||
    (props.fulfillment === "CODE" && props.codeRemain !== null && props.codeRemain <= 0);

  const disabledReason = !props.isAuthed
    ? "登录后兑换"
    : outOfStock
    ? "已售罄"
    : reachedLimit
    ? `已达单人上限 (${props.limitPerUser})`
    : insufficientBalance
    ? `积分不足 (需 ${formatPoints(props.pricePoints)})`
    : null;

  if (!props.isAuthed) {
    return (
      <Link
        href={`/login?from=/products/${encodeURIComponent(props.productSlug)}`}
        className="btn-brand h-12 px-7 text-base"
      >
        登录后兑换 →
      </Link>
    );
  }

  async function doRedeem() {
    setPhase("submitting");
    const idempotencyKey =
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;

    try {
      const res = await fetch("/api/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          productSlug: props.productSlug,
          idempotencyKey,
        }),
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data?.message || data?.error || "兑换失败");
      }
      toast.success("兑换成功", {
        description: `已为你扣 ${formatPoints(props.pricePoints)} pts,正在前往订单详情。`,
      });
      setPhase("idle");
      startTransition(() => {
        router.push("/orders");
        router.refresh();
      });
    } catch (e) {
      toast.error("兑换失败", { description: (e as Error).message });
      setPhase("idle");
    }
  }

  return (
    <div className="flex flex-col items-end gap-2">
      <button
        type="button"
        disabled={!!disabledReason || phase === "submitting" || pending}
        onClick={() => setPhase("confirm")}
        className={cn(
          "btn-brand h-12 px-7 text-base disabled:cursor-not-allowed disabled:opacity-50",
          "transition-all"
        )}
      >
        {phase === "submitting" || pending ? "处理中…" : "立即兑换"}
      </button>
      {disabledReason && (
        <span className="text-xs text-muted">{disabledReason}</span>
      )}

      {/* ───── 二次确认 dialog (Editorial 风, 无遮罩卡片, 用底部抽屉感) ─── */}
      {phase === "confirm" && (
        <ConfirmDialog
          title={props.productTitle}
          pricePoints={props.pricePoints}
          balance={props.userPoints}
          fulfillment={props.fulfillment}
          onCancel={() => setPhase("idle")}
          onConfirm={doRedeem}
        />
      )}
    </div>
  );
}

function ConfirmDialog({
  title,
  pricePoints,
  balance,
  fulfillment,
  onCancel,
  onConfirm,
}: {
  title: string;
  pricePoints: number;
  balance: number | null;
  fulfillment: "CODE" | "SOFT";
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center"
    >
      <button
        type="button"
        onClick={onCancel}
        aria-label="关闭"
        className="absolute inset-0 bg-black/30 backdrop-blur-sm"
      />
      <div className="relative w-full max-w-md animate-rise rounded-t-3xl bg-surface p-6 shadow-pop sm:rounded-3xl sm:p-8">
        <p className="eyebrow-brand">confirm · 确认兑换</p>
        <h3 className="mt-3 text-xl font-bold text-ink">{title}</h3>
        <p className="mt-2 text-sm text-muted">
          {fulfillment === "CODE"
            ? "兑换完成后会立即生成一条兑换码,请妥善保存。"
            : "兑换完成后,权益会立即下发到你的账户。"}
        </p>

        <dl className="mt-6 space-y-3 border-t border-b border-divider py-4 text-sm">
          <Row label="本次消耗" value={`${formatPoints(pricePoints)} pts`} accent />
          <Row label="当前余额" value={balance === null ? "—" : `${formatPoints(balance)} pts`} />
          <Row
            label="兑换后剩余"
            value={
              balance === null ? "—" : `${formatPoints(balance - pricePoints)} pts`
            }
          />
        </dl>

        <div className="mt-6 flex gap-2.5">
          <button type="button" onClick={onCancel} className="btn-ghost h-11 flex-1">
            再想想
          </button>
          <button type="button" onClick={onConfirm} className="btn-brand h-11 flex-[2]">
            确认,扣 {formatPoints(pricePoints)} pts
          </button>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="flex items-baseline justify-between">
      <dt className="text-xs uppercase tracking-widest text-faint">{label}</dt>
      <dd
        className={cn(
          "font-mono tabular-nums",
          accent ? "text-lg font-semibold text-ink" : "text-ink-soft"
        )}
      >
        {value}
      </dd>
    </div>
  );
}
