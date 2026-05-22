package config

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"strings"
	"sync"

	"go-media-service/internal/utils"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Server   ServerConfig   `yaml:"server"`
	Database DatabaseConfig `yaml:"database"`
	Storage  StorageConfig  `yaml:"storage"`
	Upload   UploadConfig   `yaml:"upload"`
	Auth     AuthConfig     `yaml:"auth"`
	Security SecurityConfig `yaml:"security"`
	Logging  LoggingConfig  `yaml:"logging"`
	Panel    PanelConfig    `yaml:"panel"`
}

type ServerConfig struct {
	Name                   string `yaml:"name"`
	Host                   string `yaml:"host"`
	Port                   int    `yaml:"port"`
	ReadTimeoutSeconds     int    `yaml:"read_timeout_seconds"`
	WriteTimeoutSeconds    int    `yaml:"write_timeout_seconds"`
	IdleTimeoutSeconds     int    `yaml:"idle_timeout_seconds"`
	ShutdownTimeoutSeconds int    `yaml:"shutdown_timeout_seconds"`
	PublicBaseURL          string `yaml:"public_base_url"`
	TrustProxy             bool   `yaml:"trust_proxy"`
	EnablePprof            bool   `yaml:"enable_pprof"`
	EnableMetrics          bool   `yaml:"enable_metrics"`
}

type DatabaseConfig struct {
	Driver string `yaml:"driver"`
	DSN    string `yaml:"dsn"`
}

type StorageConfig struct {
	Driver         string `yaml:"driver"`
	RootDir        string `yaml:"root_dir"`
	ImageDir       string `yaml:"image_dir"`
	VideoDir       string `yaml:"video_dir"`
	ChunkDir       string `yaml:"chunk_dir"`
	TempDir        string `yaml:"temp_dir"`
	DatePathLayout string `yaml:"date_path_layout"`
	DirMode        string `yaml:"dir_mode"`
	FileMode       string `yaml:"file_mode"`
}

type UploadConfig struct {
	Enabled                 bool     `yaml:"enabled"`
	MaxMultipartMemoryMB    int64    `yaml:"max_multipart_memory_mb"`
	MaxBatchFiles           int      `yaml:"max_batch_files"`
	MaxImageSizeMB          int64    `yaml:"max_image_size_mb"`
	MaxVideoSizeMB          int64    `yaml:"max_video_size_mb"`
	ChunkSizeMB             int64    `yaml:"chunk_size_mb"`
	MaxChunkCount           int      `yaml:"max_chunk_count"`
	ChunkExpireHours        int      `yaml:"chunk_expire_hours"`
	MergeConcurrency        int      `yaml:"merge_concurrency"`
	GlobalUploadConcurrency int      `yaml:"global_upload_concurrency"`
	AllowImageExts          []string `yaml:"allow_image_exts"`
	AllowVideoExts          []string `yaml:"allow_video_exts"`
	AllowImageMimes         []string `yaml:"allow_image_mimes"`
	AllowVideoMimes         []string `yaml:"allow_video_mimes"`
}

type AuthConfig struct {
	Upload UploadAuthConfig `yaml:"upload"`
	Admin  AdminAuthConfig  `yaml:"admin"`
}

type UploadAuthConfig struct {
	Enabled              bool   `yaml:"enabled"`
	JWTSecret            string `yaml:"jwt_secret"`
	Issuer               string `yaml:"issuer"`
	Audience             string `yaml:"audience"`
	HeaderName           string `yaml:"header_name"`
	AllowDebugUserHeader bool   `yaml:"allow_debug_user_header"`
	DebugUserHeader      string `yaml:"debug_user_header"`
}

type AdminAuthConfig struct {
	JWTSecret      string   `yaml:"jwt_secret"`
	Issuer         string   `yaml:"issuer"`
	Username       string   `yaml:"username"`
	Password       string   `yaml:"password"`
	TokenTTLMinute int      `yaml:"token_ttl_minutes"`
	CookieName     string   `yaml:"cookie_name"`
	ServiceTokens  []string `yaml:"service_tokens"`
}

