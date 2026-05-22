package storage

import (
	"context"
	"io"

	"go-media-service/internal/model"
)

type SaveOptions struct {
	MediaType    string
	FileID       string
	OriginalName string
	FileExt      string
}

type StoredObject struct {
	RelativePath string
	AbsolutePath string
	SizeBytes    int64
	SHA256       string
}

type ChunkObject struct {
	RelativePath string
	AbsolutePath string
	SizeBytes    int64
	SHA256       string
}

type Driver interface {
	SaveStream(ctx context.Context, src io.Reader, options SaveOptions) (*StoredObject, error)
	SaveChunk(ctx context.Context, taskID string, index int, src io.Reader) (*ChunkObject, error)
	MergeChunks(ctx context.Context, taskID string, chunkRelativePaths []string, options SaveOptions) (*StoredObject, error)
	Open(relativePath string) (io.ReadCloser, error)
	Remove(relativePath string) error
	RemoveChunks(taskID string) error
	DiskUsage(ctx context.Context) (*model.DiskUsage, error)
}
