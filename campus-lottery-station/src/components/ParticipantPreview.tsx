import { Loader2, MessageCircleMore, UsersRound } from "lucide-react";
import { AvatarMark } from "./AvatarMark";
import { formatDateTime, formatFullDateTime } from "../lib/format";
import type { Participant } from "../types/lottery";

type ParticipantPreviewProps = {
  participants: Participant[];
  visible: boolean;
  loading: boolean;
  syncedAt?: string;
  source?: string;
};

export function ParticipantPreview({
  participants,
  visible,
  loading,
  syncedAt,
  source,
}: ParticipantPreviewProps) {
  if (!visible) {
    return null;
  }

  return (
    <section className="section-enter space-y-4 border-t border-line pt-6" aria-label="参与名单预览">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-medium text-muted">
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageCircleMore className="h-4 w-4" />}
            {loading ? "正在同步帖子评论" : "参与名单预览"}
          </p>
          <h2 className="mt-1 text-xl font-semibold text-ink">{participants.length} 名有效参与者</h2>
          {syncedAt ? (
            <p className="mt-1 text-sm text-muted">
              来源：{source || "帖子回复用户"}，同步于 {formatFullDateTime(syncedAt)}
            </p>
          ) : null}
        </div>
        <span className="hidden rounded-lg border border-line bg-white px-3 py-2 text-sm text-muted sm:inline-flex">
          <UsersRound className="mr-2 h-4 w-4" />
          已按用户去重
        </span>
      </div>

      {loading ? <SyncSkeleton /> : null}

      {participants.length ? (
        <ul className="divide-y divide-line rounded-lg border border-line bg-white">
          {participants.map((participant, index) => (
            <li
              key={participant.id}
              className="list-row-enter grid grid-cols-[40px_minmax(0,1fr)_70px] items-start gap-3 px-4 py-3 transition hover:bg-cream/40 sm:grid-cols-[40px_minmax(0,1fr)_110px]"
              style={{ animationDelay: `${Math.min(index, 10) * 28}ms` }}
            >
              <AvatarMark value={participant.avatar} fallback={participant.displayName || participant.username} />
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                  <strong className="max-w-full truncate text-sm font-semibold text-ink">
                    {participant.displayName}
                  </strong>
                  <span className="max-w-full truncate text-sm text-muted">@{participant.username}</span>
                </div>
                <p className="mt-1 truncate text-sm text-muted">{participant.excerpt}</p>
              </div>
              <div className="shrink-0 text-right text-sm text-muted">
                <span className="block font-medium text-ink">{participant.floor} 楼</span>
                <span>{formatDateTime(participant.repliedAt)}</span>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <div className="rounded-lg border border-dashed border-line bg-white px-4 py-8 text-center text-sm text-muted">
          当前条件下没有可参与用户。
        </div>
      )}
    </section>
  );
}

function SyncSkeleton() {
  return (
    <div className="sync-pulse rounded-lg border border-amber/25 bg-cream px-4 py-3 text-sm text-amber-ink">
      <div className="flex items-center gap-3">
        <span className="h-2.5 w-2.5 rounded-full bg-amber" />
        <span>正在从 Zens 原帖读取评论楼层、用户和回复时间。</span>
      </div>
    </div>
  );
}
