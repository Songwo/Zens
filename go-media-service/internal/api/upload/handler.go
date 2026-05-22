package upload

import (
	"io"
	"net/http"
	"strconv"
	"strings"

	"go-media-service/internal/api"
	"go-media-service/internal/config"
	"go-media-service/internal/constants"
	"go-media-service/internal/model"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
)

type Handler struct {
	manager       *config.Manager
	uploadService *service.UploadService
}

func NewHandler(manager *config.Manager, uploadService *service.UploadService) *Handler {
	return &Handler{
		manager:       manager,
		uploadService: uploadService,
	}
}

func (h *Handler) UploadImage(c *gin.Context) {
	h.handleMultipartUpload(c, constants.MediaTypeImage)
}

func (h *Handler) UploadVideo(c *gin.Context) {
	h.handleMultipartUpload(c, constants.MediaTypeVideo)
}

func (h *Handler) UploadFiles(c *gin.Context) {
	h.handleMultipartUpload(c, "")
}

func (h *Handler) Precheck(c *gin.Context) {
	var payload struct {
		ClientHash   string `json:"clientHash"`
		SizeBytes    int64  `json:"sizeBytes"`
		MediaType    string `json:"mediaType"`
		OriginalName string `json:"originalName"`
		BizType      string `json:"bizType"`
		BizID        string `json:"bizId"`
		SourceType   string `json:"sourceType"`
	}
	if err := c.ShouldBindJSON(&payload); err != nil {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "invalid precheck payload")
		return
	}
	if strings.TrimSpace(payload.ClientHash) == "" || payload.SizeBytes <= 0 {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "clientHash and sizeBytes are required")
		return
	}
	mediaType := strings.TrimSpace(payload.MediaType)
	if mediaType == "" {
		mediaType = mediaTypeByFilename(payload.OriginalName)
	}
	if mediaType == "" {
		api.Error(c, http.StatusBadRequest, constants.CodeValidation, "cannot determine media type")
		return
	}
	result, appErr := h.uploadService.Precheck(c.Request.Context(), service.UploadRequest{
		MediaType:    mediaType,
		OriginalName: payload.OriginalName,
		ClientHash:   strings.TrimSpace(payload.ClientHash),
		SizeHint:     payload.SizeBytes,
		UploaderID:   uploaderID(c),
		UploaderIP:   c.ClientIP(),
		BizType:      strings.TrimSpace(payload.BizType),
		BizID:        strings.TrimSpace(payload.BizID),
		SourceType:   strings.TrimSpace(payload.SourceType),
	})
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func (h *Handler) UploadChunk(c *gin.Context) {
	reader, err := c.Request.MultipartReader()
	if err != nil {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "invalid multipart request")
		return
	}

	fields := map[string]string{
		"taskId":      c.Query("taskId"),
		"chunkIndex":  c.Query("chunkIndex"),
		"totalChunks": c.Query("totalChunks"),
		"fileName":    c.Query("fileName"),
		"fileSize":    c.Query("fileSize"),
		"clientHash":  c.Query("clientHash"),
		"mediaType":   c.Query("mediaType"),
		"bizType":     c.Query("bizType"),
		"bizId":       c.Query("bizId"),
	}

	var chunkPart io.Reader
	for {
		part, nextErr := reader.NextPart()
		if nextErr == io.EOF {
			break
		}
		if nextErr != nil {
			api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "read multipart part failed")
			return
		}
		if part.FileName() == "" {
			payload, _ := io.ReadAll(io.LimitReader(part, 1024*1024))
			fields[part.FormName()] = string(payload)
			continue
		}
		chunkPart = part
		if fields["fileName"] == "" {
			fields["fileName"] = part.FileName()
		}
		break
	}

	if chunkPart == nil {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "missing chunk file")
		return
	}

	chunkIndex, _ := strconv.Atoi(fields["chunkIndex"])
	totalChunks, _ := strconv.Atoi(fields["totalChunks"])
	fileSize, _ := strconv.ParseInt(fields["fileSize"], 10, 64)
	detail, appErr := h.uploadService.SaveChunk(c.Request.Context(), service.ChunkUploadRequest{
		TaskID:      strings.TrimSpace(fields["taskId"]),
		ChunkIndex:  chunkIndex,
		TotalChunks: totalChunks,
		FileName:    strings.TrimSpace(fields["fileName"]),
		FileSize:    fileSize,
		ClientHash:  strings.TrimSpace(fields["clientHash"]),
		MediaType:   strings.TrimSpace(fields["mediaType"]),
		UploaderID:  uploaderID(c),
		UploaderIP:  c.ClientIP(),
		BizType:     strings.TrimSpace(fields["bizType"]),
		BizID:       strings.TrimSpace(fields["bizId"]),
	}, chunkPart)
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, detail)
}