type SecurityConfig struct {
	RequestIDHeader    string   `yaml:"request_id_header"`
	MaxRequestBodyMB   int64    `yaml:"max_request_body_mb"`
	PerIPRPS           int      `yaml:"per_ip_rps"`
	PerUserRPS         int      `yaml:"per_user_rps"`
	PublicFileAccess   bool     `yaml:"public_file_access"`
	JavaAdminAllowIPs  []string `yaml:"java_admin_allow_ips"`
	EnableDeleteAudit  bool     `yaml:"enable_delete_audit"`
	CORSAllowedOrigins []string `yaml:"cors_allowed_origins"`
}

type LoggingConfig struct {
	Level  string `yaml:"level"`
	Pretty bool   `yaml:"pretty"`
}

type PanelConfig struct {
	Enabled  bool   `yaml:"enabled"`
	BasePath string `yaml:"base_path"`
}

type RuntimeConfig struct {
	UploadEnabled           bool
	MaxBatchFiles           int
	MaxImageSizeMB          int64
	MaxVideoSizeMB          int64
	ChunkSizeMB             int64
	GlobalUploadConcurrency int
	MergeConcurrency        int
	PerIPRPS                int
	PerUserRPS              int
	MaxChunkCount           int
	ChunkExpireHours        int
	PublicFileAccess        bool
	AllowedImageExts        []string
	AllowedVideoExts        []string
	AllowedImageMimes       []string
	AllowedVideoMimes       []string
	MaxMultipartMemoryMB    int64
}

type Manager struct {
	base    *Config
	mu      sync.RWMutex
	runtime RuntimeConfig
}

type RuntimeConfigStore interface {
	LoadRuntimeConfigValues(ctx context.Context) (map[string]string, error)
}

func Load(path string) (*Config, error) {
	if path == "" {
		path = "config.yaml"
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config file: %w", err)
	}
	var cfg Config
	if err := yaml.Unmarshal(raw, &cfg); err != nil {
		return nil, fmt.Errorf("unmarshal config file: %w", err)
	}
	applyEnvOverrides(&cfg)
	normalize(&cfg)
	return &cfg, nil
}

func NewManager(cfg *Config) *Manager {
	return &Manager{
		base: cfg,
		runtime: RuntimeConfig{
			UploadEnabled:           cfg.Upload.Enabled,
			MaxBatchFiles:           cfg.Upload.MaxBatchFiles,
			MaxImageSizeMB:          cfg.Upload.MaxImageSizeMB,
			MaxVideoSizeMB:          cfg.Upload.MaxVideoSizeMB,
			ChunkSizeMB:             cfg.Upload.ChunkSizeMB,
			GlobalUploadConcurrency: cfg.Upload.GlobalUploadConcurrency,
			MergeConcurrency:        cfg.Upload.MergeConcurrency,
			PerIPRPS:                cfg.Security.PerIPRPS,
			PerUserRPS:              cfg.Security.PerUserRPS,
			MaxChunkCount:           cfg.Upload.MaxChunkCount,
			ChunkExpireHours:        cfg.Upload.ChunkExpireHours,
			PublicFileAccess:        cfg.Security.PublicFileAccess,
			AllowedImageExts:        cloneStrings(cfg.Upload.AllowImageExts),
			AllowedVideoExts:        cloneStrings(cfg.Upload.AllowVideoExts),
			AllowedImageMimes:       cloneStrings(cfg.Upload.AllowImageMimes),
			AllowedVideoMimes:       cloneStrings(cfg.Upload.AllowVideoMimes),
			MaxMultipartMemoryMB:    cfg.Upload.MaxMultipartMemoryMB,
		},
	}
}

func (m *Manager) Base() *Config {
	return m.base
}

func (m *Manager) Runtime() RuntimeConfig {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.runtime.clone()
}

func (m *Manager) UpdateRuntime(updates map[string]string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	next := m.runtime.clone()
	for key, value := range updates {
		if err := applyRuntimeValue(&next, key, value); err != nil {
			return err
		}
	}
	m.runtime = next
	return nil
}

func HydrateRuntimeFromStore(ctx context.Context, manager *Manager, store RuntimeConfigStore) error {
	values, err := store.LoadRuntimeConfigValues(ctx)
	if err != nil {
		return err
	}
	if len(values) == 0 {
		return nil
	}
	return manager.UpdateRuntime(values)
}

