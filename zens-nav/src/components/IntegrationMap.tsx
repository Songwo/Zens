import { resolveIcon } from "../lib/icons";
import type { IntegrationFlow } from "../types/nav";

type IntegrationMapProps = {
  flows: IntegrationFlow[];
};

export function IntegrationMap({ flows }: IntegrationMapProps) {
  return (
    <section id="服务联动" className="mx-auto max-w-7xl px-5 pb-10 sm:px-7 lg:px-8" aria-labelledby="integration-title">
      <div className="mb-5 flex flex-col gap-2 border-b border-line pb-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-normal text-amber-strong">Service Links</p>
          <h2 id="integration-title" className="text-2xl font-semibold text-ink">
            服务联动关系
          </h2>
        </div>
        <p className="max-w-2xl text-sm leading-6 text-muted">
          这些关系来自当前仓库的主站、CDK、抽奖、商城和媒体服务源码，用来说明各子站如何接入社区基础设施。
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {flows.map((flow) => {
          const Icon = resolveIcon(flow.icon);

          return (
            <article key={flow.id} className="border-t-2 border-ink pt-4">
              <div className="flex items-start gap-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-nav border border-amber/50 bg-amber-softer text-amber-strong">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                </span>
                <div className="min-w-0">
                  <h3 className="text-base font-semibold text-ink">{flow.title}</h3>
                  <p className="mt-1 text-xs text-muted">
                    {flow.from} <span className="text-amber-strong">→</span> {flow.to}
                  </p>
                </div>
              </div>
              <p className="mt-4 text-sm leading-6 text-mutedStrong">{flow.description}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                {flow.touchpoints.map((touchpoint) => (
                  <span key={touchpoint} className="rounded-nav border border-line bg-surface px-2 py-1 font-mono text-[11px] text-muted">
                    {touchpoint}
                  </span>
                ))}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
