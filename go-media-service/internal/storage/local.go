package storage

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/constants"
	"go-media-service/internal/model"
	"go-media-service/internal/utils"

	"github.com/shirou/gopsutil/v4/disk"
)

type LocalStorage struct {
	cfg      *config.Config
	rootDir  string
	imageDir string
	videoDir string
	chunkDir string
	tempDir  string
	dirMode  os.FileMode
	fileMode os.FileMode
}

func NewLocalStorage(cfg *config.Config) (*LocalStorage, error) {
	dirMode, err := strconv.ParseUint(cfg.Storage.DirMode, 8, 32)
	if err != nil {
		return nil, fmt.Errorf("parse storage dir mode: %w", err)
	}
	fileMode, err := strconv.ParseUint(cfg.Storage.FileMode, 8, 32)
	if err != nil {
		return nil, fmt.Errorf("parse storage file mode: %w", err)
	}

	driver := &LocalStorage{
		cfg:      cfg,
		rootDir:  filepath.Clean(cfg.Storage.RootDir),
		imageDir: cfg.Storage.ImageDir,
		videoDir: cfg.Storage.VideoDir,
		chunkDir: cfg.Storage.ChunkDir,
		tempDir:  filepath.Clean(cfg.Storage.TempDir),
		dirMode:  os.FileMode(dirMode),
		fileMode: os.FileMode(fileMode),
	}

	for _, dir := range []string{driver.rootDir, driver.tempDir, filepath.Join(driver.rootDir, driver.imageDir), filepath.Join(driver.rootDir, driver.videoDir), filepath.Join(driver.rootDir, driver.chunkDir)} {
		if err := os.MkdirAll(dir, driver.dirMode); err != nil {
			return nil, err
		}
	}

	return driver, nil
}

func (s *LocalStorage) SaveStream(ctx context.Context, src io.Reader, options SaveOptions) (*StoredObject, error) {
	relativeDir := s.mediaSubDir(options.MediaType)
	absDir := filepath.Join(s.rootDir, relativeDir)
	if err := os.MkdirAll(absDir, s.dirMode); err != nil {
		return nil, err
	}

	fileName := options.FileID + options.FileExt
	relativePath := filepath.ToSlash(filepath.Join(relativeDir, fileName))
	absolutePath := filepath.Join(s.rootDir, filepath.FromSlash(relativePath))

	file, err := os.OpenFile(absolutePath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, s.fileMode)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	size, sha256Value, err := utils.CopyWithHash(ctx, file, src)
	if err != nil {
		_ = os.Remove(absolutePath)
		return nil, err
	}
	_ = file.Sync()

	return &StoredObject{
		RelativePath: relativePath,
		AbsolutePath: absolutePath,
		SizeBytes:    size,
		SHA256:       sha256Value,
	}, nil
}

func (s *LocalStorage) SaveChunk(ctx context.Context, taskID string, index int, src io.Reader) (*ChunkObject, error) {
	chunkDir := filepath.Join(s.rootDir, s.chunkDir, taskID)
	if err := os.MkdirAll(chunkDir, s.dirMode); err != nil {
		return nil, err
	}
	relativePath := filepath.ToSlash(filepath.Join(s.chunkDir, taskID, fmt.Sprintf("%06d.part", index)))
	absolutePath := filepath.Join(s.rootDir, filepath.FromSlash(relativePath))

	file, err := os.OpenFile(absolutePath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, s.fileMode)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	size, sha256Value, err := utils.CopyWithHash(ctx, file, src)
	if err != nil {
		_ = os.Remove(absolutePath)
		return nil, err
	}
	_ = file.Sync()

	return &ChunkObject{
		RelativePath: relativePath,
		AbsolutePath: absolutePath,
		SizeBytes:    size,
		SHA256:       sha256Value,
	}, nil
}

func (s *LocalStorage) MergeChunks(ctx context.Context, taskID string, chunkRelativePaths []string, options SaveOptions) (*StoredObject, error) {
	relativeDir := s.mediaSubDir(options.MediaType)
	absDir := filepath.Join(s.rootDir, relativeDir)
	if err := os.MkdirAll(absDir, s.dirMode); err != nil {
		return nil, err
	}

	fileName := options.FileID + options.FileExt
	relativePath := filepath.ToSlash(filepath.Join(relativeDir, fileName))
	absolutePath := filepath.Join(s.rootDir, filepath.FromSlash(relativePath))

	target, err := os.OpenFile(absolutePath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, s.fileMode)
	if err != nil {
		return nil, err
	}
	defer target.Close()

	size, sha256Value, err := s.mergeIntoTarget(ctx, target, chunkRelativePaths)
	if err != nil {
		_ = os.Remove(absolutePath)
		return nil, err
	}
	_ = target.Sync()

	return &StoredObject{
		RelativePath: relativePath,
		AbsolutePath: absolutePath,
		SizeBytes:    size,
		SHA256:       sha256Value,
	}, nil
}

func (s *LocalStorage) Open(relativePath string) (io.ReadCloser, error) {
	return os.Open(filepath.Join(s.rootDir, filepath.FromSlash(relativePath)))
}

func (s *LocalStorage) Remove(relativePath string) error {
	if relativePath == "" {
		return nil
	}
	if err := os.Remove(filepath.Join(s.rootDir, filepath.FromSlash(relativePath))); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func (s *LocalStorage) RemoveChunks(taskID string) error {
	return os.RemoveAll(filepath.Join(s.rootDir, s.chunkDir, taskID))
}

func (s *LocalStorage) DiskUsage(_ context.Context) (*model.DiskUsage, error) {
	stats, err := disk.Usage(s.rootDir)
	if err != nil {
		return nil, err
	}

	return &model.DiskUsage{
		RootDir:     s.rootDir,
		TotalBytes:  stats.Total,
		UsedBytes:   stats.Used,
		FreeBytes:   stats.Free,
		UsedPercent: stats.UsedPercent,
	}, nil
}

func (s *LocalStorage) mediaSubDir(mediaType string) string {
	datePath := time.Now().Format(s.cfg.Storage.DatePathLayout)
	if mediaType == constants.MediaTypeVideo {
		return filepath.ToSlash(filepath.Join(s.videoDir, datePath))
	}
	return filepath.ToSlash(filepath.Join(s.imageDir, datePath))
}

func (s *LocalStorage) mergeIntoTarget(ctx context.Context, dst io.Writer, chunkRelativePaths []string) (int64, string, error) {
	hasher := sha256.New()
	writer := io.MultiWriter(dst, hasher)
	bufferPtr := utils.CopyBuffer()
	defer utils.PutCopyBuffer(bufferPtr)

	buffer := *bufferPtr
	var total int64
	for _, relativePath := range chunkRelativePaths {
		select {
		case <-ctx.Done():
			return total, "", ctx.Err()
		default:
		}

		chunkFile, err := os.Open(filepath.Join(s.rootDir, filepath.FromSlash(relativePath)))
		if err != nil {
			return total, "", err
		}

		for {
			n, readErr := chunkFile.Read(buffer)
			if n > 0 {
				if _, err := writer.Write(buffer[:n]); err != nil {
					_ = chunkFile.Close()
					return total, "", err
				}
				total += int64(n)
			}
			if readErr == io.EOF {
				break
			}
			if readErr != nil {
				_ = chunkFile.Close()
				return total, "", readErr
			}
		}

		if err := chunkFile.Close(); err != nil {
			return total, "", err
		}
	}

	return total, hex.EncodeToString(hasher.Sum(nil)), nil
}
