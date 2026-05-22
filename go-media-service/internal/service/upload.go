package service

import (
	"context"
	"database/sql"
	"errors"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"net/http"
	"path"
	"slices"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/constants"
	"go-media-service/internal/metrics"
	"go-media-service/internal/model"
	"go-media-service/internal/repository"
	"go-media-service/internal/storage"
	"go-media-service/internal/utils"
)

type UploadRequest struct {
	MediaType    string
	OriginalName string
	ClientHash   string
	SizeHint     int64
	UploaderID   string
	UploaderIP   string
	BizType      string
	BizID        string
	SourceType   string
}

type ChunkUploadRequest struct {
	TaskID      string
	ChunkIndex  int
	TotalChunks int
	FileName    string
	FileSize    int64
	ClientHash  string
	MediaType   string
	UploaderID  string
	UploaderIP  string
	BizType     string
	BizID       string
}

type UploadService struct {
	manager *config.Manager
	repo    *repository.SQLiteRepository
	storage storage.Driver
	metrics *metrics.Collector

	accepting     atomic.Bool
	activeUploads atomic.Int64
	activeMerges  atomic.Int64

	// taskCreated 缓存已落库 task ID，避免每个分片 upsert upload_tasks。
	// key: taskID，value: struct{}。MergeChunks / CancelTask 完成时删除。
	taskCreated sync.Map
}

func NewUploadService(manager *config.Manager, repo *repository.SQLiteRepository, storageDriver storage.Driver, metricsCollector *metrics.Collector) *UploadService {
	service := &UploadService{
		manager: manager,
		repo:    repo,
		storage: storageDriver,
		metrics: metricsCollector,
	}
	service.accepting.Store(true)
	return service
}

func (s *UploadService) StopAccepting() {
	s.accepting.Store(false)
}

