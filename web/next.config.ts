import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://localhost:7800/:path*', // 代理到 Spring Boot 后端
      },
      // 静态资源 图片/资源代理
      {
        source: '/static/:path*',
        destination: 'http://localhost:7800/static/:path*',
      }
    ]
  }
};

export default nextConfig;
