"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { ImageUploader } from "./image-uploader";

export interface ProductFormValues {
  slug: string;
  title: string;
  subtitle: string;
  description: string;
  coverUrl: string;
  coverGradient: string;
  category: "ai" | "badge" | "welfare" | "domain" | "theme";
  pricePoints: number;
  stock: number;
  limitPerUser: number;
  status: "ACTIVE" | "DRAFT" | "SOLDOUT";
  sortWeight: number;
  highlight: boolean;
  thisWeek: boolean;
  fulfillment: "CODE" | "SOFT";
}

const DEFAULT: ProductFormValues = {
  slug: "",
  title: "",
  subtitle: "",
  description: "",
  coverUrl: "",
  coverGradient: "linear-gradient(135deg, #fff7d6, #f4b400)",
  category: "ai",
  pricePoints: 100,
  stock: -1,
  limitPerUser: 1,
  status: "ACTIVE",
  sortWeight: 0,
  highlight: false,
  thisWeek: false,
  fulfillment: "CODE",
};

const PRESET_GRADIENTS: { label: string; value: string }[] = [
  { label: "黄金", value: "linear-gradient(135deg, #fff7d6 0%, #f4b400 60%, #d89b00 100%)" },
  { label: "暖黄", value: "linear-gradient(135deg, #fff7d6 0%, #f4b400 100%)" },
  { label: "深金", value: "linear-gradient(135deg, #fff6d6 0%, #d89b00 100%)" },
  { label: "夜屿", value: "linear-gradient(135deg, #2a2a30 0%, #0a0a0b 100%)" },
  { label: "晴空", value: "linear-gradient(135deg, #e8eef9 0%, #c2d4f0 100%)" },
  { label: "落日", value: "linear-gradient(135deg, #fef3c7 0%, #f59e0b 100%)" },
];

interface Props {
  mode: "create" | "edit";
  productId?: string;
  initial?: Partial<ProductFormValues>;
}