func (s *UploadService) UploadMedia(ctx context.Context, request UploadRequest, src any) (*model.UploadResult, *AppError) {
	reader, ok := src.(interface{ Read([]byte) (int, error) })
	if !ok {
		return nil, NewAppError(constants.CodeBadRequest, http.StatusBadRequest, "invalid upload stream", nil)
	}
	if err := s.ensureUploadAllowed(); err != nil {
		return nil, err
	}
	if result, appErr := s.tryInstantUpload(ctx, request); result != nil || appErr != nil {
		return result, appErr
	}
	release, appErr := s.enterUpload(request.MediaType)
	if appErr != nil {
		return nil, appErr
	}
	success := false
	defer func() {
		release(success)
	}()

	limitedReader := io.LimitReader(reader, s.maxAllowedBytes(request.MediaType)+1)
	contentType, contentReader, err := utils.DetectContentType(limitedReader)
	if err != nil {
		return nil, NewAppError(constants.CodeValidation, http.StatusBadRequest, "detect content type failed", err)
	}
	fileExt, appErr := s.validateMedia(request.MediaType, request.OriginalName, contentType)
	if appErr != nil {
		return nil, appErr
	}

	now := time.Now()
	taskID := utils.NewID("task")
	task := &model.UploadTask{
		ID:                taskID,
		SourceType:        defaultValue(request.SourceType, constants.TaskSourceSingle),
		MediaType:         request.MediaType,
		FileName:          utils.SafeFileName(request.OriginalName),
		OriginalName:      request.OriginalName,
		ClientFileHash:    request.ClientHash,
		Status:            constants.TaskStatusUploading,
		TotalSizeBytes:    request.SizeHint,
		UploadedSizeBytes: 0,
		UploaderID:        request.UploaderID,
		UploaderIP:        request.UploaderIP,
		StartedAt:         &now,
		CreatedAt:         now,
		UpdatedAt:         now,
	}
	if err := s.repo.SaveTask(ctx, task); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "create upload task failed", err)
	}

	fileID := utils.NewID("file")
	object, err := s.storage.SaveStream(ctx, contentReader, storage.SaveOptions{
		MediaType:    request.MediaType,
		FileID:       fileID,
		OriginalName: request.OriginalName,
		FileExt:      fileExt,
	})
	if err != nil {
		task.Status = constants.TaskStatusFailed
		task.ErrorMessage = err.Error()
		task.UpdatedAt = time.Now()
		_ = s.repo.SaveTask(ctx, task)
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "save upload stream failed", err)
	}
	if object.SizeBytes > s.maxAllowedBytes(request.MediaType) {
		_ = s.storage.Remove(object.RelativePath)
		task.Status = constants.TaskStatusFailed
		task.ErrorMessage = "file size exceeds limit"
		task.UpdatedAt = time.Now()
		_ = s.repo.SaveTask(ctx, task)
		return nil, NewAppError(constants.CodeValidation, http.StatusBadRequest, "file size exceeds limit", nil)
	}

	fileRecord := &model.MediaFile{
		ID:            fileID,
		FileName:      path.Base(object.RelativePath),
		OriginalName:  request.OriginalName,
		Ext:           fileExt,
		MediaType:     request.MediaType,
		MIMEType:      contentType,
		StorageDriver: s.manager.Base().Storage.Driver,
		StoragePath:   object.RelativePath,
		AccessURL:     strings.TrimRight(s.manager.Base().Server.PublicBaseURL, "/") + "/api/file/" + fileID,
		SizeBytes:     object.SizeBytes,
		SHA256:        object.SHA256,
		Status:        constants.FileStatusActive,
		Source:        task.SourceType,
		UploaderID:    request.UploaderID,
		UploaderIP:    request.UploaderIP,
		BizType:       request.BizType,
		BizID:         request.BizID,
		CreatedAt:     time.Now(),
		UpdatedAt:     time.Now(),
	}
	s.fillMediaMeta(fileRecord)
	if err := s.repo.SaveFile(ctx, fileRecord); err != nil {
		task.Status = constants.TaskStatusFailed
		task.ErrorMessage = err.Error()
		task.UpdatedAt = time.Now()
		_ = s.repo.SaveTask(ctx, task)
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "save media metadata failed", err)
	}

	finishedAt := time.Now()
	task.StoredFileID = fileID
	task.Status = constants.TaskStatusSuccess
	task.UploadedSizeBytes = object.SizeBytes
	task.TotalSizeBytes = object.SizeBytes
	task.FinishedAt = &finishedAt
	task.UpdatedAt = finishedAt
	if err := s.repo.SaveTask(ctx, task); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "update upload task failed", err)
	}

	success = true

	return &model.UploadResult{
		TaskID:       taskID,
		FileID:       fileID,
		MediaType:    request.MediaType,
		AccessURL:    fileRecord.AccessURL,
		FileName:     fileRecord.FileName,
		OriginalName: request.OriginalName,
		MIMEType:     contentType,
		SizeBytes:    object.SizeBytes,
		SHA256:       object.SHA256,
		Width:        fileRecord.Width,
		Height:       fileRecord.Height,
		DurationSec:  fileRecord.DurationSeconds,
		CreatedAt:    &finishedAt,
	}, nil
}

