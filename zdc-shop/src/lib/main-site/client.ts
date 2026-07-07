import { buildSignedHeaders } from "./hmac";

/**
 * 主站 InternalUserController 的客户端。
 * 与主站新增的两个端点对应:
 *   GET  /api/internal/user/{userId}/points
 *   POST /api/internal/user/{userId}/points/consume
 */

const BASE = (process.env.MAIN_SITE_API_URL || process.env.MAIN_SITE_BACKEND_URL || "http://localhost:7800").replace(/\/$/, "");
const SUCCESS_CODES = new Set([0, 200, 2000]);
const REQUEST_TIMEOUT_MS = 8000;
const RETRY_BACKOFF_MS = 200;

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

export interface SubsiteNotificationRequest {
  source: string;
  title: string;
  content: string;
  relatedId?: string;
}

export interface SubsiteEventRequest {
  eventId: string;
  source: string;
  eventType: string;
  userId?: string;
  title: string;
  content: string;
  relatedId?: string;
  severity?: "info" | "success" | "warning" | "danger" | "error" | "default";
  status?: string;
  notifyUser?: boolean;
  payload?: Record<string, unknown>;
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

  try {
    return await callOnce<T>(method, path, bodyStr);
  } catch (e) {
    // 网络错误(fetch reject/超时)或 5xx:重试一次。4xx 与业务错误不重试。
    // 重试前必须重新签名——timestamp/nonce 不可复用,幂等键保证重试安全。
    if (!isRetryable(e)) throw e;
    await sleep(RETRY_BACKOFF_MS);
    return callOnce<T>(method, path, bodyStr);
  }
}

function isRetryable(e: unknown): boolean {
  if (e instanceof MainSiteApiError) {
    return e.status >= 500 || e.status === 0;
  }
  return true; // fetch 网络层异常(含 AbortError 超时)
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function callOnce<T>(method: string, path: string, bodyStr: string): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...buildSignedHeaders({ method, path, body: bodyStr }),
  };
  if (bodyStr) headers["Content-Type"] = "application/json";

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
      body: bodyStr || undefined,
      cache: "no-store",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (e) {
    const message = e instanceof Error ? e.message : "网络请求失败";
    throw new MainSiteApiError("MAIN_SITE_UNREACHABLE", message, 0);
  }

  const text = await res.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : {};
  } catch {
    data = { message: text };
  }

  if (!res.ok) {
    const d = (data || {}) as Record<string, unknown>;
    const message = (d.message as string) || res.statusText || "主站调用失败";
    throw new MainSiteApiError(
      resolveErrorCode(d.code, message, `HTTP_${res.status}`),
      message,
      res.status
    );
  }
  // 主站走的是 Result<T> 风格的统一包装
  const wrapped = data as { code?: number; data?: T; message?: string };
  if (wrapped && typeof wrapped === "object" && "code" in wrapped && "data" in wrapped) {
    if (typeof wrapped.code === "number" && !SUCCESS_CODES.has(wrapped.code)) {
      const message = wrapped.message || "主站返回业务错误";
      throw new MainSiteApiError(
        resolveErrorCode(wrapped.code, message, String(wrapped.code)),
        message,
        res.status
      );
    }
    return wrapped.data as T;
  }
  return data as T;
}

function resolveErrorCode(rawCode: unknown, message: string, fallback: string) {
  const messageCode = /^([A-Z][A-Z0-9_]+):/.exec(message)?.[1];
  if (messageCode) return messageCode;
  if (typeof rawCode === "string" && rawCode.trim()) return rawCode;
  if (typeof rawCode === "number") return String(rawCode);
  return fallback;
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

export async function sendSubsiteNotification(
  userId: string,
  req: SubsiteNotificationRequest
): Promise<void> {
  if (!userId) throw new MainSiteApiError("INVALID_USER", "userId 必填", 400);
  await call<void>(
    "POST",
    `/api/internal/user/${encodeURIComponent(userId)}/notifications`,
    req
  );
}

export async function sendSubsiteEvent(req: SubsiteEventRequest): Promise<void> {
  if (!req.eventId) throw new MainSiteApiError("INVALID_EVENT", "eventId 必填", 400);
  if (!req.source) throw new MainSiteApiError("INVALID_EVENT", "source 必填", 400);
  if (!req.eventType) throw new MainSiteApiError("INVALID_EVENT", "eventType 必填", 400);
  await call<void>("POST", "/api/internal/subsite/events", req);
}
