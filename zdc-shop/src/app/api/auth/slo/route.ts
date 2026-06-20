import { NextResponse } from "next/server";
import { getMutableSession } from "@/lib/auth/session";

/**
 * 单点登出(SLO)前端通道端点。
 * 主站登出时用隐藏 iframe GET 加载本端点,销毁本站 iron-session cookie。
 * 返回 204、不重定向(供 iframe 静默调用)。
 */
export async function GET() {
  try {
    const session = await getMutableSession();
    session.destroy();
  } catch {
    // ignore (session 未配置或已失效)
  }
  return new NextResponse(null, {
    status: 204,
    headers: { "Cache-Control": "no-store" },
  });
}
