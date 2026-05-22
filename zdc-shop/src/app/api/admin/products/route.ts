import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/admin-guard";
import { prisma } from "@/lib/db";

/** 商品 CRUD - 共用 schema */
const ProductBody = z.object({
  slug: z
    .string()
    .min(2)
    .max(64)
    .regex(/^[a-z0-9][a-z0-9-]*$/, "slug 仅允许小写字母、数字与短横线"),
  title: z.string().min(1).max(120),
  subtitle: z.string().max(200).optional().nullable(),
  description: z.string().min(1).max(8000),
  coverUrl: z.string().min(1).max(500),
  coverGradient: z.string().max(255).optional().nullable(),
  category: z.enum(["ai", "badge", "welfare", "domain", "theme"]),
  pricePoints: z.number().int().min(0).max(1_000_000),
  stock: z.number().int().min(-1).max(1_000_000), // -1 表示无限
  limitPerUser: z.number().int().min(1).max(100).default(1),
  status: z.enum(["ACTIVE", "DRAFT", "SOLDOUT"]).default("ACTIVE"),
  sortWeight: z.number().int().default(0),
  highlight: z.boolean().default(false),
  thisWeek: z.boolean().default(false),
  fulfillment: z.enum(["CODE", "SOFT"]).default("CODE"),
});

/** GET 全部商品列表 (admin 用,不限 status) */
export async function GET() {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const products = await prisma.product.findMany({
    orderBy: [{ status: "asc" }, { sortWeight: "desc" }, { createdAt: "desc" }],
  });
  return NextResponse.json({ products });
}

/** POST 新建商品 */
export async function POST(req: NextRequest) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;

  const parsed = ProductBody.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return NextResponse.json(
      {
        error: "INVALID_BODY",
        message: parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; "),
      },
      { status: 400 }
    );
  }

  // slug 唯一性
  const dup = await prisma.product.findUnique({ where: { slug: parsed.data.slug } });
  if (dup) {
    return NextResponse.json(
      { error: "SLUG_TAKEN", message: `slug "${parsed.data.slug}" 已被占用` },
      { status: 409 }
    );
  }

  const created = await prisma.product.create({
    data: {
      ...parsed.data,
      subtitle: parsed.data.subtitle ?? null,
      coverGradient: parsed.data.coverGradient ?? null,
    },
  });
  return NextResponse.json({ ok: true, product: created }, { status: 201 });
}

/** PATCH 局部更新 - 状态切换之类,字段全可选 */
export async function PATCH(req: NextRequest) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;

  const partial = ProductBody.partial().extend({
    productId: z.string().min(1),
  });
  const parsed = partial.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return NextResponse.json(
      {
        error: "INVALID_BODY",
        message: parsed.error.issues.map((i) => i.message).join("; "),
      },
      { status: 400 }
    );
  }
  const { productId, ...rest } = parsed.data;
  if (Object.keys(rest).length === 0) {
    return NextResponse.json({ error: "NO_CHANGES" }, { status: 400 });
  }
  const updated = await prisma.product.update({
    where: { id: productId },
    data: rest as Record<string, unknown>,
  });
  return NextResponse.json({ ok: true, product: updated });
}
