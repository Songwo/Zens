import { Bot, CheckCircle2, Copy, Download, Loader2, RefreshCcw, Send, ShieldCheck } from "lucide-react";
import { formatDateTime, formatFullDateTime } from "../lib/format";
import type { BotAccount, DrawResult, PublishedComment } from "../types/lottery";

type LotteryResultProps = {
  result: DrawResult | null;
  botAccount: BotAccount | null;
  publishedComment: PublishedComment | null;
  onCopy: () => void;
  onRedraw: () => void;
  onExport: () => void;
  onPublish: () => void;
  drawing: boolean;
  publishing: boolean;
};

export function LotteryResult({
  result,
  botAccount,
  publishedComment,
  onCopy,
  onRedraw,
  onExport,
  onPublish,
  drawing,
  publishing,
}: LotteryResultProps) {
  if (!result) {
    return null;
  }
  const botName = botAccount?.displayName ?? "Zens 抽奖机器人";

  return (
    <section className="result-enter space-y-5 border-t border-line pt-7" aria-live="polite">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="flex items-center gap-2 text-sm font-medium text-amber-ink">
            <ShieldCheck className="h-4 w-4" />
            随机种子：{result.seed}
          </p>
          <h2 className="mt-2 text-2xl font-semibold text-ink">抽奖结果</h2>
          <p className="mt-1 text-sm text-muted">
            共 {result.participantCount} 名有效参与者，抽取 {result.winners.length} 名中奖者。
          </p>
        </div>

        <div className="flex flex-col gap-2 sm:flex-row">
          <button className="btn-primary" type="button" onClick={onPublish} disabled={publishing}>
            {publishing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            发布到原帖
          </button>
          <button className="btn-secondary" type="button" onClick={onCopy}>
            <Copy className="h-4 w-4" />
            复制公示文本
          </button>
          <button className="btn-secondary" type="button" onClick={onRedraw} disabled={drawing}>
            <RefreshCcw className="h-4 w-4" />
            重新抽取
          </button>
          <button className="btn-secondary" type="button" onClick={onExport}>
            <Download className="h-4 w-4" />
            导出结果
          </button>
        </div>
      </div>

      <div className="rounded-lg border border-line bg-white px-4 py-3 text-sm text-muted">
        {publishedComment ? (
          <p className="flex flex-wrap items-center gap-2 text-emerald-700">
            <CheckCircle2 className="h-4 w-4" />
            已由 {publishedComment.botName} 发布在原帖 {publishedComment.commentFloor} 楼，
            <a className="font-medium text-amber-ink hover:text-amber-strong" href={publishedComment.commentUrl}>
              查看评论
            </a>
            <span className="text-muted">{formatFullDateTime(publishedComment.postedAt)}</span>
          </p>
        ) : (
          <p className="flex items-center gap-2">
            <Bot className="h-4 w-4 text-amber-ink" />
            将使用 {botName} 在对应帖子下发布中奖名单评论。
          </p>
        )}
      </div>

      <ul className="divide-y divide-line rounded-lg border border-line bg-white">
        {result.winners.map((winner, index) => (
          <li
            key={`${result.drawId}-${winner.id}`}
            className="list-row-enter grid gap-3 px-4 py-4 transition hover:bg-cream/40 sm:grid-cols-[auto_minmax(0,1fr)_120px_150px] sm:items-center"
            style={{ animationDelay: `${index * 48}ms` }}
          >
            <div className="flex items-center gap-3">
              <span className="avatar-mark">{winner.avatar ?? winner.displayName.slice(0, 1)}</span>
              <span className="grid h-7 w-7 place-items-center rounded-md bg-amber-soft text-sm font-semibold text-amber-ink">
                {winner.rank}
              </span>
            </div>
            <div className="min-w-0">
              <strong className="block truncate text-sm font-semibold text-ink">{winner.displayName}</strong>
              <span className="block truncate text-sm text-muted">@{winner.username}</span>
            </div>
            <span className="text-sm text-muted sm:text-right">{winner.floor} 楼</span>
            <span className="text-sm text-muted sm:text-right">{formatDateTime(winner.repliedAt)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
