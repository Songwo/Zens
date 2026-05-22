package router

import (
	"net/http/pprof"

	"go-media-service/internal/api/admin"
	fileapi "go-media-service/internal/api/file"
	"go-media-service/internal/api/health"
	"go-media-service/internal/api/upload"
	"go-media-service/internal/config"
	"go-media-service/internal/metrics"
	"go-media-service/internal/middleware"
	"go-media-service/internal/panel"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

type Dependencies struct {
	Config           *config.Config
	ConfigManager    *config.Manager
	MetricsCollector *metrics.Collector
	AuthService      *service.AuthService
	RuntimeService   *service.RuntimeConfigService
	UploadHandler    *upload.Handler
	FileHandler      *fileapi.Handler
	AdminHandler     *admin.Handler
	HealthHandler    *health.Handler
	PanelHandler     *panel.Handler
}

func New(dep Dependencies) *gin.Engine {
	engine := gin.New()
	engine.MaxMultipartMemory = dep.Config.Upload.MaxMultipartMemoryMB << 20
	engine.Use(
		middleware.RequestContext(dep.Config),
		middleware.Recovery(),
		middleware.CORS(dep.Config.Security.CORSAllowedOrigins),
		middleware.RequestBodyLimit(dep.ConfigManager),
		middleware.AccessLog(dep.MetricsCollector),
	)

	if !dep.Config.Server.TrustProxy {
		_ = engine.SetTrustedProxies(nil)
	}

	engine.LoadHTMLGlob("web/templates/*")
	engine.Static(dep.Config.Panel.BasePath+"/static", "./web/static")

	engine.GET("/health", dep.HealthHandler.Health)
	if dep.Config.Server.EnableMetrics {
		engine.GET("/metrics", gin.WrapH(promhttp.Handler()))
	}
	if dep.Config.Server.EnablePprof {
		registerPprof(engine)
	}

	fileGroup := engine.Group("/api/file")
	{
		fileGroup.GET("/list", middleware.AdminAPIAuth(dep.Config, dep.AuthService), dep.FileHandler.List)
		fileGroup.GET("/detail/:id", middleware.AdminAPIAuth(dep.Config, dep.AuthService), dep.FileHandler.Detail)
		fileGroup.DELETE("/:id", middleware.AdminAPIAuth(dep.Config, dep.AuthService), dep.FileHandler.Delete)
		fileGroup.POST("/batch-delete", middleware.AdminAPIAuth(dep.Config, dep.AuthService), dep.FileHandler.BatchDelete)
		fileGroup.GET("/:id", dep.FileHandler.ServeByID)
	}

	uploadGroup := engine.Group("/api/upload")
	uploadGroup.Use(middleware.UploadAuth(dep.Config, dep.AuthService), middleware.UploadRateLimit(dep.ConfigManager))
	{
		uploadGroup.POST("/image", dep.UploadHandler.UploadImage)
		uploadGroup.POST("/video", dep.UploadHandler.UploadVideo)
		uploadGroup.POST("/files", dep.UploadHandler.UploadFiles)
		uploadGroup.POST("/precheck", dep.UploadHandler.Precheck)
		uploadGroup.POST("/chunk", dep.UploadHandler.UploadChunk)
		uploadGroup.POST("/merge", dep.UploadHandler.MergeChunks)
		uploadGroup.GET("/status/:taskId", dep.UploadHandler.TaskStatus)
		uploadGroup.DELETE("/task/:taskId", dep.UploadHandler.CancelTask)
	}

	adminGroup := engine.Group("/api/admin")
	adminGroup.Use(middleware.AdminAPIAuth(dep.Config, dep.AuthService))
	{
		adminGroup.GET("/dashboard", dep.AdminHandler.Dashboard)
		adminGroup.GET("/uploads", dep.AdminHandler.Uploads)
		adminGroup.GET("/files", dep.AdminHandler.Files)
		adminGroup.GET("/files/:id", dep.AdminHandler.FileDetail)
		adminGroup.DELETE("/files/:id", dep.AdminHandler.DeleteFile)
		adminGroup.POST("/files/batch-delete", dep.AdminHandler.BatchDeleteFiles)
		adminGroup.GET("/tasks", dep.AdminHandler.Tasks)
		adminGroup.GET("/stats", dep.AdminHandler.Stats)
		adminGroup.GET("/system", dep.AdminHandler.System)
		adminGroup.GET("/config", dep.AdminHandler.Config)
		adminGroup.PUT("/config", dep.AdminHandler.UpdateConfig)
	}

	if dep.Config.Panel.Enabled {
		panelBase := engine.Group(dep.Config.Panel.BasePath)
		{
			panelBase.GET("/login", dep.PanelHandler.LoginPage)
			panelBase.POST("/login", dep.PanelHandler.DoLogin)
			panelBase.POST("/logout", dep.PanelHandler.Logout)
		}
		panelProtected := engine.Group(dep.Config.Panel.BasePath)
		panelProtected.Use(middleware.PanelAuth(dep.Config, dep.AuthService))
		{
			panelProtected.GET("", dep.PanelHandler.Dashboard)
			panelProtected.GET("/", dep.PanelHandler.Dashboard)
			panelProtected.GET("/uploads", dep.PanelHandler.Uploads)
			panelProtected.GET("/files", dep.PanelHandler.Files)
			panelProtected.GET("/system", dep.PanelHandler.System)
			panelProtected.GET("/config", dep.PanelHandler.Config)
		}
	}

	return engine
}

func registerPprof(engine *gin.Engine) {
	pprofGroup := engine.Group("/debug/pprof")
	pprofGroup.GET("/", gin.WrapF(pprof.Index))
	pprofGroup.GET("/cmdline", gin.WrapF(pprof.Cmdline))
	pprofGroup.GET("/profile", gin.WrapF(pprof.Profile))
	pprofGroup.POST("/symbol", gin.WrapF(pprof.Symbol))
	pprofGroup.GET("/symbol", gin.WrapF(pprof.Symbol))
	pprofGroup.GET("/trace", gin.WrapF(pprof.Trace))
	pprofGroup.GET("/allocs", gin.WrapH(pprof.Handler("allocs")))
	pprofGroup.GET("/block", gin.WrapH(pprof.Handler("block")))
	pprofGroup.GET("/goroutine", gin.WrapH(pprof.Handler("goroutine")))
	pprofGroup.GET("/heap", gin.WrapH(pprof.Handler("heap")))
	pprofGroup.GET("/mutex", gin.WrapH(pprof.Handler("mutex")))
	pprofGroup.GET("/threadcreate", gin.WrapH(pprof.Handler("threadcreate")))
}
