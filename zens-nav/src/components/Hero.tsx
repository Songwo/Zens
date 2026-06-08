import { Compass, Network, ShieldCheck } from "lucide-react";

export function Hero() {
  return (
    <section
      id="社区首页"
      className="mx-auto max-w-7xl px-5 pb-8 pt-12 sm:px-7 sm:pb-10 sm:pt-16 lg:px-8"
      aria-labelledby="hero-title"
    >
      <div className="grid gap-8 border-b border-line pb-10 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-end">
        <div className="max-w-3xl">
          <p className="mb-3 text-xs font-semibold uppercase tracking-normal text-amber-strong">Zens Community Infrastructure</p>
          <h1 id="hero-title" className="text-4xl font-semibold leading-tight text-ink sm:text-5xl lg:text-6xl">
            Zens 社区导航
          </h1>
          <p className="mt-5 text-base leading-8 text-mutedStrong sm:text-lg">
            汇总 Zens 主社区、CDK 空投台、抽奖工具、积分商城、媒体服务和规则文档，让成员更快找到真实可用的入口。
          </p>
          <div className="mt-6 inline-flex max-w-full items-center gap-3 rounded-nav border border-amber/60 bg-amber-softer px-4 py-3 text-sm text-amber-ink">
            <Compass className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>探索知识，分享生活，结识同好。</span>
          </div>
        </div>

        <div className="grid gap-3 rounded-nav border border-line bg-surface p-4">
          <div className="flex items-start gap-3 border-b border-lineSoft pb-3">
            <Network className="mt-1 h-5 w-5 text-amber-strong" aria-hidden="true" />
            <div>
              <p className="text-sm font-medium text-ink">官方服务入口</p>
              <p className="mt-1 text-sm leading-6 text-muted">集中维护主站、子站、后台和本地联调入口。</p>
            </div>
          </div>
          <div className="flex items-start gap-3">
            <ShieldCheck className="mt-1 h-5 w-5 text-amber-strong" aria-hidden="true" />
            <div>
              <p className="text-sm font-medium text-ink">可信、有序、可扩展</p>
              <p className="mt-1 text-sm leading-6 text-muted">展示 SSO、积分、抽奖、CDK 和媒体服务的协作边界。</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
