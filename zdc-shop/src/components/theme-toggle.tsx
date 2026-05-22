"use client";

import { useEffect, useState } from "react";
import { useTheme } from "next-themes";
import { cn } from "@/lib/utils";

export function ThemeToggle({ className }: { className?: string }) {
  const [mounted, setMounted] = useState(false);
  const { resolvedTheme, setTheme } = useTheme();

  useEffect(() => {
    setMounted(true);
  }, []);

  const isDark = mounted && resolvedTheme === "dark";

  return (
    <button
      type="button"
      onClick={() => setTheme(isDark ? "light" : "dark")}
      aria-label={isDark ? "切换到浅色主题" : "切换到深色主题"}
      title={isDark ? "切换到浅色主题" : "切换到深色主题"}
      className={cn(
        "relative inline-flex h-9 w-9 items-center justify-center rounded-pill text-ink-soft transition-colors hover:bg-surface-elev hover:text-ink",
        className
      )}
    >
      {/* sun */}
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={cn(
          "h-[18px] w-[18px] transition-transform duration-500",
          isDark ? "scale-0 -rotate-90" : "scale-100 rotate-0"
        )}
        style={{ position: isDark ? "absolute" : "static" }}
        aria-hidden
      >
        <circle cx="12" cy="12" r="4" />
        <path d="M12 3v1.8M12 19.2V21M3 12h1.8M19.2 12H21M5.6 5.6l1.3 1.3M17.1 17.1l1.3 1.3M5.6 18.4l1.3-1.3M17.1 6.9l1.3-1.3" />
      </svg>
      {/* moon */}
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={cn(
          "absolute h-[18px] w-[18px] transition-transform duration-500",
          isDark ? "scale-100 rotate-0" : "scale-0 rotate-90"
        )}
        aria-hidden
      >
        <path d="M21 13.5A8.5 8.5 0 1 1 10.5 3a6.5 6.5 0 0 0 10.5 10.5Z" />
      </svg>
    </button>
  );
}
