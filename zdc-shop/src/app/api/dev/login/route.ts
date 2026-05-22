import { NextRequest, NextResponse } from "next/server";
import { getMutableSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";

/**
 * !! DEV ONLY !! 一键模拟登录,绕开 SSO,仅用于预览 /admin / /orders 等需要登录的页面。
 *
 * NODE_ENV === 'production' 时本路由直接 404。
 *
 * 用法:
 *   GET /api/dev/login?role=ROLE_ADMIN&username=preview-admin
 *   GET /api/dev/login           (默认管理员)
 *   GET /api/dev/login?role=ROLE_USER  (普通用户)
 */
export async function GET(req: NextRequest) {
  if (process.env.NODE_ENV === "production") {
    return NextResponse.json({ error: "NOT_FOUND" }, { status: 404 });
  }

  const url = new URL(req.url);
  const role = url.searchParams.get("role") || "ROLE_ADMIN";
  const username = url.searchParams.get("username") || "preview-admin";
  const next = url.searchParams.get("next") || "/admin";

  const fakeUserId = "dev-" + Buffer.from(username).toString("hex").slice(0, 16);

  try {
    // 给个真实的 userId,这样下单流程也能 mock 走
    await prisma.userSync.upsert({
      where: { userId: fakeUserId },
      create: {
        userId: fakeUserId,
        username,
        nickname: username + " (预览)",
        role,
        avatar: null,
        email: null,
      },
      update: { username, role, lastSyncAt: new Date() },
    });
  } catch (e) {
    console.warn("[dev/login] upsert userSync 失败 (可忽略):", (e as Error).message);
  }

  const session = await getMutableSession();
  session.userId = fakeUserId;
  session.username = username;
  session.nickname = username + " (预览)";
  session.avatar = null;
  session.email = null;
  session.role = role;
  session.level = 1;
  session.issuedAt = Math.floor(Date.now() / 1000);
  await session.save();

  // 跳到目标页 (默认 /admin)
  const safeNext = next.startsWith("/") ? next : "/admin";
  return NextResponse.redirect(new URL(safeNext, req.url));
}
