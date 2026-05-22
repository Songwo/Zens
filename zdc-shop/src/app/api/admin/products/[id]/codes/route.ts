import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/admin-guard";
import { prisma } from "@/lib/db";

const Body = z.object({
  codes: z
    .array(z.string().trim().min(2).max(100))
    .min(1, "至少传 1 个兑换码")
    .max(2000, "单次最多 2000 个"),
});

interface RouteCtx {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/admin/products/{id}/codes
 *  返回兑换码列表 (默认仅未消费),供管理面板用。
 */
export async function GET(req: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;
  const onlyUnused = new URL(req.url).searchParams.get("unused") !== "0";

  const codes = await prisma.redemptionCode.findMany({
    where: {
      productId: id,
      ...(onlyUnused ? { consumedBy: null } : {}),
    },
    orderBy: { createdAt: "asc" },
    take: 500,
  });
  return NextResponse.json({ codes });
}

/**
 * POST /api/admin/products/{id}/codes
 *  批量导入兑换码,自动去重。
 *  body: { codes: string[] }
 */
export async function POST(req: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;

  const product = await prisma.product.findUnique({ where: { id } });
  if (!product) {
    return NextResponse.json({ error: "PRODUCT_NOT_FOUND" }, { status: 404 });
  }
  if (product.fulfillment !== "CODE") {
    return NextResponse.json(
      {
        error: "PRODUCT_NOT_CODE_TYPE",
        message: "该商品 fulfillment 不是 CODE,无需兑换码池。改成 CODE 类后再导入。",
      },
      { status: 400 }
    );
  }

  const parsed = Body.safeParse(await req.json().catch(() => null));
  if (!parsed.success) {
    return NextResponse.json(
      {
        error: "INVALID_BODY",
        message: parsed.error.issues.map((i) => i.message).join("; "),
      },
      { status: 400 }
    );
  }
  // 去掉重复 + 空行
  const unique = Array.from(
    new Set(parsed.data.codes.map((c) => c.trim()).filter((c) => c.length > 0))
  );
  const created = await prisma.redemptionCode.createMany({
    data: unique.map((code) => ({ productId: id, code })),
    skipDuplicates: true,
  });
  return NextResponse.json({ ok: true, added: created.count, requested: unique.length });
}

/**
 * DELETE /api/admin/products/{id}/codes?codeId=xxx
 *  删除单条「未消费」兑换码; 已消费的拒绝删除 (留审计)。
 */
export async function DELETE(req: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;
  const codeId = new URL(req.url).searchParams.get("codeId");
  if (!codeId) {
    return NextResponse.json({ error: "MISSING_CODE_ID" }, { status: 400 });
  }
  const code = await prisma.redemptionCode.findFirst({
    where: { id: codeId, productId: id },
  });
  if (!code) {
    return NextResponse.json({ error: "NOT_FOUND" }, { status: 404 });
  }
  if (code.consumedBy) {
    return NextResponse.json(
      { error: "ALREADY_CONSUMED", message: "已被消费的兑换码不可删除" },
      { status: 409 }
    );
  }
  await prisma.redemptionCode.delete({ where: { id: codeId } });
  return NextResponse.json({ ok: true });
}
