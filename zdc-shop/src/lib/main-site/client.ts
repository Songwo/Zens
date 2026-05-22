import { buildSignedHeaders } from "./hmac";

/**
 * 主站 InternalUserController 的客户端。
 * 与主站新增的两个端点对应:
 *   GET  /api/internal/user/{userId}/points
 *   POST /api/internal/user/{userId}/points/consume
 */

const BASE = (process.env.MAIN_SITE_BACKEND_URL || "http://localhost:7800").replace(/\/$/, "");

export interface PointsInfo {
  userId: string;
  username: string;
  points: number;
  level: number | null;
}

export interface ConsumeRequest {
  amount: number;
  reason: string;
  orderId: string;
  idempotencyKey: string;
}

export interface ConsumeResult {
  userId: string;
  pointsAfter: number;
  consumedAmount: number;
  reason: string;
  orderId: string;
  idempotencyKey: string;
}

export class MainSiteApiError extends Error {
  code: string;
  status: number;
  constructor(code: string, message: string, status: number) {
    super(message);
    this.name = "MainSiteApiError";
    this.code = code;
    this.status = status;
  }
}

async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const bodyStr = body === undefined ? "" : JSON.stringify(body);
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...buildSignedHeaders({ method, path, body: bodyStr }),
  };
  if (bodyStr) headers["Content-Type"] = "application/json";

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: bodyStr || undefined,
    cache: "no-store",
  });

  const text = await res.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { message: text };
  }

  if (!res.ok) {
    const d = (data || {}) as Record<string, unknown>;
    throw new MainSiteApiError(
      (d.code as string) || `HTTP_${res.status}`,
      (d.message as string) || res.statusText || "主站调用失败",
      res.status
    );
  }
  // 主站走的是 Result<T> 风格的统一包装
  const wrapped = data as { code?: number; data?: T; message?: string };
  if (wrapped && typeof wrapped === "object" && "code" in wrapped && "data" in wrapped) {
    if (wrapped.code && wrapped.code !== 200 && wrapped.code !== 0) {
      throw new MainSiteApiError(
        String(wrapped.code),
        wrapped.message || "主站返回业务错误",
        res.status
      );
    }
    return wrapped.data as T;
  }
  return data as T;
}

export async function fetchPoints(userId: string): Promise<PointsInfo> {
  if (!userId) throw new MainSiteApiError("INVALID_USER", "userId 必填", 400);
  return call<PointsInfo>("GET", `/api/internal/user/${encodeURIComponent(userId)}/points`);
}

export async function consumePoints(
  userId: string,
  req: ConsumeRequest
): Promise<ConsumeResult> {
  if (!userId) throw new MainSiteApiError("INVALID_USER", "userId 必填", 400);
  if (!Number.isInteger(req.amount) || req.amount <= 0) {
    throw new MainSiteApiError("INVALID_AMOUNT", "扣减金额必须为正整数", 400);
  }
  return call<ConsumeResult>(
    "POST",
    `/api/internal/user/${encodeURIComponent(userId)}/points/consume`,
    req
  );
}

export async function creditPoints(
  userId: string,
  req: ConsumeRequest
): Promise<ConsumeResult & { creditedAmount?: number }> {
  if (!userId) throw new MainSiteApiError("INVALID_USER", "userId 必填", 400);
  if (!Number.isInteger(req.amount) || req.amount <= 0) {
    throw new MainSiteApiError("INVALID_AMOUNT", "充值金额必须为正整数", 400);
  }
  return call<ConsumeResult & { creditedAmount?: number }>(
    "POST",
    `/api/internal/user/${encodeURIComponent(userId)}/points/credit`,
    req
  );
}
