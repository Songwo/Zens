import { Suspense } from "react";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { CallbackClient } from "./callback-client";

export const metadata = {
  title: "登录中…",
};

export default function LoginCallbackPage() {
  return (
    <>
      <SiteHeader session={null} />
      <main className="editorial-container flex min-h-[calc(100vh-200px)] items-center justify-center py-16">
        <Suspense fallback={<CallbackSkeleton />}>
          <CallbackClient />
        </Suspense>
      </main>
      <SiteFooter />
    </>
  );
}

function CallbackSkeleton() {
  return (
    <div className="space-y-4 text-center">
      <div className="mx-auto h-12 w-12 animate-spin rounded-full border-2 border-divider border-t-brand" />
      <p className="text-sm text-muted">正在准备…</p>
    </div>
  );
}
