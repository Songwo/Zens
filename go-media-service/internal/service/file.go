package service

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"sync/atomic"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/constants"
	"go-media-service/internal/metrics"
	"go-media-service/internal/model"
	"go-media-service/internal/repository"
	"go-media-service/internal/storage"
)

type FileService struct {
	manager *config.Manager
	repo    *repository.SQLiteRepository
	storage storage.Driver
	metrics *metrics.Collector

	accepting atomic.Bool
}

func NewFileService(manager *config.Manager, repo *repository.SQLiteRepository, storageDriver storage.Driver, metricsCollector *metrics.Collector) *FileService {
	service := &FileService{
		manager: manager,
		repo:    repo,
		storage: storageDriver,
		metrics: metricsCollector,
	}
	service.accepting.Store(true)
	return service
}

func (s *FileService) StopAccepting() {
	s.accepting.Store(false)
}

func (s *FileService) GetFile(ctx context.Context, id string) (*model.MediaFile, *AppError) {
	file, err := s.repo.GetFileByID(ctx, id)
	if err != nil {
		return nil, NewAppError(constants.CodeNotFound, http.StatusNotFound, "media file not found", err)
	}
	if file.Status == constants.FileStatusDeleted {
		return nil, NewAppError(constants.CodeNotFound, http.StatusNotFound, "media file deleted", nil)
	}
	return file, nil
}

func (s *FileService) ListFiles(ctx context.Context, filter model.FileListFilter) ([]model.MediaFile, int64, *AppError) {
	items, total, err := s.repo.ListFiles(ctx, filter)
	if err != nil {
		return nil, 0, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "list media files failed", err)
	}
	return items, total, nil
}

func (s *FileService) Open(ctx context.Context, id string) (*model.MediaFile, *AppError) {
	file, appErr := s.GetFile(ctx, id)
	if appErr != nil {
		return nil, appErr
	}
	if !s.manager.Runtime().PublicFileAccess {
		return nil, NewAppError(constants.CodeForbidden, http.StatusForbidden, "public file access disabled", nil)
	}
	return file, nil
}

func (s *FileService) OpenStream(file *model.MediaFile) (any, *AppError) {
	stream, err := s.storage.Open(file.StoragePath)
	if err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "open media file failed", err)
	}
	return stream, nil
}

func (s *FileService) DeleteFiles(ctx context.Context, ids []string, actor string) ([]model.MediaFile, *AppError) {
	if len(ids) == 0 {
		return []model.MediaFile{}, nil
	}
	files, err := s.repo.GetFilesByIDs(ctx, ids)
	if err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "load media files failed", err)
	}
	for _, file := range files {
		if file.Status == constants.FileStatusDeleted {
			continue
		}
		if removeErr := s.storage.Remove(file.StoragePath); removeErr != nil {
			return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "remove media file failed", removeErr)
		}
	}
	now := time.Now()
	if err := s.repo.SoftDeleteFiles(ctx, ids, now); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "mark media deleted failed", err)
	}
	if s.manager.Base().Security.EnableDeleteAudit {
		_ = s.repo.AppendAuditLog(ctx, &model.AuditLog{
			Actor:      actor,
			Action:     "media.delete",
			TargetType: "media_file",
			TargetID:   strings.Join(ids, ","),
			Detail:     fmt.Sprintf("batch delete %d media files", len(ids)),
			CreatedAt:  now,
		})
	}
	return files, nil
}
