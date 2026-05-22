package middleware

import (
	"net/http"
	"net/netip"
	"strings"

	"go-media-service/internal/config"
	"go-media-service/internal/constants"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
)

func UploadAuth(cfg *config.Config, authService *service.AuthService) gin.HandlerFunc {
	return func(c *gin.Context) {
		authorization := bearerToken(c.GetHeader(cfg.Auth.Upload.HeaderName))
		if authorization != "" {
			principal, err := authService.ValidateUploadToken(authorization)
			if err == nil {
				c.Set("uploader_id", principal.UserID)
				c.Set("uploader_biz_type", principal.BizType)
				c.Next()
				return
			}
		}
		if cfg.Auth.Upload.AllowDebugUserHeader {
			if userID := strings.TrimSpace(c.GetHeader(cfg.Auth.Upload.DebugUserHeader)); userID != "" {
				c.Set("uploader_id", userID)
				c.Next()
				return
			}
		}
		if cfg.Auth.Upload.Enabled {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"code":      constants.CodeUnauthorized,
				"message":   "upload auth required",
				"requestId": requestID(c),
			})
			return
		}
		c.Next()
	}
}

func AdminAPIAuth(cfg *config.Config, authService *service.AuthService) gin.HandlerFunc {
	return func(c *gin.Context) {
		serviceToken := strings.TrimSpace(c.GetHeader("X-Service-Token"))
		if authService.IsServiceToken(serviceToken) {
			if !ipAllowed(c.ClientIP(), cfg.Security.JavaAdminAllowIPs) {
				c.AbortWithStatusJSON(http.StatusForbidden, gin.H{
					"code":      constants.CodeForbidden,
					"message":   "admin caller ip is not allowed",
					"requestId": requestID(c),
				})
				return
			}
			c.Set("admin_actor", "java-backend")
			c.Next()
			return
		}

		token := bearerToken(c.GetHeader("Authorization"))
		if token == "" {
			if cookie, err := c.Cookie(cfg.Auth.Admin.CookieName); err == nil {
				token = cookie
			}
		}
		if token == "" {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"code":      constants.CodeUnauthorized,
				"message":   "admin auth required",
				"requestId": requestID(c),
			})
			return
		}
		principal, err := authService.ValidateAdminToken(token)
		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{
				"code":      constants.CodeUnauthorized,
				"message":   "invalid admin token",
				"requestId": requestID(c),
			})
			return
		}
		c.Set("admin_actor", principal.Username)
		c.Next()
	}
}

func PanelAuth(cfg *config.Config, authService *service.AuthService) gin.HandlerFunc {
	return func(c *gin.Context) {
		cookie, err := c.Cookie(cfg.Auth.Admin.CookieName)
		if err != nil {
			c.Redirect(http.StatusFound, cfg.Panel.BasePath+"/login")
			c.Abort()
			return
		}
		if _, err := authService.ValidateAdminToken(cookie); err != nil {
			c.Redirect(http.StatusFound, cfg.Panel.BasePath+"/login")
			c.Abort()
			return
		}
		c.Next()
	}
}

func bearerToken(header string) string {
	if header == "" {
		return ""
	}
	parts := strings.SplitN(header, " ", 2)
	if len(parts) == 2 && strings.EqualFold(parts[0], "bearer") {
		return strings.TrimSpace(parts[1])
	}
	return strings.TrimSpace(header)
}

func ipAllowed(clientIP string, allowList []string) bool {
	if len(allowList) == 0 {
		return true
	}
	addr, err := netip.ParseAddr(strings.TrimSpace(clientIP))
	if err != nil {
		return false
	}
	for _, item := range allowList {
		item = strings.TrimSpace(item)
		if item == "" {
			continue
		}
		if prefix, err := netip.ParsePrefix(item); err == nil && prefix.Contains(addr) {
			return true
		}
		if allowedAddr, err := netip.ParseAddr(item); err == nil && allowedAddr == addr {
			return true
		}
	}
	return false
}