func (s *UploadService) SaveChunk(ctx context.Context, request ChunkUploadRequest, src any) (*model.TaskStatusDetail, *AppError) {
	reader, ok := src.(interface{ Read([]byte) (int, error) })
	if !ok {
		return nil, NewAppError(constants.CodeBadRequest, http.StatusBadRequest, "invalid chunk stream", nil)
	}
	if err := s.ensureUploadAllowed(); err != nil {
		return nil, err
	}
	if request.TotalChunks <= 0 || request.TotalChunks > s.manager.Runtime().MaxChunkCount {
		return nil, NewAppError(constants.CodeValidation, http.StatusBadRequest, "invalid total chunks", nil)
	}
	if request.ChunkIndex < 0 || request.ChunkIndex >= request.TotalChunks {
		return nil, NewAppError(constants.CodeValidation, http.StatusBadRequest, "invalid chunk index", nil)
	}

	release, appErr := s.enterUpload(request.MediaType)
	if appErr != nil {
		return nil, appErr
	}
	success := false
	defer func() {
		release(success)
	}()

	if request.TaskID == "" {
		request.TaskID = utils.NewID("chunk")
	}

	now := time.Now()
	_, cached := s.taskCreated.Load(request.TaskID)
	var task *model.UploadTask
	if !cached {
		var err error
		task, err = s.repo.GetTaskByID(ctx, request.TaskID)
		if err != nil && !errors.Is(err, sql.ErrNoRows) {
			return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "load chunk task failed", err)
		}
		if errors.Is(err, sql.ErrNoRows) || task == nil || task.ID == "" {
			task = &model.UploadTask{
				ID:                request.TaskID,
				SourceType:        constants.TaskSourceChunk,
				MediaType:         request.MediaType,
				FileName:          utils.SafeFileName(request.FileName),
				OriginalName:      request.FileName,
				ClientFileHash:    request.ClientHash,
				Status:            constants.TaskStatusUploading,
				TotalSizeBytes:    request.FileSize,
				UploadedSizeBytes: 0,
				TotalChunks:       request.TotalChunks,
				UploadedChunks:    0,
				UploaderID:        request.UploaderID,
				UploaderIP:        request.UploaderIP,
				StartedAt:         &now,
				CreatedAt:         now,
				UpdatedAt:         now,
			}
		}

		// 先落库 task，再写 chunk。
		// upload_chunks.task_id 对 upload_tasks.id 有外键，首片到达时必须先建立父行。
		// SaveTask 使用 ON CONFLICT(id) DO UPDATE，并发 worker 重复 upsert 安全。
		if err := s.repo.SaveTask(ctx, task); err != nil {
			return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "prepare chunk task failed", err)
		}
		s.taskCreated.Store(request.TaskID, struct{}{})
	}

	chunk, err := s.storage.SaveChunk(ctx, request.TaskID, request.ChunkIndex, io.LimitReader(reader, s.maxChunkBytes()+1))
	if err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "save chunk failed", err)
	}
	if chunk.SizeBytes > s.maxChunkBytes() {
		_ = s.storage.Remove(chunk.RelativePath)
		return nil, NewAppError(constants.CodeValidation, http.StatusBadRequest, "chunk size exceeds limit", nil)
	}
	chunkRecord := &model.UploadChunk{
		TaskID:    request.TaskID,
		ChunkIdx:  request.ChunkIndex,
		ChunkSize: chunk.SizeBytes,
		ChunkPath: chunk.RelativePath,
		Status:    constants.TaskStatusUploading,
		SHA256:    chunk.SHA256,
		CreatedAt: now,
		UpdatedAt: time.Now(),
	}
	if err := s.repo.SaveChunk(ctx, chunkRecord); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "save chunk metadata failed", err)
	}

	// 去掉 ListChunksByTaskID + 第二次 SaveTask 的 N+1：
	// UploadedChunks / UploadedSizeBytes 会在 MergeChunks 最终写入，
	// 用户主动 GetTaskStatus 时按需查询。响应只回基础元数据。
	success = true
	return &model.TaskStatusDetail{
		Task: &model.UploadTask{
			ID:          request.TaskID,
			TotalChunks: request.TotalChunks,
			MediaType:   request.MediaType,
		},
		Chunks:             []model.UploadChunk{*chunkRecord},
		UploadedChunkIndex: []int{request.ChunkIndex},
		CompletionPercent:  0,
	}, nil
}

