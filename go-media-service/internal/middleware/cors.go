package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

// CORS 允许浏览器直传到媒体服务。未配置 allowed 时默认拒绝跨域（不给通配，避免误开）。
// Credentials 常关（直传走 Bearer token，不依赖 Cookie）。
func CORS(allowedOrigins []string) gin.HandlerFunc {
	allowed := make(map[string]struct{}, len(allowedOrigins))
	allowAll := false
	for _, o := range allowedOrigins {
		trimmed := strings.TrimRight(strings.TrimSpace(o), "/")
		if trimmed == "*" {
			allowAll = true
			continue
		}
		if trimmed != "" {
			allowed[trimmed] = struct{}{}
		}
	}

	return func(c *gin.Context) {
		origin := strings.TrimRight(c.GetHeader("Origin"), "/")
		if origin != "" {
			ok := allowAll
			if !ok {
				_, ok = allowed[origin]
			}
			if ok {
				c.Header("Access-Control-Allow-Origin", origin)
				c.Header("Vary", "Origin")
				c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
				c.Header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id, X-User-Id, X-Request-Signature, X-Device-Id")
				c.Header("Access-Control-Expose-Headers", "X-Request-Id")
				c.Header("Access-Control-Max-Age", "86400")
			}
		}
		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}
