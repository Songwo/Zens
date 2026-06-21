# syntax=docker/dockerfile:1

# --- Build frontend ---
FROM node:20-alpine AS web-builder
WORKDIR /web

COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY web/ ./
ARG VITE_HCAPTCHA_SITE_KEY=10000000-ffff-ffff-ffff-000000000001
ENV VITE_HCAPTCHA_SITE_KEY=${VITE_HCAPTCHA_SITE_KEY}
RUN npm run build

# --- Build backend ---
FROM golang:1.24-alpine AS backend-builder
WORKDIR /server

ENV GOPROXY=https://mirrors.aliyun.com/goproxy/,direct

COPY server/go.mod server/go.sum ./
RUN go mod download

COPY server/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/miubox main.go

# --- Runtime image ---
FROM alpine:3.20
WORKDIR /app

RUN apk add --no-cache ca-certificates tzdata \
    && addgroup -S cdk && adduser -S cdk -G cdk \
    && mkdir -p /app/data /app/dist \
    && chown -R cdk:cdk /app

COPY --from=backend-builder /out/miubox /app/miubox
COPY --from=web-builder /web/dist /app/dist

ENV CDK_AIRDROP_ADDR=:8088 \
    CDK_AIRDROP_DATA_FILE=/app/data/state.json \
    CDK_AIRDROP_PUBLIC_DIR=/app/dist

EXPOSE 8088
VOLUME ["/app/data"]

USER cdk
CMD ["/app/miubox"]