func (s *UploadService) MergeChunks(ctx context.Context, taskID string) (*model.UploadResult, *AppError) {
	task, err := s.repo.GetTaskByID(ctx, taskID)
	if err != nil {
		return nil, NewAppError(constants.CodeNotFound, http.StatusNotFound, "upload task not found", err)
	}
	if task.SourceType != constants.TaskSourceChunk {
		return nil, NewAppError(constants.CodeBadRequest, http.StatusBadRequest, "task is not a chunk upload task", nil)
	}
	chunks, err := s.repo.ListChunksByTaskID(ctx, taskID)
	if err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "load chunk list failed", err)
	}
	if len(chunks) != task.TotalChunks {
		return nil, NewAppError(constants.CodeChunkIncomplete, http.StatusConflict, "chunks are not complete", nil)
	}

	release, appErr := s.enterMerge()
	if appErr != nil {
		return nil, appErr
	}
	defer release()

	sort.Slice(chunks, func(i int, j int) bool {
		return chunks[i].ChunkIdx < chunks[j].ChunkIdx
	})

	chunkPaths := make([]string, 0, len(chunks))
	for idx, chunk := range chunks {
		if idx != chunk.ChunkIdx {
			return nil, NewAppError(constants.CodeChunkIncomplete, http.StatusConflict, "missing chunk before merge", nil)
		}
		chunkPaths = append(chunkPaths, chunk.ChunkPath)
	}

	task.Status = constants.TaskStatusMerging
	task.UpdatedAt = time.Now()
	if err := s.repo.SaveTask(ctx, task); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "update merge task failed", err)
	}

	fileID := utils.NewID("file")
	contentType := s.guessContentTypeByMediaType(task.MediaType, task.OriginalName)
	fileExt, appErr := s.validateMedia(task.MediaType, task.OriginalName, contentType)
	if appErr != nil {
		return nil, appErr
	}

	object, err := s.storage.MergeChunks(ctx, taskID, chunkPaths, storage.SaveOptions{
		MediaType:    task.MediaType,
		FileID:       fileID,
		OriginalName: task.OriginalName,
		FileExt:      fileExt,
	})
	if err != nil {
		task.Status = constants.TaskStatusFailed
		task.ErrorMessage = err.Error()
		task.UpdatedAt = time.Now()
		_ = s.repo.SaveTask(ctx, task)
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "merge chunks failed", err)
	}

	fileRecord := &model.MediaFile{
		ID:            fileID,
		FileName:      path.Base(object.RelativePath),
		OriginalName:  task.OriginalName,
		Ext:           fileExt,
		MediaType:     task.MediaType,
		MIMEType:      contentType,
		StorageDriver: s.manager.Base().Storage.Driver,
		StoragePath:   object.RelativePath,
		AccessURL:     strings.TrimRight(s.manager.Base().Server.PublicBaseURL, "/") + "/api/file/" + fileID,
		SizeBytes:     object.SizeBytes,
		SHA256:        object.SHA256,
		Status:        constants.FileStatusActive,
		Source:        constants.TaskSourceChunk,
		UploaderID:    task.UploaderID,
		UploaderIP:    task.UploaderIP,
		CreatedAt:     time.Now(),
		UpdatedAt:     time.Now(),
	}
	s.fillMediaMeta(fileRecord)
	if err := s.repo.SaveFile(ctx, fileRecord); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "save merged file metadata failed", err)
	}

	finishedAt := time.Now()
	task.StoredFileID = fileID
	task.Status = constants.TaskStatusSuccess
	task.UploadedChunks = len(chunks)
	task.UploadedSizeBytes = object.SizeBytes
	task.TotalSizeBytes = object.SizeBytes
	task.FinishedAt = &finishedAt
	task.UpdatedAt = finishedAt
	if err := s.repo.SaveTask(ctx, task); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "update merged task failed", err)
	}
	_ = s.repo.DeleteChunksByTaskID(ctx, taskID)
	_ = s.storage.RemoveChunks(taskID)
	s.taskCreated.Delete(taskID)

	return &model.UploadResult{
		TaskID:       task.ID,
		FileID:       fileID,
		MediaType:    task.MediaType,
		AccessURL:    fileRecord.AccessURL,
		FileName:     fileRecord.FileName,
		OriginalName: fileRecord.OriginalName,
		MIMEType:     fileRecord.MIMEType,
		SizeBytes:    fileRecord.SizeBytes,
		SHA256:       fileRecord.SHA256,
		Width:        fileRecord.Width,
		Height:       fileRecord.Height,
		DurationSec:  fileRecord.DurationSeconds,
		CreatedAt:    &finishedAt,
	}, nil
}

func (s *UploadService) GetTaskStatus(ctx context.Context, taskID string) (*model.TaskStatusDetail, *AppError) {
	task, err := s.repo.GetTaskByID(ctx, taskID)
	if err != nil {
		return nil, NewAppError(constants.CodeNotFound, http.StatusNotFound, "upload task not found", err)
	}
	chunks, err := s.repo.ListChunksByTaskID(ctx, taskID)
	if err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "load chunk list failed", err)
	}
	indexes := make([]int, 0, len(chunks))
	for _, chunk := range chunks {
		indexes = append(indexes, chunk.ChunkIdx)
	}
	sort.Ints(indexes)
	return &model.TaskStatusDetail{
		Task:               task,
		Chunks:             chunks,
		UploadedChunkIndex: indexes,
		CompletionPercent:  completion(task.UploadedChunks, task.TotalChunks),
	}, nil
}

