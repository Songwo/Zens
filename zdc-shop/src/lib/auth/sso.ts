import { jwtVerify } from "jose";
import { requireEnvFirst } from "@/lib/env";

/**
 * 主站 SsoController 颁发的 SSO Token 的 claims 形状。
 * 字段对应:
 *   src/main/java/.../controller/SsoController.java#authorize (line 191-201)
 */
export interface SsoClaims {
  sub: string; // userId
  iat: number;
  exp: number;
  username: string;
  nickname?: string;
  avatar?: string;
  email?: string;
  roles: string[];
  school?: string;
  level?: number;
  sso: boolean;
  client_id: string;
  [key: string]: unknown;
}

/**
 * 验证主站颁发的 SSO Token。
 * - HMAC SHA256/384/512 自动识别
 * - 必须 sso === true
 * - 必须 client_id === 期望值（环境变量 NEXT_PUBLIC_SSO_CLIENT_ID）
 * - exp 由 jose 自动校验
 */
export async function verifySsoToken(token: string): Promise<SsoClaims> {
  const jwtSecret = requireEnvFirst("MAIN_SITE_JWT_SECRET", "JWT_SECRET");
  const expectedClientId = process.env.NEXT_PUBLIC_SSO_CLIENT_ID || "zdc-shop";

  const secretKey = new TextEncoder().encode(jwtSecret);

  // jose 默认会校验 exp / nbf
  const { payload } = await jwtVerify(token, secretKey, {
    // SsoController 用的是 Keys.hmacShaKeyFor(jwtSecret.getBytes(UTF_8))
    // 主站没有显式设置 algorithm,默认会用 HS256/HS384/HS512 之一,自动推断
    algorithms: ["HS256", "HS384", "HS512"],
  });

  if (typeof payload.sub !== "string") {
    throw new SsoVerificationError("INVALID_SUBJECT", "SSO Token 缺少 subject");
  }
  if (payload.sso !== true) {
    throw new SsoVerificationError(
      "NOT_SSO_TOKEN",
      "该 Token 不是 SSO 授权 Token (sso flag 缺失)"
    );
  }
  if (payload.client_id !== expectedClientId) {
    throw new SsoVerificationError(
      "WRONG_CLIENT",
      `Token 不是颁发给本站的 (期望 ${expectedClientId}, 实际 ${String(payload.client_id)})`
    );
  }
  const roles = (payload.roles as unknown as string[] | undefined) || ["ROLE_USER"];
  return {
    sub: payload.sub,
    iat: payload.iat || Math.floor(Date.now() / 1000),
    exp: payload.exp || Math.floor(Date.now() / 1000) + 300,
    username: (payload.username as string) || "",
    nickname: payload.nickname as string | undefined,
    avatar: payload.avatar as string | undefined,
    email: payload.email as string | undefined,
    roles,
    school: payload.school as string | undefined,
    level: payload.level as number | undefined,
    sso: true,
    client_id: payload.client_id as string,
  };
}

export class SsoVerificationError extends Error {
  code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = "SsoVerificationError";
  }
}
