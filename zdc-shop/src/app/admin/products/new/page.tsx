import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Eyebrow } from "@/components/editorial/eyebrow";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { ProductForm } from "@/components/admin/product-form";
import { getSession } from "@/lib/auth/session";
import { fetchUserPointsSafe } from "@/lib/products";

export const metadata = { title: "新建商品" };
export const dynamic = "force-dynamic";

const ADMIN_ROLES = new Set(["ROLE_ADMIN", "ROLE_SUPER_ADMIN"]);

export default async function NewProductPage() {
  const session = await getSession();
  if (!session) redirect("/login?from=/admin/products/new");
  if (!ADMIN_ROLES.has(session.role)) notFound();

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
          <Eyebrow tone="brand">new · 新建商品</Eyebrow>
          <h1 className="mt-4 max-w-[16ch] text-display font-extrabold tracking-tightest text-ink text-balance">
            上一件新商品。
          </h1>
          <p className="mt-4 max-w-xl text-muted">
            填好基础信息和封面,保存后即可在前台展示。需要兑换码的商品创建后再去
            <Link href="/admin?tab=codes" className="ml-1 underline-offset-4 hover:underline">兑换码池</Link>
            批量导入。
          </p>
        </section>

        <div className="mt-12 border-t border-divider pt-12">
          <ProductForm mode="create" />
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
