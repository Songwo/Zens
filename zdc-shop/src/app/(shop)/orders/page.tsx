import Link from "next/link";
import { redirect } from "next/navigation";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { Eyebrow } from "@/components/editorial/eyebrow";
import { MobileBottomNav } from "@/components/mobile-bottom-nav";
import { getSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";
import { fetchUserPointsSafe, CATEGORY_META } from "@/lib/products";
import { formatDateTime, formatPoints } from "@/lib/utils";
import { CopyCodeButton } from "./copy-code-button";

export const metadata = { title: "我的兑换" };
export const dynamic = "force-dynamic";

export default async function OrdersPage() {
  const session = await getSession();
  if (!session) {
    redirect("/login?from=/orders");
  }

  const [orders, points] = await Promise.all([
    prisma.order.findMany({
      where: { userId: session.userId },
      orderBy: { createdAt: "desc" },
      take: 60,
      include: {
        product: {
          select: {
            slug: true,
            title: true,
            subtitle: true,
            coverGradient: true,
            category: true,
            fulfillment: true,
          },
        },
      },
    }),
    fetchUserPointsSafe(session.userId),
  ]);

  const totalSpent = orders
    .filter((o) => o.status === "DELIVERED")
    .reduce((s, o) => s + o.pricePoints, 0);
  const deliveredCount = orders.filter((o) => o.status === "DELIVERED").length;

  return (
    <>
      <SiteHeader
        session={{
          userId: session.userId,
          username: session.username,
          nickname: session.nickname,
          avatar: session.avatar,
          role: session.role,
          points,
        }}
      />

      <main className="editorial-container pb-32">
        <section className="animate-rise pt-16 sm:pt-24">
          <Eyebrow tone="brand">orders · 兑换历史</Eyebrow>
          <h1 className="mt-4 max-w-[16ch] text-display font-extrabold tracking-tightest text-ink text-balance">
            你换过的一切。
          </h1>
          <p className="mt-4 max-w-xl text-base text-muted text-pretty sm:text-lg">
            兑换码请妥善保存，丢失可联系运营组找回（一次性）。
          </p>

          <dl className="mt-10 grid max-w-2xl grid-cols-3 gap-px overflow-hidden rounded-2xl bg-divider">
            <Stat label="已兑换" value={`${deliveredCount}`} suffix="件" />
            <Stat label="累计消耗" value={formatPoints(totalSpent)} suffix="pts" />
            <Stat label="当前余额" value={points === null ? "—" : formatPoints(points)} suffix="pts" />
          </dl>
        </section>

        <section className="mt-16">
          <div className="section-title">
            <h2 className="text-2xl font-bold tracking-tight">全部订单</h2>
            <span className="text-sm text-muted">{orders.length} 条</span>
          </div>

          {orders.length === 0 ? (
            <EmptyOrders />
          ) : (
            <ul role="list" className="divide-y divide-divider">
              {orders.map((o) => (
                <li key={o.id} className="py-6">
                  <div className="grid gap-4 sm:grid-cols-[88px_1fr_auto] sm:items-start">
                    {/* 缩略色块替代图片,符合 Editorial 风 */}
                    <div
                      className="h-20 w-20 shrink-0 rounded-2xl"
                      style={{
                        background:
                          o.product.coverGradient ||
                          "linear-gradient(135deg, #fff7d6, #f4b400)",
                      }}
                      aria-hidden
                    />
                    <div className="min-w-0 space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <StatusBadge status={o.status} />
                        <span className="rounded-pill bg-surface-elev px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-muted">
                          {CATEGORY_META[o.product.category]?.eyebrow ||
                            o.product.category.toUpperCase()}
                        </span>
                        <span className="text-xs text-faint">
                          {formatDateTime(o.createdAt)}
                        </span>
                      </div>
                      <Link
                        href={`/products/${o.product.slug}`}
                        className="block text-lg font-semibold tracking-tight text-ink transition-colors hover:text-brand"
                      >
                        {o.product.title}
                      </Link>
                      {o.product.subtitle && (
                        <p className="text-sm text-muted">{o.product.subtitle}</p>
                      )}

                      {o.status === "DELIVERED" && o.deliveredCode && (
                        <div className="mt-2 flex flex-wrap items-center gap-3">
                          <code className="rounded-md border border-divider bg-surface-elev px-3 py-1.5 font-mono text-sm font-semibold text-ink">
                            {o.deliveredCode}
                          </code>
                          <CopyCodeButton code={o.deliveredCode} />
                        </div>
                      )}
                      {o.status === "DELIVERED" && !o.deliveredCode && (
                        <p className="text-xs text-muted">
                          ✓ 权益已下发到你的主站账户
                        </p>
                      )}
                      {o.status === "FAILED" && o.failureReason && (
                        <p className="text-xs text-rose-500">
                          失败原因: {o.failureReason}
                        </p>
                      )}
                    </div>
                    <div className="sm:text-right">
                      <div className="flex items-baseline gap-1.5 font-mono tabular-nums sm:justify-end">
                        <span className="text-xs uppercase tracking-widest text-faint">
                          −
                        </span>
                        <span className="text-xl font-semibold text-ink">
                          {formatPoints(o.pricePoints)}
                        </span>
                        <span className="text-[11px] uppercase tracking-widest text-faint">
                          pts
                        </span>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </main>

      <SiteFooter />
      <MobileBottomNav />
    </>
  );
}

function Stat({
  label,
  value,
  suffix,
}: {
  label: string;
  value: string;
  suffix?: string;
}) {
  return (
    <div className="bg-surface p-5">
      <div className="text-xs uppercase tracking-widest text-faint">{label}</div>
      <div className="mt-2 flex items-baseline gap-1.5 font-mono tabular-nums">
        <span className="text-2xl font-semibold text-ink">{value}</span>
        {suffix && (
          <span className="text-[11px] uppercase tracking-widest text-faint">
            {suffix}
          </span>
        )}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; cls: string }> = {
    PENDING: {
      label: "处理中",
      cls: "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    },
    DELIVERED: {
      label: "已发放",
      cls: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
    },
    FAILED: {
      label: "失败",
      cls: "bg-rose-50 text-rose-600 dark:bg-rose-950 dark:text-rose-300",
    },
    REFUNDED: {
      label: "已退款",
      cls: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
    },
  };
  const m = map[status] ?? { label: status, cls: "bg-surface-elev text-muted" };
  return (
    <span
      className={`inline-flex items-center rounded-pill px-2 py-0.5 text-[11px] font-semibold uppercase tracking-widest ${m.cls}`}
    >
      {m.label}
    </span>
  );
}

function EmptyOrders() {
  return (
    <div className="mt-12 flex flex-col items-center gap-4 py-20 text-center text-muted">
      <div
        className="inline-flex h-14 w-14 items-center justify-center rounded-full"
        style={{ background: "var(--zens-yellow-soft)" }}
      >
        <svg
          viewBox="0 0 24 24"
          width="22"
          height="22"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="text-brand"
        >
          <path d="M4 7h13l2 11H6L4 7Z" />
          <path d="M8 7V5a4 4 0 0 1 8 0v2" />
        </svg>
      </div>
      <p>还没有兑换记录。</p>
      <Link href="/" className="btn-ghost h-10">
        去逛逛 →
      </Link>
    </div>
  );
}
