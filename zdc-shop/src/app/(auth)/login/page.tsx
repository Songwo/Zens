import Link from "next/link";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { getSession } from "@/lib/auth/session";
import { SSOLoginButton } from "./sso-login-button";

export const metadata = {
  title: "登录",
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ from?: string }>;
}) {
  const session = await getSession();
  const params = await searchParams;
  const from = params.from || "/";

  if (session) {
    return (
      <>
        <SiteHeader
          session={{
            userId: session.userId,
            username: session.username,
            nickname: session.nickname,
            avatar: session.avatar,
            role: session.role,
            points: null,
          }}
        />
        <main className="editorial-container pt-24 pb-32">
          <p className="text-muted">
            你已登录为 <span className="font-semibold text-ink">{session.username}</span>。
          </p>
          <Link href={from} className="btn-brand mt-6 inline-flex h-11 px-6">
            回到首页 →
          </Link>
        </main>
        <SiteFooter />
      </>
    );
  }

  return (
    <>
      <SiteHeader session={null} />
      <main className="editorial-container flex min-h-[calc(100vh-200px)] items-center pt-16 pb-32">
        <div className="mx-auto w-full max-w-md animate-rise space-y-8 text-center">
          <div className="space-y-3">
            <p className="eyebrow-brand">sso · zens only</p>
            <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">
              用你的 Zens 社区账号
              <br />
              继续。
            </h1>
            <p className="text-muted">
              本商城仅对 Zens 社区开放。你会被带到主站完成登录授权，再回到这里。
            </p>
          </div>

          <SSOLoginButton from={from} />

          <p className="text-xs text-muted">
            登录即代表你同意 Zens 社区的服务条款。
          </p>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
