package api

import (
	"math"
	"net/http"
	"strconv"

	"go-media-service/internal/model"
	"go-media-service/internal/service"

	"github.com/gin-gonic/gin"
	"github.com/rs/zerolog/log"
)

func Success(c *gin.Context, data any) {
	c.JSON(http.StatusOK, model.Response{
		Code:      0,
		Message:   "ok",
		RequestID: requestID(c),
		Data:      data,
	})
}

func Error(c *gin.Context, status int, code int, message string) {
	c.JSON(status, model.Response{
		Code:      code,
		Message:   message,
		RequestID: requestID(c),
	})
}

func FromAppError(c *gin.Context, err error) {
	if err == nil {
		return
	}
	if appErr, ok := err.(*service.AppError); ok {
		// 5xx 时把底层 cause 打进日志，方便排查（上层 Message 不变以兼容前端展示）。
		if appErr.HTTPStatus >= 500 {
			log.Error().
				Err(appErr.Cause).
				Int("code", appErr.Code).
				Int("status", appErr.HTTPStatus).
				Str("path", c.FullPath()).
				Str("request_id", requestID(c)).
				Msg(appErr.Message)
		}
		Error(c, appErr.HTTPStatus, appErr.Code, appErr.Message)
		return
	}
	log.Error().Err(err).Str("path", c.FullPath()).Msg("unhandled error")
	Error(c, http.StatusInternalServerError, 50000, err.Error())
}

func PageQuery(c *gin.Context) model.ListQuery {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	return model.ListQuery{
		Page:      page,
		PageSize:  pageSize,
		Keyword:   c.Query("keyword"),
		Status:    c.Query("status"),
		MediaType: c.Query("mediaType"),
		SortBy:    c.DefaultQuery("sortBy", "created_at"),
		SortOrder: c.DefaultQuery("sortOrder", "desc"),
		StartAt:   c.Query("startAt"),
		EndAt:     c.Query("endAt"),
	}
}

func PageData(items any, page int, pageSize int, total int64) model.PagedData {
	totalPage := int64(0)
	if pageSize > 0 {
		totalPage = int64(math.Ceil(float64(total) / float64(pageSize)))
	}
	return model.PagedData{
		Items: items,
		Pagination: model.Pagination{
			Page:      page,
			PageSize:  pageSize,
			Total:     total,
			TotalPage: totalPage,
		},
	}
}

func requestID(c *gin.Context) string {
	value, _ := c.Get("request_id")
	if requestID, ok := value.(string); ok {
		return requestID
	}
	return ""
}
