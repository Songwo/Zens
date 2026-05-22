import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { getSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";
import { consumePoints, MainSiteApiError } from "@/lib/main-site/client";

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

  // ── 分发兑换码 / 软发放 ────────────────────────────────────────
  if (product.fulfillment === "CODE") {
    const code = await prisma.redemptionCode.findFirst({
      where: { productId: product.id, consumedBy: null },
      orderBy: { createdAt: "asc" },
    });
    if (!code) {
      // 兑换码已耗尽: 标记订单 FAILED, 但主站积分已扣 → 应当发起补偿(out of scope v1)
      order = await prisma.order.update({
        where: { id: order.id },
        data: {
          status: "FAILED",
          failureReason: "CODE_EXHAUSTED: 兑换码库存被耗尽,请联系运营组退款",
        },
      });
      return NextResponse.json(
        {
          error: "CODE_EXHAUSTED",
          message:
            "兑换码已被领完。已为你扣积分但暂未发放兑换码,请联系运营组退款或补发。",
          orderId: order.id,
        },
        { status: 503 }
      );
    }
    await prisma.redemptionCode.update({
      where: { id: code.id },
      data: { consumedBy: session.userId, consumedAt: new Date() },
    });
    order = await prisma.order.update({
      where: { id: order.id },
      data: {
        status: "DELIVERED",
        deliveredCode: code.code,
      },
    });
  } else {
    // SOFT: 不发兑换码,直接 DELIVERED, 实际发放由运营组在主站完成
    order = await prisma.order.update({
      where: { id: order.id },
      data: { status: "DELIVERED" },
    });
  }

  // 实物库存扣减 (CODE 类已经通过兑换码池间接控制了)
  if (product.stock > 0) {
    await prisma.product.update({
      where: { id: product.id },
      data: { stock: { decrement: 1 } },
    });
  }

  return NextResponse.json({ ok: true, order: serializeOrder(order) });
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
