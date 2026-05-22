import { redirect } from "next/navigation";
import { getSession } from "@/lib/auth/session";

export default async function MePage() {
  const session = await getSession();
  if (!session) {
    redirect("/login?from=/me");
  }
  // 移动端"我的"按钮先重定向到兑换历史
  redirect("/orders");
}
