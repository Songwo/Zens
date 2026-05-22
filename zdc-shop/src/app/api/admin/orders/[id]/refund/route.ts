import { NextRequest, NextResponse } from "next/server";
import { randomBytes } from "node:crypto";
import { requireAdmin } from "@/lib/auth/admin-guard";
import { prisma } from "@/lib/db";
import { creditPoints, MainSiteApiError } from "@/lib/main-site/client";

interface RouteCtx {
  params: Promise<{ id: string }>;
}

/**
 * POST /api/admin/orders/{id}/refund
 *
 * 把订单标记 REFUNDED 并把积分原路返还到主站。
 * 限制:
 *   - 只有 DELIVERED 或 PENDING 状态可退
 *   - 兑换码会标 consumedBy=null 重新可用(若有)
 *   - 退款幂等: idempotencyKey = "refund:" + orderId
 */
export async function POST(_: NextRequest, ctx: RouteCtx) {
  const guard = await requireAdmin();
  if (guard.error) return guard.error;
  const { id } = await ctx.params;

  const order = await prisma.order.findUnique({ where: { id } });
  if (!order) return NextResponse.json({ error: "NOT_FOUND" }, { status: 404 });
  if (order.status === "REFUNDED") {
    return NextResponse.json({ error: "ALREADY_REFUNDED" }, { status: 409 });
  }
  if (order.status !== "DELIVERED" && order.status !== "PENDING") {
    return NextResponse.json(
      { error: "INVALID_STATE", message: `当前状态 ${order.status} 不可退款` },
      { status: 409 }
    );
  }

  // 1) 主站返还积分
  const idempotencyKey = "refund:" + order.id + ":" + randomBytes(6).toString("hex");
  try {
    await creditPoints(order.userId, {
      amount: order.pricePoints,
      reason: "shop:refund",
      orderId: order.id,
      idempotencyKey,
    });
  } catch (e) {
    const msg = e instanceof MainSiteApiError ? e.message : (e as Error).message;
    return NextResponse.json(
      { error: "MAIN_SITE_FAIL", message: "主站返还积分失败: " + msg },
      { status: 502 }
    );
  }

  // 2) 释放兑换码 + 标记订单
  await prisma.$transaction([
    ...(order.deliveredCode
      ? [
          prisma.redemptionCode.updateMany({
            where: {
              productId: order.productId,
              code: order.deliveredCode,
            },
            data: { consumedBy: null, consumedAt: null },
          }),
        ]
      : []),
    prisma.order.update({
      where: { id: order.id },
      data: { status: "REFUNDED" },
    }),
    // 库存回滚
    prisma.product.updateMany({
      where: { id: order.productId, stock: { gte: 0 } },
      data: { stock: { increment: 1 } },
    }),
  ]);

  return NextResponse.json({
    ok: true,
    refundedPoints: order.pricePoints,
    orderId: order.id,
  });
}
