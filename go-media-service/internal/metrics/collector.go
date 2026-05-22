package metrics

import (
	"net"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

type Snapshot struct {
	QPS               float64
	AverageResponseMS float64
	ErrorRate         float64
	ActiveConnections int64
	CurrentUploads    int64
	UploadSuccess     int64
	UploadFailure     int64
}

type Collector struct {
	serviceName string
	startedAt   time.Time

	requestsTotal    *prometheus.CounterVec
	requestDuration  *prometheus.HistogramVec
	uploadTotal      *prometheus.CounterVec
	activeUploads    prometheus.Gauge
	activeConnection prometheus.Gauge

	totalRequests      atomic.Int64
	errorRequests      atomic.Int64
	totalLatencyMicros atomic.Int64
	uploadSuccess      atomic.Int64
	uploadFailure      atomic.Int64
	currentUploads     atomic.Int64
	activeConnections  atomic.Int64

	windowMu sync.Mutex
	buckets  [60]int64
	bucketTS [60]int64
	openConn sync.Map
}

func NewCollector(serviceName string) *Collector {
	return &Collector{
		serviceName: serviceName,
		startedAt:   time.Now(),
		requestsTotal: promauto.NewCounterVec(prometheus.CounterOpts{
			Name: "media_service_http_requests_total",
			Help: "Total number of HTTP requests",
		}, []string{"method", "path", "status"}),
		requestDuration: promauto.NewHistogramVec(prometheus.HistogramOpts{
			Name:    "media_service_http_request_duration_seconds",
			Help:    "HTTP request duration distribution",
			Buckets: prometheus.DefBuckets,
		}, []string{"method", "path", "status"}),
		uploadTotal: promauto.NewCounterVec(prometheus.CounterOpts{
			Name: "media_service_upload_total",
			Help: "Upload result counter",
		}, []string{"media_type", "status"}),
		activeUploads: promauto.NewGauge(prometheus.GaugeOpts{
			Name: "media_service_active_uploads",
			Help: "Current uploading task count",
		}),
		activeConnection: promauto.NewGauge(prometheus.GaugeOpts{
			Name: "media_service_active_connections",
			Help: "Current active connections",
		}),
	}
}

func (c *Collector) ObserveRequest(method string, path string, status int, duration time.Duration) {
	statusText := http.StatusText(status)
	if statusText == "" {
		statusText = "UNKNOWN"
	}
	c.requestsTotal.WithLabelValues(method, path, statusText).Inc()
	c.requestDuration.WithLabelValues(method, path, statusText).Observe(duration.Seconds())
	c.totalRequests.Add(1)
	c.totalLatencyMicros.Add(duration.Microseconds())
	if status >= http.StatusBadRequest {
		c.errorRequests.Add(1)
	}
	c.bumpWindow()
}

func (c *Collector) BeginUpload() func(success bool, mediaType string) {
	c.currentUploads.Add(1)
	c.activeUploads.Inc()
	return func(success bool, mediaType string) {
		c.currentUploads.Add(-1)
		c.activeUploads.Dec()
		status := "success"
		if success {
			c.uploadSuccess.Add(1)
		} else {
			status = "failed"
			c.uploadFailure.Add(1)
		}
		c.uploadTotal.WithLabelValues(mediaType, status).Inc()
	}
}

func (c *Collector) Snapshot() Snapshot {
	total := c.totalRequests.Load()
	errors := c.errorRequests.Load()
	avgMS := 0.0
	if total > 0 {
		avgMS = float64(c.totalLatencyMicros.Load()) / 1000 / float64(total)
	}
	errorRate := 0.0
	if total > 0 {
		errorRate = float64(errors) / float64(total)
	}
	return Snapshot{
		QPS:               c.qps(),
		AverageResponseMS: avgMS,
		ErrorRate:         errorRate,
		ActiveConnections: c.activeConnections.Load(),
		CurrentUploads:    c.currentUploads.Load(),
		UploadSuccess:     c.uploadSuccess.Load(),
		UploadFailure:     c.uploadFailure.Load(),
	}
}

func (c *Collector) ConnStateHook() func(net.Conn, http.ConnState) {
	return func(conn net.Conn, state http.ConnState) {
		switch state {
		case http.StateNew, http.StateActive, http.StateIdle:
			if _, loaded := c.openConn.LoadOrStore(conn, struct{}{}); !loaded {
				c.activeConnections.Add(1)
				c.activeConnection.Inc()
			}
		case http.StateHijacked, http.StateClosed:
			if _, loaded := c.openConn.LoadAndDelete(conn); loaded && c.activeConnections.Load() > 0 {
				c.activeConnections.Add(-1)
				c.activeConnection.Dec()
			}
		}
	}
}

func (c *Collector) StartedAt() time.Time {
	return c.startedAt
}

func (c *Collector) bumpWindow() {
	c.windowMu.Lock()
	defer c.windowMu.Unlock()
	sec := time.Now().Unix()
	index := sec % int64(len(c.buckets))
	if c.bucketTS[index] != sec {
		c.bucketTS[index] = sec
		c.buckets[index] = 0
	}
	c.buckets[index]++
}

func (c *Collector) qps() float64 {
	c.windowMu.Lock()
	defer c.windowMu.Unlock()
	var total int64
	now := time.Now().Unix()
	var window int64
	for idx, value := range c.buckets {
		if now-c.bucketTS[idx] >= int64(len(c.buckets)) {
			continue
		}
		total += value
		window++
	}
	if window == 0 {
		return 0
	}
	return float64(total) / float64(window)
}