func RuntimeValueMap(runtime RuntimeConfig) map[string]any {
	return map[string]any{
		"upload_enabled":            runtime.UploadEnabled,
		"max_batch_files":           runtime.MaxBatchFiles,
		"max_image_size_mb":         runtime.MaxImageSizeMB,
		"max_video_size_mb":         runtime.MaxVideoSizeMB,
		"chunk_size_mb":             runtime.ChunkSizeMB,
		"max_chunk_count":           runtime.MaxChunkCount,
		"chunk_expire_hours":        runtime.ChunkExpireHours,
		"global_upload_concurrency": runtime.GlobalUploadConcurrency,
		"merge_concurrency":         runtime.MergeConcurrency,
		"per_ip_rps":                runtime.PerIPRPS,
		"per_user_rps":              runtime.PerUserRPS,
		"public_file_access":        runtime.PublicFileAccess,
		"allowed_image_exts":        runtime.AllowedImageExts,
		"allowed_video_exts":        runtime.AllowedVideoExts,
		"allowed_image_mimes":       runtime.AllowedImageMimes,
		"allowed_video_mimes":       runtime.AllowedVideoMimes,
		"max_multipart_memory_mb":   runtime.MaxMultipartMemoryMB,
	}
}

func applyRuntimeValue(runtime *RuntimeConfig, key string, raw string) error {
	switch key {
	case "upload_enabled":
		value, err := strconv.ParseBool(raw)
		if err != nil {
			return fmt.Errorf("%s invalid bool", key)
		}
		runtime.UploadEnabled = value
	case "max_batch_files":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.MaxBatchFiles = value
	case "max_image_size_mb":
		value, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return fmt.Errorf("%s invalid int64", key)
		}
		runtime.MaxImageSizeMB = value
	case "max_video_size_mb":
		value, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return fmt.Errorf("%s invalid int64", key)
		}
		runtime.MaxVideoSizeMB = value
	case "chunk_size_mb":
		value, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return fmt.Errorf("%s invalid int64", key)
		}
		runtime.ChunkSizeMB = value
	case "max_chunk_count":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.MaxChunkCount = value
	case "chunk_expire_hours":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.ChunkExpireHours = value
	case "global_upload_concurrency":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.GlobalUploadConcurrency = value
	case "merge_concurrency":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.MergeConcurrency = value
	case "per_ip_rps":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.PerIPRPS = value
	case "per_user_rps":
		value, err := strconv.Atoi(raw)
		if err != nil {
			return fmt.Errorf("%s invalid int", key)
		}
		runtime.PerUserRPS = value
	case "public_file_access":
		value, err := strconv.ParseBool(raw)
		if err != nil {
			return fmt.Errorf("%s invalid bool", key)
		}
		runtime.PublicFileAccess = value
	case "allowed_image_exts":
		runtime.AllowedImageExts = splitCSV(raw)
	case "allowed_video_exts":
		runtime.AllowedVideoExts = splitCSV(raw)
	case "allowed_image_mimes":
		runtime.AllowedImageMimes = splitCSV(raw)
	case "allowed_video_mimes":
		runtime.AllowedVideoMimes = splitCSV(raw)
	case "max_multipart_memory_mb":
		value, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return fmt.Errorf("%s invalid int64", key)
		}
		runtime.MaxMultipartMemoryMB = value
	default:
		return fmt.Errorf("unsupported runtime config key: %s", key)
	}
	return nil
}

func cloneStrings(values []string) []string {
	result := make([]string, len(values))
	copy(result, values)
	return result
}

