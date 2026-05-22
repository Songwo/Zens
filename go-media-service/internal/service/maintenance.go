package service

import (
	"context"
	"time"

	"go-media-service/internal/config"
	"go-media-service/internal/constants"
	"go-media-service/internal/repository"
	"go-media-service/internal/storage"

	"github.com/rs/zerolog/log"
)

type MaintenanceService struct {
	manager *config.Manager
	repo    *repository.SQLiteRepository
	storage storage.Driver
}

func NewMaintenanceService(manager *config.Manager, repo *repository.SQLiteRepository, storageDriver storage.Driver) *MaintenanceService {
	return &MaintenanceService{
		manager: manager,
		repo:    repo,
		storage: storageDriver,
	}
}

func (s *MaintenanceService) Run(ctx context.Context) {
	if err := s.cleanupExpiredChunks(ctx); err != nil {
		log.Warn().Err(err).Msg("initial expired chunk cleanup failed")
	}

	ticker := time.NewTicker(15 * time.Minute)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			runCtx, cancel := context.WithTimeout(ctx, 2*time.Minute)
			if err := s.cleanupExpiredChunks(runCtx); err != nil {
				log.Warn().Err(err).Msg("scheduled expired chunk cleanup failed")
			}
			cancel()
		}
	}
}

func (s *MaintenanceService) cleanupExpiredChunks(ctx context.Context) error {
	expireHours := s.manager.Runtime().ChunkExpireHours
	if expireHours <= 0 {
		return nil
	}

	expiredBefore := time.Now().Add(-time.Duration(expireHours) * time.Hour)
	tasks, err := s.repo.ListExpiredChunkTasks(ctx, expiredBefore)
	if err != nil {
		return err
	}
	if len(tasks) == 0 {
		return nil
	}

	now := time.Now()
	for _, task := range tasks {
		task.Status = constants.TaskStatusFailed
		task.ErrorMessage = "chunk upload expired and cleaned"
		task.FinishedAt = &now
		task.UpdatedAt = now

		if err := s.repo.SaveTask(ctx, &task); err != nil {
			return err
		}
		if err := s.repo.DeleteChunksByTaskID(ctx, task.ID); err != nil {
			return err
		}
		if err := s.storage.RemoveChunks(task.ID); err != nil {
			return err
		}
	}

	log.Info().
		Int("expired_task_count", len(tasks)).
		Int("chunk_expire_hours", expireHours).
		Msg("expired chunk tasks cleaned")
	return nil
}
