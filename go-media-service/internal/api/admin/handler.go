package admin

import (
	"net/http"
	"strings"

	"go-media-service/internal/api"
	"go-media-service/internal/constants"
	"go-media-service/internal/model"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	adminService *service.AdminService
}

func NewHandler(adminService *service.AdminService) *Handler {
	return &Handler{adminService: adminService}
}

func (h *Handler) Dashboard(c *gin.Context) {
	result, appErr := h.adminService.Dashboard(c.Request.Context())
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func (h *Handler) Uploads(c *gin.Context) {
	query := api.PageQuery(c)
	items, total, appErr := h.adminService.ListTasks(c.Request.Context(), query)
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, api.PageData(items, query.Page, query.PageSize, total))
}

func (h *Handler) Files(c *gin.Context) {
	query := api.PageQuery(c)
	items, total, appErr := h.adminService.ListFiles(c.Request.Context(), model.FileListFilter{
		ListQuery: query,
		BizType:   c.Query("bizType"),
		BizID:     c.Query("bizId"),
	})
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, api.PageData(items, query.Page, query.PageSize, total))
}

func (h *Handler) FileDetail(c *gin.Context) {
	item, appErr := h.adminService.GetFile(c.Request.Context(), c.Param("id"))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, item)
}

func (h *Handler) DeleteFile(c *gin.Context) {
	items, appErr := h.adminService.DeleteFiles(c.Request.Context(), []string{c.Param("id")}, actor(c))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, gin.H{"items": items})
}

func (h *Handler) BatchDeleteFiles(c *gin.Context) {
	var payload model.BatchDeleteRequest
	if err := c.ShouldBindJSON(&payload); err != nil || len(payload.IDs) == 0 {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "ids is required")
		return
	}
	items, appErr := h.adminService.DeleteFiles(c.Request.Context(), payload.IDs, actor(c))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, gin.H{"items": items})
}

func (h *Handler) Tasks(c *gin.Context) {
	h.Uploads(c)
}

func (h *Handler) Stats(c *gin.Context) {
	result, appErr := h.adminService.Stats(c.Request.Context())
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func (h *Handler) System(c *gin.Context) {
	result, appErr := h.adminService.System(c.Request.Context())
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func (h *Handler) Config(c *gin.Context) {
	api.Success(c, h.adminService.ConfigView())
}

func (h *Handler) UpdateConfig(c *gin.Context) {
	updates := make(map[string]string)
	if err := c.ShouldBindJSON(&updates); err != nil || len(updates) == 0 {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "runtime config updates are required")
		return
	}
	result, appErr := h.adminService.UpdateConfig(c.Request.Context(), updates, actor(c))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func actor(c *gin.Context) string {
	value, _ := c.Get("admin_actor")
	if actor, ok := value.(string); ok && strings.TrimSpace(actor) != "" {
		return actor
	}
	return c.ClientIP()
}
