package middleware

import (
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/metrics"
	"go-media-service/internal/utils"

	"github.com/gin-gonic/gin"
	"github.com/rs/zerolog/log"
)

func RequestContext(cfg *config.Config) gin.HandlerFunc {
	return func(c *gin.Context) {
		requestID := c.GetHeader(cfg.Security.RequestIDHeader)
		if requestID == "" {
			requestID = utils.NewID("req")
		}
		c.Set("request_id", requestID)
		c.Writer.Header().Set(cfg.Security.RequestIDHeader, requestID)
		c.Next()
	}
}

func AccessLog(metricsCollector *metrics.Collector) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()

		duration := time.Since(start)
		metricsCollector.ObserveRequest(c.Request.Method, c.FullPath(), c.Writer.Status(), duration)
		log.Info().
			Str("request_id", requestID(c)).
			Str("method", c.Request.Method).
			Str("path", c.Request.URL.Path).
			Int("status", c.Writer.Status()).
			Dur("duration", duration).
			Str("client_ip", c.ClientIP()).
			Msg("request completed")
	}
}

func requestID(c *gin.Context) string {
	value, _ := c.Get("request_id")
	if requestID, ok := value.(string); ok {
		return requestID
	}
	return ""
}
