import Link from "next/link";
import { notFound } from "next/navigation";
import { Eyebrow } from "@/components/editorial/eyebrow";
import { MobileBottomNav } from "@/components/mobile-bottom-nav";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { getSession } from "@/lib/auth/session";
import {
  CATEGORY_META,
  fetchProductBySlug,
  fetchPurchaseStats,
  fetchUserPointsSafe,
} from "@/lib/products";
import { cn, formatPoints } from "@/lib/utils";
import { RedeemAction } from "./redeem-action";

export const dynamic = "force-dynamic";

interface PageProps {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: PageProps) {
  const { slug } = await params;
  const p = await fetchProductBySlug(slug);
  if (!p) return { title: "未找到" };
  return {
    title: p.title,
    description: p.subtitle || p.title,
  };
}

export default async function ProductPage({ params }: PageProps) {
  const { slug } = await params;
  const product = await fetchProductBySlug(slug);
  if (!product) notFound();

  const session = await getSession();
  const [points, stats] = await Promise.all([
    fetchUserPointsSafe(session?.userId),
    session ? fetchPurchaseStats(product.id, session.userId) : Promise.resolve(null),
  ]);

  const meta = CATEGORY_META[product.category] || {
    label: product.category,
    eyebrow: product.category.toUpperCase(),
  };

  const stockText =
    product.stock === -1
      ? "无限量"
      : product.stock <= 0
      ? "已售罄"
      : `剩余 ${product.stock} 份`;

  return (
    <>
      <SiteHeader
        session={
          session
            ? {
                userId: session.userId,
                username: session.username,
                nickname: session.nickname,
                avatar: session.avatar,
                role: session.role,
                points,
              }
            : null
        }
      />

      <main className="pb-32">
        {/* ───── Cover ─────────────────────────────────────────── */}
        <div
          className="relative h-[360px] w-full overflow-hidden sm:h-[480px]"
          style={{
            background: product.coverGradient || "linear-gradient(135deg, #fff7d6, #f4b400)",
          }}
        >
          <div
            className="absolute inset-x-0 bottom-0 h-1/2"
            style={{ background: "linear-gradient(180deg, transparent 0%, var(--bg) 100%)" }}
            aria-hidden
          />
          <div className="editorial-container relative flex h-full flex-col justify-end pb-10">
            <Link
              href="/"
              className="absolute left-5 top-6 inline-flex h-9 items-center gap-1.5 rounded-pill bg-black/30 px-3 text-xs font-medium text-white backdrop-blur-md transition-colors hover:bg-black/40 sm:left-8"
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                <path d="m15 18-6-6 6-6" />
              </svg>
              回到首页
            </Link>
          </div>
        </div>

        <div className="editorial-container -mt-12 animate-rise sm:-mt-16">
          <Eyebrow>{meta.eyebrow}</Eyebrow>
          <h1 className="mt-4 max-w-[18ch] text-display font-extrabold tracking-tightest text-ink text-balance">
            {product.title}
          </h1>
          {product.subtitle && (
            <p className="mt-4 max-w-2xl text-lg text-muted text-pretty">
              {product.subtitle}
            </p>
          )}

          {/* ───── Price + Action 行 ─────────────────────────────── */}
          <div className="mt-10 flex flex-col items-start gap-6 border-t border-b border-divider py-8 sm:flex-row sm:items-end sm:justify-between sm:gap-12">
            <div className="space-y-2">
              <Eyebrow>price · 兑换价</Eyebrow>
              <div className="flex items-baseline gap-2.5 font-mono tabular-nums">
                <span className="text-5xl font-extrabold text-ink">
                  {formatPoints(product.pricePoints)}
                </span>
                <span className="text-base uppercase tracking-widest text-faint">pts</span>
              </div>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
                <MetaItem label="库存" value={stockText} />
                <MetaItem label="单人限购" value={`${product.limitPerUser} 件`} />
                {stats && (
                  <MetaItem label="你已兑换" value={`${stats.userOrderCount} 件`} />
                )}
              </div>
            </div>

            <RedeemAction
              productSlug={product.slug}
              productTitle={product.title}
              pricePoints={product.pricePoints}
              isAuthed={!!session}
              userPoints={points}
              userOrderCount={stats?.userOrderCount ?? 0}
              limitPerUser={product.limitPerUser}
              stock={product.stock}
              codeRemain={stats?.codeRemain ?? null}
              fulfillment={product.fulfillment as "CODE" | "SOFT"}
            />
          </div>

          {/* ───── 描述 (无富文本, prose 简洁排版) ─────────────────── */}
          <article className="mx-auto mt-12 max-w-2xl">
            <Eyebrow>about · 详细说明</Eyebrow>
            <div className="mt-6 space-y-4 text-[15px] leading-relaxed text-ink-soft">
              {product.description.split("\n\n").map((para, i) => (
                <Paragraph key={i} text={para} />
              ))}
            </div>
          </article>

          {/* ───── 兜底链接 ──────────────────────────────────────── */}
          <div className="mt-16 flex justify-center">
            <Link href="/" className="btn-ghost h-10">
              继续浏览其他商品 →
            </Link>
          </div>
        </div>
      </main>

      <SiteFooter />
      <MobileBottomNav />
    </>
  );
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return (
    <span className="inline-flex items-baseline gap-1.5">
      <span className="text-xs uppercase tracking-widest text-faint">{label}</span>
      <span className="font-medium text-ink-soft">{value}</span>
    </span>
  );
}

function Paragraph({ text }: { text: string }) {
  // 简单识别 "- " 开头的连续行为列表
  const lines = text.split("\n");
  const isList = lines.every((l) => l.trim().startsWith("- ") || l.trim() === "");
  if (isList && lines.some((l) => l.trim())) {
    return (
      <ul className="ml-5 list-disc space-y-1.5 marker:text-faint">
        {lines
          .filter((l) => l.trim())
          .map((l, i) => (
            <li key={i}>{l.replace(/^\s*-\s+/, "")}</li>
          ))}
      </ul>
    );
  }
  return <p className={cn("text-pretty")}>{text}</p>;
}
