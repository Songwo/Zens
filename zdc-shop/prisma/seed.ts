/* eslint-disable @typescript-eslint/no-explicit-any */
import { PrismaClient } from "@prisma/client";
import { randomBytes } from "node:crypto";

const prisma = new PrismaClient();

interface SeedProduct {
  slug: string;
  title: string;
  subtitle: string;
  description: string;
  category: "ai" | "badge" | "welfare" | "domain" | "theme";
  pricePoints: number;
  stock: number;
  limitPerUser: number;
  sortWeight: number;
  highlight: boolean;
  thisWeek: boolean;
  fulfillment: "CODE" | "SOFT";
  coverGradient: string;
  coverUrl: string;
}

const PRODUCTS: SeedProduct[] = [
  {
    slug: "cf-domain-coupon",
    title: "Cloudflare 域名 1 年券",
    subtitle: "限量 12 张 · 老社员优先",
    description: `这是一张 Cloudflare 注册域名一年的代金券，由 Zens 社区运营专项支出兑付。

可用于:
- 新注册 .com / .net / .org / .me / .dev 等主流后缀
- 已注册域名的续费（一年）
- 暂不支持转入

兑换流程:
1. 在此页面用 680 积分换取兑换码
2. 私信「Zens 运营组」并附上兑换码
3. 运营组核验后给你打款 / 直接帮你注册

⚠️ 数量有限，先到先得。`,
    category: "domain",
    pricePoints: 680,
    stock: 12,
    limitPerUser: 1,
    sortWeight: 100,
    highlight: true,
    thisWeek: false,
    fulfillment: "CODE",
    coverGradient: "linear-gradient(135deg, #fff7d6 0%, #f4b400 60%, #d89b00 100%)",
    coverUrl: "/products/cf-domain-coupon.svg",
  },
  {
    slug: "ai-call-50",
    title: "AI 调用券 · 50 次",
    subtitle: "用于站内 AI 摘要、对话与翻译",
    description: `兑换后将在你的账户上一次性补充 50 次 AI 调用额度。

适用场景:
- AI 自动生成帖子摘要
- 评论 AI 润色
- 站内 AI 翻译

额度永不过期，但会按调用顺序消耗。`,
    category: "ai",
    pricePoints: 120,
    stock: -1,
    limitPerUser: 10,
    sortWeight: 90,
    highlight: false,
    thisWeek: true,
    fulfillment: "CODE",
    coverGradient: "linear-gradient(135deg, #fff7d6 0%, #f4b400 100%)",
    coverUrl: "/products/ai-call-50.svg",
  },
  {
    slug: "zens-badge-early",
    title: "Zens 限定徽章 · 早期社员",
    subtitle: "永久挂载在你的主页",
    description: `「早期社员」徽章是 Zens 社区在 2026 年的限定标识，只有少量首批用户能换到。

特性:
- 永久有效，不可转让
- 显示在你的个人资料卡 + 头像角标
- 评论/帖子右上会出现暗金色 ★

未来会推出"开荒社员""百帖之友"等限定徽章。`,
    category: "badge",
    pricePoints: 80,
    stock: -1,
    limitPerUser: 1,
    sortWeight: 85,
    highlight: false,
    thisWeek: true,
    fulfillment: "SOFT",
    coverGradient: "linear-gradient(135deg, #fff6d6 0%, #d89b00 100%)",
    coverUrl: "/products/badge-early.svg",
  },
  {
    slug: "anon-question-10",
    title: "匿名提问额度 +10",
    subtitle: "一次性发放到账户",
    description: `匿名提问让你在不暴露 ID 的情况下，向社区任意成员发起问题。

兑换后你的账户会立即增加 10 条匿名提问额度。

适用场景:
- 想问敏感问题但不想被关联
- 向版主咨询某条规则的细节
- 给作者递私信但不希望进入聊天列表`,
    category: "welfare",
    pricePoints: 40,
    stock: -1,
    limitPerUser: 5,
    sortWeight: 80,
    highlight: false,
    thisWeek: true,
    fulfillment: "SOFT",
    coverGradient: "linear-gradient(135deg, #e8eef9 0%, #c2d4f0 100%)",
    coverUrl: "/products/anon-question.svg",
  },
  {
    slug: "theme-skin-night",
    title: "个人主题皮肤 · 夜屿",
    subtitle: "限定深色配色",
    description: `「夜屿」是 Zens 设计组在 2026 年春发布的限定主题皮肤，深邃蓝黑底搭配琥珀色高光。

兑换后:
- 自动应用到你的个人资料卡
- 在评论区也会显示对应配色
- 你可以随时切换回默认主题

不影响他人观感，纯个人装饰。`,
    category: "theme",
    pricePoints: 200,
    stock: -1,
    limitPerUser: 1,
    sortWeight: 70,
    highlight: false,
    thisWeek: false,
    fulfillment: "SOFT",
    coverGradient: "linear-gradient(135deg, #2a2a30 0%, #0a0a0b 100%)",
    coverUrl: "/products/theme-night.svg",
  },
  {
    slug: "top-post-3day",
    title: "帖子置顶 · 3 日",
    subtitle: "板块内自由选时段",
    description: `兑换后你将获得一张「3 日置顶券」，可在任意板块内挂顶置你的一篇帖子。

使用约束:
- 帖子必须已通过审核
- 不可叠加（同一板块同时只能有一个置顶）
- 起止时段可自由选择

兑换后请联系板块版主或运营组激活。`,
    category: "welfare",
    pricePoints: 320,
    stock: 50,
    limitPerUser: 3,
    sortWeight: 60,
    highlight: false,
    thisWeek: false,
    fulfillment: "CODE",
    coverGradient: "linear-gradient(135deg, #fef3c7 0%, #f59e0b 100%)",
    coverUrl: "/products/top-post.svg",
  },
];

function generateCode(slug: string): string {
  return `ZENS-${slug.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 6)}-${randomBytes(4)
    .toString("hex")
    .toUpperCase()}`;
}

async function main() {
  console.log("[seed] 开始灌入种子数据…");

  let createdProducts = 0;
  let createdCodes = 0;

  for (const p of PRODUCTS) {
    const existing = await prisma.product.findUnique({ where: { slug: p.slug } });
    if (existing) {
      console.log(`[seed]  · 已存在,跳过: ${p.slug}`);
      continue;
    }
    const product = await prisma.product.create({
      data: {
        slug: p.slug,
        title: p.title,
        subtitle: p.subtitle,
        description: p.description,
        coverUrl: p.coverUrl,
        coverGradient: p.coverGradient,
        category: p.category,
        pricePoints: p.pricePoints,
        stock: p.stock,
        limitPerUser: p.limitPerUser,
        sortWeight: p.sortWeight,
        highlight: p.highlight,
        thisWeek: p.thisWeek,
        fulfillment: p.fulfillment,
        status: "ACTIVE",
      },
    });
    createdProducts++;

    // 仅给 CODE 类商品生成兑换码
    if (p.fulfillment === "CODE") {
      const count = p.stock === -1 ? 20 : Math.max(p.stock, 20);
      const codes = Array.from({ length: count }).map(() => ({
        productId: product.id,
        code: generateCode(p.slug),
      }));
      await prisma.redemptionCode.createMany({ data: codes, skipDuplicates: true });
      createdCodes += count;
    }
    console.log(`[seed]  + ${p.slug} (${p.pricePoints} pts)`);
  }

  console.log(`[seed] 完成 · 新增商品 ${createdProducts} 个,兑换码 ${createdCodes} 条`);
}

main()
  .catch((e) => {
    console.error("[seed] 失败:", e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
