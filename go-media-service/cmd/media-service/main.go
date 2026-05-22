package main

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net"
	"net/http"
	_ "net/http/pprof"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"go-media-service/internal/api/admin"
	fileapi "go-media-service/internal/api/file"
	"go-media-service/internal/api/health"
	"go-media-service/internal/api/upload"
	"go-media-service/internal/config"
	"go-media-service/internal/metrics"
	"go-media-service/internal/panel"
	"go-media-service/internal/repository"
	"go-media-service/internal/router"
	"go-media-service/internal/service"
	"go-media-service/internal/storage"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func main() {
	cfg, err := config.Load("")
	if err != nil {
		panic(fmt.Errorf("load config: %w", err))
	}

	initLogger(cfg)
	log.Info().
		Str("service", cfg.Server.Name).
		Int("port", cfg.Server.Port).
		Msg("starting go media service")

	db, repo, err := bootstrapRepository(cfg)
	if err != nil {
		log.Fatal().Err(err).Msg("bootstrap repository failed")
	}
	defer db.Close()

	cfgManager := config.NewManager(cfg)
	if err := config.HydrateRuntimeFromStore(context.Background(), cfgManager, repo); err != nil {
		log.Warn().Err(err).Msg("load persisted runtime config failed")
	}

	storageDriver, err := storage.NewLocalStorage(cfg)
	if err != nil {
		log.Fatal().Err(err).Msg("bootstrap storage failed")
	}

	metricsCollector := metrics.NewCollector(cfg.Server.Name)
	authService := service.NewAuthService(cfg)
	runtimeService := service.NewRuntimeConfigService(cfgManager, repo)
	fileService := service.NewFileService(cfgManager, repo, storageDriver, metricsCollector)
	uploadService := service.NewUploadService(cfgManager, repo, storageDriver, metricsCollector)
	adminService := service.NewAdminService(cfgManager, repo, storageDriver, metricsCollector, runtimeService)
	maintenanceService := service.NewMaintenanceService(cfgManager, repo, storageDriver)

	uploadHandler := upload.NewHandler(cfgManager, uploadService)
	fileHandler := fileapi.NewHandler(fileService)
	adminHandler := admin.NewHandler(adminService)
	healthHandler := health.NewHandler(cfgManager, repo, storageDriver, metricsCollector)
	panelHandler := panel.NewHandler(cfg, authService)

	backgroundCtx, stopBackground := context.WithCancel(context.Background())
	defer stopBackground()
	go maintenanceService.Run(backgroundCtx)

	engine := router.New(router.Dependencies{
		Config:           cfg,
		ConfigManager:    cfgManager,
		MetricsCollector: metricsCollector,
		AuthService:      authService,
		RuntimeService:   runtimeService,
		UploadHandler:    uploadHandler,
		FileHandler:      fileHandler,
		AdminHandler:     adminHandler,
		HealthHandler:    healthHandler,
		PanelHandler:     panelHandler,
	})

	server := &http.Server{
		Addr:         fmt.Sprintf("%s:%d", cfg.Server.Host, cfg.Server.Port),
		Handler:      engine,
		ReadTimeout:  time.Duration(cfg.Server.ReadTimeoutSeconds) * time.Second,
		WriteTimeout: time.Duration(cfg.Server.WriteTimeoutSeconds) * time.Second,
		IdleTimeout:  time.Duration(cfg.Server.IdleTimeoutSeconds) * time.Second,
		BaseContext: func(_ net.Listener) context.Context {
			return context.Background()
		},
		ConnState: metricsCollector.ConnStateHook(),
	}

	go func() {
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal().Err(err).Msg("http server failed")
		}
	}()

	waitForShutdown(cfg, server, stopBackground, uploadService, fileService)
}

func bootstrapRepository(cfg *config.Config) (*sql.DB, *repository.SQLiteRepository, error) {
	if err := os.MkdirAll(filepath.Dir(cfg.Database.DSN), 0o755); err != nil {
		return nil, nil, err
	}
	// 通过 DSN 把关键 PRAGMA 附加到每个连接，避免只在 bootstrap 单连接上生效。
	// 对分片上传很关键：foreign_keys + busy_timeout 必须在池中所有连接上一致。
	dsn := withSQLitePragmas(cfg.Database.DSN)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, nil, err
	}
	repo, err := repository.NewSQLiteRepository(db)
	if err != nil {
		return nil, nil, err
	}
	return db, repo, nil
}

func withSQLitePragmas(dsn string) string {
	pragmas := []string{
		"_pragma=foreign_keys(1)",
		"_pragma=journal_mode(WAL)",
		"_pragma=synchronous(NORMAL)",
		"_pragma=busy_timeout(5000)",
	}
	separator := "?"
	if strings.Contains(dsn, "?") {
		separator = "&"
	}
	return dsn + separator + strings.Join(pragmas, "&")
}

func initLogger(cfg *config.Config) {
	level, err := zerolog.ParseLevel(cfg.Logging.Level)
	if err != nil {
		level = zerolog.InfoLevel
	}
	zerolog.SetGlobalLevel(level)
	if cfg.Logging.Pretty {
		log.Logger = log.Output(zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.DateTime})
	}
}

func waitForShutdown(cfg *config.Config, server *http.Server, stopBackground context.CancelFunc, uploadService *service.UploadService, fileService *service.FileService) {
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, syscall.SIGINT, syscall.SIGTERM)
	<-signals

	log.Info().Msg("shutdown signal received")

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(cfg.Server.ShutdownTimeoutSeconds)*time.Second)
	defer cancel()

	uploadService.StopAccepting()
	fileService.StopAccepting()
	stopBackground()

	if err := server.Shutdown(ctx); err != nil {
		log.Error().Err(err).Msg("graceful shutdown failed")
	}

	log.Info().Msg("go media service stopped")
}
