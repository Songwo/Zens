import { NextRequest, NextResponse } from "next/server";
import { requireAdmin } from "@/lib/auth/admin-guard";
import { prisma } from "@/lib/db";
import { creditPoints, MainSiteApiError, sendSubsiteEvent } from "@/lib/main-site/client";

interface RouteCtx {
  params: Promise<{ id: string }>;
}

/**
 * POST /api/admin/orders/{id}/refund
 *
 * 把订单标记 REFUNDED 并把积分原路返还到主站。
 * 限制:
 *   - 只有 DELIVERED 状态可退; PENDING/FAILED 订单通常没有完成权益发放,由下单自动补偿处理
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
    return NextResponse.json({
      ok: true,
      refundedPoints: order.pricePoints,
      orderId: order.id,
      note: "订单已退款",
    });
  }
  if (order.status !== "DELIVERED") {
    return NextResponse.json(
      { error: "INVALID_STATE", message: `当前状态 ${order.status} 不可退款` },
      { status: 409 }
    );
  }

  // 1) 主站返还积分
  const idempotencyKey = "shop:refund:" + order.id;
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

  try {
    await sendSubsiteEvent({
      eventId: `shop:order:${order.id}:manual-refunded`,
      source: "zdc-shop",
      eventType: "shop.order.manual_refunded",
      userId: order.userId,
      title: "商城订单已退款",
      content: `订单 ${order.id.slice(0, 8)} 已退回 ${order.pricePoints} pts 到你的主站积分账户。`,
      relatedId: `zdc-shop:order:${order.id}`,
      severity: "warning",
      status: "refunded",
      notifyUser: true,
      payload: {
        orderId: order.id,
        pricePoints: order.pricePoints,
        refundedBy: guard.session?.userId || "admin",
      },
    });
  } catch (e) {
    console.warn("[admin/refund] 主站事件回流失败:", (e as Error).message);
  }

  return NextResponse.json({
    ok: true,
    refundedPoints: order.pricePoints,
    orderId: order.id,
  });
}
