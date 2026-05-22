import { NextResponse } from "next/server";

/**
 * 给登录页前端用：返回 Zens 主站的 SSO 入口与 client_id。
 * 不需要鉴权（全公开）。
 */
export function GET() {
  const communityUrl = process.env.NEXT_PUBLIC_COMMUNITY_URL || "";
  const clientId = process.env.NEXT_PUBLIC_SSO_CLIENT_ID || "zdc-shop";
  return NextResponse.json({ communityUrl, clientId });
}
