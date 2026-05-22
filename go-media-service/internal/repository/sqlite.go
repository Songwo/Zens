package repository

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"go-media-service/internal/constants"
	"go-media-service/internal/model"

	_ "modernc.org/sqlite"
)

type SQLiteRepository struct {
	db *sql.DB
}

func NewSQLiteRepository(db *sql.DB) (*SQLiteRepository, error) {
	repo := &SQLiteRepository{db: db}
	if err := repo.bootstrap(); err != nil {
		return nil, err
	}
	return repo, nil
}

func (r *SQLiteRepository) bootstrap() error {
	pragmas := []string{
		`PRAGMA journal_mode = WAL;`,
		`PRAGMA synchronous = NORMAL;`,
		`PRAGMA busy_timeout = 5000;`,
		`PRAGMA foreign_keys = ON;`,
	}
	for _, statement := range pragmas {
		if _, err := r.db.Exec(statement); err != nil {
			return err
		}
	}

	schema := []string{
		`CREATE TABLE IF NOT EXISTS media_files (
			id TEXT PRIMARY KEY,
			file_name TEXT NOT NULL,
			original_name TEXT NOT NULL,
			ext TEXT NOT NULL,
			media_type TEXT NOT NULL,
			mime_type TEXT NOT NULL,
			storage_driver TEXT NOT NULL,
			storage_path TEXT NOT NULL,
			access_url TEXT NOT NULL,
			cover_url TEXT DEFAULT '',
			size_bytes INTEGER NOT NULL DEFAULT 0,
			sha256 TEXT DEFAULT '',
			width INTEGER NOT NULL DEFAULT 0,
			height INTEGER NOT NULL DEFAULT 0,
			duration_seconds INTEGER NOT NULL DEFAULT 0,
			status TEXT NOT NULL,
			source TEXT NOT NULL,
			uploader_id TEXT DEFAULT '',
			uploader_ip TEXT DEFAULT '',
			biz_type TEXT DEFAULT '',
			biz_id TEXT DEFAULT '',
			sort_order INTEGER NOT NULL DEFAULT 0,
			created_at DATETIME NOT NULL,
			updated_at DATETIME NOT NULL,
			deleted_at DATETIME NULL
		);`,
		`CREATE INDEX IF NOT EXISTS idx_media_files_type_status_created_at ON media_files (media_type, status, created_at DESC);`,
		`CREATE INDEX IF NOT EXISTS idx_media_files_sha256 ON media_files (sha256);`,
		`CREATE INDEX IF NOT EXISTS idx_media_files_biz ON media_files (biz_type, biz_id);`,
		`CREATE TABLE IF NOT EXISTS upload_tasks (
			id TEXT PRIMARY KEY,
			source_type TEXT NOT NULL,
			media_type TEXT NOT NULL,
			file_name TEXT NOT NULL,
			original_name TEXT NOT NULL,
			client_file_hash TEXT DEFAULT '',
			stored_file_id TEXT DEFAULT '',
			status TEXT NOT NULL,
			total_size_bytes INTEGER NOT NULL DEFAULT 0,
			uploaded_size_bytes INTEGER NOT NULL DEFAULT 0,
			total_chunks INTEGER NOT NULL DEFAULT 0,
			uploaded_chunks INTEGER NOT NULL DEFAULT 0,
			error_message TEXT DEFAULT '',
			uploader_id TEXT DEFAULT '',
			uploader_ip TEXT DEFAULT '',
			started_at DATETIME NULL,
			finished_at DATETIME NULL,
			created_at DATETIME NOT NULL,
			updated_at DATETIME NOT NULL
		);`,
		`CREATE INDEX IF NOT EXISTS idx_upload_tasks_status_created_at ON upload_tasks (status, created_at DESC);`,
		`CREATE INDEX IF NOT EXISTS idx_upload_tasks_media_type ON upload_tasks (media_type);`,
		`CREATE TABLE IF NOT EXISTS upload_chunks (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			task_id TEXT NOT NULL,
			chunk_index INTEGER NOT NULL,
			chunk_size_bytes INTEGER NOT NULL DEFAULT 0,
			chunk_path TEXT NOT NULL,
			status TEXT NOT NULL,
			sha256 TEXT DEFAULT '',
			created_at DATETIME NOT NULL,
			updated_at DATETIME NOT NULL,
			UNIQUE(task_id, chunk_index),
			FOREIGN KEY(task_id) REFERENCES upload_tasks(id) ON DELETE CASCADE
		);`,
		`CREATE INDEX IF NOT EXISTS idx_upload_chunks_task_id ON upload_chunks (task_id, chunk_index ASC);`,
		`CREATE TABLE IF NOT EXISTS runtime_configs (
			config_key TEXT PRIMARY KEY,
			config_value TEXT NOT NULL,
			updated_by TEXT DEFAULT '',
			updated_at DATETIME NOT NULL
		);`,
		`CREATE TABLE IF NOT EXISTS audit_logs (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			actor TEXT NOT NULL,
			action TEXT NOT NULL,
			target_type TEXT NOT NULL,
			target_id TEXT NOT NULL,
			detail TEXT NOT NULL,
			created_at DATETIME NOT NULL
		);`,
	}

	for _, statement := range schema {
		if _, err := r.db.Exec(statement); err != nil {
			return err
		}
	}
	return nil
}