func (r RuntimeConfig) clone() RuntimeConfig {
	r.AllowedImageExts = cloneStrings(r.AllowedImageExts)
	r.AllowedVideoExts = cloneStrings(r.AllowedVideoExts)
	r.AllowedImageMimes = cloneStrings(r.AllowedImageMimes)
	r.AllowedVideoMimes = cloneStrings(r.AllowedVideoMimes)
	return r
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, item := range parts {
		trimmed := strings.TrimSpace(strings.ToLower(item))
		if trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}

func normalize(cfg *Config) {
	cfg.Upload.AllowImageExts = normalizeSlice(cfg.Upload.AllowImageExts)
	cfg.Upload.AllowVideoExts = normalizeSlice(cfg.Upload.AllowVideoExts)
	cfg.Upload.AllowImageMimes = normalizeSlice(cfg.Upload.AllowImageMimes)
	cfg.Upload.AllowVideoMimes = normalizeSlice(cfg.Upload.AllowVideoMimes)
}

func normalizeSlice(values []string) []string {
	result := make([]string, 0, len(values))
	seen := make(map[string]struct{}, len(values))
	for _, item := range values {
		trimmed := strings.TrimSpace(strings.ToLower(item))
		if trimmed == "" {
			continue
		}
		if _, exists := seen[trimmed]; exists {
			continue
		}
		seen[trimmed] = struct{}{}
		result = append(result, trimmed)
	}
	return result
}

func applyEnvOverrides(cfg *Config) {
	cfg.Server.Port = utils.EnvInt("MEDIA_SERVER_PORT", cfg.Server.Port)
	cfg.Server.PublicBaseURL = utils.EnvString("MEDIA_PUBLIC_BASE_URL", cfg.Server.PublicBaseURL)
	cfg.Server.EnablePprof = utils.EnvBool("MEDIA_ENABLE_PPROF", cfg.Server.EnablePprof)
	cfg.Server.EnableMetrics = utils.EnvBool("MEDIA_ENABLE_METRICS", cfg.Server.EnableMetrics)

	cfg.Database.DSN = utils.EnvString("MEDIA_DATABASE_DSN", cfg.Database.DSN)

	cfg.Storage.RootDir = utils.EnvString("MEDIA_STORAGE_ROOT_DIR", cfg.Storage.RootDir)
	cfg.Storage.TempDir = utils.EnvString("MEDIA_STORAGE_TEMP_DIR", cfg.Storage.TempDir)

	cfg.Upload.Enabled = utils.EnvBool("MEDIA_UPLOAD_ENABLED", cfg.Upload.Enabled)
	cfg.Upload.MaxBatchFiles = utils.EnvInt("MEDIA_UPLOAD_MAX_BATCH_FILES", cfg.Upload.MaxBatchFiles)
	cfg.Upload.MaxImageSizeMB = utils.EnvInt64("MEDIA_UPLOAD_MAX_IMAGE_MB", cfg.Upload.MaxImageSizeMB)
	cfg.Upload.MaxVideoSizeMB = utils.EnvInt64("MEDIA_UPLOAD_MAX_VIDEO_MB", cfg.Upload.MaxVideoSizeMB)
	cfg.Upload.ChunkSizeMB = utils.EnvInt64("MEDIA_UPLOAD_CHUNK_SIZE_MB", cfg.Upload.ChunkSizeMB)
	cfg.Upload.GlobalUploadConcurrency = utils.EnvInt("MEDIA_UPLOAD_MAX_CONCURRENCY", cfg.Upload.GlobalUploadConcurrency)
	cfg.Upload.MergeConcurrency = utils.EnvInt("MEDIA_UPLOAD_MERGE_CONCURRENCY", cfg.Upload.MergeConcurrency)

	cfg.Auth.Upload.Enabled = utils.EnvBool("MEDIA_UPLOAD_AUTH_ENABLED", cfg.Auth.Upload.Enabled)
	cfg.Auth.Upload.JWTSecret = utils.EnvString("MEDIA_UPLOAD_JWT_SECRET", cfg.Auth.Upload.JWTSecret)
	cfg.Auth.Admin.JWTSecret = utils.EnvString("MEDIA_ADMIN_JWT_SECRET", cfg.Auth.Admin.JWTSecret)
	cfg.Auth.Admin.Username = utils.EnvString("MEDIA_ADMIN_USERNAME", cfg.Auth.Admin.Username)
	cfg.Auth.Admin.Password = utils.EnvString("MEDIA_ADMIN_PASSWORD", cfg.Auth.Admin.Password)

	cfg.Security.PerIPRPS = utils.EnvInt("MEDIA_PER_IP_RPS", cfg.Security.PerIPRPS)
	cfg.Security.PerUserRPS = utils.EnvInt("MEDIA_PER_USER_RPS", cfg.Security.PerUserRPS)
	cfg.Security.PublicFileAccess = utils.EnvBool("MEDIA_PUBLIC_FILE_ACCESS", cfg.Security.PublicFileAccess)
}
