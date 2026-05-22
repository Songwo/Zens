package panel

import (
	"net/http"

	"go-media-service/internal/config"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	cfg         *config.Config
	authService *service.AuthService
}

func NewHandler(cfg *config.Config, authService *service.AuthService) *Handler {
	return &Handler{
		cfg:         cfg,
		authService: authService,
	}
}

func (h *Handler) LoginPage(c *gin.Context) {
	c.HTML(http.StatusOK, "login.html", gin.H{
		"title":    "Go 媒体服务登录",
		"basePath": h.cfg.Panel.BasePath,
	})
}

func (h *Handler) DoLogin(c *gin.Context) {
	_ = c.Request.ParseForm()
	username := c.PostForm("username")
	password := c.PostForm("password")
	if username == "" || password == "" {
		var payload map[string]string
		_ = c.ShouldBindJSON(&payload)
		username = payload["username"]
		password = payload["password"]
	}
	if !h.authService.ValidateAdminCredential(username, password) {
		c.HTML(http.StatusUnauthorized, "login.html", gin.H{
			"title":    "Go 媒体服务登录",
			"basePath": h.cfg.Panel.BasePath,
			"error":    "账号或密码错误",
		})
		return
	}
	token, err := h.authService.IssueAdminToken(username)
	if err != nil {
		c.HTML(http.StatusInternalServerError, "login.html", gin.H{
			"title":    "Go 媒体服务登录",
			"basePath": h.cfg.Panel.BasePath,
			"error":    "生成登录令牌失败",
		})
		return
	}
	c.SetCookie(
		h.cfg.Auth.Admin.CookieName,
		token,
		h.cfg.Auth.Admin.TokenTTLMinute*60,
		"/",
		"",
		false,
		true,
	)
	c.Redirect(http.StatusFound, h.cfg.Panel.BasePath)
}

func (h *Handler) Logout(c *gin.Context) {
	c.SetCookie(h.cfg.Auth.Admin.CookieName, "", -1, "/", "", false, true)
	c.Redirect(http.StatusFound, h.cfg.Panel.BasePath+"/login")
}

func (h *Handler) Dashboard(c *gin.Context) {
	h.render(c, "dashboard.html", "仪表盘", "dashboard")
}

func (h *Handler) Uploads(c *gin.Context) {
	h.render(c, "uploads.html", "上传记录", "uploads")
}

func (h *Handler) Files(c *gin.Context) {
	h.render(c, "files.html", "文件管理", "files")
}

func (h *Handler) System(c *gin.Context) {
	h.render(c, "system.html", "系统监控", "system")
}

func (h *Handler) Config(c *gin.Context) {
	h.render(c, "config.html", "运行配置", "config")
}

func (h *Handler) render(c *gin.Context, templateName string, title string, page string) {
	c.HTML(http.StatusOK, templateName, gin.H{
		"title":    title,
		"page":     page,
		"basePath": h.cfg.Panel.BasePath,
	})
}