func (r *SQLiteRepository) SaveFile(ctx context.Context, file *model.MediaFile) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO media_files (
			id, file_name, original_name, ext, media_type, mime_type, storage_driver, storage_path, access_url,
			cover_url, size_bytes, sha256, width, height, duration_seconds, status, source, uploader_id, uploader_ip,
			biz_type, biz_id, sort_order, created_at, updated_at, deleted_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, file.ID, file.FileName, file.OriginalName, file.Ext, file.MediaType, file.MIMEType, file.StorageDriver, file.StoragePath,
		file.AccessURL, file.CoverURL, file.SizeBytes, file.SHA256, file.Width, file.Height, file.DurationSeconds, file.Status,
		file.Source, file.UploaderID, file.UploaderIP, file.BizType, file.BizID, file.SortOrder, file.CreatedAt, file.UpdatedAt,
		nullTime(file.DeletedAt))
	return err
}

func (r *SQLiteRepository) FindFileByHash(ctx context.Context, hash string, size int64, mediaType string) (*model.MediaFile, error) {
	if hash == "" {
		return nil, nil
	}
	row := r.db.QueryRowContext(ctx, `
		SELECT id, file_name, original_name, ext, media_type, mime_type, storage_driver, storage_path, access_url,
		       cover_url, size_bytes, sha256, width, height, duration_seconds, status, source, uploader_id,
		       uploader_ip, biz_type, biz_id, sort_order, created_at, updated_at, deleted_at
		  FROM media_files
		 WHERE sha256 = ? AND size_bytes = ? AND media_type = ? AND status = ?
		 ORDER BY created_at DESC
		 LIMIT 1
	`, hash, size, mediaType, constants.FileStatusActive)
	file, err := scanMediaFile(row)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return file, err
}