func (s *UploadService) CancelTask(ctx context.Context, taskID string, actor string) *AppError {
	task, err := s.repo.GetTaskByID(ctx, taskID)
	if err != nil {
		return NewAppError(constants.CodeNotFound, http.StatusNotFound, "upload task not found", err)
	}
	now := time.Now()
	task.Status = constants.TaskStatusCanceled
	task.ErrorMessage = "task canceled by " + actor
	task.FinishedAt = &now
	task.UpdatedAt = now
	if err := s.repo.SaveTask(ctx, task); err != nil {
		return NewAppError(constants.CodeInternal, http.StatusInternalServerError, "cancel upload task failed", err)
	}
	_ = s.repo.DeleteChunksByTaskID(ctx, taskID)
	_ = s.storage.RemoveChunks(taskID)
	s.taskCreated.Delete(taskID)
	return nil
}

func (s *UploadService) ensureUploadAllowed() *AppError {
	if !s.accepting.Load() {
		return NewAppError(constants.CodeServiceBusy, http.StatusServiceUnavailable, "upload service is stopping", nil)
	}
	if !s.manager.Runtime().UploadEnabled {
		return NewAppError(constants.CodeUploadDisabled, http.StatusServiceUnavailable, "upload service disabled", nil)
	}
	return nil
}

func (s *UploadService) enterUpload(mediaType string) (func(success bool), *AppError) {
	runtimeCfg := s.manager.Runtime()
	limit := int64(runtimeCfg.GlobalUploadConcurrency)
	for {
		current := s.activeUploads.Load()
		if current >= limit {
			return nil, NewAppError(constants.CodeServiceBusy, http.StatusServiceUnavailable, "upload concurrency limit reached", nil)
		}
		if s.activeUploads.CompareAndSwap(current, current+1) {
			finish := s.metrics.BeginUpload()
			return func(success bool) {
				s.activeUploads.Add(-1)
				finish(success, mediaType)
			}, nil
		}
	}
}

func (s *UploadService) enterMerge() (func(), *AppError) {
	limit := int64(s.manager.Runtime().MergeConcurrency)
	for {
		current := s.activeMerges.Load()
		if current >= limit {
			return nil, NewAppError(constants.CodeServiceBusy, http.StatusServiceUnavailable, "merge concurrency limit reached", nil)
		}
		if s.activeMerges.CompareAndSwap(current, current+1) {
			return func() {
				s.activeMerges.Add(-1)
			}, nil
		}
	}
}

func (s *UploadService) validateMedia(mediaType string, originalName string, contentType string) (string, *AppError) {
	ext := strings.ToLower(path.Ext(utils.SafeFileName(originalName)))
	runtimeCfg := s.manager.Runtime()
	switch mediaType {
	case constants.MediaTypeImage:
		if ext == "" || !slices.Contains(runtimeCfg.AllowedImageExts, ext) {
			return "", NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported image extension", nil)
		}
		if !slices.Contains(runtimeCfg.AllowedImageMimes, strings.ToLower(contentType)) {
			return "", NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported image mime type", nil)
		}
	case constants.MediaTypeVideo:
		if ext == "" || !slices.Contains(runtimeCfg.AllowedVideoExts, ext) {
			return "", NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported video extension", nil)
		}
		if !slices.Contains(runtimeCfg.AllowedVideoMimes, strings.ToLower(contentType)) {
			return "", NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported video mime type", nil)
		}
	default:
		return "", NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported media type", nil)
	}
	return ext, nil
}

func (s *UploadService) fillMediaMeta(file *model.MediaFile) {
	if file.MediaType != constants.MediaTypeImage {
		return
	}
	stream, err := s.storage.Open(file.StoragePath)
	if err != nil {
		return
	}
	defer stream.Close()
	cfg, _, err := image.DecodeConfig(stream)
	if err != nil {
		return
	}
	file.Width = cfg.Width
	file.Height = cfg.Height
}

func (s *UploadService) guessContentTypeByMediaType(mediaType string, fileName string) string {
	ext := strings.ToLower(path.Ext(fileName))
	if mediaType == constants.MediaTypeImage {
		switch ext {
		case ".png":
			return "image/png"
		case ".gif":
			return "image/gif"
		case ".webp":
			return "image/webp"
		default:
			return "image/jpeg"
		}
	}
	switch ext {
	case ".mov":
		return "video/quicktime"
	case ".webm":
		return "video/webm"
	case ".ogg":
		return "video/ogg"
	default:
		return "video/mp4"
	}
}

func completion(done int, total int) float64 {
	if total <= 0 {
		return 0
	}
	return float64(done) * 100 / float64(total)
}

func defaultValue(value string, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}

