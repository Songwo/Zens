import { cn, formatPoints } from "@/lib/utils";

export function BalancePill({
  points,
  className,
  size = "sm",
}: {
  points: number | null | undefined;
  className?: string;
  size?: "sm" | "lg";
}) {
  const display = points === null || points === undefined ? "—" : formatPoints(points);

  return (
    <span
      className={cn(
        "balance-pill",
        size === "lg" && "px-5 py-2.5 text-base",
        className
      )}
      title={`当前积分：${display}`}
    >
      <span className="balance-pill__star" aria-hidden>
        ★
      </span>
      <span className="balance-pill__num">{display}</span>
      <span className="text-faint">pts</span>
    </span>
  );
}