func (r *SQLiteRepository) GetFileByID(ctx context.Context, id string) (*model.MediaFile, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id, file_name, original_name, ext, media_type, mime_type, storage_driver, storage_path, access_url,
		       cover_url, size_bytes, sha256, width, height, duration_seconds, status, source, uploader_id,
		       uploader_ip, biz_type, biz_id, sort_order, created_at, updated_at, deleted_at
		  FROM media_files
		 WHERE id = ?
		 LIMIT 1
	`, id)
	return scanMediaFile(row)
}

func (r *SQLiteRepository) GetFilesByIDs(ctx context.Context, ids []string) ([]model.MediaFile, error) {
	if len(ids) == 0 {
		return []model.MediaFile{}, nil
	}
	query, args := inQuery(`
		SELECT id, file_name, original_name, ext, media_type, mime_type, storage_driver, storage_path, access_url,
		       cover_url, size_bytes, sha256, width, height, duration_seconds, status, source, uploader_id,
		       uploader_ip, biz_type, biz_id, sort_order, created_at, updated_at, deleted_at
		  FROM media_files
		 WHERE id IN (%s)
	`, ids)
	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanMediaFiles(rows)
}

func (r *SQLiteRepository) ListFiles(ctx context.Context, filter model.FileListFilter) ([]model.MediaFile, int64, error) {
	page, pageSize := normalizePage(filter.Page, filter.PageSize)
	where := []string{"1 = 1"}
	args := make([]any, 0)

	if filter.MediaType != "" {
		where = append(where, "media_type = ?")
		args = append(args, filter.MediaType)
	}
	if filter.Status != "" {
		where = append(where, "status = ?")
		args = append(args, filter.Status)
	}
	if filter.Keyword != "" {
		where = append(where, "(original_name LIKE ? OR file_name LIKE ? OR uploader_id LIKE ?)")
		keyword := "%" + filter.Keyword + "%"
		args = append(args, keyword, keyword, keyword)
	}
	if filter.BizType != "" {
		where = append(where, "biz_type = ?")
		args = append(args, filter.BizType)
	}
	if filter.BizID != "" {
		where = append(where, "biz_id = ?")
		args = append(args, filter.BizID)
	}
	if filter.StartAt != "" {
		where = append(where, "created_at >= ?")
		args = append(args, filter.StartAt)
	}
	if filter.EndAt != "" {
		where = append(where, "created_at <= ?")
		args = append(args, filter.EndAt)
	}

	whereSQL := strings.Join(where, " AND ")
	totalQuery := "SELECT COUNT(1) FROM media_files WHERE " + whereSQL
	var total int64
	if err := r.db.QueryRowContext(ctx, totalQuery, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	sortBy := allowSort(filter.SortBy, map[string]struct{}{
		"created_at": {},
		"size_bytes": {},
		"media_type": {},
		"status":     {},
	}, "created_at")
	sortOrder := allowSortOrder(filter.SortOrder)

	query := fmt.Sprintf(`
		SELECT id, file_name, original_name, ext, media_type, mime_type, storage_driver, storage_path, access_url,
		       cover_url, size_bytes, sha256, width, height, duration_seconds, status, source, uploader_id,
		       uploader_ip, biz_type, biz_id, sort_order, created_at, updated_at, deleted_at
		  FROM media_files
		 WHERE %s
		 ORDER BY %s %s
		 LIMIT ? OFFSET ?
	`, whereSQL, sortBy, sortOrder)
	args = append(args, pageSize, (page-1)*pageSize)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	items, err := scanMediaFiles(rows)
	return items, total, err
}

func (r *SQLiteRepository) SoftDeleteFiles(ctx context.Context, ids []string, deletedAt time.Time) error {
	if len(ids) == 0 {
		return nil
	}
	query, args := inQuery("UPDATE media_files SET status = ?, deleted_at = ?, updated_at = ? WHERE id IN (%s)", ids)
	args = append([]any{constants.FileStatusDeleted, deletedAt, deletedAt}, args...)
	_, err := r.db.ExecContext(ctx, query, args...)
	return err
}

func (r *SQLiteRepository) SaveTask(ctx context.Context, task *model.UploadTask) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO upload_tasks (
			id, source_type, media_type, file_name, original_name, client_file_hash, stored_file_id, status,
			total_size_bytes, uploaded_size_bytes, total_chunks, uploaded_chunks, error_message, uploader_id, uploader_ip,
			started_at, finished_at, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(id) DO UPDATE SET
			source_type = excluded.source_type,
			media_type = excluded.media_type,
			file_name = excluded.file_name,
			original_name = excluded.original_name,
			client_file_hash = excluded.client_file_hash,
			stored_file_id = excluded.stored_file_id,
			status = excluded.status,
			total_size_bytes = excluded.total_size_bytes,
			uploaded_size_bytes = excluded.uploaded_size_bytes,
			total_chunks = excluded.total_chunks,
			uploaded_chunks = excluded.uploaded_chunks,
			error_message = excluded.error_message,
			uploader_id = excluded.uploader_id,
			uploader_ip = excluded.uploader_ip,
			started_at = excluded.started_at,
			finished_at = excluded.finished_at,
			updated_at = excluded.updated_at
	`, task.ID, task.SourceType, task.MediaType, task.FileName, task.OriginalName, task.ClientFileHash, task.StoredFileID, task.Status,
		task.TotalSizeBytes, task.UploadedSizeBytes, task.TotalChunks, task.UploadedChunks, task.ErrorMessage, task.UploaderID,
		task.UploaderIP, nullTime(task.StartedAt), nullTime(task.FinishedAt), task.CreatedAt, task.UpdatedAt)
	return err
}

