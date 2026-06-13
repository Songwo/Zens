import {
  Check,
  Eye,
  Info,
  Loader2,
  MessageCircleMore,
  Play,
  ShieldCheck,
} from "lucide-react";
import type { FormEvent } from "react";
import type { FormState } from "../types/lottery";

type LotteryFormProps = {
  form: FormState;
  onChange: (patch: Partial<FormState>) => void;
  onPreview: () => void;
  onSyncComments: () => void;
  onDraw: () => void;
  previewing: boolean;
  syncing: boolean;
  drawing: boolean;
};

export function LotteryForm({
  form,
  onChange,
  onPreview,
  onSyncComments,
  onDraw,
  previewing,
  syncing,
  drawing,
}: LotteryFormProps) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onDraw();
  }

  return (
    <form onSubmit={submit} className="space-y-8">
      <div className="space-y-4">
        <p className="text-sm font-medium text-amber-ink">Zens 社区可信工具</p>
        <div className="max-w-3xl space-y-4">
          <h1 className="text-3xl font-semibold leading-tight text-ink md:text-4xl">
            公平、透明地完成一次社区抽奖
          </h1>
          <p className="text-base leading-7 text-muted md:text-lg">
            基于帖子互动数据生成参与名单，排除发起人，支持楼层范围与人数设置。
          </p>
        </div>
      </div>

      <div className="space-y-6 border-t border-line pt-7">
        <label className="field">
          <span className="field-label">帖子链接</span>
          <input
            className="input"
            value={form.topicUrl}
            onChange={(event) => onChange({ topicUrl: event.target.value })}
            placeholder="粘贴 Zens 社区帖子链接"
            autoComplete="url"
          />
        </label>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="field">
            <span className="field-label">中奖人数</span>
            <input
              className="input"
              type="number"
              min={1}
              max={50}
              value={form.winnerCount}
              onChange={(event) => onChange({ winnerCount: Number(event.target.value) || 1 })}
            />
          </label>

          <label className="field">
            <span className="field-label">截止楼层</span>
            <input
              className="input"
              type="number"
              min={1}
              value={form.maxFloor ?? ""}
              onChange={(event) =>
                onChange({
                  maxFloor: event.target.value ? Number(event.target.value) : null,
                })
              }
              placeholder="可选"
            />
          </label>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <Toggle
            label="排除发帖人"
            checked={form.excludeAuthor}
            onChange={(checked) => onChange({ excludeAuthor: checked })}
          />
          <Toggle
            label="去重用户"
            checked={form.dedupeUser}
            onChange={(checked) => onChange({ dedupeUser: checked })}
          />
          <Toggle
            label="只统计回复用户"
            checked={form.replyOnly}
            onChange={(checked) => onChange({ replyOnly: checked })}
          />
        </div>

        <div className="flex items-start gap-3 rounded-lg border border-amber/25 bg-cream px-4 py-3 text-sm leading-6 text-amber-ink">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <strong className="block font-semibold text-ink">抽奖种子 / 随机源</strong>
            使用服务端随机种子生成结果，可复验。
          </div>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
          <button className="btn-primary min-h-11 flex-1 sm:flex-none" type="submit" disabled={drawing}>
            {drawing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}
            开始抽奖
          </button>
          <button
            className="btn-secondary min-h-11 flex-1 sm:flex-none"
            type="button"
            onClick={onSyncComments}
            disabled={syncing}
          >
            {syncing ? <Loader2 className="h-4 w-4 animate-spin" /> : <MessageCircleMore className="h-4 w-4" />}
            同步帖子评论
          </button>
          <button
            className="btn-secondary min-h-11 flex-1 sm:flex-none"
            type="button"
            onClick={onPreview}
            disabled={previewing}
          >
            {previewing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Eye className="h-4 w-4" />}
            预览参与名单
          </button>
        </div>

        <p className="flex items-center gap-2 text-sm text-muted">
          <Info className="h-4 w-4" />
          粘贴链接后会自动加载帖子评论用户，也可手动同步最新楼层。
        </p>
      </div>
    </form>
  );
}

type ToggleProps = {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
};

function Toggle({ label, checked, onChange }: ToggleProps) {
  return (
    <label className="flex min-h-12 cursor-pointer items-center gap-3 rounded-lg border border-line bg-white px-3 text-sm text-ink transition hover:border-amber/45 hover:bg-cream/55">
      <span
        className={`grid h-5 w-5 shrink-0 place-items-center rounded-md border transition ${
          checked ? "border-amber bg-amber text-white" : "border-line bg-white text-transparent"
        }`}
      >
        <Check className="h-3.5 w-3.5" />
      </span>
      <input
        className="sr-only"
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="font-medium">{label}</span>
    </label>
  );
}
