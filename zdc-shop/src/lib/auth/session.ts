import { cookies } from "next/headers";
import { getIronSession } from "iron-session";

/**
 * 子站会话载荷。
 * userId / username 来自主站 SSO Token 的 subject + claims。
 * role 决定是否能看 /admin。
 */
export interface ShopSession {
  userId: string;
  username: string;
  nickname?: string | null;
  avatar?: string | null;
  email?: string | null;
  role: string;
  level?: number | null;
  /** 创建时间秒级时间戳，用于强制刷新窗口 */
  issuedAt: number;
}

const COOKIE_NAME = process.env.SESSION_COOKIE_NAME || "zs_session";
// 8 小时
const TTL_SECONDS = 60 * 60 * 8;

function sessionPassword(): string {
  const pw = process.env.SESSION_PASSWORD;
  if (!pw || pw.length < 32) {
    throw new Error(
      "SESSION_PASSWORD 未配置或长度不足 32 字符，请检查 .env.local"
    );
  }
  return pw;
}

export const sessionOptions = {
  get password() {
    return sessionPassword();
  },
  cookieName: COOKIE_NAME,
  ttl: TTL_SECONDS,
  cookieOptions: {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge: TTL_SECONDS,
  },
};

/**
 * 在 RSC / Server Action / route handler 里获取当前会话。
 * 未登录返回 null。
 */
export async function getSession(): Promise<ShopSession | null> {
  // SESSION_PASSWORD 未设置时（dev 跑空脚手架）返回 null，避免崩溃
  if (!process.env.SESSION_PASSWORD || process.env.SESSION_PASSWORD.length < 32) {
    return null;
  }
  const cookieStore = await cookies();
  const session = await getIronSession<Partial<ShopSession>>(cookieStore, sessionOptions);
  if (!session?.userId) return null;
  return {
    userId: session.userId,
    username: session.username || "",
    nickname: session.nickname ?? null,
    avatar: session.avatar ?? null,
    email: session.email ?? null,
    role: session.role || "ROLE_USER",
    level: session.level ?? null,
    issuedAt: session.issuedAt || Math.floor(Date.now() / 1000),
  };
}

/** 仅在 route handler 内部用，能写入。 */
export async function getMutableSession() {
  const cookieStore = await cookies();
  return getIronSession<Partial<ShopSession>>(cookieStore, sessionOptions);
}