func (r *SQLiteRepository) GetTaskByID(ctx context.Context, id string) (*model.UploadTask, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id, source_type, media_type, file_name, original_name, client_file_hash, stored_file_id, status,
		       total_size_bytes, uploaded_size_bytes, total_chunks, uploaded_chunks, error_message, uploader_id,
		       uploader_ip, started_at, finished_at, created_at, updated_at
		  FROM upload_tasks
		 WHERE id = ?
		 LIMIT 1
	`, id)
	return scanTask(row)
}

func (r *SQLiteRepository) ListTasks(ctx context.Context, filter model.ListQuery) ([]model.UploadTask, int64, error) {
	page, pageSize := normalizePage(filter.Page, filter.PageSize)
	where := []string{"1 = 1"}
	args := make([]any, 0)

	if filter.MediaType != "" {
		where = append(where, "media_type = ?")
		args = append(args, filter.MediaType)
	}
	if filter.Status != "" {
		where = append(where, "status = ?")
		args = append(args, filter.Status)
	}
	if filter.Keyword != "" {
		where = append(where, "(original_name LIKE ? OR file_name LIKE ? OR uploader_id LIKE ?)")
		keyword := "%" + filter.Keyword + "%"
		args = append(args, keyword, keyword, keyword)
	}
	if filter.StartAt != "" {
		where = append(where, "created_at >= ?")
		args = append(args, filter.StartAt)
	}
	if filter.EndAt != "" {
		where = append(where, "created_at <= ?")
		args = append(args, filter.EndAt)
	}

	whereSQL := strings.Join(where, " AND ")
	totalQuery := "SELECT COUNT(1) FROM upload_tasks WHERE " + whereSQL
	var total int64
	if err := r.db.QueryRowContext(ctx, totalQuery, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	sortBy := allowSort(filter.SortBy, map[string]struct{}{
		"created_at":          {},
		"status":              {},
		"uploaded_size_bytes": {},
	}, "created_at")
	sortOrder := allowSortOrder(filter.SortOrder)
	query := fmt.Sprintf(`
		SELECT id, source_type, media_type, file_name, original_name, client_file_hash, stored_file_id, status,
		       total_size_bytes, uploaded_size_bytes, total_chunks, uploaded_chunks, error_message, uploader_id,
		       uploader_ip, started_at, finished_at, created_at, updated_at
		  FROM upload_tasks
		 WHERE %s
		 ORDER BY %s %s
		 LIMIT ? OFFSET ?
	`, whereSQL, sortBy, sortOrder)
	args = append(args, pageSize, (page-1)*pageSize)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	items, err := scanTasks(rows)
	return items, total, err
}

func (r *SQLiteRepository) SaveChunk(ctx context.Context, chunk *model.UploadChunk) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO upload_chunks (
			task_id, chunk_index, chunk_size_bytes, chunk_path, status, sha256, created_at, updated_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(task_id, chunk_index) DO UPDATE SET
			chunk_size_bytes = excluded.chunk_size_bytes,
			chunk_path = excluded.chunk_path,
			status = excluded.status,
			sha256 = excluded.sha256,
			updated_at = excluded.updated_at
	`, chunk.TaskID, chunk.ChunkIdx, chunk.ChunkSize, chunk.ChunkPath, chunk.Status, chunk.SHA256, chunk.CreatedAt, chunk.UpdatedAt)
	return err
}

