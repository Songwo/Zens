FROM golang:1.22-alpine AS builder

WORKDIR /app

RUN apk add --no-cache ca-certificates tzdata

COPY go.mod go.sum ./
RUN go mod download

COPY cmd ./cmd
COPY internal ./internal
COPY web ./web
COPY config.yaml ./

RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o /out/media-service ./cmd/media-service

FROM alpine:3.20

WORKDIR /app

RUN apk add --no-cache ca-certificates tzdata

COPY --from=builder /out/media-service ./media-service
COPY config.yaml ./config.yaml
COPY web ./web

RUN mkdir -p /app/data /app/uploads /app/tmp /app/logs

EXPOSE 8090

ENTRYPOINT ["./media-service"]
