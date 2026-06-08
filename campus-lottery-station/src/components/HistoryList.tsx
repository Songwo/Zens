import { ArrowRight, FileClock } from "lucide-react";
import { formatFullDateTime } from "../lib/format";
import type { HistoryRecord } from "../types/lottery";

type HistoryListProps = {
  records: HistoryRecord[];
};

export function HistoryList({ records }: HistoryListProps) {
  return (
    <section id="history" className="border-t border-line pt-10">
      <div className="mb-5 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium text-muted">历史记录</p>
          <h2 className="mt-1 text-2xl font-semibold text-ink">最近抽奖记录</h2>
        </div>
        <p className="text-sm text-muted">记录帖子、参与人数与开奖摘要，便于回溯。</p>
      </div>

      {records.length ? (
        <div className="overflow-hidden rounded-lg border border-line bg-white">
          <div className="hidden grid-cols-[minmax(0,1fr)_170px_110px_110px_100px] border-b border-line bg-slate-50 px-4 py-3 text-sm font-medium text-muted md:grid">
            <span>帖子标题</span>
            <span>开奖时间</span>
            <span className="text-right">参与人数</span>
            <span className="text-right">中奖人数</span>
            <span className="text-right">操作</span>
          </div>
          <ul className="divide-y divide-line">
            {records.map((record) => (
              <li
                key={record.id}
                className="grid gap-2 px-4 py-4 text-sm md:grid-cols-[minmax(0,1fr)_170px_110px_110px_100px] md:items-center"
              >
                <strong className="truncate font-medium text-ink">{record.topicTitle}</strong>
                <span className="text-muted">{formatFullDateTime(record.drawnAt)}</span>
                <span className="text-muted md:text-right">{record.participantCount} 人</span>
                <span className="text-muted md:text-right">{record.winnerCount} 人</span>
                <button className="inline-flex items-center gap-1 text-amber-ink transition hover:text-amber-strong md:justify-end">
                  查看结果
                  <ArrowRight className="h-4 w-4" />
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <div className="grid min-h-52 place-items-center rounded-lg border border-dashed border-line bg-white px-5 text-center">
          <div className="max-w-sm">
            <FileClock className="mx-auto h-11 w-11 text-amber" />
            <p className="mt-4 text-base font-medium text-ink">还没有抽奖记录，粘贴一个帖子链接开始第一次抽奖。</p>
          </div>
        </div>
      )}
    </section>
  );
}
