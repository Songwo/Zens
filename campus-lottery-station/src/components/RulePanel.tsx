import { AlertTriangle, Bot, CheckCircle2, ListChecks, ShieldCheck } from "lucide-react";
import type { BotAccount, PublishedComment } from "../types/lottery";

type RulePanelProps = {
  botAccount: BotAccount | null;
  publishedComment: PublishedComment | null;
};

export function RulePanel({ botAccount, publishedComment }: RulePanelProps) {
  return (
    <aside id="rules" className="space-y-8 border-line lg:border-l lg:pl-8">
      <section className="info-block">
        <div className="info-heading">
          <Bot className="h-5 w-5" />
          <h2>官方机器人</h2>
        </div>
        <div className="space-y-3 text-sm leading-6 text-muted">
          <div className="flex items-center gap-3 rounded-lg border border-line bg-white px-3 py-3">
            <span className="avatar-mark h-9 w-9">{botAccount?.avatar ?? "抽"}</span>
            <span className="min-w-0">
              <strong className="block truncate font-semibold text-ink">
                {botAccount?.displayName ?? "Zens 抽奖机器人"}
              </strong>
              <span className="block truncate">@{botAccount?.username ?? "zens-lottery-bot"}</span>
            </span>
          </div>
          <p>
            用于在原帖下发布中奖名单评论，内容包含中奖用户、楼层、回复时间、参与人数和随机种子。
          </p>
          <p className="inline-flex items-center gap-2 rounded-lg bg-emerald-50 px-3 py-2 text-emerald-700">
            <CheckCircle2 className="h-4 w-4" />
            默认已配置，开奖结果可直接发布
          </p>
          {publishedComment ? (
            <p className="rounded-lg border border-amber/25 bg-cream px-3 py-2 text-amber-ink">
              已由 {publishedComment.botName} 发布在 {publishedComment.commentFloor} 楼。
            </p>
          ) : null}
        </div>
      </section>

      <section className="info-block">
        <div className="info-heading">
          <ListChecks className="h-5 w-5" />
          <h2>抽奖规则</h2>
        </div>
        <ol className="space-y-3 text-sm leading-6 text-muted">
          <li>1. 自动排除发帖人</li>
          <li>2. 同一用户只计入一次</li>
          <li>3. 可设置截止楼层</li>
          <li>4. 不允许要求关注、点赞、加群等附加条件</li>
        </ol>
      </section>

      <section className="info-block">
        <div className="info-heading">
          <ShieldCheck className="h-5 w-5" />
          <h2>公平性说明</h2>
        </div>
        <p className="text-sm leading-6 text-muted">
          抽奖完成后生成结果摘要，可复制到原帖公示。
        </p>
      </section>

      <section className="info-block">
        <div className="info-heading">
          <AlertTriangle className="h-5 w-5" />
          <h2>安全提示</h2>
        </div>
        <p className="text-sm leading-6 text-muted">
          请在帖子关闭或活动截止后再开奖，避免名单继续变化。
        </p>
      </section>
    </aside>
  );
}
