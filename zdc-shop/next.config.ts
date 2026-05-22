import type { NextConfig } from "next";

const r2Public = process.env.R2_PUBLIC_BASE_URL;
const r2Host = (() => {
  try {
    return r2Public ? new URL(r2Public).hostname : null;
  } catch {
    return null;
  }
})();

const nextConfig: NextConfig = {
  poweredByHeader: false,
  output: "standalone",
  reactStrictMode: true,
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "**.allinsong.top" },
      { protocol: "https", hostname: "images.unsplash.com" },
      { protocol: "https", hostname: "cdn.zens.community" },
      ...(r2Host && !["cdn.zens.community"].includes(r2Host)
        ? [{ protocol: "https" as const, hostname: r2Host }]
        : []),
    ],
  },
  experimental: {
    optimizePackageImports: ["lucide-react"],
  },
};

export default nextConfig;
