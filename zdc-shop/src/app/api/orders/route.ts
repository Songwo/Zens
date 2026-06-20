import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { getSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";
import {
  consumePoints,
  creditPoints,
  MainSiteApiError,
  sendSubsiteEvent,
} from "@/lib/main-site/client";

const POST_SCHEMA = z.object({
  productSlug: z.string().min(1, "productSlug 必填"),
  // 客户端生成的幂等 key,服务端同时校验,避免重复扣减
  idempotencyKey: z.string().min(8).max(100),
});

/**
 * POST /api/orders   下单 + 扣积分
 *
 * 流程:
 *   1. 校验登录 + body
 *   2. 找商品 / 检查在售、库存、单人上限
 *   3. 在事务里:
 *      a. INSERT zs_order(PENDING, idempotencyKey)
 *      b. 若是 CODE 类商品,锁一条未消费的 RedemptionCode (FOR UPDATE 风格,Prisma 用 update 自带)
 *   4. 调主站 /api/internal/user/{id}/points/consume (HMAC s2s, 同 idempotencyKey)
 *      - 失败 → 把订单标记 FAILED, 把刚锁的兑换码释放
 *   5. 成功 → 订单标记 DELIVERED, 兑换码记录 consumedBy / consumedAt
 *   6. 返回订单详情 (含兑换码)
 */
export async function POST(req: NextRequest) {
  const session = await getSession();
  if (!session) {
    return NextResponse.json(
      { error: "UNAUTHENTICATED", message: "请先登录" },
      { status: 401 }
    );
  }

  let parsed;
  try {
    parsed = POST_SCHEMA.safeParse(await req.json());
  } catch {
    return NextResponse.json(
      { error: "INVALID_BODY", message: "请求体不是合法 JSON" },
      { status: 400 }
    );
  }
  if (!parsed.success) {
    return NextResponse.json(
      {
        error: "INVALID_BODY",
        message: parsed.error.issues.map((i) => i.message).join("; "),
      },
      { status: 400 }
    );
  }
  const { productSlug, idempotencyKey } = parsed.data;

  // ── 幂等: 已有同 idempotencyKey 订单则直接返回 ────────────────────
  const existing = await prisma.order.findUnique({ where: { idempotencyKey } });
  if (existing) {
    if (existing.userId !== session.userId) {
      return NextResponse.json(
        { error: "IDEMPOTENT_KEY_REUSE", message: "幂等 key 与其它用户冲突" },
        { status: 409 }
      );
    }
    return NextResponse.json({ ok: true, order: serializeOrder(existing) });
  }

  // ── 商品 ────────────────────────────────────────────────────────
  const product = await prisma.product.findUnique({ where: { slug: productSlug } });
  if (!product) {
    return NextResponse.json({ error: "PRODUCT_NOT_FOUND" }, { status: 404 });
  }
  if (product.status !== "ACTIVE") {
    return NextResponse.json({ error: "PRODUCT_INACTIVE" }, { status: 409 });
  }
  if (product.stock === 0) {
    return NextResponse.json({ error: "SOLD_OUT", message: "已售罄" }, { status: 409 });
  }
  if (product.fulfillment === "CODE") {
    const availableCodes = await prisma.redemptionCode.count({
      where: { productId: product.id, consumedBy: null },
    });
    if (availableCodes <= 0) {
      return NextResponse.json(
        { error: "CODE_EXHAUSTED", message: "兑换码库存已耗尽,请稍后再试" },
        { status: 409 }
      );
    }
  }

  // 单人限购
  const userCount = await prisma.order.count({
    where: {
      userId: session.userId,
      productId: product.id,
      status: { in: ["PENDING", "DELIVERED"] },
    },
  });
  if (userCount >= product.limitPerUser) {
    return NextResponse.json(
      { error: "LIMIT_REACHED", message: `已达单人上限 (${product.limitPerUser})` },
      { status: 409 }
    );
  }

  // ── 创建 PENDING 订单 ─────────────────────────────────────────
  let order = await prisma.order.create({
    data: {
      userId: session.userId,
      username: session.username,
      productId: product.id,
      pricePoints: product.pricePoints,
      status: "PENDING",
      idempotencyKey,
    },
  });

  // ── 调主站扣积分 ───────────────────────────────────────────────
  try {
    await consumePoints(session.userId, {
      amount: product.pricePoints,
      reason: product.slug,
      orderId: order.id,
      idempotencyKey,
    });
  } catch (e) {
    const msg = e instanceof MainSiteApiError ? e.message : (e as Error).message;
    const code =
      e instanceof MainSiteApiError ? e.code : "MAIN_SITE_UNREACHABLE";
    order = await prisma.order.update({
      where: { id: order.id },
      data: { status: "FAILED", failureReason: code + ": " + msg },
    });
    const status =
      e instanceof MainSiteApiError && e.code === "INSUFFICIENT_POINTS"
        ? 402
        : 502;
    return NextResponse.json(
      {
        error: code,
        message: msg,
        orderId: order.id,
      },
      { status }
    );
  }

  try {
    if (product.fulfillment === "CODE") {
      order = await prisma.$transaction(async (tx) => {
        const code = await tx.redemptionCode.findFirst({
          where: { productId: product.id, consumedBy: null },
          orderBy: { createdAt: "asc" },
        });
        if (!code) {
          throw new Error("CODE_EXHAUSTED: 兑换码库存被并发耗尽");
        }

        const claimResult = await tx.redemptionCode.updateMany({
          where: { id: code.id, consumedBy: null },
          data: { consumedBy: session.userId, consumedAt: new Date() },
        });
        if (claimResult.count !== 1) {
          throw new Error("CODE_DELIVERY_CONFLICT: 兑换码并发发放失败");
        }

        if (product.stock > 0) {
          const stockResult = await tx.product.updateMany({
            where: { id: product.id, stock: { gt: 0 } },
            data: { stock: { decrement: 1 } },
          });
          if (stockResult.count !== 1) {
            throw new Error("STOCK_CONFLICT: 商品库存被并发耗尽");
          }
        }

        return tx.order.update({
          where: { id: order.id },
          data: {
            status: "DELIVERED",
            deliveredCode: code.code,
          },
        });
      });
    } else {
      // SOFT: 不发兑换码,直接 DELIVERED,实际权益可由主站/运营后台继续完成。
      order = await prisma.$transaction(async (tx) => {
        if (product.stock > 0) {
          const stockResult = await tx.product.updateMany({
            where: { id: product.id, stock: { gt: 0 } },
            data: { stock: { decrement: 1 } },
          });
          if (stockResult.count !== 1) {
            throw new Error("STOCK_CONFLICT: 商品库存被并发耗尽");
          }
        }

        return tx.order.update({
          where: { id: order.id },
          data: { status: "DELIVERED" },
        });
      });
    }
  } catch (e) {
    const deliveryMessage = e instanceof Error ? e.message : "DELIVERY_FAILED";
    const refundResult = await refundConsumedPoints({
      userId: session.userId,
      amount: product.pricePoints,
      productSlug: product.slug,
      orderId: order.id,
      idempotencyKey,
      deliveryMessage,
    });

    order = await prisma.order.update({
      where: { id: order.id },
      data: {
        status: refundResult.refunded ? "REFUNDED" : "FAILED",
        failureReason: refundResult.refunded
          ? `${deliveryMessage}; 已自动退回 ${product.pricePoints} pts`
          : `${deliveryMessage}; 自动退款失败: ${refundResult.message}`,
      },
    });

    await notifyOrderEvent({
      userId: session.userId,
      eventId: refundResult.refunded
        ? `shop:order:${order.id}:auto-refunded`
        : `shop:order:${order.id}:delivery-failed`,
      eventType: refundResult.refunded ? "shop.order.auto_refunded" : "shop.order.delivery_failed",
      status: refundResult.refunded ? "refunded" : "manual_required",
      severity: refundResult.refunded ? "warning" : "danger",
      title: refundResult.refunded ? "商城兑换已自动退款" : "商城兑换需要人工处理",
      content: refundResult.refunded
        ? `「${product.title}」发放失败,${product.pricePoints} pts 已退回你的主站积分账户。`
        : `「${product.title}」发放失败且自动退款未完成,请联系运营处理。`,
      orderId: order.id,
      payload: {
        productSlug: product.slug,
        productTitle: product.title,
        pricePoints: product.pricePoints,
        deliveryMessage,
        refundMessage: refundResult.message,
      },
    });

    return NextResponse.json(
      {
        error: "DELIVERY_FAILED",
        message: refundResult.refunded
          ? "权益发放失败,系统已自动退回积分。"
          : "权益发放失败,自动退款也失败,请联系运营处理。",
        orderId: order.id,
      },
      { status: refundResult.refunded ? 409 : 502 }
    );
  }

  await notifyOrderEvent({
    userId: session.userId,
    eventId: `shop:order:${order.id}:delivered`,
    eventType: "shop.order.delivered",
    status: "delivered",
    severity: "success",
    title: "商城兑换成功",
    content: `你已成功兑换「${product.title}」,消耗 ${product.pricePoints} pts。`,
    orderId: order.id,
    payload: {
      productSlug: product.slug,
      productTitle: product.title,
      pricePoints: product.pricePoints,
      fulfillment: product.fulfillment,
    },
  });

  return NextResponse.json({ ok: true, order: serializeOrder(order) });
}

async function refundConsumedPoints({
  userId,
  amount,
  productSlug,
  orderId,
  idempotencyKey,
  deliveryMessage,
}: {
  userId: string;
  amount: number;
  productSlug: string;
  orderId: string;
  idempotencyKey: string;
  deliveryMessage: string;
}): Promise<{ refunded: boolean; message: string }> {
  try {
    await creditPoints(userId, {
      amount,
      reason: `shop:auto-refund:${productSlug}`,
      orderId,
      idempotencyKey: `${idempotencyKey}:auto-refund`,
    });
    return { refunded: true, message: "REFUNDED" };
  } catch (e) {
    const msg = e instanceof MainSiteApiError ? `${e.code}: ${e.message}` : (e as Error).message;
    console.error("[orders] 自动退款失败", { orderId, userId, deliveryMessage, refundError: msg });
    return { refunded: false, message: msg };
  }
}

async function notifyOrderEvent(payload: {
  userId: string;
  eventId: string;
  eventType: string;
  status: string;
  severity: "info" | "success" | "warning" | "danger" | "error" | "default";
  title: string;
  content: string;
  orderId: string;
  payload?: Record<string, unknown>;
}) {
  try {
    await sendSubsiteEvent({
      eventId: payload.eventId,
      source: "zdc-shop",
      eventType: payload.eventType,
      userId: payload.userId,
      title: payload.title,
      content: payload.content,
      relatedId: `zdc-shop:order:${payload.orderId}`,
      severity: payload.severity,
      status: payload.status,
      notifyUser: true,
      payload: payload.payload,
    });
  } catch (e) {
    console.warn("[orders] 主站事件回流失败:", (e as Error).message);
  }
}

/**
 * GET /api/orders   我的兑换历史
 */
export async function GET() {
  const session = await getSession();
  if (!session) {
    return NextResponse.json(
      { error: "UNAUTHENTICATED", message: "请先登录" },
      { status: 401 }
    );
  }
  const orders = await prisma.order.findMany({
    where: { userId: session.userId },
    orderBy: { createdAt: "desc" },
    include: {
      product: {
        select: { slug: true, title: true, subtitle: true, coverGradient: true, category: true },
      },
    },
  });
  return NextResponse.json({
    orders: orders.map((o) => ({
      ...serializeOrder(o),
      product: o.product,
    })),
  });
}

function serializeOrder(o: {
  id: string;
  userId: string;
  username: string;
  productId: string;
  pricePoints: number;
  status: string;
  idempotencyKey: string;
  deliveredCode: string | null;
  failureReason: string | null;
  createdAt: Date;
  updatedAt: Date;
}) {
  return {
    id: o.id,
    userId: o.userId,
    username: o.username,
    productId: o.productId,
    pricePoints: o.pricePoints,
    status: o.status,
    idempotencyKey: o.idempotencyKey,
    deliveredCode: o.deliveredCode,
    failureReason: o.failureReason,
    createdAt: o.createdAt.toISOString(),
    updatedAt: o.updatedAt.toISOString(),
  };
}
