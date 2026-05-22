import { cn } from "@/lib/utils";

export function Eyebrow({
  children,
  className,
  tone = "muted",
}: {
  children: React.ReactNode;
  className?: string;
  tone?: "muted" | "brand";
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 font-bold uppercase",
        tone === "brand" ? "text-brand" : "text-muted",
        "text-eyebrow",
        className
      )}
    >
      <span className="inline-block h-[1px] w-5 bg-current opacity-50" aria-hidden />
      {children}
    </span>
  );
}
