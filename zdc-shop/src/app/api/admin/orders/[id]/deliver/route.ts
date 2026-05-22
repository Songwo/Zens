import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/admin-guard";
import { prisma } from "@/lib/db";

interface RouteCtx {
  params: Promise<{ id: string }>;
}

/**
 * POST /api/admin/orders/{id}/deliver
 *
 * 用于 SOFT 类商品(徽章/主题等无兑换码)在运营手动配置完后,把订单标为 DELIVERED。
 * 也可强制把 PENDING 推到 DELIVERED,但需要传 force=true 显式确认。
 */
const Body = z.object({
  force: z.boolean().default(false),
  code: z.string().max(200).optional(), // 也可手动塞个兑换码
});

export async function POST(req: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;
  const parsed = Body.safeParse(await req.json().catch(() => ({})));
  const force = parsed.success ? parsed.data.force : false;
  const manualCode = parsed.success ? parsed.data.code : undefined;

  const order = await prisma.order.findUnique({
    where: { id },
    include: { product: true },
  });
  if (!order) return NextResponse.json({ error: "NOT_FOUND" }, { status: 404 });

  if (order.status === "DELIVERED") {
    return NextResponse.json({ ok: true, order, note: "已是 DELIVERED" });
  }

  if (order.status !== "PENDING" && order.status !== "FAILED") {
    return NextResponse.json(
      { error: "INVALID_STATE", message: `当前状态 ${order.status} 不可手动发放` },
      { status: 409 }
    );
  }
  if (order.status === "FAILED" && !force) {
    return NextResponse.json(
      {
        error: "REQUIRE_FORCE",
        message: "失败订单需带 force=true 才能手动改为已发放(确认你已外部补偿用户)",
      },
      { status: 409 }
    );
  }

  const updated = await prisma.order.update({
    where: { id: order.id },
    data: {
      status: "DELIVERED",
      deliveredCode: manualCode ?? order.deliveredCode,
      failureReason: null,
    },
  });
  return NextResponse.json({ ok: true, order: updated });
}
