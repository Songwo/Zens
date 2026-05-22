import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Eyebrow } from "@/components/editorial/eyebrow";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { ProductForm } from "@/components/admin/product-form";
import { getSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";
import { fetchUserPointsSafe } from "@/lib/products";

export const metadata = { title: "编辑商品" };
export const dynamic = "force-dynamic";

const ADMIN_ROLES = new Set(["ROLE_ADMIN", "ROLE_SUPER_ADMIN"]);

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function EditProductPage({ params }: PageProps) {
  const session = await getSession();
  if (!session) redirect("/login?from=/admin");
  if (!ADMIN_ROLES.has(session.role)) notFound();

  const { id } = await params;
  const product = await prisma.product.findUnique({
    where: { id },
    include: {
      _count: {
        select: {
          orders: true,
          codes: { where: { consumedBy: null } },
        },
      },
    },
  });
  if (!product) notFound();

  const points = await fetchUserPointsSafe(session.userId);

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

      <main className="editorial-container pb-16">
        <section className="animate-rise pt-16">
          <Eyebrow tone="brand">edit · 编辑商品</Eyebrow>
          <h1 className="mt-4 max-w-[20ch] text-display font-extrabold tracking-tightest text-ink text-balance">
            {product.title}
          </h1>
          <p className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-1 text-sm text-muted">
            <span>
              <span className="text-faint">slug:</span>{" "}
              <code className="font-mono text-ink-soft">{product.slug}</code>
            </span>
            <span>
              <span className="text-faint">订单数:</span>{" "}
              <span className="font-mono text-ink-soft">{product._count.orders}</span>
            </span>
            <span>
              <span className="text-faint">未消费兑换码:</span>{" "}
              <span className="font-mono text-ink-soft">{product._count.codes}</span>
            </span>
            <Link
              href={`/products/${product.slug}`}
              target="_blank"
              className="ml-auto inline-flex items-center gap-1 text-xs underline-offset-4 hover:text-ink hover:underline"
            >
              前台预览 ↗
            </Link>
          </p>
        </section>

        <div className="mt-12 border-t border-divider pt-12">
          <ProductForm
            mode="edit"
            productId={product.id}
            initial={{
              slug: product.slug,
              title: product.title,
              subtitle: product.subtitle || "",
              description: product.description,
              coverUrl: product.coverUrl,
              coverGradient: product.coverGradient || "",
              category: product.category as
                | "ai" | "badge" | "welfare" | "domain" | "theme",
              pricePoints: product.pricePoints,
              stock: product.stock,
              limitPerUser: product.limitPerUser,
              status: product.status as "ACTIVE" | "DRAFT" | "SOLDOUT",
              sortWeight: product.sortWeight,
              highlight: product.highlight,
              thisWeek: product.thisWeek,
              fulfillment: product.fulfillment as "CODE" | "SOFT",
            }}
          />
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
