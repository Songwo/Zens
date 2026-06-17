# syntax=docker/dockerfile:1

FROM node:20-alpine AS web-build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY index.html postcss.config.cjs tailwind.config.* tsconfig*.json vite.config.* ./
COPY public ./public
COPY src ./src
COPY server ./server
RUN npm run build

FROM golang:1.22-alpine AS go-build
WORKDIR /src

COPY server/go.mod ./server/go.mod
COPY server/main.go ./server/main.go
COPY --from=web-build /app/server/web ./server/web

WORKDIR /src/server
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/zens-lottery .

FROM alpine:3.20
WORKDIR /app

RUN addgroup -S zens && adduser -S zens -G zens \
  && mkdir -p /app/data \
  && chown -R zens:zens /app

COPY --from=go-build /out/zens-lottery /app/zens-lottery

ENV LOTTERY_ADDR=:8093 \
  LOTTERY_DATA=/app/data/state.json \
  LOTTERY_LOGO_URL=/logo.png \
  LOTTERY_ALLOW_DEMO_SSO_FALLBACK=false \
  LOTTERY_COMMENT_MAX_PAGES=50

EXPOSE 8093
VOLUME ["/app/data"]

USER zens
ENTRYPOINT ["/app/zens-lottery"]
