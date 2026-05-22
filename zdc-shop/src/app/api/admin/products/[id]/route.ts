import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/admin-guard";
import { prisma } from "@/lib/db";

const Body = z.object({
  slug: z.string().min(2).max(64).regex(/^[a-z0-9][a-z0-9-]*$/),
  title: z.string().min(1).max(120),
  subtitle: z.string().max(200).optional().nullable(),
  description: z.string().min(1).max(8000),
  coverUrl: z.string().min(1).max(500),
  coverGradient: z.string().max(255).optional().nullable(),
  category: z.enum(["ai", "badge", "welfare", "domain", "theme"]),
  pricePoints: z.number().int().min(0).max(1_000_000),
  stock: z.number().int().min(-1).max(1_000_000),
  limitPerUser: z.number().int().min(1).max(100),
  status: z.enum(["ACTIVE", "DRAFT", "SOLDOUT"]),
  sortWeight: z.number().int(),
  highlight: z.boolean(),
  thisWeek: z.boolean(),
  fulfillment: z.enum(["CODE", "SOFT"]),
});

interface RouteCtx {
  params: Promise<{ id: string }>;
}

/** GET 单个商品 (含未消费兑换码计数) */
export async function GET(_: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;
  const product = await prisma.product.findUnique({
    where: { id },
    include: {
      _count: { select: { codes: { where: { consumedBy: null } }, orders: true } },
    },
  });
  if (!product) {
    return NextResponse.json({ error: "NOT_FOUND" }, { status: 404 });
  }
  return NextResponse.json({ product });
}

/** PUT 全量更新 */
export async function PUT(req: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;

  const parsed = Body.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return NextResponse.json(
      {
        error: "INVALID_BODY",
        message: parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; "),
      },
      { status: 400 }
    );
  }

  // slug 冲突检测(可改自己的 slug,但不能撞别人的)
  const dup = await prisma.product.findFirst({
    where: { slug: parsed.data.slug, NOT: { id } },
  });
  if (dup) {
    return NextResponse.json(
      { error: "SLUG_TAKEN", message: `slug "${parsed.data.slug}" 已被占用` },
      { status: 409 }
    );
  }

  const updated = await prisma.product.update({
    where: { id },
    data: {
      ...parsed.data,
      subtitle: parsed.data.subtitle ?? null,
      coverGradient: parsed.data.coverGradient ?? null,
    },
  });
  return NextResponse.json({ ok: true, product: updated });
}

/**
 * DELETE 删除商品。
 * 安全策略: 若已有任何订单,默认拒绝。除非 query 带 ?force=1。
 * - force=1: 同时删除关联订单(谨慎!) 与未消费兑换码; 已消费的兑换码不动 (留审计)
 */
export async function DELETE(req: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;

  const force = new URL(req.url).searchParams.get("force") === "1";

  const orderCount = await prisma.order.count({ where: { productId: id } });
  if (orderCount > 0 && !force) {
    return NextResponse.json(
      {
        error: "HAS_ORDERS",
        message: `此商品已有 ${orderCount} 笔订单,默认拒绝删除。若确认要级联删除,请在请求 URL 加 ?force=1 (会同时删订单与未消费兑换码)。`,
        orderCount,
      },
      { status: 409 }
    );
  }

  await prisma.$transaction([
    // 未消费兑换码: 删
    prisma.redemptionCode.deleteMany({ where: { productId: id, consumedBy: null } }),
    // 已消费的兑换码: 保留(留审计,后续可手动清理)
    ...(force
      ? [
          prisma.order.deleteMany({ where: { productId: id } }),
          // 已消费的兑换码也强删
          prisma.redemptionCode.deleteMany({ where: { productId: id } }),
        ]
      : []),
    prisma.product.delete({ where: { id } }),
  ]);

  return NextResponse.json({ ok: true, deletedOrders: force ? orderCount : 0 });
}
