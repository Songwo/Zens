import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./src/app/**/*.{ts,tsx}",
    "./src/components/**/*.{ts,tsx}",
    "./src/lib/**/*.{ts,tsx}",
  ],
  theme: {
    container: {
      center: true,
      padding: {
        DEFAULT: "1.25rem",
        sm: "1.5rem",
        lg: "2.5rem",
      },
      screens: {
        sm: "640px",
        md: "768px",
        lg: "1024px",
        xl: "1180px",
      },
    },
    extend: {
      colors: {
        brand: {
          DEFAULT: "var(--zens-yellow)",
          soft: "var(--zens-yellow-soft)",
          hover: "var(--zens-yellow-hover)",
        },
        bg: "var(--bg)",
        surface: "var(--surface)",
        "surface-elev": "var(--surface-elev)",
        ink: "var(--ink)",
        "ink-soft": "var(--ink-soft)",
        muted: "var(--muted)",
        faint: "var(--faint)",
        divider: "var(--divider)",
      },
      fontFamily: {
        sans: [
          "var(--font-inter)",
          "Inter",
          "PingFang SC",
          "Hiragino Sans GB",
          "Microsoft YaHei",
          "system-ui",
          "sans-serif",
        ],
        mono: [
          "var(--font-mono)",
          "JetBrains Mono",
          "SFMono-Regular",
          "Menlo",
          "Consolas",
          "monospace",
        ],
      },
      fontSize: {
        display: ["clamp(2.5rem, 6vw, 4rem)", { lineHeight: "1.05", letterSpacing: "-0.02em" }],
        eyebrow: ["0.72rem", { lineHeight: "1", letterSpacing: "0.16em" }],
      },
      letterSpacing: {
        tightest: "-0.03em",
      },
      borderRadius: {
        pill: "9999px",
      },
      backdropBlur: {
        xs: "2px",
      },
      keyframes: {
        "fade-in": {
          "0%": { opacity: "0", transform: "translateY(4px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "rise": {
          "0%": { opacity: "0", transform: "translateY(8px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "shimmer": {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
      animation: {
        "fade-in": "fade-in 360ms cubic-bezier(.2,.7,.2,1) both",
        "rise": "rise 480ms cubic-bezier(.2,.7,.2,1) both",
        "shimmer": "shimmer 1.6s linear infinite",
      },
    },
  },
  plugins: [],
};

export default config;