func (s *UploadService) maxAllowedBytes(mediaType string) int64 {
	runtimeCfg := s.manager.Runtime()
	if mediaType == constants.MediaTypeVideo {
		return runtimeCfg.MaxVideoSizeMB * 1024 * 1024
	}
	return runtimeCfg.MaxImageSizeMB * 1024 * 1024
}

func (s *UploadService) maxChunkBytes() int64 {
	return s.manager.Runtime().ChunkSizeMB * 1024 * 1024
}

func (s *UploadService) tryInstantUpload(ctx context.Context, request UploadRequest) (*model.UploadResult, *AppError) {
	if strings.TrimSpace(request.ClientHash) == "" || request.SizeHint <= 0 {
		return nil, nil
	}

	ext := strings.ToLower(path.Ext(utils.SafeFileName(request.OriginalName)))
	if err := s.validateExtensionOnly(request.MediaType, ext); err != nil {
		return nil, err
	}

	existing, err := s.repo.FindFileByHash(ctx, request.ClientHash, request.SizeHint, request.MediaType)
	if err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "find instant upload file failed", err)
	}
	if existing == nil {
		return nil, nil
	}

	now := time.Now()
	task := &model.UploadTask{
		ID:                utils.NewID("task"),
		SourceType:        defaultValue(request.SourceType, constants.TaskSourceSingle),
		MediaType:         request.MediaType,
		FileName:          utils.SafeFileName(request.OriginalName),
		OriginalName:      request.OriginalName,
		ClientFileHash:    request.ClientHash,
		StoredFileID:      existing.ID,
		Status:            constants.TaskStatusSuccess,
		TotalSizeBytes:    existing.SizeBytes,
		UploadedSizeBytes: existing.SizeBytes,
		UploaderID:        request.UploaderID,
		UploaderIP:        request.UploaderIP,
		StartedAt:         &now,
		FinishedAt:        &now,
		CreatedAt:         now,
		UpdatedAt:         now,
	}
	if err := s.repo.SaveTask(ctx, task); err != nil {
		return nil, NewAppError(constants.CodeInternal, http.StatusInternalServerError, "save instant upload task failed", err)
	}

	return mediaFileToUploadResult(task.ID, existing, true, &now), nil
}

func (s *UploadService) Precheck(ctx context.Context, request UploadRequest) (*model.UploadResult, *AppError) {
	if strings.TrimSpace(request.ClientHash) == "" || request.SizeHint <= 0 {
		return nil, NewAppError(constants.CodeBadRequest, http.StatusBadRequest, "clientHash and sizeBytes are required", nil)
	}
	if err := s.ensureUploadAllowed(); err != nil {
		return nil, err
	}
	result, appErr := s.tryInstantUpload(ctx, request)
	if appErr != nil {
		return nil, appErr
	}
	if result == nil {
		return nil, NewAppError(constants.CodeNotFound, http.StatusNotFound, "no cached copy", nil)
	}
	return result, nil
}

func (s *UploadService) validateExtensionOnly(mediaType string, ext string) *AppError {
	runtimeCfg := s.manager.Runtime()
	switch mediaType {
	case constants.MediaTypeImage:
		if ext == "" || !slices.Contains(runtimeCfg.AllowedImageExts, ext) {
			return NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported image extension", nil)
		}
	case constants.MediaTypeVideo:
		if ext == "" || !slices.Contains(runtimeCfg.AllowedVideoExts, ext) {
			return NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported video extension", nil)
		}
	default:
		return NewAppError(constants.CodeValidation, http.StatusBadRequest, "unsupported media type", nil)
	}
	return nil
}

func mediaFileToUploadResult(taskID string, file *model.MediaFile, instant bool, createdAt *time.Time) *model.UploadResult {
	return &model.UploadResult{
		TaskID:        taskID,
		FileID:        file.ID,
		MediaType:     file.MediaType,
		AccessURL:     file.AccessURL,
		CoverURL:      file.CoverURL,
		FileName:      file.FileName,
		OriginalName:  file.OriginalName,
		MIMEType:      file.MIMEType,
		SizeBytes:     file.SizeBytes,
		SHA256:        file.SHA256,
		Width:         file.Width,
		Height:        file.Height,
		DurationSec:   file.DurationSeconds,
		InstantUpload: instant,
		CreatedAt:     createdAt,
	}
}
