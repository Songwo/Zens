package middleware

import (
	"go-media-service/internal/constants"

	"github.com/gin-gonic/gin"
	"github.com/rs/zerolog/log"
)

func Recovery() gin.HandlerFunc {
	return gin.CustomRecovery(func(c *gin.Context, recovered any) {
		log.Error().
			Interface("panic", recovered).
			Str("path", c.Request.URL.Path).
			Msg("panic recovered")
		c.AbortWithStatusJSON(500, gin.H{
			"code":      constants.CodeInternal,
			"message":   "internal server error",
			"requestId": requestID(c),
		})
	})
}
