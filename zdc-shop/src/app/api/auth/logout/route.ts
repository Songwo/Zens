import { NextResponse } from "next/server";
import { getMutableSession } from "@/lib/auth/session";

export async function POST() {
  try {
    const session = await getMutableSession();
    session.destroy();
  } catch {
    // ignore (e.g., session 未配置)
  }
  return NextResponse.redirect(new URL("/", process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000"), {
    status: 303,
  });
}
