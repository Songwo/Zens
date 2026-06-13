import { Search, X } from "lucide-react";

type SearchBarProps = {
  value: string;
  resultCount: number;
  totalCount: number;
  onChange: (value: string) => void;
};

export function SearchBar({ value, resultCount, totalCount, onChange }: SearchBarProps) {
  return (
    <section className="mx-auto max-w-7xl px-5 sm:px-7 lg:px-8" aria-label="搜索社区入口">
      <div className="flex flex-col gap-3 border-b border-line pb-7 md:flex-row md:items-center md:justify-between">
        <label className="flex h-12 w-full items-center gap-3 rounded-nav border border-line bg-surface px-4 text-muted transition-colors focus-within:border-amber focus-within:bg-amber-softer md:max-w-xl">
          <Search className="h-4 w-4 shrink-0" aria-hidden="true" />
          <input
            type="search"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder="搜索社区服务、文档、工具或资源"
            className="min-w-0 flex-1 bg-transparent text-sm text-ink outline-none placeholder:text-muted"
          />
          {value ? (
            <button
              type="button"
              onClick={() => onChange("")}
              className="rounded-nav p-1 text-muted hover:bg-surface hover:text-ink"
              aria-label="清空搜索"
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </button>
          ) : null}
        </label>
        <p className="text-sm text-muted">
          {value ? `找到 ${resultCount} 个相关入口` : `共维护 ${totalCount} 个社区入口`}
        </p>
      </div>
    </section>
  );
}
