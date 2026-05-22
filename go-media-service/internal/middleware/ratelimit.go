package middleware

import (
	"net/http"
	"strconv"
	"sync"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/constants"

	"github.com/gin-gonic/gin"
	"golang.org/x/time/rate"
)

type limiterEntry struct {
	limit   int
	limiter *rate.Limiter
}

type LimiterStore struct {
	mu    sync.Mutex
	items map[string]*limiterEntry
}

func NewLimiterStore() *LimiterStore {
	return &LimiterStore{
		items: make(map[string]*limiterEntry),
	}
}

func (s *LimiterStore) Allow(key string, limit int) bool {
	if key == "" || limit <= 0 {
		return true
	}
	s.mu.Lock()
	defer s.mu.Unlock()

	entry, exists := s.items[key]
	if !exists || entry.limit != limit {
		entry = &limiterEntry{
			limit:   limit,
			limiter: rate.NewLimiter(rate.Limit(limit), limit),
		}
		s.items[key] = entry
	}
	return entry.limiter.Allow()
}

func UploadRateLimit(manager *config.Manager) gin.HandlerFunc {
	ipStore := NewLimiterStore()
	userStore := NewLimiterStore()

	return func(c *gin.Context) {
		runtimeCfg := manager.Runtime()
		if !ipStore.Allow("ip:"+c.ClientIP(), runtimeCfg.PerIPRPS) {
			rejectRateLimit(c, "ip")
			return
		}
		if userID, exists := c.Get("uploader_id"); exists {
			if value, ok := userID.(string); ok && value != "" {
				if !userStore.Allow("user:"+value, runtimeCfg.PerUserRPS) {
					rejectRateLimit(c, "user")
					return
				}
			}
		}
		c.Next()
	}
}

func RequestBodyLimit(manager *config.Manager) gin.HandlerFunc {
	return func(c *gin.Context) {
		maxBytes := manager.Base().Security.MaxRequestBodyMB * 1024 * 1024
		c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxBytes)
		c.Next()
	}
}

func rejectRateLimit(c *gin.Context, dimension string) {
	c.Header("Retry-After", strconv.FormatInt(int64(1), 10))
	c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
		"code":      constants.CodeRateLimited,
		"message":   "rate limit exceeded by " + dimension,
		"requestId": requestID(c),
		"at":        time.Now().Format(time.RFC3339),
	})
}
