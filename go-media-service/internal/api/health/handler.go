package health

import (
	"context"
	"time"

	"go-media-service/internal/api"
	"go-media-service/internal/config"
	"go-media-service/internal/metrics"
	"go-media-service/internal/repository"
	"go-media-service/internal/storage"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	manager *config.Manager
	repo    *repository.SQLiteRepository
	storage storage.Driver
	metrics *metrics.Collector
}

func NewHandler(manager *config.Manager, repo *repository.SQLiteRepository, storageDriver storage.Driver, metricsCollector *metrics.Collector) *Handler {
	return &Handler{
		manager: manager,
		repo:    repo,
		storage: storageDriver,
		metrics: metricsCollector,
	}
}

func (h *Handler) Health(c *gin.Context) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), 2*time.Second)
	defer cancel()

	disk, err := h.storage.DiskUsage(ctx)
	status := "ok"
	if err != nil {
		status = "degraded"
	}
	api.Success(c, gin.H{
		"status":    status,
		"service":   h.manager.Base().Server.Name,
		"disk":      disk,
		"metrics":   h.metrics.Snapshot(),
		"runtime":   config.RuntimeValueMap(h.manager.Runtime()),
		"timestamp": time.Now().Format(time.RFC3339),
	})
}
