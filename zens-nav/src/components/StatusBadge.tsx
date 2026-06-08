import type { NavStatus } from "../types/nav";

type StatusBadgeProps = {
  status?: NavStatus;
};

const statusMeta: Record<NavStatus, { label: string; dot: string }> = {
  normal: { label: "正常", dot: "bg-success" },
  maintenance: { label: "维护中", dot: "bg-caution" },
  "coming-soon": { label: "即将上线", dot: "bg-slate-400" },
  beta: { label: "内测", dot: "bg-amber-strong" },
};

export function StatusBadge({ status = "normal" }: StatusBadgeProps) {
  const meta = statusMeta[status];

  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-muted">
      <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} aria-hidden="true" />
      {meta.label}
    </span>
  );
}
