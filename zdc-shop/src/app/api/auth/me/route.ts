import { NextResponse } from "next/server";
import { getSession } from "@/lib/auth/session";

export async function GET() {
  const session = await getSession();
  if (!session) {
    return NextResponse.json({ session: null }, { status: 200 });
  }
  return NextResponse.json({
    session: {
      userId: session.userId,
      username: session.username,
      nickname: session.nickname,
      avatar: session.avatar,
      role: session.role,
    },
  });
}
