import { S3Client, PutObjectCommand } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { randomBytes } from "node:crypto";
import { requireEnv } from "./env";

/**
 * Cloudflare R2 客户端 + presigned PUT URL 工具。
 *
 * R2 完全兼容 S3 协议:
 *   endpoint = https://<account-id>.r2.cloudflarestorage.com
 *   region   = "auto"
 *
 * 公开访问通过自定义域名 (R2_PUBLIC_BASE_URL) 走 Cloudflare CDN,
 * bucket 内的 GET 不经过 SDK。
 */

let _client: S3Client | null = null;

export function r2Client(): S3Client {
  if (_client) return _client;
  const accountId = requireEnv("R2_ACCOUNT_ID");
  const accessKeyId = requireEnv("R2_ACCESS_KEY_ID");
  const secretAccessKey = requireEnv("R2_SECRET_ACCESS_KEY");
  _client = new S3Client({
    region: "auto",
    endpoint: `https://${accountId}.r2.cloudflarestorage.com`,
    credentials: { accessKeyId, secretAccessKey },
  });
  return _client;
}

export function r2Bucket(): string {
  return requireEnv("R2_BUCKET");
}

export function r2PublicBase(): string {
  return requireEnv("R2_PUBLIC_BASE_URL").replace(/\/$/, "");
}

/** 拼最终对外可访问的 URL: <R2_PUBLIC_BASE_URL>/<key> */
export function publicUrlFor(key: string): string {
  const cleanKey = key.replace(/^\/+/, "");
  return `${r2PublicBase()}/${cleanKey}`;
}

const EXT_BY_TYPE: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/gif": "gif",
  "image/webp": "webp",
  "image/svg+xml": "svg",
};

export const ALLOWED_IMAGE_TYPES = new Set(Object.keys(EXT_BY_TYPE));
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5MB

/**
 * 生成一个新的 object key。
 * 形如: products/2026/05/<ts>-<rand>.jpg
 */
export function generateObjectKey(opts: {
  prefix?: string;
  contentType: string;
}): string {
  const prefix = (opts.prefix ?? "products").replace(/^\/+|\/+$/g, "");
  const ext = EXT_BY_TYPE[opts.contentType] ?? "bin";
  const d = new Date();
  const yyyy = d.getUTCFullYear();
  const mm = String(d.getUTCMonth() + 1).padStart(2, "0");
  const ts = d.getTime().toString(36);
  const rand = randomBytes(6).toString("hex");
  return `${prefix}/${yyyy}/${mm}/${ts}-${rand}.${ext}`;
}

/**
 * 为单个 key 生成 presigned PUT URL。
 * 浏览器拿到后必须用相同的 Content-Type 头 PUT 上去。
 */
export async function presignPut(opts: {
  key: string;
  contentType: string;
  contentLength?: number;
  expiresIn?: number;
}): Promise<string> {
  const cmd = new PutObjectCommand({
    Bucket: r2Bucket(),
    Key: opts.key,
    ContentType: opts.contentType,
    ContentLength: opts.contentLength,
  });
  return getSignedUrl(r2Client(), cmd, {
    expiresIn: opts.expiresIn ?? 60, // 60s 足够浏览器立刻 PUT
    // 让客户端必须带这两个签进 URL 的头,避免被人改大小或类型
    signableHeaders: new Set(
      opts.contentLength != null ? ["content-type", "content-length"] : ["content-type"]
    ),
  });
}
