import { createHash, createHmac, randomBytes } from "node:crypto";

/**
 * 子站调主站 /api/internal/* 时的 HMAC 签名。
 * 与主站 InternalServiceFilter 对应。
 *
 * 算法:
 *   sha256(body)  → bodyHash (hex 小写, 空 body 用 sha256("") )
 *   payload     = METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + bodyHash
 *   signature   = HMAC_SHA256(SHOP_SERVICE_SECRET, payload)  // hex 小写
 */

export interface SignedHeaders {
  "X-Service-Id": string;
  "X-Service-Timestamp": string;
  "X-Service-Nonce": string;
  "X-Service-Signature": string;
}

export interface SignArgs {
  method: string;
  /** 不带 query 的 path，例如 /api/internal/user/abc/points */
  path: string;
  /** 请求体字符串（GET 用 ""） */
  body?: string;
}

export function buildSignedHeaders({ method, path, body = "" }: SignArgs): SignedHeaders {
  const secret = process.env.SHOP_SERVICE_SECRET;
  const serviceId = process.env.SHOP_SERVICE_ID || "zdc-shop";
  if (!secret) {
    throw new Error("SHOP_SERVICE_SECRET 未配置");
  }
  const timestamp = Date.now().toString();
  const nonce = randomBytes(12).toString("hex");
  const bodyHash = sha256Hex(body);
  const payload = [method.toUpperCase(), path, timestamp, nonce, bodyHash].join("\n");
  const signature = createHmac("sha256", secret).update(payload).digest("hex");
  return {
    "X-Service-Id": serviceId,
    "X-Service-Timestamp": timestamp,
    "X-Service-Nonce": nonce,
    "X-Service-Signature": signature,
  };
}

export function sha256Hex(input: string): string {
  return createHash("sha256").update(input, "utf8").digest("hex");
}