export function ProductForm({ mode, productId, initial }: Props) {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [values, setValues] = useState<ProductFormValues>({
    ...DEFAULT,
    ...initial,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function set<K extends keyof ProductFormValues>(key: K, v: ProductFormValues[K]) {
    setValues((s) => ({ ...s, [key]: v }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    // 客户端先做一次基础校验
    if (!values.slug || !/^[a-z0-9][a-z0-9-]*$/.test(values.slug)) {
      setError("slug 仅允许小写字母、数字与短横线开头");
      setSubmitting(false);
      return;
    }
    if (!values.title) {
      setError("标题必填");
      setSubmitting(false);
      return;
    }
    if (!values.description) {
      setError("详细描述必填");
      setSubmitting(false);
      return;
    }
    if (!values.coverUrl) {
      setError("请上传封面图,或填写一个图片 URL");
      setSubmitting(false);
      return;
    }

    try {
      const url =
        mode === "create"
          ? "/api/admin/products"
          : `/api/admin/products/${productId}`;
      const method = mode === "create" ? "POST" : "PUT";
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
        credentials: "include",
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data?.message || data?.error || "保存失败");
      toast.success(mode === "create" ? "商品已创建" : "已保存");
      startTransition(() => {
        router.push("/admin?tab=products");
        router.refresh();
      });
    } catch (e) {
      setError((e as Error).message);
      toast.error("保存失败", { description: (e as Error).message });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!productId) return;
    if (!confirm("确认删除这个商品?\n如果已有订单,系统默认会拒绝;强制删除会同时清掉订单。")) return;
    setSubmitting(true);
    try {
      let res = await fetch(`/api/admin/products/${productId}`, {
        method: "DELETE",
        credentials: "include",
      });
      let data = await res.json();
      if (res.status === 409 && data?.error === "HAS_ORDERS") {
        if (
          !confirm(
            `此商品已有 ${data.orderCount} 笔订单。\n确认级联删除订单 + 兑换码?(不可逆!)`
          )
        ) {
          setSubmitting(false);
          return;
        }
        res = await fetch(`/api/admin/products/${productId}?force=1`, {
          method: "DELETE",
          credentials: "include",
        });
        data = await res.json();
      }
      if (!res.ok) throw new Error(data?.message || "删除失败");
      toast.success("已删除");
      startTransition(() => {
        router.push("/admin?tab=products");
        router.refresh();
      });
    } catch (e) {
      toast.error("删除失败", { description: (e as Error).message });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-14 pb-32">
      {/* ───── Section: 封面 ───────────────────────────────────────── */}
      <Section eyebrow="cover" title="封面">
        <ImageUploader
          value={values.coverUrl}
          gradient={values.coverGradient}
          onChange={(url) => set("coverUrl", url)}
        />
        <Field
          label="或填一个外链 URL"
          hint="也可以不上传,直接贴 Cloudflare R2 / OSS / 第三方图床的链接"
        >
          <InputBare
            value={values.coverUrl}
            onChange={(v) => set("coverUrl", v)}
            placeholder="https://cdn.zens.community/products/…"
          />
        </Field>
        <Field label="无图时的渐变备用色" hint="详情页 cover 与首页 featured 都会用到">
          <div className="flex flex-wrap gap-2">
            {PRESET_GRADIENTS.map((g) => (
              <button
                type="button"
                key={g.value}
                onClick={() => set("coverGradient", g.value)}
                className={cn(
                  "h-10 w-20 rounded-xl border transition-all hover:scale-105",
                  values.coverGradient === g.value
                    ? "border-brand ring-2 ring-brand-soft"
                    : "border-divider"
                )}
                style={{ background: g.value }}
                aria-label={g.label}
                title={g.label}
              />
            ))}
            <InputBare
              value={values.coverGradient}
              onChange={(v) => set("coverGradient", v)}
              placeholder="自定义 CSS gradient"
              className="ml-2 min-w-[280px] flex-1"
            />
          </div>
        </Field>
      </Section>

      {/* ───── Section: 基本信息 ───────────────────────────────────── */}
      <Section eyebrow="meta" title="基本信息">
        <div className="grid gap-6 md:grid-cols-2">
          <Field label="标题 *" hint="详情页主标题与列表展示">
            <InputBare
              value={values.title}
              onChange={(v) => set("title", v)}
              placeholder="如:Cloudflare 域名 1 年券"
            />
          </Field>
          <Field label="slug *" hint="URL 路径片段,仅 a-z 0-9 -">
            <InputBare
              value={values.slug}
              onChange={(v) => set("slug", v.toLowerCase().replace(/[^a-z0-9-]/g, "-"))}
              placeholder="cf-domain-coupon"
            />
          </Field>
        </div>
        <Field label="副标题" hint="可空,列表与详情副标">
          <InputBare
            value={values.subtitle}
            onChange={(v) => set("subtitle", v)}
            placeholder="如:限量 12 张 · 老社员优先"
          />
        </Field>
        <Field label="详细说明 *" hint="支持空行分段,连续 `- ` 自动识别成列表">
          <TextareaBare
            value={values.description}
            onChange={(v) => set("description", v)}
            rows={8}
            placeholder="如:这是一张 Cloudflare 注册域名一年的代金券…"
          />
        </Field>
      </Section>

      {/* ───── Section: 销售设置 ───────────────────────────────────── */}
      <Section eyebrow="sale" title="销售设置">
        <div className="grid gap-6 md:grid-cols-3">
          <Field label="积分单价 *">
            <NumberBare value={values.pricePoints} onChange={(v) => set("pricePoints", v)} min={0} />
          </Field>
          <Field label="库存" hint="-1 表示无限量">
            <NumberBare value={values.stock} onChange={(v) => set("stock", v)} min={-1} />
          </Field>
          <Field label="单人限购">
            <NumberBare value={values.limitPerUser} onChange={(v) => set("limitPerUser", v)} min={1} />
          </Field>
        </div>
        <div className="grid gap-6 md:grid-cols-2">
          <Field label="分类">
            <SelectBare
              value={values.category}
              onChange={(v) => set("category", v as ProductFormValues["category"])}
              options={[
                { value: "ai", label: "AI 工具" },
                { value: "badge", label: "社区徽章" },
                { value: "welfare", label: "社区福利" },
                { value: "domain", label: "域名 / 网络" },
                { value: "theme", label: "外观主题" },
              ]}
            />
          </Field>
          <Field label="发放方式" hint="CODE=自动发兑换码 · SOFT=运营手动配置(徽章/主题)">
            <SelectBare
              value={values.fulfillment}
              onChange={(v) => set("fulfillment", v as "CODE" | "SOFT")}
              options={[
                { value: "CODE", label: "CODE - 兑换码池自动发放" },
                { value: "SOFT", label: "SOFT - 软发放(运营手动)" },
              ]}
            />
          </Field>
        </div>
      </Section>

      {/* ───── Section: 上架与展示 ─────────────────────────────────── */}
      <Section eyebrow="display" title="上架与展示">
        <div className="grid gap-6 md:grid-cols-3">
          <Field label="状态">
            <SelectBare
              value={values.status}
              onChange={(v) => set("status", v as ProductFormValues["status"])}
              options={[
                { value: "ACTIVE", label: "ACTIVE - 在售" },
                { value: "DRAFT", label: "DRAFT - 草稿(前台不见)" },
                { value: "SOLDOUT", label: "SOLDOUT - 售罄" },
              ]}
            />
          </Field>
          <Field label="排序权重" hint="越大越靠前">
            <NumberBare value={values.sortWeight} onChange={(v) => set("sortWeight", v)} />
          </Field>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <Toggle
            label="作为首页 FEATURED 焦点"
            hint="首页大图区,只有一个商品会被选中(优先级最高的)"
            checked={values.highlight}
            onChange={(v) => set("highlight", v)}
          />
          <Toggle
            label="进入首页 THIS WEEK 排行"
            hint="出现在首页 03 排行式列表里"
            checked={values.thisWeek}
            onChange={(v) => set("thisWeek", v)}
          />
        </div>
      </Section>

      {/* ───── 底部操作条 (sticky) ──────────────────────────────────── */}
      {error && (
        <p className="rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950 dark:text-rose-300">
          {error}
        </p>
      )}

      <div className="sticky bottom-4 z-30 flex flex-wrap items-center justify-end gap-3 rounded-2xl border border-divider bg-surface/90 px-5 py-3 backdrop-blur-xl">
        {mode === "edit" && (
          <button
            type="button"
            onClick={handleDelete}
            disabled={submitting}
            className="mr-auto inline-flex h-10 items-center rounded-pill px-4 text-sm font-medium text-rose-500 transition-colors hover:bg-rose-50 disabled:opacity-50 dark:hover:bg-rose-950"
          >
            删除此商品
          </button>
        )}
        <Link href="/admin?tab=products" className="btn-ghost h-10">
          取消
        </Link>
        <button
          type="submit"
          disabled={submitting || pending}
          className="btn-brand h-10 px-6 disabled:cursor-wait disabled:opacity-70"
        >
          {submitting ? "保存中…" : mode === "create" ? "创建商品" : "保存修改"}
        </button>
      </div>
    </form>
  );
}

// ─── 排版与表单原语 (Editorial 风,无外框) ─────────────────────────

function Section({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="border-t border-divider pt-10 first:border-t-0 first:pt-0">
      <div className="mb-8">
        <p className="eyebrow">{eyebrow}</p>
        <h2 className="mt-2 text-xl font-bold tracking-tight text-ink">{title}</h2>
      </div>
      <div className="space-y-6">{children}</div>
    </section>
  );
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block space-y-2">
      <div className="flex items-baseline gap-3">
        <span className="text-[13px] font-semibold text-ink-soft">{label}</span>
        {hint && <span className="text-xs text-faint">{hint}</span>}
      </div>
      {children}
    </label>
  );
}

function InputBare({
  value,
  onChange,
  placeholder,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  className?: string;
}) {
  return (
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className={cn(
        "block w-full border-0 border-b border-divider bg-transparent px-0 py-2 text-base text-ink",
        "focus:border-brand focus:ring-0 focus:outline-none placeholder:text-faint",
        className
      )}
    />
  );
}

function NumberBare({
  value,
  onChange,
  min,
  max,
}: {
  value: number;
  onChange: (v: number) => void;
  min?: number;
  max?: number;
}) {
  return (
    <input
      type="number"
      value={Number.isFinite(value) ? value : 0}
      onChange={(e) => onChange(parseInt(e.target.value, 10) || 0)}
      min={min}
      max={max}
      className="block w-full border-0 border-b border-divider bg-transparent px-0 py-2 font-mono text-lg tabular-nums text-ink focus:border-brand focus:ring-0 focus:outline-none"
    />
  );
}

function TextareaBare({
  value,
  onChange,
  rows,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  rows?: number;
  placeholder?: string;
}) {
  return (
    <textarea
      value={value}
      onChange={(e) => onChange(e.target.value)}
      rows={rows}
      placeholder={placeholder}
      className="block w-full resize-y border-0 border-b border-divider bg-transparent px-0 py-2 text-base leading-relaxed text-ink focus:border-brand focus:ring-0 focus:outline-none placeholder:text-faint"
    />
  );
}

function SelectBare<V extends string>({
  value,
  onChange,
  options,
}: {
  value: V;
  onChange: (v: V) => void;
  options: { value: V; label: string }[];
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as V)}
      className="block w-full appearance-none border-0 border-b border-divider bg-transparent px-0 py-2 text-base text-ink focus:border-brand focus:ring-0 focus:outline-none"
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

function Toggle({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={cn(
        "flex w-full items-start gap-3 rounded-2xl border p-4 text-left transition-colors",
        checked
          ? "border-brand bg-brand-soft"
          : "border-divider hover:border-divider-strong"
      )}
    >
      <span
        className={cn(
          "mt-0.5 inline-flex h-5 w-9 shrink-0 items-center rounded-pill transition-colors",
          checked ? "bg-brand" : "bg-divider"
        )}
      >
        <span
          className={cn(
            "h-4 w-4 rounded-pill bg-surface shadow transition-transform",
            checked ? "translate-x-4" : "translate-x-0.5"
          )}
        />
      </span>
      <span className="flex-1">
        <span className="block text-sm font-semibold text-ink">{label}</span>
        {hint && <span className="mt-0.5 block text-xs text-muted">{hint}</span>}
      </span>
    </button>
  );
}
