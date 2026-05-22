import { NextResponse } from "next/server";
import { getSession } from "@/lib/auth/session";

const ADMIN_ROLES = new Set(["ROLE_ADMIN", "ROLE_SUPER_ADMIN"]);

/** RSC / Server Action / Route Handler 公用的管理员守卫。 */
export async function requireAdmin() {
  const session = await getSession();
  if (!session) {
    return {
      error: NextResponse.json(
        { error: "UNAUTHENTICATED", message: "请先登录" },
        { status: 401 }
      ),
    };
  }
  if (!ADMIN_ROLES.has(session.role)) {
    return {
      error: NextResponse.json(
        { error: "FORBIDDEN", message: "需要管理员权限" },
        { status: 403 }
      ),
    };
  }
  return { session };
}
