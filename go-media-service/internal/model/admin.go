package model

import "time"

type Dashboard struct {
	ServiceStatus         string         `json:"serviceStatus"`
	CurrentUploadingTasks int64          `json:"currentUploadingTasks"`
	UploadSuccessCount    int64          `json:"uploadSuccessCount"`
	UploadFailureCount    int64          `json:"uploadFailureCount"`
	ImageCount            int64          `json:"imageCount"`
	VideoCount            int64          `json:"videoCount"`
	FileTotalSizeBytes    int64          `json:"fileTotalSizeBytes"`
	Disk                  DiskUsage      `json:"disk"`
	QPS                   float64        `json:"qps"`
	AverageResponseMS     float64        `json:"averageResponseMs"`
	ErrorRate             float64        `json:"errorRate"`
	ActiveConnections     int64          `json:"activeConnections"`
	ChunkInProgressCount  int64          `json:"chunkInProgressCount"`
	RecentUploads         []UploadTask   `json:"recentUploads"`
	RateLimit             map[string]any `json:"rateLimit"`
	RuntimeConfig         map[string]any `json:"runtimeConfig"`
}

type DiskUsage struct {
	RootDir     string  `json:"rootDir"`
	TotalBytes  uint64  `json:"totalBytes"`
	UsedBytes   uint64  `json:"usedBytes"`
	FreeBytes   uint64  `json:"freeBytes"`
	UsedPercent float64 `json:"usedPercent"`
}

type SystemStatus struct {
	Hostname            string    `json:"hostname"`
	UptimeSeconds       uint64    `json:"uptimeSeconds"`
	ProcessCPUPercent   float64   `json:"processCpuPercent"`
	SystemCPUPercent    float64   `json:"systemCpuPercent"`
	ProcessMemoryBytes  uint64    `json:"processMemoryBytes"`
	SystemMemoryBytes   uint64    `json:"systemMemoryBytes"`
	SystemMemoryUsed    uint64    `json:"systemMemoryUsedBytes"`
	SystemMemoryPercent float64   `json:"systemMemoryPercent"`
	Goroutines          int       `json:"goroutines"`
	GCCount             uint32    `json:"gcCount"`
	LastConfigUpdateAt  *string   `json:"lastConfigUpdateAt,omitempty"`
	StartedAt           time.Time `json:"startedAt"`
}

type RuntimeConfigView struct {
	Static  map[string]any `json:"static"`
	Runtime map[string]any `json:"runtime"`
}

type BatchDeleteRequest struct {
	IDs []string `json:"ids"`
}

type SummaryStats struct {
	ImageCount           int64 `json:"imageCount"`
	VideoCount           int64 `json:"videoCount"`
	FileTotalSizeBytes   int64 `json:"fileTotalSizeBytes"`
	UploadSuccessCount   int64 `json:"uploadSuccessCount"`
	UploadFailureCount   int64 `json:"uploadFailureCount"`
	ChunkInProgressCount int64 `json:"chunkInProgressCount"`
}

type AuditLog struct {
	ID         int64     `json:"id"`
	Actor      string    `json:"actor"`
	Action     string    `json:"action"`
	TargetType string    `json:"targetType"`
	TargetID   string    `json:"targetId"`
	Detail     string    `json:"detail"`
	CreatedAt  time.Time `json:"createdAt"`
}
