import { Layers3, MessageSquareText, UserRound } from "lucide-react";
import type { TopicPreviewData } from "../types/lottery";

type TopicPreviewProps = {
  topic: TopicPreviewData | null;
  loading: boolean;
};

export function TopicPreview({ topic, loading }: TopicPreviewProps) {
  if (loading) {
    return (
      <section className="space-y-4 border-t border-line pt-6" aria-label="帖子预览加载中">
        <div className="h-4 w-28 animate-pulse rounded bg-slate-200" />
        <div className="space-y-3">
          <div className="h-5 w-4/5 animate-pulse rounded bg-slate-200" />
          <div className="h-4 w-2/3 animate-pulse rounded bg-slate-200" />
        </div>
      </section>
    );
  }

  if (!topic) {
    return null;
  }

  return (
    <section className="space-y-4 border-t border-line pt-6" aria-label="帖子预览">
      <div>
        <p className="text-sm font-medium text-muted">已识别的 Zens 帖子</p>
        <h2 className="mt-2 text-xl font-semibold leading-snug text-ink">{topic.topicTitle}</h2>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Meta icon={<UserRound />} label="作者" value={topic.author} />
        <Meta icon={<MessageSquareText />} label="回复数" value={`${topic.replyCount} 条`} />
        <Meta icon={<Layers3 />} label="有效参与" value={`${topic.participantCount} 人`} />
        <Meta icon={<Layers3 />} label="最后楼层" value={`${topic.lastFloor} 楼`} />
      </div>
    </section>
  );
}

type MetaProps = {
  icon: React.ReactNode;
  label: string;
  value: string;
};

function Meta({ icon, label, value }: MetaProps) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-line bg-white px-3 py-3">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-amber-soft text-amber-ink [&_svg]:h-4 [&_svg]:w-4">
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block text-xs text-muted">{label}</span>
        <strong className="block truncate text-sm font-semibold text-ink">{value}</strong>
      </span>
    </div>
  );
}
