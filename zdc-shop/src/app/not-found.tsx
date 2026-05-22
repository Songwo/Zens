import Link from "next/link";

export default function NotFound() {
  return (
    <main className="editorial-container flex min-h-[60vh] flex-col items-center justify-center pt-24 pb-32 text-center">
      <p className="eyebrow-brand">404 · not here</p>
      <h1 className="mt-3 text-4xl font-bold tracking-tight sm:text-5xl">
        你找的东西
        <br />
        好像不在这。
      </h1>
      <p className="mt-4 max-w-md text-muted">
        要么它从未上架，要么它的兑换通道已经关闭。回到首页看看本周有什么。
      </p>
      <Link href="/" className="btn-brand mt-8 h-11 px-6">
        回到首页 →
      </Link>
    </main>
  );
}
