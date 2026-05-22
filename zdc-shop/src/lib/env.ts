import { z } from "zod";

/**
 * 服务端环境变量校验。
 * 在启动期或第一次访问时调用 getEnv() 触发一次性校验。
 * 缺失的非必填项不会抛错，而是给出 warning（通过 zod 的 .optional() 体现）。
 */
const Schema = z.object({
  NODE_ENV: z.enum(["development", "production", "test"]).default("development"),

  // 数据库
  DATABASE_URL: z.string().min(1, "DATABASE_URL 必填"),

  // 与主站共享的密钥
  JWT_SECRET: z.string().min(32, "JWT_SECRET 必须至少 32 字符且与主站一致"),
  SHOP_SERVICE_SECRET: z.string().min(16, "SHOP_SERVICE_SECRET 必须至少 16 字符"),
  SHOP_SERVICE_ID: z.string().default("zdc-shop"),

  // 主站地址
  MAIN_SITE_BACKEND_URL: z.string().url("MAIN_SITE_BACKEND_URL 必须为 URL"),
  NEXT_PUBLIC_COMMUNITY_URL: z.string().url().default("http://localhost:5173"),
  NEXT_PUBLIC_SSO_CLIENT_ID: z.string().default("zdc-shop"),

  // session
  SESSION_PASSWORD: z.string().min(32, "SESSION_PASSWORD 必须至少 32 字符"),
  SESSION_COOKIE_NAME: z.string().default("zs_session"),

  // 站点元
  NEXT_PUBLIC_SITE_NAME: z.string().default("Zens · 积分商城"),
  NEXT_PUBLIC_SITE_URL: z.string().url().default("http://localhost:3000"),

  // Cloudflare R2 (图片对象存储,S3 兼容)
  R2_ACCOUNT_ID: z.string().min(1, "R2_ACCOUNT_ID 必填"),
  R2_ACCESS_KEY_ID: z.string().min(1, "R2_ACCESS_KEY_ID 必填"),
  R2_SECRET_ACCESS_KEY: z.string().min(1, "R2_SECRET_ACCESS_KEY 必填"),
  R2_BUCKET: z.string().min(1, "R2_BUCKET 必填"),
  // 自定义域名,如 https://cdn.zens.community,结尾不要带 /
  R2_PUBLIC_BASE_URL: z
    .string()
    .url("R2_PUBLIC_BASE_URL 必须是完整 URL,如 https://cdn.zens.community")
    .refine((v) => !v.endsWith("/"), "R2_PUBLIC_BASE_URL 结尾不要带 /"),
});

export type AppEnv = z.infer<typeof Schema>;

let cached: AppEnv | null = null;

/**
 * 严格校验：dev 启动空脚手架时也允许缺 SHOP_SERVICE_SECRET / DATABASE_URL 等，
 * 用 ensure() 在真正调用对应模块时再校验子集。
 */
export function getEnv(): Partial<AppEnv> {
  if (cached) return cached;
  const parsed = Schema.safeParse(process.env);
  if (!parsed.success) {
    if (process.env.NODE_ENV === "production") {
      throw new Error(
        "环境变量校验失败:\n" +
          parsed.error.issues.map((i) => `  ${i.path.join(".")}: ${i.message}`).join("\n")
      );
    } else {
      console.warn(
        "[env] 部分环境变量缺失/不合法(开发模式忽略):",
        parsed.error.issues.map((i) => `${i.path.join(".")}`).join(", ")
      );
    }
    // 开发模式返回 process.env 的浅拷贝，让调用方自己处理缺失
    return { ...process.env } as Partial<AppEnv>;
  }
  cached = parsed.data;
  return cached;
}

/** 在调用方校验某个必填项确实存在，未配置时直接抛错。 */
export function requireEnv<K extends keyof AppEnv>(key: K): string {
  const v = process.env[key];
  if (!v) {
    throw new Error(`缺少必需的环境变量: ${key}`);
  }
  return v;
}
