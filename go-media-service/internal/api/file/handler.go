package file

import (
	"io"
	"net/http"

	"go-media-service/internal/api"
	"go-media-service/internal/constants"
	"go-media-service/internal/model"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	fileService *service.FileService
}

func NewHandler(fileService *service.FileService) *Handler {
	return &Handler{fileService: fileService}
}

func (h *Handler) ServeByID(c *gin.Context) {
	file, appErr := h.fileService.Open(c.Request.Context(), c.Param("id"))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	streamAny, streamErr := h.fileService.OpenStream(file)
	if streamErr != nil {
		api.FromAppError(c, streamErr)
		return
	}
	stream, ok := streamAny.(io.ReadCloser)
	if !ok {
		api.Error(c, http.StatusInternalServerError, constants.CodeInternal, "invalid media stream")
		return
	}
	defer stream.Close()

	c.Header("Content-Type", file.MIMEType)
	c.Header("Cache-Control", "public, max-age=86400")
	c.DataFromReader(http.StatusOK, file.SizeBytes, file.MIMEType, stream, nil)
}

func (h *Handler) List(c *gin.Context) {
	query := api.PageQuery(c)
	filter := model.FileListFilter{
		ListQuery: query,
		BizType:   c.Query("bizType"),
		BizID:     c.Query("bizId"),
	}
	items, total, appErr := h.fileService.ListFiles(c.Request.Context(), filter)
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, api.PageData(items, query.Page, query.PageSize, total))
}

func (h *Handler) Detail(c *gin.Context) {
	file, appErr := h.fileService.GetFile(c.Request.Context(), c.Param("id"))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, file)
}

func (h *Handler) Delete(c *gin.Context) {
	deleted, appErr := h.fileService.DeleteFiles(c.Request.Context(), []string{c.Param("id")}, actor(c))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, gin.H{"items": deleted})
}

func (h *Handler) BatchDelete(c *gin.Context) {
	var payload model.BatchDeleteRequest
	if err := c.ShouldBindJSON(&payload); err != nil || len(payload.IDs) == 0 {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "ids is required")
		return
	}
	deleted, appErr := h.fileService.DeleteFiles(c.Request.Context(), payload.IDs, actor(c))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, gin.H{"items": deleted})
}

func actor(c *gin.Context) string {
	if value, exists := c.Get("admin_actor"); exists {
		if actor, ok := value.(string); ok {
			return actor
		}
	}
	return c.ClientIP()
}
