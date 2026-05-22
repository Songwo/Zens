import Link from "next/link";
import { Eyebrow } from "@/components/editorial/eyebrow";
import { MobileBottomNav } from "@/components/mobile-bottom-nav";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { getSession } from "@/lib/auth/session";
import {
  CATEGORY_META,
  fetchHomeSections,
  fetchUserPointsSafe,
  type ProductCard,
} from "@/lib/products";
import { cn, formatPoints } from "@/lib/utils";

export const dynamic = "force-dynamic";

export default async function HomePage() {
  const session = await getSession();
  const [sections, points] = await Promise.all([
    fetchHomeSections(),
    fetchUserPointsSafe(session?.userId),
  ]);

  const empty = sections.total === 0;

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

      <main className="editorial-container pb-32">
        <Hero session={session} points={points} />

        {empty ? <EmptyState /> : (
          <>
            {sections.featured && (
              <>
                <Hr className="mt-20" />
                <FeaturedSection product={sections.featured} />
              </>
            )}

            {sections.thisWeek.length > 0 && (
              <>
                <Hr className="mt-20" />
                <ThisWeekSection items={sections.thisWeek} />
              </>
            )}

            {Object.entries(sections.byCategory).map(([cat, items]) =>
              items.length === 0 ? null : (
                <span key={cat}>
                  <Hr className="mt-20" />
                  <CategorySection
                    category={cat}
                    items={items.slice(0, 4)}
                  />
                </span>
              )
            )}
          </>
        )}

        {!session && <JoinCta />}
      </main>

      <SiteFooter />
      <MobileBottomNav />
    </>
  );
}

// ─── 子组件 ──────────────────────────────────────────────────────────

function Hero({
  session,
  points,
}: {
  session: { username: string } | null;
  points: number | null;
}) {
  return (
    <section className="relative animate-rise pt-16 sm:pt-24">
      <Eyebrow tone="brand">store · 仅限 zens 社区</Eyebrow>
      <h1 className="mt-4 max-w-[14ch] text-display font-extrabold tracking-tightest text-ink text-balance">
        把你的耕耘
        <br />
        换成想要的东西。
      </h1>
      <p className="mt-5 max-w-xl text-base text-muted text-pretty sm:text-lg">
        你在 Zens 社区写下的每一个帖子、被点亮的每一次赞，都在悄悄积累。这里是把它们兑现的地方。
      </p>

      <div className="mt-9 flex flex-wrap items-center gap-3">
        {session ? (
          <>
            <span className="balance-pill" style={{ padding: "10px 18px" }}>
              <span className="balance-pill__star text-base" aria-hidden>
                ★
              </span>
              <span className="balance-pill__num font-mono text-base">
                {formatPoints(points)}
              </span>
              <span className="text-xs uppercase tracking-widest text-faint">
                your balance
              </span>
            </span>
            <Link href="#this-week" className="btn-ghost h-10">
              浏览本周精选 ↓
            </Link>
          </>
        ) : (
          <>
            <Link href="/login" className="btn-brand h-11 px-6 text-[15px]">
              使用 Zens 账号登录
            </Link>
            <Link
              href={process.env.NEXT_PUBLIC_COMMUNITY_URL || "#"}
              target="_blank"
              className="btn-ghost h-10"
            >
              了解 Zens 社区 →
            </Link>
          </>
        )}
      </div>
    </section>
  );
}

function FeaturedSection({ product }: { product: ProductCard }) {
  return (
    <section className="pt-16">
      <div className="section-title">
        <div className="space-y-3">
          <Eyebrow>featured · 本期焦点</Eyebrow>
          <h2 className="text-2xl font-bold tracking-tight">
            值得动用大额积分的
            <span className="text-brand"> 一件事</span>。
          </h2>
        </div>
        <Link
          href={`/products/${product.slug}`}
          className="hidden text-sm text-muted transition-colors hover:text-ink md:inline-flex"
        >
          查看详情 →
        </Link>
      </div>

      <Link
        href={`/products/${product.slug}`}
        className="feature-frame group block"
        aria-label={product.title}
      >
        <div
          className="relative aspect-[16/7] w-full"
          style={{ background: product.coverGradient || "linear-gradient(135deg, #fff7d6, #f4b400)" }}
        >
          <div className="absolute left-6 top-6 z-10">
            <span className="rounded-pill bg-black/30 px-3 py-1 font-mono text-[11px] tracking-widest text-white backdrop-blur-md">
              FEATURED
            </span>
          </div>
          <div className="absolute inset-x-0 bottom-0 z-10 flex flex-col gap-3 p-6 text-white sm:flex-row sm:items-end sm:justify-between sm:p-10">
            <div>
              <h3 className="text-2xl font-bold tracking-tight sm:text-4xl">
                {product.title}
              </h3>
              {product.subtitle && (
                <p className="mt-2 text-sm opacity-85 sm:text-base">
                  {product.subtitle}
                </p>
              )}
            </div>
            <div className="flex items-baseline gap-2 font-mono tabular-nums">
              <span className="text-3xl font-bold sm:text-4xl">
                {formatPoints(product.pricePoints)}
              </span>
              <span className="text-sm uppercase tracking-widest opacity-80">
                pts
              </span>
            </div>
          </div>
        </div>
      </Link>
    </section>
  );
}

