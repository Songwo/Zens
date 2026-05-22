import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { Eyebrow } from "@/components/editorial/eyebrow";
import { getSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";
import { fetchUserPointsSafe, CATEGORY_META } from "@/lib/products";
import { formatDateTime, formatPoints } from "@/lib/utils";
import { ProductStatusToggle } from "./product-status-toggle";
import { CodesImportButton } from "@/components/admin/codes-import-button";
import { OrderActions } from "@/components/admin/order-actions";

export const metadata = { title: "管理" };
export const dynamic = "force-dynamic";

const ADMIN_ROLES = new Set(["ROLE_ADMIN", "ROLE_SUPER_ADMIN"]);

interface PageProps {
  searchParams: Promise<{ tab?: string }>;
}

export default async function AdminPage({ searchParams }: PageProps) {
  const session = await getSession();
  if (!session) redirect("/login?from=/admin");
  if (!ADMIN_ROLES.has(session.role)) notFound();

  const { tab = "products" } = await searchParams;
  const points = await fetchUserPointsSafe(session.userId);

  const [products, recentOrders, codeStats] = await Promise.all([
    prisma.product.findMany({
      orderBy: [{ status: "asc" }, { sortWeight: "desc" }, { createdAt: "desc" }],
      include: {
        _count: {
          select: {
            codes: { where: { consumedBy: null } },
            orders: true,
          },
        },
      },
    }),
    prisma.order.findMany({
      orderBy: { createdAt: "desc" },
      take: 30,
      include: {
        product: { select: { slug: true, title: true, fulfillment: true } },
      },
    }),
    prisma.product.findMany({
      select: {
        id: true,
        slug: true,
        title: true,
        fulfillment: true,
        _count: {
          select: {
            codes: { where: { consumedBy: null } },
            orders: true,
          },
        },
      },
      orderBy: [{ sortWeight: "desc" }, { createdAt: "desc" }],
    }),
  ]);

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
        <section className="animate-rise pt-16">
          <Eyebrow tone="brand">admin · 仅管理员可见</Eyebrow>
          <h1 className="mt-4 max-w-[16ch] text-display font-extrabold tracking-tightest text-ink text-balance">
            幕后操盘室。
          </h1>
          <p className="mt-4 max-w-xl text-muted">
            商品上下架、订单退款、兑换码池都在这里。深度数据修复用
            <code className="mx-1 font-mono text-sm">npm run db:studio</code>。
          </p>

          <nav className="mt-10 flex flex-wrap items-end gap-x-7 gap-y-3 border-b border-divider">
            <TabLink href="/admin?tab=products" active={tab === "products"}>
              商品 · {products.length}
            </TabLink>
            <TabLink href="/admin?tab=orders" active={tab === "orders"}>
              订单 · {recentOrders.length}
            </TabLink>
            <TabLink href="/admin?tab=codes" active={tab === "codes"}>
              兑换码池
            </TabLink>
            <Link
              href="/admin/products/new"
              className="btn-brand ml-auto mb-2 h-9 px-4 text-sm"
            >
              + 新建商品
            </Link>
          </nav>
        </section>

        {tab === "products" && (
          <section className="pt-10">
            <ul role="list" className="divide-y divide-divider">
              {products.map((p) => (
                <li
                  key={p.id}
                  className="grid gap-3 py-5 sm:grid-cols-[64px_1fr_auto_auto_auto] sm:items-center sm:gap-5"
                >
                  {/* 缩略色块 - 即使没有图也展示渐变,保持视觉节奏 */}
                  <div
                    className="hidden h-14 w-16 rounded-2xl sm:block"
                    style={{
                      background: p.coverUrl
                        ? `url(${p.coverUrl}) center/cover`
                        : p.coverGradient || "linear-gradient(135deg, var(--surface-elev), var(--surface))",
                    }}
                    aria-hidden
                  />
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-1.5">
                      <span className="rounded-pill bg-surface-elev px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-muted">
                        {CATEGORY_META[p.category]?.eyebrow || p.category.toUpperCase()}
                      </span>
                      {p.highlight && (
                        <span className="rounded-pill bg-brand-soft px-2 py-0.5 font-mono text-[10px] font-bold uppercase tracking-widest text-brand">
                          FEAT
                        </span>
                      )}
                      {p.thisWeek && (
                        <span className="rounded-pill bg-surface-elev px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-muted">
                          WEEK
                        </span>
                      )}
                      <span className="rounded-pill bg-surface-elev px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-muted">
                        {p.fulfillment}
                      </span>
                    </div>
                    <Link
                      href={`/admin/products/${p.id}/edit`}
                      className="mt-1 block font-semibold text-ink transition-colors hover:text-brand"
                    >
                      {p.title}
                    </Link>
                    <p className="mt-0.5 text-xs text-muted">
                      <code className="font-mono">{p.slug}</code>
                      {" · "}订单 {p._count.orders}
                      {" · "}兑换码 {p._count.codes}
                    </p>
                  </div>
                  <div className="text-right">
                    <div className="font-mono text-base font-semibold tabular-nums text-ink">
                      {formatPoints(p.pricePoints)}
                      <span className="ml-1 text-[10px] uppercase tracking-widest text-faint">
                        pts
                      </span>
                    </div>
                  </div>
                  <div className="text-right text-xs">
                    <div className="text-faint">库存</div>
                    <div className="font-mono text-ink-soft">
                      {p.stock === -1 ? "∞" : p.stock}
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center justify-end gap-2">
                    <ProductStatusToggle productId={p.id} status={p.status} />
                    <Link
                      href={`/admin/products/${p.id}/edit`}
                      className="inline-flex h-8 items-center gap-1 rounded-pill border border-divider px-3 text-xs font-medium text-muted transition-all hover:border-divider-strong hover:text-ink"
                    >
                      编辑
                    </Link>
                  </div>
                </li>
              ))}
              {products.length === 0 && (
                <li className="py-12 text-center text-muted">
                  还没有商品。
                  <Link
                    href="/admin/products/new"
                    className="ml-2 text-brand underline-offset-4 hover:underline"
                  >
                    现在新建一个 →
                  </Link>
                </li>
              )}
            </ul>
          </section>
        )}

        {tab === "orders" && (
          <section className="pt-10">
            <ul role="list" className="divide-y divide-divider">
              {recentOrders.map((o) => (
                <li
                  key={o.id}
                  className="grid gap-3 py-5 sm:grid-cols-[1fr_auto_auto] sm:items-start sm:gap-6"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2 text-xs">
                      <Badge status={o.status} />
                      <span className="text-faint">{formatDateTime(o.createdAt)}</span>
                    </div>
                    <Link
                      href={`/products/${o.product.slug}`}
                      className="mt-1 block font-semibold text-ink transition-colors hover:text-brand"
                    >
                      {o.product.title}
                    </Link>
                    <div className="mt-0.5 text-xs text-muted">
                      <span className="font-mono">{o.username}</span>
                      {" · order#"}
                      <code className="font-mono">{o.id.slice(0, 8)}</code>
                    </div>
                    {o.deliveredCode && (
                      <code className="mt-1.5 inline-block rounded-md border border-divider bg-surface-elev px-2 py-0.5 font-mono text-xs">
                        {o.deliveredCode}
                      </code>
                    )}
                    {o.failureReason && (
                      <p className="mt-1 text-xs text-rose-500">{o.failureReason}</p>
                    )}
                  </div>
                  <div className="text-right font-mono tabular-nums">
                    <span className="text-faint">−</span>
                    <span className="ml-1 font-semibold text-ink">
                      {formatPoints(o.pricePoints)}
                    </span>
                    <span className="ml-1 text-[10px] uppercase tracking-widest text-faint">
                      pts
                    </span>
                  </div>
                  <OrderActions
                    orderId={o.id}
                    status={o.status}
                    fulfillment={o.product.fulfillment as "CODE" | "SOFT"}
                  />
                </li>
              ))}
              {recentOrders.length === 0 && (
                <li className="py-10 text-center text-muted">还没有订单。</li>
              )}
            </ul>
          </section>
        )}

        {tab === "codes" && (
          <section className="pt-10">
            <p className="mb-6 text-sm text-muted">
              每个商品的可用兑换码池。下单成功时按 FIFO 自动消费。
              CODE 类商品支持批量导入,SOFT 类商品不需要兑换码池。
            </p>
            <ul role="list" className="divide-y divide-divider">
              {codeStats.map((c) => (
                <li
                  key={c.id}
                  className="grid gap-3 py-5 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-6"
                >
                  <div className="min-w-0">
                    <Link
                      href={`/admin/products/${c.id}/edit`}
                      className="font-semibold text-ink-soft transition-colors hover:text-ink"
                    >
                      {c.title}
                    </Link>
                    <div className="mt-0.5 text-xs text-muted">
                      <span className="font-mono">{c.fulfillment}</span>
                      {" · "}已下单 {c._count.orders} 笔
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-xs uppercase tracking-widest text-faint">剩余</span>
                    <span className="ml-2 font-mono text-lg font-semibold text-ink">
                      {c._count.codes}
                    </span>
                  </div>
                  {c.fulfillment === "CODE" ? (
                    <CodesImportButton productId={c.id} productTitle={c.title} />
                  ) : (
                    <span className="text-xs text-faint">SOFT 无需兑换码</span>
                  )}
                </li>
              ))}
              {codeStats.length === 0 && (
                <li className="py-10 text-center text-muted">还没有商品。</li>
              )}
            </ul>
          </section>
        )}
      </main>
      <SiteFooter />
    </>
  );
}

function TabLink({
  href,
  active,
  children,
}: {
  href: string;
  active: boolean;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      className={`relative pb-3 text-sm font-semibold transition-colors ${
        active ? "text-ink" : "text-muted hover:text-ink"
      }`}
    >
      {children}
      {active && (
        <span className="absolute inset-x-0 -bottom-px h-[2px] rounded-pill bg-brand" />
      )}
    </Link>
  );
}

function Badge({ status }: { status: string }) {
  const map: Record<string, string> = {
    PENDING: "bg-amber-50 text-amber-700 dark:bg-amber-950 dark:text-amber-300",
    DELIVERED: "bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300",
    FAILED: "bg-rose-50 text-rose-600 dark:bg-rose-950 dark:text-rose-300",
    REFUNDED: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  };
  return (
    <span
      className={`inline-flex items-center rounded-pill px-2 py-0.5 text-[10px] font-semibold uppercase tracking-widest ${
        map[status] || "bg-surface-elev text-muted"
      }`}
    >
      {status}
    </span>
  );
}
