import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { verifySsoToken, SsoVerificationError } from "@/lib/auth/sso";
import { getMutableSession } from "@/lib/auth/session";
import { prisma } from "@/lib/db";

/**
 * 子站消化主站颁发的 SSO Token,签发本站会话(iron-session cookie)。
 * 与主站 SsoController 颁发 token 的逻辑严格对应。
 */

const Schema = z.object({
  ssoToken: z.string().min(20, "ssoToken 不合法"),
});

export async function POST(req: NextRequest) {
  let parsed;
  try {
    const body = await req.json();
    parsed = Schema.safeParse(body);
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

  const { ssoToken } = parsed.data;

  let claims;
  try {
    claims = await verifySsoToken(ssoToken);
  } catch (e) {
    if (e instanceof SsoVerificationError) {
      return NextResponse.json(
        { error: e.code, message: e.message },
        { status: 401 }
      );
    }
    // jose 的 JWTExpired / JWSSignatureVerificationFailed 等
    const err = e as Error & { code?: string };
    const code = err.code || "INVALID_SSO_TOKEN";
    return NextResponse.json(
      { error: code, message: err.message || "SSO Token 校验失败" },
      { status: 401 }
    );
  }

  // 同步用户档案到子站本地 (容错: DB 失败不阻断登录)
  try {
    await prisma.userSync.upsert({
      where: { userId: claims.sub },
      create: {
        userId: claims.sub,
        username: claims.username,
        nickname: claims.nickname ?? null,
        avatar: claims.avatar ?? null,
        email: claims.email ?? null,
        role: claims.roles?.[0] || "ROLE_USER",
      },
      update: {
        username: claims.username,
        nickname: claims.nickname ?? null,
        avatar: claims.avatar ?? null,
        email: claims.email ?? null,
        role: claims.roles?.[0] || "ROLE_USER",
        lastSyncAt: new Date(),
      },
    });
  } catch (e) {
    console.warn("[community-login] userSync upsert 失败:", (e as Error).message);
  }

  // 写 iron-session cookie
  try {
    const session = await getMutableSession();
    session.userId = claims.sub;
    session.username = claims.username;
    session.nickname = claims.nickname ?? null;
    session.avatar = claims.avatar ?? null;
    session.email = claims.email ?? null;
    session.role = claims.roles?.[0] || "ROLE_USER";
    session.level = claims.level ?? null;
    session.issuedAt = Math.floor(Date.now() / 1000);
    await session.save();
  } catch (e) {
    return NextResponse.json(
      {
        error: "SESSION_FAIL",
        message: "无法写入会话: " + (e as Error).message,
      },
      { status: 500 }
    );
  }

  return NextResponse.json({
    ok: true,
    userId: claims.sub,
    username: claims.username,
    role: claims.roles?.[0] || "ROLE_USER",
    redirectTo: "/",
  });
}