function ThisWeekSection({ items }: { items: ProductCard[] }) {
  return (
    <section id="this-week" className="pt-16">
      <div className="section-title">
        <div className="space-y-3">
          <Eyebrow>this week · 本周精选</Eyebrow>
          <h2 className="text-2xl font-bold tracking-tight">本周大家在兑换。</h2>
        </div>
      </div>

      <ul role="list" className="divide-y divide-divider">
        {items.map((item, idx) => (
          <li key={item.slug}>
            <Link
              href={`/products/${item.slug}`}
              className="product-row group"
            >
              <span className="font-mono text-2xl font-semibold tabular-nums text-faint group-hover:text-brand sm:text-3xl">
                {String(idx + 1).padStart(2, "0")}
              </span>
              <div className="min-w-0 space-y-1">
                <div className="flex items-center gap-2.5">
                  <span className="rounded-pill bg-surface-elev px-2 py-0.5 font-mono text-[10px] uppercase tracking-widest text-muted">
                    {CATEGORY_META[item.category]?.eyebrow || item.category.toUpperCase()}
                  </span>
                  {item.stock > 0 && item.stock <= 5 && (
                    <span className="rounded-pill bg-rose-50 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-widest text-rose-500 dark:bg-rose-950">
                      仅剩 {item.stock}
                    </span>
                  )}
                </div>
                <div className="product-row__title">{item.title}</div>
                {item.subtitle && (
                  <div className="text-sm text-muted line-clamp-1">
                    {item.subtitle}
                  </div>
                )}
              </div>
              <div className="flex flex-col items-end gap-1.5">
                <div className="flex items-baseline gap-1.5 font-mono tabular-nums">
                  <span className="text-lg font-semibold text-ink sm:text-xl">
                    {formatPoints(item.pricePoints)}
                  </span>
                  <span className="text-[11px] uppercase tracking-widest text-faint">
                    pts
                  </span>
                </div>
                <span className="text-xs text-muted opacity-0 transition-opacity group-hover:opacity-100">
                  点击查看 →
                </span>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

function CategorySection({
  category,
  items,
}: {
  category: string;
  items: ProductCard[];
}) {
  const meta = CATEGORY_META[category] || { label: category, eyebrow: category.toUpperCase() };
  return (
    <section className="pt-16">
      <div className="section-title">
        <div className="space-y-3">
          <Eyebrow>{meta.eyebrow}</Eyebrow>
          <h2 className="text-2xl font-bold tracking-tight">{meta.label}</h2>
        </div>
      </div>

      <div
        className={cn(
          "grid gap-6 sm:gap-8",
          items.length >= 2 ? "md:grid-cols-[2fr_1fr]" : "md:grid-cols-1"
        )}
      >
        {items.slice(0, 3).map((item, idx) => (
          <Link
            key={item.slug}
            href={`/products/${item.slug}`}
            className={cn(
              "group relative block overflow-hidden rounded-3xl transition-transform duration-500 hover:-translate-y-1",
              idx === 0
                ? "aspect-[16/10] md:aspect-auto md:min-h-[320px]"
                : "aspect-[16/9] md:aspect-auto"
            )}
            style={{
              background: item.coverGradient || "linear-gradient(135deg, #f0f0f3, #d4d4d8)",
            }}
          >
            <div
              className="absolute inset-0 bg-gradient-to-t from-black/45 via-transparent to-transparent"
              aria-hidden
            />
            <div className="absolute inset-0 flex flex-col justify-end gap-2 p-6 text-white sm:p-8">
              <div className="text-xs uppercase tracking-widest opacity-75">
                {CATEGORY_META[item.category]?.eyebrow || item.category.toUpperCase()}
              </div>
              <h3 className="text-xl font-bold tracking-tight sm:text-2xl">
                {item.title}
              </h3>
              <div className="flex items-baseline gap-1.5 font-mono tabular-nums">
                <span className="text-2xl font-bold">
                  {formatPoints(item.pricePoints)}
                </span>
                <span className="text-xs uppercase tracking-widest opacity-80">
                  pts
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}

function EmptyState() {
  return (
    <section className="mt-24 animate-fade-in text-center">
      <Eyebrow tone="brand" className="justify-center">stocking · 上新中</Eyebrow>
      <h2 className="mt-4 text-3xl font-bold tracking-tight sm:text-4xl">
        商品准备中。
      </h2>
      <p className="mx-auto mt-4 max-w-md text-muted">
        商城刚搭好不久，新品正在录入。运营组很快会把第一批奖励放上来——保持关注就好。
      </p>
    </section>
  );
}

function JoinCta() {
  return (
    <section className="mt-32 animate-fade-in">
      <div className="relative overflow-hidden rounded-3xl px-8 py-16 text-center sm:px-16 sm:py-24">
        <div
          className="absolute inset-0 -z-10"
          style={{
            background:
              "radial-gradient(circle at 50% 0%, var(--zens-yellow-glow) 0%, transparent 50%)",
          }}
          aria-hidden
        />
        <Eyebrow tone="brand" className="justify-center">
          join · 加入 zens
        </Eyebrow>
        <h2 className="mt-4 text-3xl font-bold tracking-tight text-balance sm:text-4xl">
          你还没登录 —— 而你的积分可能正在攒。
        </h2>
        <p className="mx-auto mt-4 max-w-lg text-muted text-pretty">
          用你的 Zens 社区账号登录，看看自己存了多少分，能换些什么。
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link href="/login" className="btn-brand h-11 px-6 text-[15px]">
            使用 Zens 账号登录
          </Link>
        </div>
      </div>
    </section>
  );
}

function Hr({ className }: { className?: string }) {
  return <div className={cn("h-px w-full bg-divider", className)} aria-hidden />;
}
