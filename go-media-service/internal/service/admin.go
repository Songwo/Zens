package service

import (
	"context"
	"net/http"
	"os"
	"runtime"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/metrics"
	"go-media-service/internal/model"
	"go-media-service/internal/repository"
	"go-media-service/internal/storage"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/mem"
	"github.com/shirou/gopsutil/v4/process"
)

type AdminService struct {
	manager        *config.Manager
	repo           *repository.SQLiteRepository
	storage        storage.Driver
	metrics        *metrics.Collector
	runtimeService *RuntimeConfigService
	startedAt      time.Time
}

func NewAdminService(manager *config.Manager, repo *repository.SQLiteRepository, storageDriver storage.Driver, metricsCollector *metrics.Collector, runtimeService *RuntimeConfigService) *AdminService {
	return &AdminService{
		manager:        manager,
		repo:           repo,
		storage:        storageDriver,
		metrics:        metricsCollector,
		runtimeService: runtimeService,
		startedAt:      time.Now(),
	}
}

func (s *AdminService) Dashboard(ctx context.Context) (*model.Dashboard, *AppError) {
	stats, err := s.repo.SummaryStats(ctx)
	if err != nil {
		return nil, NewAppError(50000, http.StatusInternalServerError, "load dashboard stats failed", err)
	}
	recentUploads, err := s.repo.ListRecentUploads(ctx, 12)
	if err != nil {
		return nil, NewAppError(50000, http.StatusInternalServerError, "load recent uploads failed", err)
	}
	disk, err := s.storage.DiskUsage(ctx)
	if err != nil {
		return nil, NewAppError(50000, http.StatusInternalServerError, "load disk usage failed", err)
	}
	snapshot := s.metrics.Snapshot()
	return &model.Dashboard{
		ServiceStatus:         "running",
		CurrentUploadingTasks: snapshot.CurrentUploads,
		UploadSuccessCount:    stats.UploadSuccessCount,
		UploadFailureCount:    stats.UploadFailureCount,
		ImageCount:            stats.ImageCount,
		VideoCount:            stats.VideoCount,
		FileTotalSizeBytes:    stats.FileTotalSizeBytes,
		Disk:                  *disk,
		QPS:                   snapshot.QPS,
		AverageResponseMS:     snapshot.AverageResponseMS,
		ErrorRate:             snapshot.ErrorRate,
		ActiveConnections:     snapshot.ActiveConnections,
		ChunkInProgressCount:  stats.ChunkInProgressCount,
		RecentUploads:         recentUploads,
		RateLimit: map[string]any{
			"per_ip_rps":   s.manager.Runtime().PerIPRPS,
			"per_user_rps": s.manager.Runtime().PerUserRPS,
		},
		RuntimeConfig: config.RuntimeValueMap(s.manager.Runtime()),
	}, nil
}

func (s *AdminService) ListTasks(ctx context.Context, query model.ListQuery) ([]model.UploadTask, int64, *AppError) {
	items, total, err := s.repo.ListTasks(ctx, query)
	if err != nil {
		return nil, 0, NewAppError(50000, http.StatusInternalServerError, "list upload tasks failed", err)
	}
	return items, total, nil
}

func (s *AdminService) ListFiles(ctx context.Context, query model.FileListFilter) ([]model.MediaFile, int64, *AppError) {
	items, total, err := s.repo.ListFiles(ctx, query)
	if err != nil {
		return nil, 0, NewAppError(50000, http.StatusInternalServerError, "list media files failed", err)
	}
	return items, total, nil
}

func (s *AdminService) GetFile(ctx context.Context, id string) (*model.MediaFile, *AppError) {
	file, err := s.repo.GetFileByID(ctx, id)
	if err != nil {
		return nil, NewAppError(40400, http.StatusNotFound, "media file not found", err)
	}
	return file, nil
}

func (s *AdminService) DeleteFiles(ctx context.Context, ids []string, actor string) ([]model.MediaFile, *AppError) {
	fileService := NewFileService(s.manager, s.repo, s.storage, s.metrics)
	return fileService.DeleteFiles(ctx, ids, actor)
}

func (s *AdminService) Stats(ctx context.Context) (*model.SummaryStats, *AppError) {
	stats, err := s.repo.SummaryStats(ctx)
	if err != nil {
		return nil, NewAppError(50000, http.StatusInternalServerError, "load summary stats failed", err)
	}
	return stats, nil
}

func (s *AdminService) System(ctx context.Context) (*model.SystemStatus, *AppError) {
	host, _ := os.Hostname()
	memStats := runtime.MemStats{}
	runtime.ReadMemStats(&memStats)

	systemMem, _ := mem.VirtualMemoryWithContext(ctx)
	systemCPU, _ := cpu.PercentWithContext(ctx, 0, false)
	var systemCPUPercent float64
	if len(systemCPU) > 0 {
		systemCPUPercent = systemCPU[0]
	}
	var processCPUPercent float64
	proc, err := process.NewProcess(int32(os.Getpid()))
	if err == nil {
		processCPUPercent, _ = proc.CPUPercentWithContext(ctx)
	}

	status := &model.SystemStatus{
		Hostname:           host,
		UptimeSeconds:      uint64(time.Since(s.startedAt).Seconds()),
		ProcessCPUPercent:  processCPUPercent,
		SystemCPUPercent:   systemCPUPercent,
		ProcessMemoryBytes: memStats.Alloc,
		Goroutines:         runtime.NumGoroutine(),
		GCCount:            memStats.NumGC,
		StartedAt:          s.startedAt,
		LastConfigUpdateAt: s.runtimeService.LastUpdatedAt(),
	}
	if systemMem != nil {
		status.SystemMemoryBytes = systemMem.Total
		status.SystemMemoryUsed = systemMem.Used
		status.SystemMemoryPercent = systemMem.UsedPercent
	}
	return status, nil
}

func (s *AdminService) ConfigView() model.RuntimeConfigView {
	return s.runtimeService.View()
}

func (s *AdminService) UpdateConfig(ctx context.Context, updates map[string]string, actor string) (model.RuntimeConfigView, *AppError) {
	view, err := s.runtimeService.Update(ctx, updates, actor)
	if err != nil {
		return model.RuntimeConfigView{}, NewAppError(42200, http.StatusUnprocessableEntity, "update runtime config failed", err)
	}
	if s.manager.Base().Security.EnableDeleteAudit {
		_ = s.repo.AppendAuditLog(ctx, &model.AuditLog{
			Actor:      actor,
			Action:     "config.update",
			TargetType: "runtime_config",
			TargetID:   "runtime",
			Detail:     "runtime config updated",
			CreatedAt:  time.Now(),
		})
	}
	return view, nil
}
