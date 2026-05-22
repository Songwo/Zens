"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

interface Props {
  value: string;
  onChange: (url: string) => void;
  /** 渐变备用色 - 如果还没传图,展示这个 */
  gradient?: string | null;
  className?: string;
}

export function ImageUploader({ value, onChange, gradient, className }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    const file = files[0];
    if (!file.type.startsWith("image/")) {
      toast.error("请选择图片文件");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast.error("图片不能超过 5MB");
      return;
    }
    setUploading(true);
    try {
      // 1) 向子站申请一个 R2 presigned PUT URL
      const signRes = await fetch("/api/admin/upload", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          filename: file.name,
          contentType: file.type,
          size: file.size,
        }),
        credentials: "include",
      });
      const sign = await signRes.json();
      if (!signRes.ok) throw new Error(sign?.message || "签发上传链接失败");

      // 2) 浏览器直接 PUT 到 R2,不再走子站服务器
      const putRes = await fetch(sign.uploadUrl, {
        method: "PUT",
        body: file,
        headers: sign.headers ?? { "Content-Type": file.type },
      });
      if (!putRes.ok) {
        throw new Error(`R2 PUT ${putRes.status}: ${await putRes.text().catch(() => "")}`);
      }

      onChange(sign.publicUrl);
      toast.success("封面已上传");
    } catch (e) {
      toast.error("上传失败", { description: (e as Error).message });
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  const hasImg = !!value;

  return (
    <div className={cn("space-y-3", className)}>
      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") inputRef.current?.click();
        }}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          handleFiles(e.dataTransfer.files);
        }}
        className={cn(
          "group relative flex h-56 w-full cursor-pointer items-center justify-center overflow-hidden rounded-3xl transition-all",
          "border border-dashed",
          dragging
            ? "border-brand bg-brand-soft"
            : "border-divider hover:border-divider-strong"
        )}
        style={
          hasImg
            ? { backgroundImage: `url(${value})`, backgroundSize: "cover", backgroundPosition: "center" }
            : { background: gradient || "linear-gradient(135deg, var(--surface-elev), var(--surface))" }
        }
      >
        <div
          className={cn(
            "flex flex-col items-center gap-2 rounded-2xl bg-black/40 px-5 py-3 text-white opacity-0 transition-opacity backdrop-blur-md",
            (!hasImg || dragging) && "opacity-100",
            "group-hover:opacity-100"
          )}
        >
          {uploading ? (
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" className="animate-spin">
              <path d="M21 12a9 9 0 1 1-6.2-8.55" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 16V4M5 9l7-7 7 7M5 20h14" />
            </svg>
          )}
          <span className="text-xs font-medium">
            {uploading ? "上传中…" : hasImg ? "点击替换" : "点击或拖拽上传封面"}
          </span>
          <span className="text-[10px] opacity-70">JPG / PNG / WebP · ≤ 5MB</span>
        </div>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => handleFiles(e.target.files)}
      />

      {hasImg && (
        <div className="flex items-center gap-2 text-xs text-muted">
          <code className="truncate font-mono">{value}</code>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onChange("");
            }}
            className="ml-auto rounded-pill px-2 py-0.5 text-faint transition-colors hover:bg-surface-elev hover:text-rose-500"
          >
            移除
          </button>
        </div>
      )}
    </div>
  );
}
