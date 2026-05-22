import { prisma } from "@/lib/db";

export interface ProductCard {
  id: string;
  slug: string;
  title: string;
  subtitle: string | null;
  category: string;
  pricePoints: number;
  stock: number;
  coverUrl: string;
  coverGradient: string | null;
  highlight: boolean;
  thisWeek: boolean;
}

export interface ProductDetail extends ProductCard {
  description: string;
  limitPerUser: number;
  fulfillment: string;
  status: string;
}

/** 首页所有展示位 (Featured + This Week + 分类) 在一次查询里取齐, 客户端再拆分。 */
export async function fetchHomeSections(): Promise<{
  featured: ProductCard | null;
  thisWeek: ProductCard[];
  byCategory: Record<string, ProductCard[]>;
  total: number;
}> {
  const products = await prisma.product.findMany({
    where: { status: "ACTIVE" },
    orderBy: [{ sortWeight: "desc" }, { createdAt: "desc" }],
  });

  const cards: ProductCard[] = products.map(toCard);
  const featured =
    cards.find((p) => p.highlight) ?? cards[0] ?? null;
  const thisWeek = cards
    .filter((p) => p.thisWeek && p.slug !== featured?.slug)
    .slice(0, 5);

  const byCategory: Record<string, ProductCard[]> = {};
  for (const p of cards) {
    if (p.slug === featured?.slug) continue;
    if (thisWeek.some((t) => t.slug === p.slug)) continue;
    if (!byCategory[p.category]) byCategory[p.category] = [];
    byCategory[p.category].push(p);
  }

  return { featured, thisWeek, byCategory, total: cards.length };
}

export async function fetchProductBySlug(slug: string): Promise<ProductDetail | null> {
  const p = await prisma.product.findUnique({ where: { slug } });
  if (!p || p.status !== "ACTIVE") return null;
  return {
    ...toCard(p),
    description: p.description,
    limitPerUser: p.limitPerUser,
    fulfillment: p.fulfillment,
    status: p.status,
  };
}

/** 某商品当前对单个用户的"已兑换次数"与"剩余可兑换"。 */
export async function fetchPurchaseStats(productId: string, userId: string) {
  const [delivered, available] = await Promise.all([
    prisma.order.count({
      where: { productId, userId, status: { in: ["DELIVERED", "PENDING"] } },
    }),
    prisma.redemptionCode.count({ where: { productId, consumedBy: null } }),
  ]);
  return { userOrderCount: delivered, codeRemain: available };
}

function toCard(p: Awaited<ReturnType<typeof prisma.product.findUnique>>): ProductCard {
  if (!p) throw new Error("product null");
  return {
    id: p.id,
    slug: p.slug,
    title: p.title,
    subtitle: p.subtitle,
    category: p.category,
    pricePoints: p.pricePoints,
    stock: p.stock,
    coverUrl: p.coverUrl,
    coverGradient: p.coverGradient,
    highlight: p.highlight,
    thisWeek: p.thisWeek,
  };
}

/** 安全地拉一次用户的实时积分。失败时返回 null,前端显示「—」。 */
export async function fetchUserPointsSafe(userId: string | null | undefined): Promise<number | null> {
  if (!userId) return null;
  try {
    const { fetchPoints } = await import("@/lib/main-site/client");
    const info = await fetchPoints(userId);
    return info.points;
  } catch (e) {
    console.warn("[fetchUserPointsSafe] 主站积分查询失败:", (e as Error).message);
    return null;
  }
}

export const CATEGORY_META: Record<string, { label: string; eyebrow: string }> = {
  ai: { label: "AI 工具", eyebrow: "AI · TOOLS" },
  badge: { label: "社区徽章", eyebrow: "BADGES" },
  welfare: { label: "社区福利", eyebrow: "WELFARE" },
  domain: { label: "域名 / 网络", eyebrow: "DOMAIN" },
  theme: { label: "外观主题", eyebrow: "THEMES" },
};
