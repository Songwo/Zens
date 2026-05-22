# ─── Stage 1: deps ───────────────────────────────────────────────
FROM node:20-alpine AS deps
WORKDIR /app

# 用 Alpine 自带 musl + 安装 openssl 依赖 (prisma 需要)
RUN apk add --no-cache libc6-compat openssl

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund


# ─── Stage 2: build ──────────────────────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /app

RUN apk add --no-cache libc6-compat openssl

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Prisma 生成
RUN npx prisma generate

# 注入构建期需要的占位 env (NEXT_PUBLIC_* 会被打进静态产物)
ENV NEXT_TELEMETRY_DISABLED=1 \
    NODE_ENV=production

RUN npm run build


# ─── Stage 3: runtime ────────────────────────────────────────────
FROM node:20-alpine AS runner
WORKDIR /app

RUN apk add --no-cache libc6-compat openssl tini

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Next.js standalone 模式产出
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
# Prisma 引擎二进制 + schema 留一份给运行时
COPY --from=builder /app/node_modules/@prisma ./node_modules/@prisma
COPY --from=builder /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=builder /app/prisma ./prisma

# 非 root 运行
RUN addgroup -S nodejs && adduser -S nextjs -G nodejs && chown -R nextjs:nodejs /app
USER nextjs

EXPOSE 3000

# tini 处理信号 + zombie
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "server.js"]