func (r *SQLiteRepository) ListChunksByTaskID(ctx context.Context, taskID string) ([]model.UploadChunk, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, task_id, chunk_index, chunk_size_bytes, chunk_path, status, sha256, created_at, updated_at
		  FROM upload_chunks
		 WHERE task_id = ?
		 ORDER BY chunk_index ASC
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanChunks(rows)
}

func (r *SQLiteRepository) DeleteChunksByTaskID(ctx context.Context, taskID string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM upload_chunks WHERE task_id = ?`, taskID)
	return err
}

func (r *SQLiteRepository) LoadRuntimeConfigValues(ctx context.Context) (map[string]string, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT config_key, config_value FROM runtime_configs`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make(map[string]string)
	for rows.Next() {
		var key string
		var value string
		if err := rows.Scan(&key, &value); err != nil {
			return nil, err
		}
		result[key] = value
	}
	return result, rows.Err()
}

func (r *SQLiteRepository) SaveRuntimeConfigValues(ctx context.Context, values map[string]string, actor string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	now := time.Now()
	for key, value := range values {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO runtime_configs (config_key, config_value, updated_by, updated_at)
			VALUES (?, ?, ?, ?)
			ON CONFLICT(config_key) DO UPDATE SET
				config_value = excluded.config_value,
				updated_by = excluded.updated_by,
				updated_at = excluded.updated_at
		`, key, value, actor, now); err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *SQLiteRepository) AppendAuditLog(ctx context.Context, logEntry *model.AuditLog) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO audit_logs (actor, action, target_type, target_id, detail, created_at)
		VALUES (?, ?, ?, ?, ?, ?)
	`, logEntry.Actor, logEntry.Action, logEntry.TargetType, logEntry.TargetID, logEntry.Detail, logEntry.CreatedAt)
	return err
}

func (r *SQLiteRepository) SummaryStats(ctx context.Context) (*model.SummaryStats, error) {
	stats := &model.SummaryStats{}
	err := r.db.QueryRowContext(ctx, `
		SELECT
			COALESCE(SUM(CASE WHEN media_type = 'image' AND status = 'active' THEN 1 ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN media_type = 'video' AND status = 'active' THEN 1 ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN status = 'active' THEN size_bytes ELSE 0 END), 0)
		  FROM media_files
	`).Scan(&stats.ImageCount, &stats.VideoCount, &stats.FileTotalSizeBytes)
	if err != nil {
		return nil, err
	}

	if err := r.db.QueryRowContext(ctx, `
		SELECT
			COALESCE(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END), 0),
			COALESCE(SUM(CASE WHEN status IN ('uploading', 'merging') THEN 1 ELSE 0 END), 0)
		  FROM upload_tasks
	`).Scan(&stats.UploadSuccessCount, &stats.UploadFailureCount, &stats.ChunkInProgressCount); err != nil {
		return nil, err
	}
	return stats, nil
}