func (h *Handler) MergeChunks(c *gin.Context) {
	var payload struct {
		TaskID string `json:"taskId"`
	}
	if err := c.ShouldBindJSON(&payload); err != nil || strings.TrimSpace(payload.TaskID) == "" {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "taskId is required")
		return
	}
	result, appErr := h.uploadService.MergeChunks(c.Request.Context(), payload.TaskID)
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func (h *Handler) TaskStatus(c *gin.Context) {
	result, appErr := h.uploadService.GetTaskStatus(c.Request.Context(), c.Param("taskId"))
	if appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, result)
}

func (h *Handler) CancelTask(c *gin.Context) {
	actor := uploaderID(c)
	if actor == "" {
		actor = c.ClientIP()
	}
	if appErr := h.uploadService.CancelTask(c.Request.Context(), c.Param("taskId"), actor); appErr != nil {
		api.FromAppError(c, appErr)
		return
	}
	api.Success(c, gin.H{"taskId": c.Param("taskId"), "status": "canceled"})
}

func (h *Handler) handleMultipartUpload(c *gin.Context, forcedMediaType string) {
	reader, err := c.Request.MultipartReader()
	if err != nil {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "invalid multipart request")
		return
	}

	fields := map[string]string{
		"clientHash": c.Query("clientHash"),
		"bizType":    c.Query("bizType"),
		"bizId":      c.Query("bizId"),
		"sourceType": c.Query("sourceType"),
		"sizeHint":   c.Query("sizeHint"),
	}
	results := make([]model.UploadResult, 0)
	fileCount := 0

	for {
		part, nextErr := reader.NextPart()
		if nextErr == io.EOF {
			break
		}
		if nextErr != nil {
			api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "read multipart part failed")
			return
		}
		if part.FileName() == "" {
			payload, _ := io.ReadAll(io.LimitReader(part, 1024*1024))
			fields[part.FormName()] = string(payload)
			continue
		}
		fileCount++
		if fileCount > h.manager.Runtime().MaxBatchFiles {
			api.Error(c, http.StatusBadRequest, constants.CodeValidation, "batch file count exceeds limit")
			return
		}
		mediaType := forcedMediaType
		if mediaType == "" {
			mediaType = mediaTypeByFilename(part.FileName())
		}
		if mediaType == "" {
			api.Error(c, http.StatusBadRequest, constants.CodeValidation, "cannot determine media type")
			return
		}
		sizeHint, _ := strconv.ParseInt(strings.TrimSpace(fields["sizeHint"]), 10, 64)
		result, appErr := h.uploadService.UploadMedia(c.Request.Context(), service.UploadRequest{
			MediaType:    mediaType,
			OriginalName: part.FileName(),
			ClientHash:   strings.TrimSpace(fields["clientHash"]),
			SizeHint:     sizeHint,
			UploaderID:   uploaderID(c),
			UploaderIP:   c.ClientIP(),
			BizType:      strings.TrimSpace(fields["bizType"]),
			BizID:        strings.TrimSpace(fields["bizId"]),
			SourceType:   strings.TrimSpace(fields["sourceType"]),
		}, part)
		if appErr != nil {
			api.FromAppError(c, appErr)
			return
		}
		results = append(results, *result)
	}

	if len(results) == 0 {
		api.Error(c, http.StatusBadRequest, constants.CodeBadRequest, "no file found in multipart request")
		return
	}
	api.Success(c, gin.H{
		"items": results,
	})
}

func mediaTypeByFilename(name string) string {
	lower := strings.ToLower(name)
	switch {
	case strings.HasSuffix(lower, ".jpg"), strings.HasSuffix(lower, ".jpeg"), strings.HasSuffix(lower, ".png"), strings.HasSuffix(lower, ".gif"), strings.HasSuffix(lower, ".webp"):
		return constants.MediaTypeImage
	case strings.HasSuffix(lower, ".mp4"), strings.HasSuffix(lower, ".mov"), strings.HasSuffix(lower, ".webm"), strings.HasSuffix(lower, ".ogg"):
		return constants.MediaTypeVideo
	default:
		return ""
	}
}

func uploaderID(c *gin.Context) string {
	value, _ := c.Get("uploader_id")
	if result, ok := value.(string); ok {
		return result
	}
	return ""
}
