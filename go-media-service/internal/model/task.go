package model

import "time"

type UploadTask struct {
	ID                string     `json:"id"`
	SourceType        string     `json:"sourceType"`
	MediaType         string     `json:"mediaType"`
	FileName          string     `json:"fileName"`
	OriginalName      string     `json:"originalName"`
	ClientFileHash    string     `json:"clientFileHash,omitempty"`
	StoredFileID      string     `json:"storedFileId,omitempty"`
	Status            string     `json:"status"`
	TotalSizeBytes    int64      `json:"totalSizeBytes"`
	UploadedSizeBytes int64      `json:"uploadedSizeBytes"`
	TotalChunks       int        `json:"totalChunks"`
	UploadedChunks    int        `json:"uploadedChunks"`
	ErrorMessage      string     `json:"errorMessage,omitempty"`
	UploaderID        string     `json:"uploaderId,omitempty"`
	UploaderIP        string     `json:"uploaderIp,omitempty"`
	StartedAt         *time.Time `json:"startedAt,omitempty"`
	FinishedAt        *time.Time `json:"finishedAt,omitempty"`
	CreatedAt         time.Time  `json:"createdAt"`
	UpdatedAt         time.Time  `json:"updatedAt"`
}

type UploadChunk struct {
	ID        int64     `json:"id"`
	TaskID    string    `json:"taskId"`
	ChunkIdx  int       `json:"chunkIndex"`
	ChunkSize int64     `json:"chunkSizeBytes"`
	ChunkPath string    `json:"chunkPath"`
	Status    string    `json:"status"`
	SHA256    string    `json:"sha256,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type TaskStatusDetail struct {
	Task               *UploadTask   `json:"task"`
	Chunks             []UploadChunk `json:"chunks,omitempty"`
	UploadedChunkIndex []int         `json:"uploadedChunkIndexes,omitempty"`
	CompletionPercent  float64       `json:"completionPercent"`
}