func (r *SQLiteRepository) ListRecentUploads(ctx context.Context, limit int) ([]model.UploadTask, error) {
	if limit <= 0 {
		limit = 10
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, source_type, media_type, file_name, original_name, client_file_hash, stored_file_id, status,
		       total_size_bytes, uploaded_size_bytes, total_chunks, uploaded_chunks, error_message, uploader_id,
		       uploader_ip, started_at, finished_at, created_at, updated_at
		  FROM upload_tasks
		 ORDER BY created_at DESC
		 LIMIT ?
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanTasks(rows)
}

func (r *SQLiteRepository) ListExpiredChunkTasks(ctx context.Context, before time.Time) ([]model.UploadTask, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT id, source_type, media_type, file_name, original_name, client_file_hash, stored_file_id, status,
		       total_size_bytes, uploaded_size_bytes, total_chunks, uploaded_chunks, error_message, uploader_id,
		       uploader_ip, started_at, finished_at, created_at, updated_at
		  FROM upload_tasks
		 WHERE source_type = ?
		   AND status IN (?, ?)
		   AND updated_at < ?
		 ORDER BY updated_at ASC
	`, constants.TaskSourceChunk, constants.TaskStatusUploading, constants.TaskStatusMerging, before)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanTasks(rows)
}

func scanMediaFile(scanner interface{ Scan(dest ...any) error }) (*model.MediaFile, error) {
	var file model.MediaFile
	var deletedAt sql.NullTime
	err := scanner.Scan(
		&file.ID, &file.FileName, &file.OriginalName, &file.Ext, &file.MediaType, &file.MIMEType, &file.StorageDriver,
		&file.StoragePath, &file.AccessURL, &file.CoverURL, &file.SizeBytes, &file.SHA256, &file.Width, &file.Height,
		&file.DurationSeconds, &file.Status, &file.Source, &file.UploaderID, &file.UploaderIP, &file.BizType, &file.BizID,
		&file.SortOrder, &file.CreatedAt, &file.UpdatedAt, &deletedAt,
	)
	if err != nil {
		return nil, err
	}
	if deletedAt.Valid {
		file.DeletedAt = &deletedAt.Time
	}
	return &file, nil
}

func scanMediaFiles(rows *sql.Rows) ([]model.MediaFile, error) {
	items := make([]model.MediaFile, 0)
	for rows.Next() {
		item, err := scanMediaFile(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, *item)
	}
	return items, rows.Err()
}

func scanTask(scanner interface{ Scan(dest ...any) error }) (*model.UploadTask, error) {
	var task model.UploadTask
	var startedAt sql.NullTime
	var finishedAt sql.NullTime
	err := scanner.Scan(
		&task.ID, &task.SourceType, &task.MediaType, &task.FileName, &task.OriginalName, &task.ClientFileHash,
		&task.StoredFileID, &task.Status, &task.TotalSizeBytes, &task.UploadedSizeBytes, &task.TotalChunks,
		&task.UploadedChunks, &task.ErrorMessage, &task.UploaderID, &task.UploaderIP, &startedAt, &finishedAt,
		&task.CreatedAt, &task.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	if startedAt.Valid {
		task.StartedAt = &startedAt.Time
	}
	if finishedAt.Valid {
		task.FinishedAt = &finishedAt.Time
	}
	return &task, nil
}

func scanTasks(rows *sql.Rows) ([]model.UploadTask, error) {
	items := make([]model.UploadTask, 0)
	for rows.Next() {
		item, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, *item)
	}
	return items, rows.Err()
}

func scanChunks(rows *sql.Rows) ([]model.UploadChunk, error) {
	items := make([]model.UploadChunk, 0)
	for rows.Next() {
		var item model.UploadChunk
		if err := rows.Scan(
			&item.ID, &item.TaskID, &item.ChunkIdx, &item.ChunkSize, &item.ChunkPath, &item.Status, &item.SHA256,
			&item.CreatedAt, &item.UpdatedAt,
		); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func inQuery(pattern string, ids []string) (string, []any) {
	holders := make([]string, len(ids))
	args := make([]any, len(ids))
	for idx, id := range ids {
		holders[idx] = "?"
		args[idx] = id
	}
	return fmt.Sprintf(pattern, strings.Join(holders, ",")), args
}

func normalizePage(page int, pageSize int) (int, int) {
	if page <= 0 {
		page = 1
	}
	if pageSize <= 0 {
		pageSize = 20
	}
	if pageSize > 200 {
		pageSize = 200
	}
	return page, pageSize
}

func allowSort(value string, whitelist map[string]struct{}, defaultValue string) string {
	if _, ok := whitelist[value]; ok {
		return value
	}
	return defaultValue
}

func allowSortOrder(value string) string {
	if strings.EqualFold(value, "asc") {
		return "ASC"
	}
	return "DESC"
}

func nullTime(value *time.Time) any {
	if value == nil {
		return nil
	}
	return *value
}
